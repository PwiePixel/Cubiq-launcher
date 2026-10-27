//! Kryptografie des Cubiq-Link v2: HMAC-SHA256-Beweise für den gegenseitigen
//! Handschlag und das Versiegeln des Minecraft-Tokens auf dem Weg ins Spiel.
//!
//! - Beweis des Launchers: `HMAC(K, "cubiq-link/2/launcher\n" + sid + "\n" + nc + "\n" + nl)`
//! - Beweis des Spiels: `HMAC(K, "cubiq-link/2/game\n" + sid + "\n" + nc + "\n" + nl)`
//! - Siegel-Schlüssel: `SK = HMAC(K, "cubiq-link/2/seal\n" + nc + "\n" + nl)`
//! - Siegel: Schlüsselstrom `HMAC(SK, "cubiq-link/2/stream\n" + hex(n) + "\n" + i)`, XOR,
//!   Prüfsumme `HMAC(SK, "cubiq-link/2/tag\n" + hex(n) + "\n" + hex(C))` →
//!   `hex(n) + "." + hex(C) + "." + hex(tag)`.
//!
//! Der Schlüssel `K` selbst geht nie über die Leitung.

use hmac::{Hmac, KeyInit, Mac};
use sha2::Sha256;

pub const KEY_LEN: usize = 32;
pub const NONCE_HEX_LEN: usize = 32;
pub const SID_HEX_LEN: usize = 16;

pub fn hmac(key: &[u8], message: &str) -> [u8; 32] {
    let mut mac = <Hmac<Sha256> as KeyInit>::new_from_slice(key).expect("HMAC nimmt jede Schlüssellänge");
    mac.update(message.as_bytes());
    let out = mac.finalize().into_bytes();
    let mut bytes = [0u8; 32];
    bytes.copy_from_slice(&out);
    bytes
}

pub fn hex(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push(DIGITS[(b >> 4) as usize] as char);
        s.push(DIGITS[(b & 15) as usize] as char);
    }
    s
}

/// Nur Kleinbuchstaben-Hex (so schreibt es die Mod).
pub fn unhex(s: &str) -> Option<Vec<u8>> {
    if !s.len().is_multiple_of(2) {
        return None;
    }
    let digit = |c: u8| match c {
        b'0'..=b'9' => Some(c - b'0'),
        b'a'..=b'f' => Some(c - b'a' + 10),
        _ => None,
    };
    s.as_bytes().chunks(2).map(|p| Some(digit(p[0])? << 4 | digit(p[1])?)).collect()
}

pub fn is_hex(s: &str, len: usize) -> bool {
    s.len() == len && s.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

/// Vergleich mit konstanter Laufzeit (die Länge ist kein Geheimnis).
pub fn ct_eq(a: &[u8], b: &[u8]) -> bool {
    a.len() == b.len() && a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

/// Zufallsbytes aus dem Zufallsgenerator des Systems (über `uuid` v4 → getrandom).
pub fn random_bytes(n: usize) -> Vec<u8> {
    let mut out = Vec::with_capacity(n + 16);
    while out.len() < n {
        out.extend_from_slice(uuid::Uuid::new_v4().as_bytes());
    }
    out.truncate(n);
    out
}

pub fn launcher_proof(key: &[u8], sid: &str, nc: &str, nl: &str) -> [u8; 32] {
    hmac(key, &format!("cubiq-link/2/launcher\n{sid}\n{nc}\n{nl}"))
}

pub fn game_proof(key: &[u8], sid: &str, nc: &str, nl: &str) -> [u8; 32] {
    hmac(key, &format!("cubiq-link/2/game\n{sid}\n{nc}\n{nl}"))
}

pub fn seal_key(key: &[u8], nc: &str, nl: &str) -> [u8; 32] {
    hmac(key, &format!("cubiq-link/2/seal\n{nc}\n{nl}"))
}

fn keystream_xor(sk: &[u8], nh: &str, data: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(data.len());
    for (i, chunk) in data.chunks(32).enumerate() {
        let block = hmac(sk, &format!("cubiq-link/2/stream\n{nh}\n{i}"));
        out.extend(chunk.iter().zip(block.iter()).map(|(a, b)| a ^ b));
    }
    out
}

/// Versiegelt `plain` mit einer festen Nonce (für Tests; sonst [`seal`]).
pub fn seal_with_nonce(sk: &[u8], nonce: &[u8], plain: &[u8]) -> String {
    let nh = hex(nonce);
    let cipher = hex(&keystream_xor(sk, &nh, plain));
    let tag = hmac(sk, &format!("cubiq-link/2/tag\n{nh}\n{cipher}"));
    format!("{nh}.{cipher}.{}", hex(&tag))
}

pub fn seal(sk: &[u8], plain: &[u8]) -> String {
    seal_with_nonce(sk, &random_bytes(16), plain)
}

/// Gegenstück (nur die Mod braucht es wirklich; hier für die Tests).
pub fn unseal(sk: &[u8], sealed: &str) -> Option<Vec<u8>> {
    let mut parts = sealed.split('.');
    let (nh, cipher, tag) = (parts.next()?, parts.next()?, parts.next()?);
    if parts.next().is_some() || !is_hex(nh, NONCE_HEX_LEN) {
        return None;
    }
    let expected = hmac(sk, &format!("cubiq-link/2/tag\n{nh}\n{cipher}"));
    if !ct_eq(&expected, &unhex(tag)?) {
        return None;
    }
    Some(keystream_xor(sk, nh, &unhex(cipher)?))
}

#[cfg(test)]
mod tests {
    use super::*;

    const SID: &str = "0123456789abcdef";
    const NC: &str = "00112233445566778899aabbccddeeff";
    const NL: &str = "ffeeddccbbaa99887766554433221100";

    fn key() -> Vec<u8> {
        (0u8..32).collect()
    }

    /// Gemeinsamer Testvektor mit der Mod (Java: `LinkCryptoTest`).
    #[test]
    fn testvektor_stimmt_mit_der_mod_ueberein() {
        let k = key();
        assert_eq!(hex(&launcher_proof(&k, SID, NC, NL)), "991f1e4237c9d0bf36d007ea95bb61fc10b58df4b54d7ee99bf359e737d86d42");
        assert_eq!(hex(&game_proof(&k, SID, NC, NL)), "74d8da2a2a4be6655c3839cc07ec0962a2195053b84c47f4e0b67912347e62f9");
        let sk = seal_key(&k, NC, NL);
        assert_eq!(hex(&sk), "f6cc2d52a5afa6082ede8265df4e8b57fb50b197173b07b3ea2c9282148a3be5");
        let nonce = unhex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf").unwrap();
        let plain = format!("token-{}", "x".repeat(60));
        let sealed = seal_with_nonce(&sk, &nonce, plain.as_bytes());
        assert_eq!(
            sealed,
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf.1005d887d9fe3b7465beb7a87447311928f9a8e6e985dfb7c23af80b80d240b687c8336cea59940c674dd2b2bc232c5700f5d0b25a444bb74a154c487a53a13aad0e.4118a14dc743789c9a7b4760eddd0043bcd99b03545ba69af92730526b3ca1f9"
        );
        assert_eq!(unseal(&sk, &sealed).unwrap(), plain.as_bytes());
    }

    #[test]
    fn siegel_erkennt_manipulation() {
        let sk = seal_key(&key(), NC, NL);
        let sealed = seal(&sk, b"geheim");
        assert_eq!(unseal(&sk, &sealed).unwrap(), b"geheim");
        assert_ne!(seal(&sk, b"geheim"), sealed, "jede Versiegelung hat eine eigene Nonce");
        let mut bad = sealed.clone().into_bytes();
        let i = 33; // erstes Zeichen des Chiffrats
        bad[i] = if bad[i] == b'0' { b'1' } else { b'0' };
        assert!(unseal(&sk, &String::from_utf8(bad).unwrap()).is_none());
        assert!(unseal(&seal_key(&key(), NL, NC), &sealed).is_none(), "anderer Schlüssel");
    }

    #[test]
    fn hex_hilfen() {
        assert_eq!(hex(&[0, 15, 255]), "000fff");
        assert_eq!(unhex("000fff").unwrap(), vec![0, 15, 255]);
        assert!(unhex("0F").is_none() && unhex("abc").is_none());
        assert!(is_hex(SID, SID_HEX_LEN) && !is_hex("0123456789ABCDEF", SID_HEX_LEN));
        assert!(ct_eq(b"abc", b"abc") && !ct_eq(b"abc", b"abd") && !ct_eq(b"ab", b"abc"));
        assert_eq!(random_bytes(32).len(), 32);
    }
}

//! Kryptografie des Cubiq-Link v2: HMAC-SHA256-Beweise für den gegenseitigen
//! Handschlag.
//!
//! - Beweis des Launchers: `HMAC(K, "cubiq-link/2/launcher\n" + sid + "\n" + nc + "\n" + nl)`
//! - Beweis des Spiels: `HMAC(K, "cubiq-link/2/game\n" + sid + "\n" + nc + "\n" + nl)`
//!
//! Der Schlüssel `K` selbst geht nie über die Leitung. Tokens gehen gar nicht
//! über den Link – das Spiel nutzt nur die Sitzung, mit der es gestartet wurde.

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

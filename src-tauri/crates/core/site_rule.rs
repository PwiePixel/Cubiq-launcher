// Regel für `CUBIQ_API_BASE` – eingebunden von build.rs und den Tests in src/cubiq_api/tests.rs.
// Die Oberfläche prüft beim Bauen genauso (scripts/cubiq-site.mjs).

/// `https://host[:port]` ohne Pfad, Query oder Zugangsdaten; der letzte Namensteil beginnt mit einem
/// Buchstaben (kein reines IP). Für lokale Tests auch `http://127.0.0.1:port` bzw. `http://localhost:port`.
fn is_site(base: &str) -> bool {
    let (https, rest) = match (base.strip_prefix("https://"), base.strip_prefix("http://")) {
        (Some(rest), _) => (true, rest),
        (None, Some(rest)) => (false, rest),
        (None, None) => return false,
    };
    let (host, port) = match rest.split_once(':') {
        Some((host, port)) => (host, Some(port)),
        None => (rest, None),
    };
    let port_ok = port.is_none_or(|p| {
        (1..=5).contains(&p.len()) && p.bytes().all(|b| b.is_ascii_digit()) && p.parse::<u32>().is_ok_and(|n| (1..=65_535).contains(&n))
    });
    if !https {
        return matches!(host, "127.0.0.1" | "localhost") && port.is_some() && port_ok;
    }
    let labels_ok = host.split('.').all(|l| !l.is_empty() && l.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-'));
    let named = host.rsplit('.').next().and_then(|l| l.bytes().next()).is_some_and(|b| b.is_ascii_alphabetic());
    labels_ok && named && port_ok
}

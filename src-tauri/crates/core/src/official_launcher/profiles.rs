//! `launcher_profiles.json` bzw. `launcher_profiles_microsoft_store.json` des offiziellen Launchers.
//!
//! Die Datei gehört Mojangs Launcher. Neben den Installationen (`profiles`) können dort ältere
//! Kontodaten stehen (`clientToken`, `authenticationDatabase` …). Deshalb gilt:
//! - Gelesen wird nur die oberste Ebene, und dort nur die Schlüssel: Werte anderer Schlüssel werden
//!   Byte für Byte übersprungen, nie dekodiert, nie gespeichert, nie geloggt.
//! - Geändert wird nur der Text des Werts von `profiles`, und darin nur Cubiqs eigene Einträge
//!   (`cubiq-<Instanz-ID>`). Alles davor und danach bleibt Byte für Byte, wie es war – auch die
//!   übrigen Installationen.
//! - Geschrieben wird über eine Temp-Datei und Umbenennen (nie eine halbe Datei); vor der ersten
//!   Änderung bleibt der Originalzustand einmalig als `*.cubiq-bak` daneben stehen.

use std::io::Write as _;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use serde::Serialize;

/// Schlüssel-Präfix unserer Einträge in `profiles`.
pub const KEY_PREFIX: &str = "cubiq-";
/// Endung der einmaligen Sicherung neben der Datei.
pub const BACKUP_SUFFIX: &str = ".cubiq-bak";
/// Größer ist keine echte Profil-Datei – lieber nicht anfassen.
const MAX_FILE_BYTES: u64 = 32 * 1024 * 1024;

/// Nie zwei Änderungen gleichzeitig (Übergabe, Instanz gelöscht, „Entfernen“ in den Einstellungen).
static EDIT_LOCK: Mutex<()> = Mutex::new(());

/// Fehler beim Lesen oder Schreiben – ohne Inhalt der Datei (der bleibt, wo er ist).
#[derive(Debug)]
pub enum ProfilesError {
    /// Datei fehlt (der offizielle Launcher wurde noch nie geöffnet).
    Missing,
    /// Kein gültiges JSON-Objekt, `profiles` kein Objekt, doppelt vorhanden oder zu groß.
    Unreadable(&'static str),
    Io(std::io::Error),
}

impl std::fmt::Display for ProfilesError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Missing => f.write_str("Datei fehlt"),
            Self::Unreadable(why) => write!(f, "nicht lesbar ({why})"),
            Self::Io(e) => write!(f, "{e}"),
        }
    }
}

impl From<std::io::Error> for ProfilesError {
    fn from(e: std::io::Error) -> Self {
        if e.kind() == std::io::ErrorKind::NotFound { Self::Missing } else { Self::Io(e) }
    }
}

/// Unser Eintrag – Felder wie in Mojangs Format (siehe `launcher_profiles.json` im Minecraft Wiki).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub name: String,
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub created: String,
    pub last_used: String,
    pub icon: String,
    pub last_version_id: String,
    pub game_dir: String,
    pub java_args: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resolution: Option<Resolution>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Resolution {
    pub width: u32,
    pub height: u32,
}

/// Schlüssel einer Instanz in `profiles`.
pub fn key_for(instance_id: &str) -> String {
    format!("{KEY_PREFIX}{instance_id}")
}

/// Pfad der einmaligen Sicherung (`launcher_profiles.json.cubiq-bak`).
pub fn backup_path(file: &Path) -> PathBuf {
    let mut name = file.file_name().map(std::ffi::OsStr::to_os_string).unwrap_or_default();
    name.push(BACKUP_SUFFIX);
    file.with_file_name(name)
}

// --- Text-Ebene (rein, ohne Dateien) ---------------------------------------------------------

/// Ein Mitglied eines JSON-Objekts als Byte-Positionen im Originaltext.
#[derive(Debug, Clone)]
struct Member {
    key: String,
    key_start: usize,
    value_start: usize,
    value_end: usize,
}

/// Ein JSON-Objekt im Originaltext: `{` bei `open`, `}` bei `close`.
#[derive(Debug, Clone)]
struct ObjectSpan {
    open: usize,
    close: usize,
    members: Vec<Member>,
}

const UNREADABLE: ProfilesError = ProfilesError::Unreadable("json");

fn skip_ws(b: &[u8], mut i: usize) -> usize {
    while i < b.len() && matches!(b[i], b' ' | b'\t' | b'\n' | b'\r') {
        i += 1;
    }
    i
}

/// Ende (exklusiv) des Strings, der bei `start` mit `"` beginnt – Escapes werden nur übersprungen.
fn string_end(b: &[u8], start: usize) -> Result<usize, ProfilesError> {
    let mut i = start + 1;
    while i < b.len() {
        match b[i] {
            b'\\' => i += 2,
            b'"' => return Ok(i + 1),
            _ => i += 1,
        }
    }
    Err(UNREADABLE)
}

/// Ende (exklusiv) des Werts, der bei `start` beginnt. Der Wert wird nur übersprungen, nicht gelesen.
fn value_end(b: &[u8], start: usize) -> Result<usize, ProfilesError> {
    match b.get(start) {
        Some(b'"') => string_end(b, start),
        Some(b'{' | b'[') => {
            let mut depth = 0usize;
            let mut i = start;
            while i < b.len() {
                match b[i] {
                    b'"' => {
                        i = string_end(b, i)?;
                        continue;
                    }
                    b'{' | b'[' => depth += 1,
                    b'}' | b']' => {
                        depth = depth.checked_sub(1).ok_or(UNREADABLE)?;
                        if depth == 0 {
                            return Ok(i + 1);
                        }
                    }
                    _ => {}
                }
                i += 1;
            }
            Err(UNREADABLE)
        }
        // Zahl, true, false, null
        Some(_) => {
            let mut i = start;
            while i < b.len() && !matches!(b[i], b',' | b'}' | b']' | b' ' | b'\t' | b'\n' | b'\r') {
                i += 1;
            }
            if i == start { Err(UNREADABLE) } else { Ok(i) }
        }
        None => Err(UNREADABLE),
    }
}

/// Liest die Schlüssel des Objekts bei `open` (nur die Schlüssel – Werte werden übersprungen).
fn scan_object(text: &str, open: usize) -> Result<ObjectSpan, ProfilesError> {
    let b = text.as_bytes();
    if b.get(open) != Some(&b'{') {
        return Err(UNREADABLE);
    }
    let mut members = Vec::new();
    let mut i = skip_ws(b, open + 1);
    if b.get(i) == Some(&b'}') {
        return Ok(ObjectSpan { open, close: i, members });
    }
    loop {
        if b.get(i) != Some(&b'"') {
            return Err(UNREADABLE);
        }
        let key_start = i;
        let key_end = string_end(b, i)?;
        // Nur der Schlüssel wird dekodiert (Escapes wie p zählen wie der Buchstabe).
        let key: String = serde_json::from_str(&text[key_start..key_end]).map_err(|_| UNREADABLE)?;
        i = skip_ws(b, key_end);
        if b.get(i) != Some(&b':') {
            return Err(UNREADABLE);
        }
        let value_start = skip_ws(b, i + 1);
        let value_end = value_end(b, value_start)?;
        members.push(Member { key, key_start, value_start, value_end });
        i = skip_ws(b, value_end);
        match b.get(i) {
            Some(b',') => i = skip_ws(b, i + 1),
            Some(b'}') => return Ok(ObjectSpan { open, close: i, members }),
            _ => return Err(UNREADABLE),
        }
    }
}

/// Wo `profiles` im Text steht.
enum ProfilesAt {
    /// Objekt (auch leer).
    Object(ObjectSpan),
    /// `"profiles": null` – Wertbereich zum Ersetzen.
    Null { start: usize, end: usize },
    /// Kein Schlüssel `profiles`: Einfügen direkt nach der `{` der Wurzel.
    Missing { root: ObjectSpan },
}

/// Prüft die ganze Datei (ohne etwas zu behalten) und sucht `profiles`.
fn locate(text: &str) -> Result<ProfilesAt, ProfilesError> {
    // Gültiges JSON? `IgnoredAny` prüft nur die Syntax und verwirft jeden Wert sofort.
    serde_json::from_str::<serde::de::IgnoredAny>(text).map_err(|_| UNREADABLE)?;
    let b = text.as_bytes();
    let root = scan_object(text, skip_ws(b, 0)).map_err(|_| ProfilesError::Unreadable("root"))?;
    let mut found = root.members.iter().filter(|m| m.key == "profiles");
    let (Some(member), None) = (found.next(), found.next()) else {
        return match root.members.iter().filter(|m| m.key == "profiles").count() {
            0 => Ok(ProfilesAt::Missing { root }),
            _ => Err(ProfilesError::Unreadable("duplicate")),
        };
    };
    match b.get(member.value_start) {
        Some(b'{') => Ok(ProfilesAt::Object(scan_object(text, member.value_start)?)),
        Some(b'n') if &text[member.value_start..member.value_end] == "null" => {
            Ok(ProfilesAt::Null { start: member.value_start, end: member.value_end })
        }
        _ => Err(ProfilesError::Unreadable("profiles")),
    }
}

fn splice(text: &str, start: usize, end: usize, with: &str) -> String {
    let mut out = String::with_capacity(text.len() - (end - start) + with.len());
    out.push_str(&text[..start]);
    out.push_str(with);
    out.push_str(&text[end..]);
    out
}

/// Unser Eintrag als JSON, eingerückt für die Tiefe innerhalb von `profiles`.
fn render(key: &str, entry: &Entry) -> String {
    let key = serde_json::to_string(key).unwrap_or_else(|_| "\"\"".into());
    let value = serde_json::to_string_pretty(entry).unwrap_or_else(|_| "{}".into()).replace('\n', "\n    ");
    format!("{key}: {value}")
}

/// `created` eines vorhandenen eigenen Eintrags (nur unser Eintrag wird dafür gelesen).
fn existing_created(text: &str, member: &Member) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(&text[member.value_start..member.value_end]).ok()?;
    value.get("created")?.as_str().filter(|s| s.len() <= 64 && !s.trim().is_empty()).map(str::to_owned)
}

/// Setzt `profiles[key]` auf `entry` (behält nur dessen bisheriges `created`) und liefert den neuen Text.
/// Doppelte Einträge mit demselben Schlüssel fallen weg; alles andere bleibt Byte für Byte erhalten.
pub fn upsert_text(text: &str, key: &str, entry: &Entry) -> Result<String, ProfilesError> {
    let mut entry = entry.clone();
    let out = match locate(text)? {
        ProfilesAt::Object(profiles) => match profiles.members.iter().find(|m| m.key == key) {
            Some(member) => {
                if let Some(created) = existing_created(text, member) {
                    entry.created = created;
                }
                let rendered = render(key, &entry);
                let replaced = splice(text, member.key_start, member.value_end, &rendered);
                // Weitere Einträge mit unserem Schlüssel (von Hand kopiert?) – der erste gilt.
                remove_where(&replaced, |k, n| k == key && n > 0)?.0
            }
            None if profiles.members.is_empty() => {
                splice(text, profiles.open + 1, profiles.close, &format!("\n    {}\n  ", render(key, &entry)))
            }
            None => {
                let last = profiles.members.last().map_or(profiles.open + 1, |m| m.value_end);
                splice(text, last, last, &format!(",\n    {}", render(key, &entry)))
            }
        },
        ProfilesAt::Null { start, end } => splice(text, start, end, &format!("{{\n    {}\n  }}", render(key, &entry))),
        ProfilesAt::Missing { root } => {
            let tail = if root.members.is_empty() { "\n" } else { "," };
            let insert = format!("\n  \"profiles\": {{\n    {}\n  }}{tail}", render(key, &entry));
            if root.members.is_empty() {
                splice(text, root.open + 1, root.close, &insert)
            } else {
                splice(text, root.open + 1, root.open + 1, &insert)
            }
        }
    };
    // Gegenprobe: wieder gültig, und genau ein Eintrag mit unserem Schlüssel.
    match locate(&out)? {
        ProfilesAt::Object(p) if p.members.iter().filter(|m| m.key == key).count() == 1 => Ok(out),
        _ => Err(ProfilesError::Unreadable("check")),
    }
}

/// Entfernt Einträge aus `profiles`, für die `matches(Schlüssel, n-tes Vorkommen dieses Schlüssels)`
/// gilt. Liefert den neuen Text und die Zahl entfernter Einträge (0 = Text unverändert).
fn remove_where(text: &str, matches: impl Fn(&str, usize) -> bool) -> Result<(String, usize), ProfilesError> {
    let mut out = text.to_owned();
    let mut removed = 0;
    loop {
        let ProfilesAt::Object(profiles) = locate(&out)? else { return Ok((out, removed)) };
        // Vorkommen je Schlüssel zählen; das letzte passende Mitglied zuerst entfernen.
        let mut seen: Vec<(&str, usize)> = Vec::new();
        let mut target = None;
        for (index, member) in profiles.members.iter().enumerate() {
            let n = match seen.iter_mut().find(|(k, _)| *k == member.key) {
                Some((_, count)) => {
                    *count += 1;
                    *count - 1
                }
                None => {
                    seen.push((&member.key, 1));
                    0
                }
            };
            if matches(&member.key, n) {
                target = Some(index);
            }
        }
        let Some(index) = target else { return Ok((out, removed)) };
        let members = &profiles.members;
        out = if members.len() == 1 {
            splice(&out, profiles.open + 1, profiles.close, "")
        } else if index == 0 {
            // Schlüssel, Wert, Komma und Leerraum bis zum nächsten Schlüssel.
            splice(&out, members[0].key_start, members[1].key_start, "")
        } else {
            // Komma nach dem vorigen Wert bis zum Ende dieses Werts.
            splice(&out, members[index - 1].value_end, members[index].value_end, "")
        };
        removed += 1;
    }
}

/// Entfernt unsere Einträge, für die `matches(Schlüssel, Eintrag)` gilt. Nur Einträge mit unserem
/// Präfix werden überhaupt angesehen (und nur deren Wert wird gelesen).
pub fn remove_text(text: &str, matches: impl Fn(&str, &serde_json::Value) -> bool) -> Result<(String, usize), ProfilesError> {
    // Werte vorher bestimmen: nur eigene Einträge, nur wenn sie passen.
    let ProfilesAt::Object(profiles) = locate(text)? else { return Ok((text.to_owned(), 0)) };
    let mut doomed: Vec<String> = Vec::new();
    for member in profiles.members.iter().filter(|m| m.key.starts_with(KEY_PREFIX)) {
        let value: serde_json::Value =
            serde_json::from_str(&text[member.value_start..member.value_end]).unwrap_or(serde_json::Value::Null);
        if matches(&member.key, &value) && !doomed.contains(&member.key) {
            doomed.push(member.key.clone());
        }
    }
    if doomed.is_empty() {
        return Ok((text.to_owned(), 0));
    }
    remove_where(text, |key, _| doomed.iter().any(|d| d == key))
}

/// Schlüssel unserer Einträge (für Anzeige und „Entfernen“).
pub fn own_keys_text(text: &str) -> Result<Vec<String>, ProfilesError> {
    Ok(match locate(text)? {
        ProfilesAt::Object(profiles) => {
            profiles.members.into_iter().filter(|m| m.key.starts_with(KEY_PREFIX)).map(|m| m.key).collect()
        }
        _ => Vec::new(),
    })
}

// --- Datei-Ebene -------------------------------------------------------------------------------

/// Datei lesen (UTF-8, ggf. mit BOM); das BOM bleibt beim Schreiben erhalten.
fn read(file: &Path) -> Result<(String, &'static str), ProfilesError> {
    let len = std::fs::metadata(file)?.len();
    if len > MAX_FILE_BYTES {
        return Err(ProfilesError::Unreadable("size"));
    }
    let bytes = std::fs::read(file)?;
    let text = String::from_utf8(bytes).map_err(|_| ProfilesError::Unreadable("utf8"))?;
    Ok(match text.strip_prefix('\u{feff}') {
        Some(rest) => (rest.to_owned(), "\u{feff}"),
        None => (text, ""),
    })
}

/// Einmalige Sicherung des Originals, bevor Cubiq die Datei zum ersten Mal ändert. Am liebsten als
/// harter Link: Dann kopiert Cubiq keinen einzigen Byte der Datei – der alte Inhalt bleibt nach dem
/// Umbenennen einfach unter dem zweiten Namen stehen (gleiche Rechte). Sonst als Kopie.
fn backup_once(file: &Path) -> std::io::Result<()> {
    let backup = backup_path(file);
    if std::fs::symlink_metadata(&backup).is_ok() {
        return Ok(());
    }
    if std::fs::hard_link(file, &backup).is_ok() {
        return Ok(());
    }
    std::fs::copy(file, &backup).map(|_| ())
}

/// Neue Fassung über eine Temp-Datei daneben schreiben und umbenennen – Rechte wie das Original.
fn replace_atomic(file: &Path, contents: &str) -> std::io::Result<()> {
    let name = file.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    let tmp = file.with_file_name(format!("{name}.cubiq-tmp-{}", uuid::Uuid::new_v4().simple()));
    let result = (|| {
        let mut out = std::fs::OpenOptions::new().write(true).create_new(true).open(&tmp)?;
        out.write_all(contents.as_bytes())?;
        out.sync_all()?;
        drop(out);
        // Strengere Rechte des Originals (z. B. 0600) dürfen nicht verloren gehen.
        if let Ok(meta) = std::fs::metadata(file) {
            std::fs::set_permissions(&tmp, meta.permissions())?;
        }
        std::fs::rename(&tmp, file)
    })();
    if result.is_err() {
        let _ = std::fs::remove_file(&tmp);
    }
    result
}

fn write(file: &Path, bom: &str, text: &str) -> Result<(), ProfilesError> {
    backup_once(file)?;
    replace_atomic(file, &format!("{bom}{text}"))?;
    Ok(())
}

fn lock() -> std::sync::MutexGuard<'static, ()> {
    EDIT_LOCK.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
}

/// Trägt unseren Eintrag in die Datei ein (bzw. ersetzt ihn). Die Datei muss existieren.
pub fn upsert(file: &Path, key: &str, entry: &Entry) -> Result<(), ProfilesError> {
    let _guard = lock();
    let (text, bom) = read(file)?;
    let updated = upsert_text(&text, key, entry)?;
    if updated == text {
        return Ok(());
    }
    write(file, bom, &updated)
}

/// Entfernt passende eigene Einträge; liefert, wie viele. Ohne Treffer bleibt die Datei unberührt.
pub fn remove(file: &Path, matches: impl Fn(&str, &serde_json::Value) -> bool) -> Result<usize, ProfilesError> {
    let _guard = lock();
    let (text, bom) = match read(file) {
        Ok(read) => read,
        Err(ProfilesError::Missing) => return Ok(0),
        Err(e) => return Err(e),
    };
    let (updated, removed) = remove_text(&text, matches)?;
    if removed > 0 {
        write(file, bom, &updated)?;
    }
    Ok(removed)
}

/// Unsere Einträge in der Datei (fehlt sie, keine).
pub fn own_keys(file: &Path) -> Result<Vec<String>, ProfilesError> {
    match read(file) {
        Ok((text, _)) => own_keys_text(&text),
        Err(ProfilesError::Missing) => Ok(Vec::new()),
        Err(e) => Err(e),
    }
}

use super::*;
use crate::instance::{Loader, NewInstance};

fn instance(id: &str, total: u64) -> Instance {
    serde_json::from_value(serde_json::json!({
        "id": id, "name": format!("Name {id}"), "gameVersion": "1.21.1",
        "loader": { "kind": "fabric", "version": "0.16.10" },
        "createdAt": "2026-01-01T00:00:00Z", "lastPlayed": null, "totalPlaySeconds": total
    }))
    .unwrap()
}

fn meta(id: &str) -> SessionMeta {
    SessionMeta::of(&instance(id, 0))
}

fn entry(kind: HistoryKind, at: i64) -> HistoryEntry {
    HistoryEntry { at: DateTime::<Utc>::from_timestamp(at, 0).unwrap(), ..HistoryEntry::new(kind) }
}

#[test]
fn migrates_history_with_its_versions_and_carries_the_rest() {
    let base = 1_760_000_000;
    // Verlauf: neueste zuerst. Vor dem Versionswechsel lief 1.20.1 mit Forge.
    let history = vec![
        entry(HistoryKind::Stopped, base + 9_000).seconds(1_000),
        entry(HistoryKind::Launched, base + 8_000),
        entry(HistoryKind::VersionSwitched, base + 5_000).from("1.20.1 Forge 47.3.0").to("1.21.1 Fabric 0.16.10"),
        entry(HistoryKind::Crashed, base + 4_000).seconds(500),
        entry(HistoryKind::Stopped, base + 2_000).seconds(0),
        entry(HistoryKind::Created, base),
    ];
    let mut file = StatsFile::default();
    migrate(&mut file, &[(instance("survival", 10_000), history)], base + 10_000);

    assert!(file.migrated);
    assert_eq!(file.sessions.len(), 2, "Einträge ohne Spielzeit zählen nicht");
    let (old, new) = (&file.sessions[0], &file.sessions[1]);
    assert_eq!((old.version.as_str(), old.loader.as_str(), old.crashed), ("1.20.1", "forge", true));
    assert_eq!((old.start, old.end), (base + 3_500, base + 4_000));
    assert_eq!((new.version.as_str(), new.loader.as_str(), new.crashed), ("1.21.1", "fabric", false));
    assert!(old.imported && new.imported);
    // Der Rest der bekannten Spielzeit bleibt als Übertrag ohne Datum.
    assert_eq!(file.carried.get("survival"), Some(&8_500));
    assert!(file.known.contains("survival"));
    assert_eq!(file.names.get("survival").map(String::as_str), Some("Name survival"));

    // Zweiter Aufruf ändert nichts.
    let before = file.clone();
    migrate(&mut file, &[(instance("survival", 10_000), Vec::new())], base + 20_000);
    assert_eq!(file, before);
}

#[test]
fn version_texts_from_the_history() {
    assert_eq!(parse_described("1.21.1 Fabric 0.16.10"), Some(("1.21.1".into(), "fabric".into())));
    assert_eq!(parse_described("1.8.9 Vanilla"), Some(("1.8.9".into(), "vanilla".into())));
    assert_eq!(parse_described("1.14 Pre-Release 1 NeoForge"), Some(("1.14 Pre-Release 1".into(), "neoforge".into())));
    assert_eq!(parse_described("Fabric"), None);
    assert_eq!(parse_described("irgendwas"), None);
}

#[test]
fn start_and_exit_make_one_session_with_its_servers() {
    let mut file = StatsFile::default();
    let t = 1_760_000_000;
    start(&mut file, "survival", &meta("survival"), t, Some(" Play.Example.NET:25565 "), Some(&instance("survival", 0)));
    assert_eq!(file.open.len(), 1);
    join_server(&mut file, "survival", "survival", "mc.example.org:25570");
    join_server(&mut file, "survival", "survival", "PLAY.example.net");
    join_server(&mut file, "andere", "andere", "nicht.hier");

    exit(&mut file, "survival", "survival", 3_600, false, t + 3_600, None);
    assert!(file.open.is_empty());
    assert_eq!(file.sessions.len(), 1);
    let s = &file.sessions[0];
    assert_eq!((s.start, s.end, s.seconds()), (t, t + 3_600, 3_600));
    assert_eq!(s.servers, ["play.example.net", "mc.example.org:25570"]);
    assert_eq!((s.version.as_str(), s.loader.as_str()), ("1.21.1", "fabric"));

    // Dasselbe Ende noch einmal gemeldet: nicht doppelt.
    exit(&mut file, "survival", "survival", 3_600, false, t + 3_600, None);
    assert_eq!(file.sessions.len(), 1);
}

#[test]
fn an_exit_without_open_session_uses_the_instance() {
    let mut file = StatsFile::default();
    let t = 1_760_000_000;
    exit(&mut file, "survival", "survival~2", 120, true, t, Some(&meta("survival")));
    assert_eq!(file.sessions.len(), 1);
    let s = &file.sessions[0];
    assert_eq!((s.start, s.end, s.crashed), (t - 120, t, true));
    assert_eq!(s.version, "1.21.1");
    // Kommt der Start danach an (Spiel sofort wieder zu), bleibt es bei einer Sitzung.
    start(&mut file, "survival~2", &meta("survival"), t - 120, Some("mc.example.org"), None);
    assert!(file.open.is_empty());
    assert_eq!(file.sessions[0].servers, ["mc.example.org"]);
    // Ein Ende ohne Spielzeit zählt nicht.
    exit(&mut file, "survival", "survival", 0, false, t + 50, None);
    assert_eq!(file.sessions.len(), 1);
}

#[test]
fn stale_sessions_close_at_the_last_log_write() {
    let mut file = StatsFile::default();
    let t = 1_760_000_000;
    start(&mut file, "a", &meta("a"), t, None, None);
    start(&mut file, "b", &meta("b"), t, None, None);
    start(&mut file, "c", &meta("c"), t, None, None);
    start(&mut file, "läuft", &meta("läuft"), t, None, None);
    let running: HashSet<String> = ["läuft".to_owned()].into();
    let booked = close_stale(&mut file, &running, t + 10_000, |instance, _| match instance {
        "a" => Some(t + 600),
        // Log jünger als „jetzt“ (Uhr verstellt): nie über jetzt hinaus.
        "b" => Some(t + 50_000),
        // Kein Log: keine Sitzung (Dauer unbekannt).
        _ => None,
    });
    assert_eq!(booked, [("a".to_owned(), 600), ("b".to_owned(), 10_000)]);
    assert_eq!(file.open.len(), 1, "Das laufende Spiel bleibt offen");
    assert!(file.sessions.iter().all(|s| s.estimated));

    // Kommt doch noch das genaue Ende (Spiel endete gerade erst), ersetzt es die Schätzung.
    exit(&mut file, "a", "a", 900, true, t + 900, None);
    let a = file.sessions.iter().find(|s| s.instance == "a").unwrap();
    assert_eq!((a.end, a.estimated, a.crashed), (t + 900, false, true));
    assert_eq!(file.sessions.len(), 2);
}

#[test]
fn a_stale_session_never_runs_into_the_next_one() {
    let mut file = StatsFile::default();
    let t = 1_760_000_000;
    start(&mut file, "a", &meta("a"), t, None, None);
    insert_sorted(
        &mut file,
        Stored {
            instance: "a".into(),
            version: String::new(),
            loader: String::new(),
            start: t + 1_000,
            end: t + 2_000,
            servers: Vec::new(),
            crashed: false,
            estimated: false,
            imported: false,
            key: "a".into(),
        },
    );
    let booked = close_stale(&mut file, &HashSet::new(), t + 5_000, |_, _| Some(t + 4_000));
    assert_eq!(booked, [("a".to_owned(), 1_000)]);
}

#[test]
fn old_sessions_fold_into_the_carried_time() {
    let mut file = StatsFile::default();
    let extra = 10;
    for i in 0..(MAX_SESSIONS + extra) as i64 {
        file.sessions.push(Stored {
            instance: if i % 2 == 0 { "a".into() } else { "b".into() },
            version: "1.21.1".into(),
            loader: "vanilla".into(),
            start: i * 100,
            end: i * 100 + 60,
            servers: Vec::new(),
            crashed: false,
            estimated: false,
            imported: false,
            key: "a".into(),
        });
    }
    cap(&mut file);
    assert_eq!(file.sessions.len(), MAX_SESSIONS);
    assert_eq!(file.sessions[0].start, extra as i64 * 100);
    assert_eq!(file.carried.values().sum::<u64>(), extra as u64 * 60);
    // Nur die jüngsten Sitzungen behalten ihren Prozess-Schlüssel.
    assert!(file.sessions[0].key.is_empty());
    assert_eq!(file.sessions.last().unwrap().key, "a");
}

#[test]
fn server_lines_from_the_log() {
    assert_eq!(parse_connect("Connecting to mc.example.org, 25565").as_deref(), Some("mc.example.org"));
    assert_eq!(parse_connect("Connecting to Play.Example.NET, 25570").as_deref(), Some("play.example.net:25570"));
    assert_eq!(parse_connect("Connecting to 192.168.1.20, 25565").as_deref(), Some("192.168.1.20"));
    assert_eq!(parse_connect("[CHAT] Connecting to evil.example, 25565"), None);
    assert_eq!(parse_connect("Connecting to , 25565"), None);
    assert_eq!(parse_connect("Connecting to mc.example.org, 99999"), None);
    assert_eq!(parse_connect("Connecting to mc example org, 25565"), None);
    assert_eq!(normalize_server("MC.Example.org:25565"), Some("mc.example.org".into()));
    assert_eq!(normalize_server("a/b"), None);
}

#[test]
fn view_lists_sessions_running_games_and_carried_time() {
    let mut file = StatsFile::default();
    let t = 1_760_000_000;
    start(&mut file, "a", &meta("a"), t, None, Some(&instance("a", 300)));
    start(&mut file, "rest", &meta("rest"), t, None, None);
    exit(&mut file, "b", "b", 60, false, t + 60, Some(&meta("b")));
    let running: HashSet<String> = ["a".to_owned()].into();
    let stats = view(&file, &running, t + 120);
    assert_eq!(stats.sessions.len(), 1);
    // Nur Sitzungen, deren Spiel wirklich läuft; Ende = jetzt.
    assert_eq!(stats.running.len(), 1);
    assert_eq!((stats.running[0].instance_id.as_str(), stats.running[0].end), ("a", t + 120));
    assert_eq!(stats.carried, [CarriedTime { instance_id: "a".into(), seconds: 300 }]);
    assert_eq!(stats.tracking_since, Some(t));
    let json = serde_json::to_value(&stats.sessions[0]).unwrap();
    assert_eq!(json["instanceId"], "b");
    assert!(json.get("crashed").is_none(), "Falsche Schalter fehlen in der Antwort");
}

#[test]
fn a_broken_file_falls_back_to_the_previous_version() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join(FILE);
    let mut file = StatsFile { since: Some(1), ..Default::default() };
    save_sync(&path, &file).unwrap();
    file.since = Some(2);
    save_sync(&path, &file).unwrap();
    assert_eq!(load_sync(&path).since, Some(2));

    std::fs::write(&path, b"{ kaputt").unwrap();
    assert_eq!(load_sync(&path).since, Some(1), "Die vorige Fassung springt ein");
    let kept = std::fs::read_dir(dir.path()).unwrap().flatten().any(|e| e.file_name().to_string_lossy().contains("broken-"));
    assert!(kept, "Die kaputte Datei bleibt daneben liegen");

    // Hauptdatei weg (Absturz zwischen den Umbenennungen): ebenfalls die .bak.
    std::fs::remove_file(&path).ok();
    assert_eq!(load_sync(&path).since, Some(1));
    // Gar nichts da: leer.
    let empty = dir.path().join("leer").join(FILE);
    assert_eq!(load_sync(&empty), StatsFile::default());
}

async fn setup() -> (tempfile::TempDir, Paths, InstanceStore) {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::new(dir.path());
    paths.ensure().await.unwrap();
    let store = InstanceStore::new(paths.clone());
    (dir, paths, store)
}

#[tokio::test]
async fn startup_closes_a_lost_session_and_books_its_time() {
    let (_dir, paths, store) = setup().await;
    let created = store
        .create(NewInstance { name: "Welt".into(), game_version: "1.21.1".into(), loader: Loader::vanilla() })
        .await
        .unwrap();
    let now = Utc::now().timestamp();
    session_started(&paths, &created.id, &created, SessionMeta::of(&created), now - 3_600, Some("mc.example.org".into())).await;

    // Das Spiel schrieb zuletzt vor zehn Minuten in sein Log, dann stürzte alles ab.
    let logs = paths.instance_dir(&created.id).join("launcher-logs");
    std::fs::create_dir_all(&logs).unwrap();
    let log = logs.join("launcher-stdout.log");
    std::fs::write(&log, b"[12:00:00] [Render thread/INFO]: Stopping!\n").unwrap();
    let written = std::time::SystemTime::now() - std::time::Duration::from_secs(600);
    std::fs::File::options().write(true).open(&log).unwrap().set_modified(written).unwrap();

    startup(&paths, &[]).await.unwrap();
    let total = store.get(&created.id).await.unwrap().total_play_seconds;
    assert!((2_995..=3_005).contains(&total), "gebucht: {total}");
    let stats = load(&paths, &[store.get(&created.id).await.unwrap()], &[]).await.unwrap();
    assert_eq!(stats.sessions.len(), 1);
    assert!(stats.sessions[0].estimated);
    assert_eq!(stats.sessions[0].servers, ["mc.example.org"]);
    assert!(stats.carried.is_empty(), "Die nachgetragene Zeit zählt nicht doppelt");
}

#[tokio::test]
async fn existing_play_time_counts_and_reset_starts_over() {
    let (_dir, paths, store) = setup().await;
    let a = store.create(NewInstance { name: "Alt".into(), game_version: "1.20.1".into(), loader: Loader::vanilla() }).await.unwrap();
    store.add_play_time(&a.id, 7_200).await.unwrap();
    crate::history::record(&paths, &a.id, HistoryEntry::new(HistoryKind::Stopped).seconds(1_200)).await;

    let instances = store.list().await.unwrap();
    let stats = load(&paths, &instances, &[]).await.unwrap();
    assert_eq!(stats.sessions.len(), 1, "Sitzung aus dem Verlauf");
    assert_eq!(stats.carried, [CarriedTime { instance_id: a.id.clone(), seconds: 6_000 }]);

    // Neue Instanz mit Spielzeit (z. B. später verbunden): wird beim nächsten Laden übernommen.
    let b = store.create(NewInstance { name: "Neu".into(), game_version: "1.21.1".into(), loader: Loader::vanilla() }).await.unwrap();
    store.add_play_time(&b.id, 300).await.unwrap();
    let stats = load(&paths, &store.list().await.unwrap(), &[]).await.unwrap();
    assert_eq!(stats.carried.iter().map(|c| c.seconds).sum::<u64>(), 6_300);

    reset(&paths, &store.list().await.unwrap()).await.unwrap();
    let stats = load(&paths, &store.list().await.unwrap(), &[]).await.unwrap();
    assert!(stats.sessions.is_empty() && stats.carried.is_empty());
    assert!(stats.tracking_since.is_some());
    // Die Spielzeit der Instanzen selbst bleibt.
    assert_eq!(store.get(&a.id).await.unwrap().total_play_seconds, 7_200);
}

#[tokio::test]
async fn running_games_are_not_carried_twice() {
    let (_dir, paths, store) = setup().await;
    let a = store.create(NewInstance { name: "Läuft".into(), game_version: "1.21.1".into(), loader: Loader::vanilla() }).await.unwrap();
    // Migration schon erledigt, Instanz noch unbekannt (z. B. vom Handy gestartet).
    update(&paths, |file| file.migrated = true).await.unwrap();
    let game = RunningGame { instance_id: a.id.clone(), key: a.id.clone(), pid: 1, started_at: Utc::now() };
    let stats = load(&paths, std::slice::from_ref(&a), std::slice::from_ref(&game)).await.unwrap();
    assert!(stats.carried.is_empty());
    // Ende kommt: Spielzeit gebucht, Sitzung aufgezeichnet – danach kein Übertrag.
    store.add_play_time(&a.id, 90).await.unwrap();
    session_exited(&paths, &a.id, &a.id, 90, false).await;
    let stats = load(&paths, &store.list().await.unwrap(), &[]).await.unwrap();
    assert_eq!(stats.sessions.len(), 1);
    assert!(stats.carried.is_empty(), "{:?}", stats.carried);
}

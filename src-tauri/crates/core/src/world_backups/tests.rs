use std::io::Write;
use std::time::{Duration, SystemTime};

use super::*;
use crate::instance::{Loader, NewInstance};
use crate::nbt::Tag;

fn level_dat(name: &str) -> Vec<u8> {
    let data = vec![
        (b"LevelName".to_vec(), Tag::string(name)),
        (b"GameType".to_vec(), Tag::Int(0)),
        (b"Version".to_vec(), Tag::Compound(vec![(b"Name".to_vec(), Tag::string("1.21.1"))])),
    ];
    let raw = crate::nbt::write_root(&[(b"Data".to_vec(), Tag::Compound(data))]);
    let mut gz = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
    gz.write_all(&raw).unwrap();
    gz.finish().unwrap()
}

fn set_mtime(path: &Path, secs_ago: u64) {
    let time = SystemTime::now() - Duration::from_secs(secs_ago);
    std::fs::File::options().write(true).open(path).unwrap().set_modified(time).unwrap();
}

/// Kleine Welt mit level.dat, Region-Datei, Unterordner und `session.lock`.
fn make_world(saves: &Path, folder: &str, name: &str) -> PathBuf {
    let dir = saves.join(folder);
    std::fs::create_dir_all(dir.join("region")).unwrap();
    std::fs::create_dir_all(dir.join("data")).unwrap();
    std::fs::write(dir.join("level.dat"), level_dat(name)).unwrap();
    std::fs::write(dir.join("region/r.0.0.mca"), vec![3u8; 64 * 1024]).unwrap();
    std::fs::write(dir.join("data/raids.dat"), b"raids").unwrap();
    std::fs::write(dir.join(lock::SESSION_LOCK), b"\xe2\x98\x83").unwrap();
    for f in ["level.dat", "region/r.0.0.mca", "data/raids.dat", lock::SESSION_LOCK] {
        set_mtime(&dir.join(f), 3_600);
    }
    dir
}

async fn setup() -> (tempfile::TempDir, Paths, Instance, PathBuf) {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::new(dir.path());
    paths.ensure().await.unwrap();
    let instance = InstanceStore::new(paths.clone())
        .create(NewInstance { name: "Backups".into(), game_version: "1.21.1".into(), loader: Loader::vanilla() })
        .await
        .unwrap();
    let saves = paths.instance_game_dir(&instance.id).join("saves");
    make_world(&saves, "Welt", "Meine Welt");
    (dir, paths, instance, saves)
}

fn backup_auto(paths: &Paths, scope: &str, saves: &Path, folder: &str) -> Result<(store::BackupFile, Meta)> {
    let baseline = lock::state(&saves.join(folder));
    create_sync(paths, scope, saves, folder, Kind::Auto, baseline, None, &|| false, &mut |_, _| {})
}

#[test]
fn the_fingerprint_follows_the_world_but_not_the_lock() {
    let dir = tempfile::tempdir().unwrap();
    let world = make_world(dir.path(), "Welt", "A");
    let first = archive::fingerprint(&archive::scan(&world).unwrap());
    assert_eq!(first, archive::fingerprint(&archive::scan(&world).unwrap()));
    // Das Spiel öffnet die Welt: session.lock ändert sich – der Inhalt nicht.
    std::fs::write(world.join(lock::SESSION_LOCK), b"neu").unwrap();
    assert_eq!(first, archive::fingerprint(&archive::scan(&world).unwrap()));
    // Region-Datei gespeichert: neuer Abdruck.
    std::fs::write(world.join("region/r.0.0.mca"), vec![4u8; 64 * 1024]).unwrap();
    assert_ne!(first, archive::fingerprint(&archive::scan(&world).unwrap()));
}

#[tokio::test]
async fn only_changed_worlds_are_planned() {
    let (_dir, paths, instance, saves) = setup().await;
    make_world(&saves, "Zweite", "Zweite Welt");
    let scope = scope_of(&paths, &instance);
    let planned = plan(&paths, &scope.key, worlds_in(&saves), None);
    assert_eq!(planned.len(), 2, "Noch kein Backup: beide Welten");

    backup_auto(&paths, &scope.key, &saves, "Welt").unwrap();
    let planned = plan(&paths, &scope.key, worlds_in(&saves), None);
    assert_eq!(planned.iter().map(|p| p.world.folder.as_str()).collect::<Vec<_>>(), ["Zweite"]);
    assert_eq!(planned[0].name, "Zweite Welt");

    // Gespielt: die Welt ist wieder dran.
    std::fs::write(saves.join("Welt/region/r.0.0.mca"), b"neu").unwrap();
    let planned = plan(&paths, &scope.key, worlds_in(&saves), None);
    assert!(planned.iter().any(|p| p.world.folder == "Welt"));

    // Eine Welt, deren session.lock seit dem Start eines laufenden Spiels geschrieben wurde, ist offen.
    let since = SystemTime::now() - Duration::from_secs(60);
    std::fs::write(saves.join("Zweite").join(lock::SESSION_LOCK), b"offen").unwrap();
    let planned = plan(&paths, &scope.key, worlds_in(&saves), Some(since));
    assert!(planned.iter().all(|p| p.world.folder != "Zweite"));

    // Ab 1.16 sperrt das Spiel session.lock, solange die Welt offen ist: Ohne Sperre ist sie wieder
    // zu, auch wenn die Datei seit dem Start geschrieben wurde (geöffnet und wieder geschlossen).
    std::fs::write(saves.join("Zweite").join(lock::SESSION_LOCK), "\u{2603}").unwrap();
    assert!(lock::is_modern(&saves.join("Zweite")));
    let planned = plan(&paths, &scope.key, worlds_in(&saves), Some(since));
    assert!(planned.iter().any(|p| p.world.folder == "Zweite"));
    // Danach wieder ein altes Minecraft (8-Byte-Uhrzeit): die Zeit entscheidet.
    std::fs::write(saves.join("Zweite").join(lock::SESSION_LOCK), 1_700_000_000_000i64.to_be_bytes()).unwrap();
    assert!(!lock::is_modern(&saves.join("Zweite")));
    assert!(busy(&saves.join("Zweite"), Some(since)));
    assert!(!busy(&saves.join("Zweite"), None), "ohne laufendes Spiel zählt nur die Sperre");
}

/// Die Sperre eines anderen Prozesses (wie Java `FileChannel.tryLock`, unter Linux per fcntl) wird
/// erkannt. Braucht Python – fehlt es, prüft der Test nichts.
#[cfg(target_os = "linux")]
#[test]
fn a_lock_held_by_another_process_is_seen() {
    use std::io::BufRead;
    let dir = tempfile::tempdir().unwrap();
    let world = make_world(dir.path(), "Welt", "A");
    assert!(!lock::is_locked(&world));
    let script = "import fcntl, sys, time\nf = open(sys.argv[1], 'r+')\nfcntl.lockf(f, fcntl.LOCK_EX)\nprint('ok', flush=True)\ntime.sleep(30)\n";
    let Ok(mut child) = std::process::Command::new("python3")
        .args(["-c", script])
        .arg(world.join(lock::SESSION_LOCK))
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::null())
        .spawn()
    else {
        return;
    };
    let mut line = String::new();
    let _ = std::io::BufReader::new(child.stdout.take().unwrap()).read_line(&mut line);
    let locked = lock::is_locked(&world);
    let opened = busy(&world, None);
    let _ = child.kill();
    let _ = child.wait();
    if line.trim() != "ok" {
        return;
    }
    assert!(locked && opened, "gesperrte Welt gilt als offen");
    assert!(!lock::is_locked(&world), "nach dem Ende des Prozesses ist die Sperre weg");
}

#[tokio::test]
async fn backups_keep_names_times_and_metadata() {
    let (_dir, paths, instance, saves) = setup().await;
    let scope = scope_of(&paths, &instance);
    // Rest eines abgebrochenen Backups (Absturz) – verschwindet mit dem nächsten.
    let dir = store::world_dir(&paths, &scope.key, "Welt");
    std::fs::create_dir_all(&dir).unwrap();
    std::fs::write(dir.join(".2026-01-01_00-00-00-auto.zip.part"), b"halb").unwrap();
    let (file, meta) = backup_auto(&paths, &scope.key, &saves, "Welt").unwrap();
    assert!(file.id.ends_with("-auto.zip"), "{}", file.id);
    assert_eq!(store::parse_name(&file.id).map(|(_, k)| k), Some(Kind::Auto));
    let read = archive::read_meta(&file.path).unwrap();
    assert_eq!(read, meta);
    assert_eq!((read.name.as_str(), read.world.as_str(), read.version.as_deref()), ("Meine Welt", "Welt", Some("1.21.1")));
    assert_eq!(read.files, 3, "ohne session.lock");

    let mut zip = zip::ZipArchive::new(std::fs::File::open(&file.path).unwrap()).unwrap();
    let names: Vec<String> = zip.file_names().map(str::to_owned).collect();
    assert!(names.contains(&"Welt/region/r.0.0.mca".to_owned()));
    assert!(!names.iter().any(|n| n.ends_with(lock::SESSION_LOCK)));
    // Kein Rest einer Temp-Datei.
    assert_eq!(std::fs::read_dir(&dir).unwrap().count(), 1);
    // Sekundengenaue Dateizeit im Archiv.
    let entry = zip.by_name("Welt/data/raids.dat").unwrap();
    let stamp = entry.extra_data_fields().find_map(|f| match f {
        zip::ExtraField::ExtendedTimestamp(ts) => ts.mod_time(),
        _ => None,
    });
    let expected = std::fs::metadata(saves.join("Welt/data/raids.dat")).unwrap().modified().unwrap();
    assert_eq!(stamp.map(i64::from), Some(archive::scan(&saves.join("Welt")).unwrap().iter().find(|e| e.rel == "data/raids.dat").unwrap().mtime));
    assert!(stamp.is_some() && expected.duration_since(SystemTime::UNIX_EPOCH).unwrap().as_secs() == u64::from(stamp.unwrap()));
}

#[test]
fn rotation_keeps_the_newest_automatic_backups() {
    let dir = tempfile::tempdir().unwrap();
    let base = chrono::Local::now() - chrono::Duration::days(10);
    for day in 0..7 {
        let name = store::file_name(base + chrono::Duration::days(day), Kind::Auto, 1);
        std::fs::write(dir.path().join(name), b"zip").unwrap();
    }
    std::fs::write(dir.path().join(store::file_name(base - chrono::Duration::days(30), Kind::Manual, 1)), b"m").unwrap();
    std::fs::write(dir.path().join(store::file_name(base - chrono::Duration::days(31), Kind::Safety, 1)), b"s").unwrap();
    std::fs::write(dir.path().join("fremd.zip"), b"x").unwrap();

    let removed = store::rotate(dir.path(), 3);
    assert_eq!(removed.len(), 4);
    let left = store::list(dir.path());
    let autos: Vec<_> = left.iter().filter(|b| b.kind == Kind::Auto).collect();
    assert_eq!(autos.len(), 3);
    assert_eq!(autos[0].id, store::file_name(base + chrono::Duration::days(6), Kind::Auto, 1), "die neuesten bleiben");
    assert!(left.iter().any(|b| b.kind == Kind::Manual) && left.iter().any(|b| b.kind == Kind::Safety));
    assert!(dir.path().join("fremd.zip").exists(), "fremde Dateien bleiben unberührt");
}

#[tokio::test]
async fn the_size_cap_never_removes_the_newest_backup_of_a_world() {
    let (_dir, paths, _instance, _saves) = setup().await;
    let base = chrono::Local::now() - chrono::Duration::days(5);
    let a = store::world_dir(&paths, "eins", "A");
    let b = store::world_dir(&paths, "zwei", "B");
    std::fs::create_dir_all(&a).unwrap();
    std::fs::create_dir_all(&b).unwrap();
    for day in 0..3 {
        std::fs::write(a.join(store::file_name(base + chrono::Duration::days(day), Kind::Auto, 1)), vec![0u8; 1000]).unwrap();
    }
    std::fs::write(b.join(store::file_name(base, Kind::Auto, 1)), vec![0u8; 1000]).unwrap();
    // 4000 Bytes, erlaubt 1500: es bleiben nur die neuesten je Welt.
    let freed = store::enforce_cap(&paths, 1500);
    assert_eq!(freed, 2000);
    assert_eq!(store::list(&a).len(), 1);
    assert_eq!(store::list(&a)[0].id, store::file_name(base + chrono::Duration::days(2), Kind::Auto, 1));
    assert_eq!(store::list(&b).len(), 1);
    assert_eq!(store::enforce_cap(&paths, 0), 0, "0 = keine Obergrenze");
}

/// Archiv mit beliebigen Einträgen (für Angriffe).
fn crafted_zip(path: &Path, entries: &[&str]) {
    let mut zip = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
    for name in entries {
        zip.start_file(*name, zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(b"boese").unwrap();
    }
    zip.finish().unwrap();
}

#[test]
fn zip_slip_and_other_tricks_are_refused() {
    let dir = tempfile::tempdir().unwrap();
    let outside = dir.path().join("evil.txt");
    for (i, bad) in [
        &["Welt/level.dat", "Welt/../../evil.txt"][..],
        &["Welt/level.dat", "../evil.txt"],
        &["/etc/evil.txt"],
        &["Welt/level.dat", "Welt\\..\\..\\evil.txt"],
        &["C:/evil.txt"],
        &["Welt/C:evil.txt"],
        &["Welt/CON"],
        &["Welt/level.dat", "Andere/level.dat"],
        &["level.dat"],
    ]
    .iter()
    .enumerate()
    {
        let zip = dir.path().join(format!("bad{i}.zip"));
        crafted_zip(&zip, bad);
        let target = dir.path().join(format!("ziel{i}"));
        assert!(archive::extract(&zip, &target, &mut |_, _| {}, &|| Ok(())).is_err(), "{bad:?}");
        assert!(!target.exists(), "{bad:?}: nichts entpackt");
        assert!(!outside.exists(), "{bad:?}: nichts außerhalb geschrieben");
    }

    // Links aus Unix-Archiven werden nie angelegt.
    let zip_path = dir.path().join("link.zip");
    let mut zip = zip::ZipWriter::new(std::fs::File::create(&zip_path).unwrap());
    zip.start_file("Welt/level.dat", zip::write::SimpleFileOptions::default()).unwrap();
    zip.write_all(b"lvl").unwrap();
    zip.add_symlink("Welt/link", "/etc/passwd", zip::write::SimpleFileOptions::default()).unwrap();
    zip.finish().unwrap();
    let target = dir.path().join("mit-link");
    let out = archive::extract(&zip_path, &target, &mut |_, _| {}, &|| Ok(())).unwrap();
    assert_eq!((out.top.as_str(), out.files), ("Welt", 1));
    assert!(std::fs::symlink_metadata(target.join("link")).is_err());
    assert_eq!(std::fs::read(target.join("level.dat")).unwrap(), b"lvl");
}

#[tokio::test]
async fn opening_the_world_during_the_backup_discards_it() {
    let (_dir, paths, instance, saves) = setup().await;
    let scope = scope_of(&paths, &instance);
    let world = saves.join("Welt");
    let baseline = lock::state(&world);
    let mut opened_once = false;
    // Das Spiel öffnet die Welt mitten im Packen (schreibt session.lock).
    let result = create_sync(&paths, &scope.key, &saves, "Welt", Kind::Auto, baseline, None, &|| false, &mut |_, _| {
        if !opened_once {
            opened_once = true;
            std::fs::write(world.join(lock::SESSION_LOCK), b"spiel").unwrap();
        }
    });
    let err = result.unwrap_err();
    assert_eq!(err.message_code(), "worldBackups.worldOpened");
    let dir = store::world_dir(&paths, &scope.key, "Welt");
    assert!(!dir.exists() || std::fs::read_dir(&dir).unwrap().count() == 0, "kein halbes Archiv bleibt liegen");

    // Abbrechen: ebenfalls nichts übrig.
    let baseline = lock::state(&world);
    let cancelled = create_sync(&paths, &scope.key, &saves, "Welt", Kind::Auto, baseline, None, &|| true, &mut |_, _| {});
    assert!(matches!(cancelled, Err(Error::Cancelled)));
    assert!(store::list(&dir).is_empty());
}

#[tokio::test]
async fn restore_as_copy_or_replace_with_a_safety_backup() {
    let (_dir, paths, instance, saves) = setup().await;
    let backups = WorldBackups::new(paths.clone());
    let before = archive::fingerprint(&archive::scan(&saves.join("Welt")).unwrap());
    let manual = backups.backup_now(&instance, "Welt", &[], |_| {}).await.unwrap();
    assert_eq!(manual.kind, Kind::Manual);

    // Weitergespielt …
    std::fs::write(saves.join("Welt/region/r.0.0.mca"), b"weitergespielt").unwrap();
    std::fs::write(saves.join("Welt/neu.dat"), b"neu").unwrap();

    // … als Kopie daneben: neuer Ordner mit dem alten Stand, eigener Name im Spiel.
    let copy = backups.restore(&instance, "Welt", &manual.id, RestoreMode::Copy, Some("Welt (restored 2026-10-27)"), &[], |_| {}).await.unwrap();
    assert_eq!(copy.folder, "Welt (restored 2026-10-27)");
    assert!(copy.safety_backup.is_none());
    let copied = saves.join(&copy.folder);
    assert_eq!(std::fs::read(copied.join("region/r.0.0.mca")).unwrap(), vec![3u8; 64 * 1024]);
    assert!(!copied.join("neu.dat").exists());
    let summary = crate::worlds::parse_level_dat(&std::fs::read(copied.join("level.dat")).unwrap()).unwrap();
    assert_eq!(summary.name.as_deref(), Some("Welt (restored 2026-10-27)"));
    assert_eq!(std::fs::read(saves.join("Welt/region/r.0.0.mca")).unwrap(), b"weitergespielt", "Original unverändert");

    // Ersetzen läuft nicht, solange das Spiel läuft.
    let game = RunningGame { instance_id: instance.id.clone(), key: instance.id.clone(), pid: 1, started_at: Utc::now() };
    assert!(backups.restore(&instance, "Welt", &manual.id, RestoreMode::Replace, None, &[game], |_| {}).await.is_err());

    // Reste eines abgebrochenen Wiederherstellens (Absturz) würde das Spiel als Welten zeigen.
    for stale in [".cubiq-restore-alt", ".cubiq-old-alt"] {
        std::fs::create_dir_all(saves.join(stale)).unwrap();
        std::fs::write(saves.join(stale).join("level.dat"), level_dat("Alt")).unwrap();
    }

    // Ersetzen: erst ein Sicherheits-Backup des jetzigen Stands, dann der alte Stand – mit Dateizeiten.
    let replaced = backups.restore(&instance, "Welt", &manual.id, RestoreMode::Replace, None, &[], |_| {}).await.unwrap();
    assert_eq!(replaced.folder, "Welt");
    let safety = replaced.safety_backup.unwrap();
    assert!(safety.ends_with("-safety.zip"));
    assert_eq!(std::fs::read(saves.join("Welt/region/r.0.0.mca")).unwrap(), vec![3u8; 64 * 1024]);
    assert!(!saves.join("Welt/neu.dat").exists());
    assert_eq!(archive::fingerprint(&archive::scan(&saves.join("Welt")).unwrap()), before, "Fingerabdruck passt wieder – kein doppeltes Backup");
    // Keine Zwischenordner übrig.
    assert!(std::fs::read_dir(&saves).unwrap().flatten().all(|e| !e.file_name().to_string_lossy().starts_with(".cubiq-")));

    // Das Sicherheits-Backup enthält den weitergespielten Stand und lässt sich ebenso zurückholen.
    let list = backups.list(&instance, "Welt").await.unwrap();
    assert_eq!(list.len(), 2);
    let undo = backups.restore(&instance, "Welt", &safety, RestoreMode::Copy, Some("Vorher"), &[], |_| {}).await.unwrap();
    assert_eq!(std::fs::read(saves.join(&undo.folder).join("neu.dat")).unwrap(), b"neu");

    // Gelöschte Welt: Wiederherstellen legt sie unter ihrem Ordner neu an.
    std::fs::remove_dir_all(saves.join("Welt")).unwrap();
    let back = backups.restore(&instance, "Welt", &manual.id, RestoreMode::Replace, None, &[], |_| {}).await.unwrap();
    assert_eq!((back.folder.as_str(), back.safety_backup), ("Welt", None));
    assert!(saves.join("Welt/level.dat").is_file());

    // Löschen eines Backups; unbekannte Kennungen werden abgelehnt.
    backups.delete(&instance, "Welt", &safety).await.unwrap();
    assert_eq!(backups.list(&instance, "Welt").await.unwrap().len(), 1);
    assert!(backups.delete(&instance, "Welt", "../../x.zip").await.is_err());
    assert!(backups.restore(&instance, "Welt", "fehlt-auto.zip", RestoreMode::Copy, None, &[], |_| {}).await.is_err());
}

#[test]
fn legacy_backups_from_the_game_folder_are_found() {
    let dir = tempfile::tempdir().unwrap();
    let legacy = dir.path().join(store::LEGACY_DIR);
    std::fs::create_dir_all(&legacy).unwrap();
    for name in ["welt1_2026-09-26_14-05-09.zip", "welt1_2026-09-27_10-00-00 (2).zip", "welt10_2026-09-26_14-05-09.zip", "welt1_kaputt.zip"] {
        std::fs::write(legacy.join(name), b"zip").unwrap();
    }
    let found = store::legacy(dir.path(), "welt1");
    assert_eq!(found.iter().map(|b| b.id.as_str()).collect::<Vec<_>>(), ["legacy:welt1_2026-09-27_10-00-00 (2).zip", "legacy:welt1_2026-09-26_14-05-09.zip"]);
    assert!(found.iter().all(|b| b.kind == Kind::Legacy));
}

#[test]
fn backup_names_round_trip() {
    use chrono::TimeZone;
    let when = chrono::Local.with_ymd_and_hms(2026, 10, 27, 14, 5, 9).unwrap();
    assert_eq!(store::file_name(when, Kind::Auto, 1), "2026-10-27_14-05-09-auto.zip");
    assert_eq!(store::file_name(when, Kind::Manual, 2), "2026-10-27_14-05-09-2-manual.zip");
    assert_eq!(store::parse_name("2026-10-27_14-05-09-2-manual.zip"), Some((when.with_timezone(&Utc), Kind::Manual)));
    for bad in ["2026-10-27_14-05-09-legacy.zip", "2026-10-27_14-05-09.zip", "x-auto.zip", "2026-10-27_14-05-09-x-auto.zip", ".2026-10-27_14-05-09-auto.zip.part"] {
        assert!(store::parse_name(bad).is_none(), "{bad}");
    }
}

#[tokio::test]
async fn the_launch_job_saves_changed_worlds_in_the_background() {
    let (_dir, paths, instance, saves) = setup().await;
    let backups = Arc::new(WorldBackups::new(paths.clone()));
    let events = Arc::new(Mutex::new(Vec::new()));
    let seen = events.clone();
    backups.set_sink(Arc::new(move |e| seen.lock().unwrap().push(e)));
    let settings = WorldBackupSettings::default();

    let wait = |backups: Arc<WorldBackups>| async move {
        for _ in 0..200 {
            if backups.jobs().is_empty() {
                return;
            }
            tokio::time::sleep(Duration::from_millis(20)).await;
        }
        panic!("Sicherung wurde nicht fertig");
    };
    backups.before_launch(&instance, &settings, &[]).await;
    wait(backups.clone()).await;
    {
        let events = events.lock().unwrap();
        assert!(matches!(events.first(), Some(BackupEvent::Started { reason: JobReason::Launch, worlds, .. }) if worlds == &["Meine Welt"]));
        assert!(matches!(events.last(), Some(BackupEvent::Finished { saved, skipped, failed, cancelled: false, .. }) if saved.len() == 1 && skipped.is_empty() && failed.is_empty()));
    }
    let overview = backups.overview(&instance, &settings).await.unwrap();
    assert_eq!((overview.worlds.len(), overview.worlds[0].count, overview.enabled), (1, 1, true));

    // Nichts geändert: kein neues Backup, keine Ereignisse.
    events.lock().unwrap().clear();
    backups.before_launch(&instance, &settings, &[]).await;
    wait(backups.clone()).await;
    assert!(events.lock().unwrap().is_empty());

    // Für diese Instanz ausgeschaltet: nichts passiert, auch wenn sich etwas geändert hat.
    std::fs::write(saves.join("Welt/region/r.0.0.mca"), b"neu").unwrap();
    backups.set_instance(&instance.id, Some(false)).await.unwrap();
    backups.before_launch(&instance, &settings, &[]).await;
    wait(backups.clone()).await;
    assert!(events.lock().unwrap().is_empty());
    let overview = backups.overview(&instance, &settings).await.unwrap();
    assert_eq!((overview.enabled, overview.instance), (false, Some(false)));

    // Wieder an: geänderte Welt wird gesichert, die Rotation hält höchstens `keep` automatische.
    backups.set_instance(&instance.id, None).await.unwrap();
    let keep_one = WorldBackupSettings { keep: 1, ..settings };
    backups.before_launch(&instance, &keep_one, &[]).await;
    wait(backups.clone()).await;
    let list = backups.list(&instance, "Welt").await.unwrap();
    assert_eq!(list.len(), 1);
}

#[tokio::test]
async fn usage_and_pruning_only_touch_backups_of_deleted_instances() {
    let (_dir, paths, instance, saves) = setup().await;
    let backups = WorldBackups::new(paths.clone());
    let scope = scope_of(&paths, &instance);
    backup_auto(&paths, &scope.key, &saves, "Welt").unwrap();
    let gone = store::world_dir(&paths, "geloescht", "Alt");
    std::fs::create_dir_all(&gone).unwrap();
    std::fs::write(gone.join(store::file_name(chrono::Local::now(), Kind::Auto, 1)), vec![0u8; 1234]).unwrap();

    let known: HashSet<String> = [instance.id.clone()].into();
    let usage = backups.usage(known.clone()).await;
    assert_eq!((usage.count, usage.worlds, usage.orphaned_bytes), (2, 2, 1234));
    assert_eq!(backups.prune_orphans(known.clone()).await.unwrap(), 1234);
    assert!(!store::scope_dir(&paths, "geloescht").exists());
    assert_eq!(backups.usage(known).await.count, 1, "die eigenen Backups bleiben");
}

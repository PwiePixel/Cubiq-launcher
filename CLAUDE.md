# Hinweise für Claude-Sitzungen in diesem Repo

## Branches ohne Merge-Konflikte

- **Nie `CHANGELOG.md` oder Versionsnummern ändern** (`package.json` → `version`, `src-tauri/tauri.conf.json`,
  `src-tauri/Cargo.toml`, `src-tauri/Cargo.lock`). Das macht nur der Release-Commit auf `main` – nach jedem grünen
  CI-Lauf. Jeder Branch, der dort schreibt, bekommt einen Merge-Konflikt.
- **Spieler-Text als eigene Datei:** Merkt ein Spieler die Änderung, gehört `changes/<thema>.md` dazu – Vorlage und
  Regeln in `changes/README.md`. Die CI-Prüfung „Player notes“ schlägt sonst fehl.
- **Vor jedem Push des fertigen Stands:** `git fetch origin main && git merge origin/main`, Konflikte selbst lösen,
  Tests laufen lassen. Nie einen PR mit Konflikt zurücklassen. Der Workflow `pr-sync.yml` mergt `main` nach jedem
  Merge automatisch in offene PRs; meldet er einen Konflikt, im eigenen Branch lösen.

## Spieler-Texte (Release-Hinweise)

- Für Spieler geschrieben, ausführlich und konkret: was neu oder behoben ist, wo man es findet, wie man es benutzt
  und was es bringt – wie ein kurzer Absatz je Punkt, nicht wie eine Commit-Zeile. Fett nur die Überschrift.
- Englisch und Deutsch, gleicher Inhalt. Keine Datei-, Funktions- oder Klassennamen.
- **Keine Namen, Erwähnungen oder Danksagungen** (auch nicht an Claude) – die Hinweise erklären nur das Update.
- **Fotos vom Update:** Bei sichtbaren Änderungen 1–3 Screenshots in `changes/` legen und im Spieler-Text eintragen
  (Kommentar `<!-- shots: … -->` über `### English`, siehe `changes/README.md`).
- Das Bild des Release (Motiv im Update-Banner) entsteht automatisch passend zum Text (`scripts/release-art.mjs`).

## Sonst

- Code-Kommentare überwiegend Deutsch, Commit-Nachrichten Englisch.
- Prüfungen vor dem Push: `pnpm test`, `pnpm typecheck`, für Rust `cargo clippy`/`cargo test`, für die Mod die
  Gradle-Tests (siehe `CONTRIBUTING.md#checks`).

# Spieler-Texte für das nächste Release

Jede Änderung, die Spieler bemerken, bringt hier **eine eigene Datei** mit: `changes/<thema>.md` (kurzer, eindeutiger
Name, z. B. `clip-trimming.md`). Das nächste Release übernimmt alle Dateien in `CHANGELOG.md` – der Launcher zeigt den
Text unter „Was ist neu“, GitHub und die Website im Release – und löscht sie danach. Bitte **nicht** direkt in
`CHANGELOG.md` schreiben: eigene Dateien kommen sich nie in die Quere, auch wenn mehrere Branches gleichzeitig
entstehen und auf `main` zwischendurch Releases erscheinen.

```markdown
### English

- **Short headline.** One or two sentences for players: what is new or fixed, where to find it and why it helps.

### Deutsch

- **Kurze Überschrift.** Ein, zwei Sätze für Spieler: was neu oder behoben ist, wo man es findet und was es bringt.
```

So schreiben:

- Für Spieler, nicht für Entwickler: was sich für sie ändert, in einfachen Worten – keine Datei-, Funktions- oder
  Klassennamen, keine Commit-Sprache („Refactor …“, „Add …“).
- Beide Sprachen, gleicher Inhalt. Fett nur die Überschrift des Punkts.
- Keine Namen, Erwähnungen oder Danksagungen – die Hinweise erklären nur das Update.
- Mehrere Punkte in einer Datei sind in Ordnung, wenn sie zu derselben Änderung gehören.
- Screenshots (freiwillig): Kommentar über `### English`, siehe `docs/release-screenshots.md`.

Keine Datei nötig für Änderungen, die Spieler nicht bemerken (Tests, CI, Doku, Umbauten) – dann den Commit mit
`refactor:`, `test:`, `ci:`, `docs:` oder `chore:` beginnen.

Das Bild des Release (Motiv im Update-Banner) entsteht automatisch passend zum Text (`scripts/release-art.mjs`).

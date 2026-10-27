# Changelog

Every version of the Cubiq Launcher is listed here – first in English, then in German. The release build copies
the section of its version into the GitHub release and the auto-update, and the launcher shows it once after
updating ("What's new").

<!--
How to write an entry:
- Collect changes under "## Unreleased" while working. For a release, rename it to "## <version> – <YYYY-MM-DD>".
- Every version needs both "### English" and "### Deutsch" with the same points. Write for players, not
  developers: what changed for them, in plain words, no file or function names.
- The release build fails when the section for its version is missing or one language is empty.
- Every release gets a theme name in the heading ("## 0.2.0 – 2026-11-30 – The Clip Update | Das Clip-Update")
  and an update banner right below it: an HTML comment with "banner: accent=#rrggbb motif=/news/0.2.0/banner.png".
  The banner keeps a fixed look (dark surface, a soft glow and a fine cube grid); only the accent colour and the
  motif (a picture with a transparent background, e.g. the Cubiq logo or an item) change.
- Right below the banner comes a second HTML comment starting with "shots:" – one screenshot of the new features per
  line: "/news/<version>/<file>.png | English caption | Deutsche Bildunterschrift" (captions optional; without the
  German one the English caption is used for both). PNG or WebP in public/news/<version>/, at most 8, each at most
  2 MB and 640×360 to 3840×2400 px. Every release needs at least one screenshot; the release check
  (scripts/changelog.mjs check) fails otherwise. How to take them: docs/release-screenshots.md.
- Pull requests: the CI check "changelog" wants at least one new point under "## Unreleased" in English AND German.
  Changes players don't notice (CI, refactoring, docs) get the label "no-changelog" instead.
- Contributors: the release build adds an HTML comment starting with "contributors:" to the section of its version
  (GitHub names of everyone who contributed since the previous release, see scripts/contributors.mjs). The launcher
  thanks them in "What's new", the GitHub release in "Thanks to / Danke an". Don't write it by hand; to keep the
  thanks in later launcher versions, commit it after the release with
  "node --experimental-strip-types scripts/changelog.mjs set-contributors <version> <name1,name2>".
-->

## 0.1.0 – 2026-10-27 – Hello, Cubiq | Hallo, Cubiq
<!-- banner: accent=#71a330 motif=/news/0.1.0/banner.png -->
<!-- shots:
/news/0.1.0/home.png | The new start page | Die neue Startseite
-->

### English
- **Cubiq Launcher is here.** The first version of the launcher under its own name, with its own logo, app icon and start screen.
- **A new, clean design.** Calm dark surfaces with the green from the logo, a clear play button that fills up while the game starts and glows while it runs, a quiet background with a fine cube grid, simple progress bars and friendly empty pages. The Inter font is used everywhere.
- **Colors your way.** Choose between dark, OLED, light and system color schemes and seven accent colors – Cubiq green is the default.
- **Short logo start animation.** The Cubiq logo fades in briefly when the launcher opens; click or press any key to skip it, or turn it off in Settings → Appearance.
- **Online features stay off for now.** Friends, chat, capes and other online features need a Cubiq server. Until one is set up for this build, they are hidden and the launcher sends nothing anywhere for them.

### Deutsch
- **Der Cubiq Launcher ist da.** Die erste Version des Launchers unter eigenem Namen – mit eigenem Logo, App-Symbol und Startbildschirm.
- **Ein neues, schlichtes Design.** Ruhige, dunkle Flächen mit dem Grün aus dem Logo, ein klarer Spielen-Knopf, der sich beim Spielstart füllt und leuchtet, solange das Spiel läuft, ein dezenter Hintergrund mit feinem Würfel-Raster, schlichte Fortschrittsbalken und freundliche leere Seiten. Überall kommt die Schrift Inter zum Einsatz.
- **Farben nach deinem Geschmack.** Wähle zwischen dunklem, OLED-, hellem und System-Farbschema und sieben Akzentfarben – ab Werk Cubiq-Grün.
- **Kurze Start-Animation mit dem Logo.** Beim Öffnen blendet kurz das Cubiq-Logo ein; ein Klick oder eine beliebige Taste überspringt sie, ausschalten lässt sie sich unter Einstellungen → Aussehen.
- **Online-Funktionen bleiben vorerst aus.** Freunde, Chat, Umhänge und andere Online-Funktionen brauchen einen Cubiq-Server. Solange für diesen Build keiner eingerichtet ist, sind sie ausgeblendet, und der Launcher schickt dafür nichts irgendwohin.

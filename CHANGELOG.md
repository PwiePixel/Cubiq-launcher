# Changelog

Every version of the Cubiq Launcher is listed here – first in English, then in German. New sections are written
automatically for every release. The release build copies the section of its version into the GitHub release and the
auto-update, and the launcher shows it once after updating ("What's new").

<!--
How this file is written:
- Automatically: after every green CI run on main, the workflow "Auto release" (.github/workflows/auto-release.yml,
  scripts/release.mjs) adds the section of the next version – from the player notes in changes/ (one file per
  change, see changes/README.md), otherwise from the first line of every commit since the last release (lines "EN: …"
  and "DE: …" in the commit message give the text per language). Details: CONTRIBUTING.md, "Release notes".
- Please don't edit this file in a branch: the release commits on main change it all the time, so every branch that
  writes here ends up with a merge conflict. Add a file in changes/ instead – the next release moves it here.
- The notes explain the update to players in plain words – what changed for them, no file or function names and no
  names, mentions or thanks.
- A release can have a theme name in the heading ("## 0.2.0 – 2026-11-30 – The Clip Update | Das Clip-Update"); it
  becomes the title of the GitHub release ("Cubiq Launcher 0.2.0 – The Clip Update").
- Right below the heading comes the update banner, an HTML comment "banner: accent=#71a330 motif=/news/<version>/
  banner.png anim=/news/<version>/banner-anim.png". The release automation draws the picture for every version to
  match its notes – a small pixel-art scene, still and animated (scripts/release-art.mjs, transparent PNGs in
  public/news/<version>/); the banner keeps a fixed look (dark surface, soft glow, fine cube grid).
- Optional screenshots: a second HTML comment starting with "shots:" – one screenshot per line:
  "/news/<version>/<file>.png | English caption | Deutsche Bildunterschrift" (captions optional; without the German one
  the English caption is used for both). PNG or WebP in public/news/<version>/, at most 8, each at most 2 MB and
  640×360 to 3840×2400 px. How to take them: docs/release-screenshots.md.
- The release build fails when the section for its version is missing, one language is empty or the banner is missing
  (scripts/changelog.mjs check <version>).
-->

## 0.1.1 – 2026-10-27
<!-- banner: accent=#71a330 motif=/news/0.1.1/banner.png anim=/news/0.1.1/banner-anim.png -->

### English
- **The Cubiq Client gets the launcher's look.** The in-game mod now looks like it belongs to the launcher: its menus, the title screen and the intro when the game starts use the same calm green design – dark surfaces, flat rounded buttons and the Cubiq logo. There is nothing to set up; you'll see it the next time you start Minecraft with the Cubiq Client.

### Deutsch
- **Der Cubiq Client bekommt den Look des Launchers.** Die Ingame-Mod sieht jetzt aus, als gehöre sie zum Launcher: Ihre Menüs, der Titelbildschirm und das Intro beim Spielstart haben dasselbe ruhige, grüne Design – dunkle Flächen, flache abgerundete Knöpfe und das Cubiq-Logo. Einstellen musst du nichts; beim nächsten Start von Minecraft mit dem Cubiq Client ist es da.

## 0.1.0 – 2026-10-27 – Hello, Cubiq | Hallo, Cubiq
<!-- banner: accent=#71a330 motif=/news/0.1.0/banner.png anim=/news/0.1.0/banner-anim.png -->
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

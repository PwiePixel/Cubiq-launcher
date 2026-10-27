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

## 0.2.5 – 2026-10-27
<!-- banner: accent=#71a330 motif=/news/0.2.5/banner.png anim=/news/0.2.5/banner-anim.png -->

<!-- shots:
/news/0.2.5/new-look-home.png | The new home screen with the stage and "Jump back in" | Der neue Startbildschirm mit Bühne und „Weiterspielen“
/news/0.2.5/new-look-library.png | The library with framed tiles | Die Bibliothek mit gerahmten Kacheln
/news/0.2.5/new-look-instance.png | An instance page with its header card and tabs | Eine Instanz-Seite mit Kopfkarte und Reitern
-->
<!-- shots:
/news/0.2.5/winter-design-home.png | The home screen in the Northern Lights design | Der Startbildschirm im Design „Polarlicht“
/news/0.2.5/winter-design-settings.png | Choosing the design under Settings → Appearance | Die Wahl des Designs unter Einstellungen → Aussehen
/news/0.2.5/winter-design-game.png | The Cubiq Client title screen in the same design | Der Startbildschirm des Cubiq Client im selben Design
-->

### English
- **Every update gets its own animated picture.** In "What's new", on the update card and in the news, each version
  now shows a little pixel-art scene instead of the logo: Cubi, the green cube, on a floating grass island next to
  something that fits the update – a rocket for a big launch, a wrench for fixes, a screen for the Cubiq Client and
  more. Cubi bobs and blinks, sparkles twinkle and the objects move a little. If you chose "Reduce animations" under
  Settings → Appearance → Animations (or your system asks for less motion), you see the same picture standing still.
- **A new look in the colours of the Cubiq logo.** The launcher no longer uses grey-green surfaces: everything now sits
  on the deep navy blue of the Cubiq logo, and the logo's green is the one colour for buttons, the page you are on and
  anything you can select. The surfaces are clearly layered – window frame, page, cards and dialogs each sit one step
  higher – corners are softly rounded like the logo, headings use a new, slightly wider typeface, and a small green
  cube marks page and section titles. The light and OLED colour schemes and every accent colour come along; under
  Settings → Appearance, small previews show what each scheme looks like.
- **Navigation with names and groups.** The sidebar now shows the Cubiq logo at the top and every area with its name,
  sorted into the groups Together, Collection and Tools; the page you are on is a green bar. Below that, Quick launch
  lists your most recently played instances – point at one and press its small play button to start it right away –
  followed by "New instance". Your account sits at the bottom and opens the account menu, the gear next to it opens
  the settings. If you prefer the slim bar, the button next to the logo folds the sidebar into icons with tooltips and
  back. The search moved to the middle of the top bar and still opens with Ctrl+K.
- **A new home screen.** Your last played instance now stands on a stage: its icon, a big name, version and play time,
  a large round Play button and an arrow that opens the instance. Underneath, "Jump back in" shows your recent
  instances in a row – click one to put it on the stage, or use its small play button to start it straight away; the
  plus creates a new instance. Further down, the news and your servers sit side by side when the window is wide enough.
- **Library tiles and instance pages.** In the library every instance is a framed tile: its picture with loader and
  version on top, the ⋮ menu in the corner and the play button when you point at it, with icon, name and when you last
  played below. An instance page now opens with a header card – icon, name, version, play time, Play, settings and
  folder – and its tabs (Content, Files, Worlds and more) sit right inside that card.
- **Dialogs, settings and more in the same style.** Dialogs, the search window and the settings use the same navy
  surfaces, and the settings section you are in is green, just like in the sidebar. Choices such as the mod loader, the
  FPS boost level or playing with or without the Cubiq Client are tiles with a green edge and a small diamond instead
  of a glowing dot. Buttons that delete or remove something and warnings such as "too large" are now clearly red, so
  they no longer look like the green actions. Big Picture mode, the update screen, the touch controls on phones and the
  menus of the Cubiq Client in the game follow the new colours too.
- **New design: Northern Lights.** Under Settings → Appearance → Design you can now choose between the familiar Cubiq
  look and the new winter design "Northern Lights". The two previews show the real scenes, so you see what you get
  before you click. Your color theme (Dark, OLED, Light or System) stays as it is. If you still use the design's own
  accent color, it changes along with the design – to Ice for Northern Lights and back to Cubiq green when you switch
  back – while any other accent color you picked stays. The design is saved with your settings and synced with your
  Cubiq account, so your other PCs and the Cubiq Client follow along.
- **A winter night in the launcher.** With Northern Lights the launcher sits under a deep night-blue sky. Bands of
  aurora in Cubiq green, fading into teal and icy cyan, slowly wave across the top of the home screen. Below them,
  small islands of snow-covered cubes with little cube pine trees float above a sea of clouds, and square pixel
  snowflakes drift down at three depths. The same scene shows behind all other pages while "Animated background" is
  on, and instances without their own banner get a matching winter picture. The details follow along: cards and menus
  look like frosted glass, a rim of pixel snow lies on top of cards, menus and the Play button, the Play button fills
  with ice while the game starts, progress bars carry an icy shimmer, everything that is running glows in ice cyan,
  and the logo and the small cubes in front of headings have snow on top. With the Light color theme the scene turns
  into a bright winter morning, with OLED the sky is pure black. If you chose "Reduce animations" under Settings →
  Appearance → Animations (or your system asks for less motion), you get a calm still picture and nothing shimmers.
- **The same winter in the game.** The Cubiq Client takes the design into Minecraft – the launcher passes it along
  when it starts the game. The title screen, the loading screens and the menus you open from there (server list, world
  list, options) show the same night sky with the same aurora, snow islands and snowfall as the launcher: the game
  calculates the scene exactly like the launcher does, so both look alike. Buttons, windows and the pause menu get the
  snow rim, the Cubiq sign has snow on its upper edges, loading bars shimmer icy, and your figure on the title screen
  stands on a snowy block with icicles. You can also switch the design in the game – Cubiq Menu → Title Screen or Menu
  Style – until the launcher passes its own choice again on the next start. "Animated background" on the title screen
  and "Reduce animations" in the launcher give you a still picture in the game too.

### Deutsch
- **Jedes Update bekommt ein eigenes, animiertes Bild.** Unter „Was ist neu“, auf der Update-Karte und in den
  Neuigkeiten zeigt jede Version jetzt statt des Logos eine kleine Pixel-Art-Szene: Cubi, der grüne Würfel, auf einer
  schwebenden Grasinsel neben etwas, das zum Update passt – eine Rakete für einen großen Start, ein Schraubenschlüssel
  für Fehlerbehebungen, ein Bildschirm für den Cubiq Client und mehr. Cubi wippt und blinzelt, es funkelt, und die
  Gegenstände bewegen sich ein wenig. Hast du unter Einstellungen → Aussehen → Animationen „Animationen reduzieren“
  gewählt (oder wünscht dein System weniger Bewegung), siehst du dasselbe Bild ruhig.
- **Ein neuer Look in den Farben des Cubiq-Logos.** Der Launcher hat keine grau-grünen Flächen mehr: Alles liegt jetzt
  auf dem tiefen Navyblau des Cubiq-Logos, und das Grün aus dem Logo ist die eine Farbe für Knöpfe, die Seite, auf der
  du bist, und alles, was du auswählen kannst. Die Flächen sind klar gestuft – Fensterrahmen, Seite, Karten und Dialoge
  liegen jeweils eine Stufe höher –, die Ecken sind weich gerundet wie beim Logo, Überschriften haben eine neue, etwas
  breitere Schrift, und ein kleiner grüner Würfel markiert Seiten- und Abschnittstitel. Das helle und das
  OLED-Farbschema und alle Akzentfarben ziehen mit; unter Einstellungen → Aussehen zeigen kleine Vorschauen, wie jedes
  Farbschema aussieht.
- **Navigation mit Namen und Gruppen.** Die Seitenleiste zeigt oben das Cubiq-Logo und jeden Bereich mit seinem
  Namen, sortiert in die Gruppen Gemeinsam, Sammlung und Werkzeuge; die Seite, auf der du bist, ist ein grüner Balken.
  Darunter listet der Schnellstart deine zuletzt gespielten Instanzen – zeig auf eine und drück ihren kleinen
  Spielen-Knopf, um sie sofort zu starten –, danach folgt „Neue Instanz“. Dein Konto sitzt ganz unten und öffnet das
  Konto-Menü, das Zahnrad daneben die Einstellungen. Magst du lieber die schmale Leiste, klappt der Knopf neben dem Logo
  die Seitenleiste zu Symbolen mit Kurzhinweisen zusammen und wieder auf. Die Suche ist in die Mitte der oberen Leiste
  gewandert und öffnet sich weiterhin mit Strg+K.
- **Ein neuer Startbildschirm.** Deine zuletzt gespielte Instanz steht jetzt auf einer Bühne: ihr Bild, ein großer
  Name, Version und Spielzeit, ein großer runder Spielen-Knopf und ein Pfeil, der die Instanz öffnet. Darunter zeigt
  „Weiterspielen“ deine letzten Instanzen in einer Reihe – ein Klick holt eine davon auf die Bühne, ihr kleiner
  Spielen-Knopf startet sie gleich; das Plus legt eine neue Instanz an. Weiter unten stehen Neuigkeiten und deine Server
  nebeneinander, sobald das Fenster breit genug ist.
- **Kacheln in der Bibliothek und Instanz-Seiten.** In der Bibliothek ist jede Instanz eine gerahmte Kachel: ihr Bild
  mit Loader und Version darauf, das ⋮-Menü in der Ecke und der Spielen-Knopf, sobald du darauf zeigst, darunter Bild,
  Name und wann du zuletzt gespielt hast. Eine Instanz-Seite beginnt jetzt mit einer Kopfkarte – Bild, Name, Version,
  Spielzeit, Spielen, Einstellungen und Ordner –, und ihre Reiter (Inhalte, Dateien, Welten und mehr) sitzen direkt in
  dieser Karte.
- **Dialoge, Einstellungen und mehr im selben Stil.** Dialoge, das Suchfenster und die Einstellungen haben dieselben
  Navy-Flächen, und der Bereich der Einstellungen, in dem du bist, ist grün – wie in der Seitenleiste. Auswahlen wie der
  Mod-Loader, die FPS-Boost-Stufe oder Spielen mit oder ohne Cubiq Client sind Kacheln mit grüner Kante und einer
  kleinen Raute statt eines leuchtenden Punkts. Knöpfe, die etwas löschen oder entfernen, und Warnungen wie „zu groß“
  sind jetzt deutlich rot und sehen nicht mehr wie die grünen Aktionen aus. Der Big-Picture-Modus, der
  Update-Bildschirm, die Touch-Steuerung am Handy und die Menüs des Cubiq Client im Spiel tragen ebenfalls die neuen
  Farben.
- **Neues Design: Polarlicht.** Unter Einstellungen → Aussehen → Design wählst du jetzt zwischen dem gewohnten
  Cubiq-Look und dem neuen Winter-Design „Polarlicht“. Die beiden Vorschauen zeigen die echten Szenen – du siehst vor
  dem Klick, was dich erwartet. Dein Farbschema (Dunkel, OLED, Hell oder System) bleibt, wie es ist. Nutzt du noch die
  Akzentfarbe des Designs, wechselt sie mit – zu Eis beim Polarlicht und zurück zu Cubiq-Grün, wenn du zurückwechselst
  –, eine andere Akzentfarbe, die du gewählt hast, bleibt. Das Design wird mit deinen Einstellungen gespeichert und
  mit deinem Cubiq-Konto abgeglichen, deine anderen PCs und der Cubiq Client ziehen also mit.
- **Eine Winternacht im Launcher.** Mit „Polarlicht“ liegt der Launcher unter einem tiefen, nachtblauen Himmel.
  Polarlicht-Bänder in Cubiq-Grün, die in Türkis und eisiges Cyan übergehen, wogen langsam oben über den
  Startbildschirm. Darunter schweben kleine Inseln aus verschneiten Würfeln mit kleinen Würfel-Tannen über einem
  Wolkenmeer, und eckige Pixel-Schneeflocken fallen in drei Tiefen. Dieselbe Szene liegt hinter allen anderen Seiten,
  solange „Animierter Hintergrund“ an ist, und Instanzen ohne eigenes Titelbild bekommen ein passendes Winterbild. Die
  Details ziehen mit: Karten und Menüs wirken wie mattes Eisglas, ein Saum aus Pixel-Schnee liegt oben auf Karten,
  Menüs und dem Spielen-Knopf, der Spielen-Knopf füllt sich beim Start mit Eis, Fortschrittsbalken tragen einen
  eisigen Schimmer, alles, was gerade läuft, leuchtet in Eis-Cyan, und auf dem Logo und den kleinen Würfeln vor
  Überschriften liegt Schnee. Im Farbschema Hell wird die Szene zu einem hellen Wintermorgen, mit OLED ist der Himmel
  tiefschwarz. Hast du unter Einstellungen → Aussehen → Animationen „Animationen reduzieren“ gewählt (oder wünscht
  dein System weniger Bewegung), siehst du ein ruhiges Standbild, und nichts schimmert.
- **Derselbe Winter im Spiel.** Der Cubiq Client nimmt das Design mit in Minecraft – der Launcher gibt es beim
  Spielstart weiter. Der Startbildschirm, die Ladebildschirme und die Menüs, die du von dort öffnest (Serverliste,
  Weltenliste, Einstellungen), zeigen denselben Nachthimmel mit demselben Polarlicht, denselben Schneeinseln und
  demselben Schneefall wie der Launcher: Das Spiel berechnet die Szene genau wie der Launcher, deshalb sehen beide
  gleich aus. Knöpfe, Fenster und das Pausenmenü bekommen den Schneesaum, das Cubiq-Zeichen trägt Schnee auf den
  oberen Kanten, Ladebalken schimmern eisig, und deine Figur auf dem Startbildschirm steht auf einem verschneiten
  Block mit Eiszapfen. Im Spiel kannst du das Design auch selbst wechseln – Cubiq-Menü → Startbildschirm oder
  Menü-Stil –, bis der Launcher beim nächsten Start wieder seine Wahl übergibt. „Animierter Hintergrund“ beim
  Startbildschirm und „Animationen reduzieren“ im Launcher sorgen auch im Spiel für ein ruhiges Standbild.

## 0.2.0 – 2026-10-27
<!-- banner: accent=#71a330 motif=/news/0.2.0/banner.png anim=/news/0.2.0/banner-anim.png -->

### English
- **Green where it belongs.** A few spots still showed red although nothing was wrong: the storage bar on the clips
  page, presets without their own color and the hint in the touch-controls editor. They now use the Cubiq green, so
  red only ever means a real problem.
- **Clearer message when a shared mod file is missing.** When you join a friend's world that uses mods and the host
  doesn't share one of the files (anymore), the launcher now tells you exactly that instead of only saying
  "connection closed" – so you know to ask your friend to share the file again.
- **Calmer animations in the game, too.** If you chose "Reduce animations" under Settings → Appearance → Animations –
  or "Follow Windows/system" while your system asks for less motion – the Cubiq Client now follows along: the
  backgrounds of the title screen, the menus and the loading screens stand still, the intro appears without fading or
  sliding in, and progress bars glow evenly instead of running. Your own in-game setting for animated backgrounds
  stays exactly as it is.

### Deutsch
- **Grün, wo es hingehört.** Ein paar Stellen waren noch rot, obwohl nichts falsch war: der Speicherplatz-Balken auf
  der Clips-Seite, Presets ohne eigene Farbe und der Hinweis im Editor für die Touch-Steuerung. Sie sind jetzt
  Cubiq-grün – Rot bedeutet damit immer ein echtes Problem.
- **Klarere Meldung, wenn eine geteilte Mod-Datei fehlt.** Trittst du der Welt eines Freundes mit Mods bei und der
  Host teilt eine der Dateien nicht (mehr), sagt dir der Launcher jetzt genau das statt nur „Verbindung getrennt“ –
  so weißt du, dass dein Freund die Datei wieder freigeben muss.
- **Ruhigere Animationen auch im Spiel.** Hast du unter Einstellungen → Aussehen → Animationen „Animationen
  reduzieren“ gewählt – oder „Wie Windows/System“, während dein System weniger Bewegung wünscht –, macht der Cubiq
  Client jetzt mit: Die Hintergründe von Titelbildschirm, Menüs und Ladebildschirmen stehen still, das Intro erscheint
  ohne Ein- und Ausblenden, und Fortschrittsbalken leuchten gleichmäßig, statt zu laufen. Deine eigene Einstellung im
  Spiel für animierte Hintergründe bleibt dabei genau so, wie sie ist.

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

# Release screenshots

A launcher release can show screenshots of its new features: in the launcher (update card, news, "What's new" – a
dialog with banner, gallery and notes), on the website blog and in the GitHub release notes. They are optional, but
every visible change should bring one to three.

The update banner gets its picture automatically: the release draws a small pixel-art scene that matches the notes –
Cubi, the cube from the logo, on a floating grass island next to an object for the topic (a rocket for a launch, a
wrench for fixes, a screen for the Cubiq Client, a snowman in winter …). `scripts/release-art.mjs` writes a still
picture (`public/news/<version>/banner.png`) and the same scene animated (`banner-anim.png`, an animated PNG) and puts
both into the banner line: `motif=/news/<version>/banner.png anim=/news/<version>/banner-anim.png`. The launcher and
the website play the animation and show the still picture when animations are reduced. Replace the files with your own
pictures (transparent background) if you like; preview all topics with
`node scripts/release-art.mjs --sheet <folder>`.

## With your player note (branches)

Put the images next to your note in `changes/` and list them by file name in a comment at the top of the note, above
`### English` – captions English | German (both optional; without German the English caption is used for both):

```md
<!-- shots:
clip-gallery.png | The clip gallery | Die Clip-Galerie
clip-trim.png | Trimming a clip | Einen Clip zuschneiden
-->

### English

- **Trim your clips.** …
```

The release moves the files to `public/news/<version>/` and rewrites the paths; the CI check "Player notes" makes sure
the files exist and fit the rules below.

- PNG or WebP, at most **8** per release, each at most **2 MB**, between **640×360** and **3840×2400** px.
- Launcher pages: 1280×800. In-game: the autotest size (854×480) or larger.
- Only made-up test data – no real account names, e-mail addresses, tokens or private paths.
- File names: letters, digits, `.`, `_`, `-`. Captions must not contain `[ ] < >` or `|`, max. 160 characters.

## Later, on `main`

For a release that is already out, save the images as `public/news/<version>/<name>.png` and list them in
`CHANGELOG.md` right below the banner line of the version:

```md
## 0.2.0 – 2026-10-01 – The Clip Update | Das Clip-Update
<!-- banner: accent=#71a330 motif=/news/0.2.0/banner.png anim=/news/0.2.0/banner-anim.png -->
<!-- shots:
/news/0.2.0/clip-gallery.png | The clip gallery | Die Clip-Galerie
-->
```

A single line works too, entries separated by `;`. Check: `node scripts/changelog.mjs check <version>` and
`npx vitest run --dir tests` (`tests/changelog.test.ts` validates the screenshots of every release). The GitHub
release text follows automatically (workflow "Release notes").

## Launcher pages (nuxt dev + headless Chrome)

The launcher UI runs in a normal browser when the Tauri commands are mocked (`scripts/release-shots/tauri-mock.js`).
Always use a **separate** Chrome instance with its own profile – never the everyday browser.

```sh
npx nuxt prepare                       # once per checkout
npx nuxt dev --port 3947 --host 127.0.0.1
```

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --remote-debugging-port=9471 `
  --user-data-dir="$env:TEMP\cubiq-release-shots" --no-first-run --mute-audio --window-size=1280,800 about:blank
```

```sh
# load the page with the mock (version / language / last seen version for "What's new")
node scripts/release-shots/cdp.mjs 9471 load http://127.0.0.1:3947/clips '{"version":"0.2.0","locale":"en"}'
# own test data for the page: a file that sets window.__MOCK_RESPONSES = { command: (args) => answer }
node scripts/release-shots/cdp.mjs 9471 load http://127.0.0.1:3947/clips '{"locale":"en"}' --extra my-clips-mock.js
# click, scroll, press keys, then take the picture
node scripts/release-shots/cdp.mjs 9471 run "document.querySelector('button.play')?.click()"
node scripts/release-shots/cdp.mjs 9471 key ArrowRight
node scripts/release-shots/cdp.mjs 9471 shot public/news/0.2.0/clip-gallery.png
```

Afterwards stop only the Chrome and nuxt processes you started (by PID). Unknown commands return `null`/`[]`;
anything that would start a game is blocked by the mock.

## In-game (Cubiq Client autotests)

The mod's autotests take screenshots on their own:

```sh
cd client-mod
./gradlew :fabric:1.21.1:runClient -PcubiqAutotest                        # full run
./gradlew :fabric:1.21.1:runClient -PcubiqAutotest -PcubiqAutotestOnly=menus # only one part (menus, wardrobe, maps, redstone, clips, …)
```

- Pictures land in the run folder of that version project: `run/screenshots/cubiqclient-<minecraft>-*.png`.
- Runs must be **silent** (`ALSOFT_DRIVERS=null`, master volume 0 – the autotest sets it) and **one at a time**.
- New feature without a matching screenshot step? Add a `shot(mc, "cubiqclient-<name>")` step to the autotest.

Copy the best picture per feature next to your player note in `changes/` and list it in its `shots:` comment.

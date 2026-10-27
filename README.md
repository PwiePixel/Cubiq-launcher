<div align="center">

[**English**](README.md) · [Deutsch](README.de.md)

<img src="docs/logo.png" alt="Cubiq Launcher" width="96" height="96" />

# Cubiq Launcher

**A fast, modern Minecraft: Java Edition launcher for Windows and Linux, with a built-in client for FPS, HUD and PvP features.**

[![Latest release](https://img.shields.io/github/v/release/PwiePixel/Cubiq-launcher?include_prereleases&sort=semver&label=release&color=71a330)](https://github.com/PwiePixel/Cubiq-launcher/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Tauri 2](https://img.shields.io/badge/Tauri-2-24C8DB?logo=tauri&logoColor=white)](https://tauri.app)
[![Nuxt 4](https://img.shields.io/badge/Nuxt-4-00DC82?logo=nuxt&logoColor=white)](https://nuxt.com)

[Download](https://github.com/PwiePixel/Cubiq-launcher/releases) ·
[Docs](https://github.com/PwiePixel/Cubiq-launcher/wiki) ·
[Features](#features) ·
[Before the first release](#before-the-first-release) ·
[Building](#building-from-source) ·
[Releases](#releases)

<img src="public/news/0.1.0/home.png" alt="Cubiq Launcher start page" width="860" />

</div>

> [!NOTE]
> Cubiq Launcher is in early development (version 0.1.0). It is based on
> [TRS Launcher](https://github.com/theredstonee/TRS-Launcher) by Ohev Tamerin (Theredstonee) and, like it, licensed
> under the GPL-3.0. What was changed is listed in [NOTICE](NOTICE).

## Features

**Playing**
- Every Minecraft version from 1.5.2 to the latest release, including snapshots
- **Fabric, Quilt, Forge and NeoForge**, installed by the launcher itself
- Automatic Java: the right Mojang runtime is downloaded for each version
- **Boost** – vanilla instances can start with Fabric, the Cubiq Client and proven performance mods (Sodium, Lithium,
  FerriteCore, …); turn it off per instance for real vanilla
- Games keep running when you close the launcher and are picked up again on the next start

**Cubiq Client (in-game mod)**
- For Fabric/Quilt, Forge and NeoForge, installed automatically in every matching instance
- In-game menu (Right Shift), HUD editor, minimap and waypoints, zoom, freelook, chat improvements and PvP displays
- Fair play: no reach or hitbox changes, no auto-clicking; the minimap only shows loaded chunks

**Content**
- Browse and install mods, modpacks, resource packs, data packs and shaders from **Modrinth** (and CurseForge with an
  API key), dependencies included
- Import instances from the official launcher, Modrinth App, CurseForge, Prism Launcher, MultiMC or any folder
- Export any instance as a `.mrpack`

**Everything else**
- **Clean design** in the green of the Cubiq logo: dark, OLED, light or system color scheme and seven accent colors
- Multiple Microsoft accounts, tokens encrypted on your PC
- Background tasks with pause, resume and cancel, server list with ping, skins & capes with a 3D preview, screenshot
  gallery, clips & recording (Windows), crash diagnosis and log sharing, command palette (Ctrl+K)
- **Silent, signed updates** that install on the next restart
- Optional **Cubiq services** (friends, chat, capes, …) – they need your own server and stay **off** until one is
  configured; see [PRIVACY.md](PRIVACY.md)

## Installation

### Windows

1. Download `Cubiq-Launcher_<version>_x64-setup.exe` from the [releases page](https://github.com/PwiePixel/Cubiq-launcher/releases).
2. Run it. It installs for your Windows user only and needs no administrator rights.
3. Sign in with the Microsoft account that owns Minecraft.

> [!TIP]
> The installer isn't code-signed yet, so Windows SmartScreen may show "Windows protected your PC". Choose
> **More info → Run anyway**.

Your data lives in `%APPDATA%\Cubiq-Launcher`.

### Linux

Every release has an **AppImage**, a **.deb** and an **.rpm** for x86_64.

| Distribution | Install |
|---|---|
| **Debian, Ubuntu**, Linux Mint, Pop!_OS | `sudo apt install ./Cubiq-Launcher_<version>_amd64.deb` |
| **Fedora**, openSUSE | `sudo dnf install ./Cubiq-Launcher-<version>-1.x86_64.rpm` |
| **Any distribution** | `chmod +x Cubiq-Launcher_<version>_amd64.AppImage && ./Cubiq-Launcher_<version>_amd64.AppImage` |
| **Arch Linux** | AUR recipes in [`packaging/aur`](packaging/aur) (not published yet) |

Your data lives in `~/.local/share/Cubiq-Launcher`. More in the [Linux guide](docs/wiki/Linux.md).

## Fair play

- Sign-in works **only with a legitimately owned Microsoft account**, using Microsoft's official OAuth 2.0 flow.
- There is **no offline or "cracked" mode**, and ownership checks are never bypassed.
- Game files are **not redistributed**; they are downloaded from Mojang's servers directly to your PC.

## Before the first release

These values are your own and are set when building – nothing of the original project is used. Without them the
matching feature stays off, the rest of the launcher works.

| What | Where | Without it |
|---|---|---|
| **Microsoft sign-in**: client ID of your own Azure app ("Personal Microsoft accounts only", platform "Mobile and desktop applications" with redirect `http://localhost/login`, "Allow public client flows" on), [approved by Mojang](https://aka.ms/mce-reviewappid) for the Minecraft API | Repository variable `CUBIQ_MSA_CLIENT_ID` | No sign-in (development builds start the demo) |
| **Update signing key**: already created (key ID `A427CF588D4DF86A`); the public key is in `src-tauri/tauri.conf.json` and `src-tauri/crates/core/src/client_mod_update.rs` | Secrets `TAURI_SIGNING_PRIVATE_KEY` (content of the key file) and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | Installers without update signature – installed launchers don't update themselves |
| **Cubiq services** (friends, chat, capes, creator codes, …): base URL of your own server implementing the API | Repository variable `CUBIQ_API_BASE`; for the in-game mod also `DEFAULT_API` in `client-mod/common/src/main/java/dev/pwiepixel/cubiqclient/core/online/OnlineConfig.java` | Services hidden, nothing is sent |
| **Discord status**: application ID of your Discord app | Repository variable `CUBIQ_DISCORD_APP_ID` | No Discord status |
| **CurseForge**: API key from the CurseForge console | Secret `CURSEFORGE_API_KEY` | Only Modrinth |
| **Cubiq Client jars**: `./gradlew collectLauncherJars` in `client-mod/`, then `node scripts/publish-client-mod.mjs --dry-run` | `src-tauri/resources/client-mod/` (`builds.json` is empty for now) | No bundled in-game mod |
| **Docs**: the pages in [`docs/wiki`](docs/wiki) go into the GitHub wiki | GitHub → Wiki | Help links lead to an empty wiki |
| **Android** (optional): release keystore | Secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Unsigned APK |

Locally the same values are read from environment variables of the same name while building
(`CUBIQ_MSA_CLIENT_ID=… pnpm app:build`).

## Building from source

**Requirements:** Node.js 22+ with pnpm and Rust (stable). On Windows the Visual Studio Build Tools ("Desktop
development with C++") and WebView2; on Linux the WebKitGTK/GTK development packages:

```sh
# Debian/Ubuntu
sudo apt install build-essential curl file pkg-config libssl-dev libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev librsvg2-dev libxdo-dev
# Arch
sudo pacman -S --needed base-devel webkit2gtk-4.1 gtk3 libayatana-appindicator librsvg openssl xdotool
# Fedora
sudo dnf install gcc-c++ openssl-devel webkit2gtk4.1-devel gtk3-devel libappindicator-gtk3-devel librsvg2-devel libxdo-devel
```

```sh
pnpm install
pnpm app:dev      # Nuxt dev server + Tauri window with hot reload
```

| Command | Purpose |
|---|---|
| `pnpm app:build` | Release build and installer – on Windows also as `release/Cubiq-Launcher_<version>_x64-setup.exe`; signs the update files if the key is in `~/.tauri/cubiq-launcher.key` (password in `cubiq-launcher.key.password`) |
| `pnpm typecheck` | Type-check TypeScript/Vue |
| `pnpm test` | Frontend tests (Vitest) |
| `cargo test --workspace` (in `src-tauri`) | Rust tests |
| `cargo clippy --workspace --all-targets` (in `src-tauri`) | Rust lints |
| `./gradlew collectLauncherJars` (in `client-mod/`) | Build the Cubiq Client jars for the launcher |

Set `CUBIQ_LAUNCHER_HOME` to use a different data directory. The launch pipeline can be tested without the UI:

```sh
cargo run -p cubiq-core --example launch -- <data-dir> 1.21.1 [vanilla|fabric|quilt|forge|neoforge] [seconds]
```

## Releases

Releases make themselves. After every green CI run on `main`, [`auto-release.yml`](.github/workflows/auto-release.yml)
checks whether anything changed for players since the last release. If so, it

1. raises the version (`feat:` commits → minor, `feat!:` → major, everything else → patch),
2. writes the new section of [CHANGELOG.md](CHANGELOG.md) in English and German – from the player notes in
   [`changes/`](changes/README.md) (one file per change), otherwise from the commit messages
   ([how](CONTRIBUTING.md#release-notes)),
3. draws the picture of the release to match its notes – a small animated pixel-art scene with Cubi, the cube from
   the logo, e.g. next to a wrench for fixes or a rocket for a launch (`scripts/release-art.mjs`); the launcher, the
   website and the GitHub release show it with the update, together with the screenshots from the player notes,
4. commits "Release vX.Y.Z", tags it and starts [`release.yml`](.github/workflows/release.yml).

`release.yml` builds the Windows installer (`Cubiq-Launcher_<version>_x64-setup.exe`), a portable ZIP and the Linux
packages and publishes them as the GitHub release "Cubiq Launcher X.Y.Z" with the changelog as description – the
launcher shows the same text after updating ("What's new"). With the signing key in the secrets it also refreshes the
update channel, so installed launchers update themselves.

- **Status right away:** as soon as the build starts, the releases page shows a draft "building". If the Windows
  installer is missing at the end, the release stays a draft (only visible to you) with the reason on top – a failed
  build step with its error lines, an incomplete CHANGELOG.md section – and a link to the log. The summary of the run
  lists what was built, whether the update signature worked and why something is missing.
- **Signing key:** checked before building (`scripts/check-signing.mjs`); pasting mistakes like a trailing line break
  are fixed automatically. A wrong key or password doesn't stop the build – the installer is built without update
  signature and the summary says what's wrong.
- **CI red:** no release; the run "Auto release" is then called "Kein Release – CI …" and names the failed jobs.
- **By hand:** Actions → Auto release → Run workflow (pick the version step), or push a tag like `v0.2.0` whose
  version has a section in CHANGELOG.md. To build an existing tag again: Actions → Release → Run workflow.
- **No merge conflicts:** branches never edit `CHANGELOG.md` or version numbers, only add files in `changes/`;
  [`pr-sync.yml`](.github/workflows/pr-sync.yml) merges `main` into open pull requests after every merge.
- **Release texts stay current:** when `CHANGELOG.md` or a release picture changes on `main`,
  [`release-notes.yml`](.github/workflows/release-notes.yml) rewrites the affected GitHub releases.
- **Pause:** set the repository variable `CUBIQ_AUTO_RELEASE` to `false`.
- **Protected `main`:** if only pull requests may change `main`, the release commit is rejected – let GitHub Actions
  bypass that rule.
- Screenshots and a custom update banner are optional ([how](docs/release-screenshots.md)).

## Architecture

```
app/                    Nuxt 4 frontend (SPA, no SSR)
src-tauri/
  src/                  Tauri app: commands, task registry, error mapping, plugins
  crates/core/          cubiq-core: the UI-independent launcher core (meta, loaders, launch, auth,
                        Modrinth/CurseForge, modpacks, clips, Cubiq services client, …)
  plugins/              Mobile game engine and push plugins
  resources/client-mod/ Bundled Cubiq Client builds + builds.json
client-mod/             Cubiq Client: common core + Fabric, Forge and NeoForge trees
server-plugin/          Optional server plugin to control Cubiq Client features
relay/                  Relay for world hosting (friends join your single-player world)
```

- **Logic lives in `cubiq-core`**, the Tauri layer stays thin, and the core validates every input itself.
- **The webview gets no file system, network or shell permissions.** Everything goes through dedicated commands,
  under a strict CSP.

## Contributing

Contributions are welcome – read [CONTRIBUTING.md](CONTRIBUTING.md). Report security problems privately (see
[SECURITY.md](SECURITY.md)).

## License

Cubiq Launcher is licensed under the [GNU General Public License v3.0](LICENSE).

Copyright (C) 2026 PwiePixel. Based on TRS Launcher, Copyright (C) 2026 Ohev Tamerin (Theredstonee). Copyright and
third-party notices: [NOTICE](NOTICE). Parts of the process handling are adapted from
[Polyfrost OneLauncher](https://github.com/Polyfrost/OneLauncher) (GPL-3.0-only).

<sub>Cubiq Launcher is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.</sub>

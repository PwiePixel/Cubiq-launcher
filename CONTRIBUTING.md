# Contributing to the Cubiq Launcher

> **Deutsch:** Beiträge auf Deutsch sind genauso willkommen – Issues, Diskussionen und Pull Requests dürfen deutsch
> sein.

Thanks for helping! Bug fixes, features, translations and docs are all welcome. How to build the launcher is in the
[README](README.md#building-from-source).

## Report bugs and ideas

- **Bugs and ideas:** [GitHub issues](https://github.com/PwiePixel/Cubiq-launcher/issues).
- **Questions and help:** [GitHub Discussions](https://github.com/PwiePixel/Cubiq-launcher/discussions).
- **Security problems:** never in public – see [SECURITY.md](SECURITY.md).

For bigger changes, open an issue first, so the work fits the plan.

## Workflow

1. **Fork** the repository and create a branch from `main`.
2. **Make your change.** Keep a pull request to one topic. Write commit messages in English.
3. **Run the checks** for the part you changed (below).
4. **Open a pull request** against `main`. Fill in the template: what changes for players, how you tested it,
   screenshots for UI changes. Link the issue, e.g. `Closes #123`.

Pull requests are merged with **squash merge**: your branch becomes one commit on `main`, with the pull request title
as its message, so you don't need to clean up your history.

## Checks

The CI runs automatically on every pull request and only for the parts you changed. The one check that has to be green
is **`ci-ok`** – it sums up all the others:

| Check | Runs when you change | What it does |
|---|---|---|
| `launcher-windows`, `launcher-linux` | `app/`, `src-tauri/`, `scripts/`, `tests/`, `public/`, `packaging/`, `package.json` … | type check, frontend tests, frontend build, Clippy, Rust tests, Linux package metadata |
| `client-mod` | `client-mod/`, `data/` | unit tests of `client-mod/common`, compiles Fabric 1.21.11 and 26.3, Forge 1.8.9 and Forge 1.7.10 |

Run them locally before you ask for a review:

```bash
# Launcher
pnpm typecheck
pnpm test
pnpm generate
cd src-tauri
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace

# Cubiq Client (build everything, or like the CI only single versions)
cd client-mod
./gradlew build
./gradlew -PcubiqVersions=1.21.11 :common:test :fabric:1.21.11:compileJava
```

Clippy warnings count as errors. Add tests for new logic: Vitest files in `tests/`, Rust unit tests next to the code,
JUnit tests in `client-mod/common`.

## Release notes

Every change that players notice brings **its own text for them**: a file `changes/<topic>.md` with the same points in
English and German ([`changes/README.md`](changes/README.md) has the template and the rules). After every green CI run
on `main`, the workflow **Auto release** turns all files in `changes/` into the next section of
[`CHANGELOG.md`](CHANGELOG.md), deletes them, draws the picture of the release and publishes it – players see the text
in the launcher ("What's new"), in the GitHub release and on the website.

- **Write it for players**, in plain words: what changed for them, where to find it and why it helps – no file or
  function names, no commit language. Bold only the short headline of each point.
- **Only the update:** no names, mentions or thanks.
- **Never edit `CHANGELOG.md` or version numbers in a branch.** The release commits on `main` change them all the
  time, so every branch that touches them ends up with a merge conflict. Files in `changes/` never collide.
- **No text needed** for changes players don't notice: start the commit with `refactor:`, `test:`, `ci:`, `docs:`,
  `chore:`, `build:` or `style:`, or put `[skip changelog]` in the message. Commits that only touch docs, tests,
  workflows or scripts are left out automatically.
- **Version step:** `feat: …` raises the minor version, `feat!: …` or `BREAKING CHANGE` in the body the major version,
  everything else the patch version.
- **Check:** the CI job "Player notes" fails when a pull request changes something for players without a text.
- **Fallback:** lines `EN: …` and `DE: …` in a commit message still work for a one-line point; without any text the
  release uses the first line of each commit.

## Code style

- **Logic lives in `cubiq-core`** (`src-tauri/crates/core`); the Tauri layer stays thin and the core checks every input.
- **The webview gets no file system, network or shell permissions** – everything goes through dedicated commands.
- **No hard-coded text in the interface.** Every text is a key in `app/locales/*.json`; keys are typed, so typos fail
  the type check. Errors reach the interface only as a stable code plus a readable message.
- **Cubiq Client:** shared logic goes into `client-mod/common` and stays **Java 8**; version differences are written as
  Stonecutter comments (see [`client-mod/README.md`](client-mod/README.md)). Fair play: no reach or hitbox changes, no
  auto-clicking, no cave view on the minimap.
- Follow the style of the file you are editing. Comments in the code are mostly German – English is fine too.
- Never commit secrets, API keys or `.env` files.

## Translating

The launcher texts are in `app/locales/<language>.json` (English is the source), the Cubiq Client texts in
`client-mod/common/src/main/resources/assets/cubiqclient/i18n/`. Keep placeholders like `{name}` unchanged.
English, German and Spanish must always be complete – the tests check that; other languages may lag behind.

## License

The Cubiq Launcher and the Cubiq Client are licensed under the
[GNU General Public License v3.0](LICENSE) (GPL-3.0-only). By contributing, you agree that your contribution is
released under the same license.

Please be kind to each other – see the [Code of Conduct](CODE_OF_CONDUCT.md).

# Microsoft sign-in (status: not approved)

**Status (29 October 2026): Mojang did not approve the Azure app "Cubiq Launcher"** (client ID
`5cc8876f-8fe1-4c27-bc28-9355a79db635`) for the allow list of the Minecraft services. The decision came as a template
e-mail without reasons. Microsoft and Xbox Live still accept the sign-in, but
`api.minecraftservices.com/authentication/login_with_xbox` answers **403** ("Invalid app registration"), so Cubiq never
gets a Minecraft token. No other endpoint, flow or Azure setting changes that. There is no date for a change.

## What does not work

- **Signing in to Cubiq Launcher** on Windows, Linux, Android and iOS. The launcher says so honestly: Microsoft sign-in
  is not available in Cubiq Launcher because Mojang has not approved the app. It promises no date.
- **Playing through Cubiq.** The launcher starts the game only for a signed-in account that owns Minecraft: Java
  Edition. There is no offline, "cracked" or demo mode – not in release builds, development builds or the trade-fair
  build either. Without an account a launch ends with the request to sign in first.
- Everything else that needs the Minecraft token: the Mojang skins and capes page, and all Cubiq services in the
  launcher (friends, chat, Cubiq capes and cosmetics, achievements, creator, remote control, world hosting, cloud
  sync, confirming website sign-ins). The Cubiq services identify players like a Minecraft server does
  (session-server `join` → `hasJoined`), which needs that token.
- The in-game sign-in of the Cubiq Client uses the same app ID and fails the same way.
- Website: "Sign in with Microsoft" has been removed. The sign-in with the launcher account needs a signed-in
  launcher; the owner's one-time link from the server shell (`deploy/cubiq-server.sh login`) still works.

## What still works

- Instances, every game version, Fabric, Quilt, Forge and NeoForge, Modrinth and CurseForge content and modpacks,
  shared folders, worlds, screenshots, the skin library and all settings.
- Export as a modpack (`.mrpack`), for example to play an instance in an approved launcher. The export leaves out the
  Cubiq Client on purpose.
- Server export: servers always run with `online-mode=true`, so only players with a real Minecraft account can join.
- In a game started by an approved launcher, the Cubiq Client logs in to the Cubiq services with the running game's
  own session (`join`/`hasJoined`) – Cubiq never sees that token. The mod needs the address of the services for that
  (`-Dcubiqclient.api.url=…`), which today only Cubiq Launcher passes.

## The old guidance is withdrawn

Earlier versions of this guide said that one app is enough for everything: the launcher, the in-game sign-in of the
Cubiq Client and the website login, with a **Web** platform and a **client secret** in the same registration. **That
advice is withdrawn.** Microsoft recommends one registration per app and no credentials on an app that is only a
public client, and a single registration that is a desktop client, an in-game account switcher and a website with a
secret at the same time looks exactly like the phishing pattern the allow list exists to stop.

## The plan: a separate, desktop-only registration

A new application is sent **once**, and only after the changes below are released and can be checked:

1. **New registration** "Cubiq Launcher" in the Azure portal (**App registrations** → **New registration**),
   supported account types **Personal Microsoft accounts only**.
2. **Only one platform:** **Mobile and desktop applications** with the redirect `http://localhost/login`. **No Web
   platform, no client secret, no certificate.**
3. **Allow public client flows → No** (this turns off the device-code sign-in). Test first that the browser sign-in
   *and* the token refresh still work.
4. **Branding & properties:** logo, home page `https://launcher.cubiq.studio`, privacy
   `https://launcher.cubiq.studio/privacy`.
5. **Used by the desktop launcher only.** The Cubiq Client contains no client ID and no Microsoft sign-in, the website
   has no Microsoft sign-in, and Android/iOS builds do not use this registration (they need their own one later).
6. **Disclose the old app ID** in the form ("Other information") together with what changed, so the new ID cannot look
   like an attempt to get around the decision.

What already changed on the way there:

- Honest error text (8 languages) instead of "waiting for approval … will work without an update".
- No launch without a signed-in account that owns the game, in any build; the account-less demo start of the
  trade-fair build was removed.
- Server export always `online-mode=true`; the Cubiq Client's hosting has no switch any more that turns the login of
  guests off.
- The notice "Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft." in the
  launcher (settings, accounts page, sign-in step of the setup wizard).
- Website: no Microsoft sign-in, page titles name Cubiq first instead of "Minecraft".

Still to do before the application: remove the Cubiq Client's own Microsoft sign-in and account switcher, split the
client ID per platform (no mobile build with the desktop ID), switch the launcher to the new ID and drop the
device-code sign-in on the desktop.

### For the owner now

- **Old registration** in the Azure portal: **Certificates & secrets** → delete the client secret; **Authentication**
  → remove the **Web** platform. Delete the whole registration later, once no release uses it.
- **Server:** the website no longer reads `MS_CLIENT_ID` and `MS_CLIENT_SECRET`; remove them from `api/.env`.
- **Optional check** whether Microsoft disabled the app: install the Azure CLI, run
  `az login --tenant f549d532-b025-4e54-8fea-8dbc13d91ed7 --allow-no-subscriptions`, then
  `az ad app show --id 5cc8876f-8fe1-4c27-bc28-9355a79db635 --query disabledByMicrosoftStatus`. `null` or
  `NotDisabled` means fine; `DisabledDueToViolationOfServicesAgreement` means Microsoft disabled it. (The Cloud Shell
  only works with an Azure subscription.)

### The review form

https://aka.ms/mce-reviewappid – one form per app ID. Its current text (changed 20 October 2026) requires that apps
verify game ownership and require sign-in before anyone can play or use servers or sites; apps that bypass or disable
authentication, ownership or license checks or other safety features are not approved, "regardless of server
configuration". Names must not contain Mojang, Minecraft, Microsoft, Live, Xbox, Discord or Hypixel. Reviews happen
weekly, and sending several submissions does not speed them up. The answers for the new registration are written
together with it; the answers that were in this file described the old, shared registration and are no longer valid.

## Never

- **Another app's client ID** (the official launcher's, Prism's or anyone else's) – not even "temporarily".
- **Offline, cracked or demo play** without a signed-in account that owns the game.
- **Tokens Cubiq did not obtain itself:** Cubiq never opens `launcher_msa_credentials*` or `launcher_accounts*`, never
  reads the `authenticationDatabase`/`clientToken` keys of `launcher_profiles*.json` and never reads other programs'
  command lines.
- **Bypassing checks:** no other endpoints, no skipped ownership or profile check, no ownership check through Xbox
  only, no servers or hosted worlds without the Mojang login.

## How sign-in works (current builds)

- Microsoft identity platform, `consumers` endpoint (personal accounts), scope `XboxLive.signin offline_access`.
- Authorization Code + PKCE in the system browser with a loopback redirect (`http://localhost/login`, Microsoft
  ignores the port for `localhost`), device code as fallback. No secret; the password is only typed on Microsoft's
  page.
- Microsoft token → Xbox Live → XSTS (`rp://api.minecraftservices.com/`) → `login_with_xbox` → **ownership check**
  (`/entitlements/mcstore`) → profile. A 403 from `login_with_xbox` becomes the error `auth_not_approved`.
- Refresh and access tokens are stored encrypted on the device. Access tokens only go to Microsoft, Xbox and Mojang
  endpoints and to the local game process – never to Cubiq servers.

## In the code

- Launcher: `CUBIQ_APP_ID` in `src-tauri/crates/core/src/auth/microsoft.rs` (still the old ID). The build variable
  `CUBIQ_MSA_CLIENT_ID` is only for a fork with its **own** approved Azure app.
- Error text: `errors.authNotApproved`, explanation `accounts.login.notApprovedHint` in
  `app/components/SignInUnavailable.vue` (shared by the accounts page and the setup wizard – the place for a later way
  to play without signing in through Cubiq).
- Without an account: `sign_in_required()` in `src-tauri/crates/core/src/lib.rs` (`launcher.signInFirst`) – the only
  result for desktop and mobile launches without an account.
- Cubiq Client: `MsAuth.CLIENT_ID` in
  `client-mod/common/src/main/java/dev/pwiepixel/cubiqclient/core/account/MsAuth.java` (to be removed together with
  the in-game sign-in).

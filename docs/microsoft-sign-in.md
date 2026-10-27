# Microsoft sign-in (Azure app + Minecraft approval)

Players sign in with their own Microsoft account. For that the launcher needs **its own Azure app** ("AppID") that
Microsoft has approved for the Minecraft services. One app is enough for everything: the launcher, the in-game
sign-in of the Cubiq Client and the website login.

How sign-in works (for the review and for you):

- Microsoft identity platform, `consumers` endpoint (personal accounts), scope `XboxLive.signin offline_access`.
- Launcher and Cubiq Client: public client, Authorization Code + PKCE with a loopback redirect
  (`http://localhost/login`, Microsoft ignores the port for `localhost`), device code as fallback. No secret, the
  player types the password only on Microsoft's page.
- Website: confidential client (Authorization Code + PKCE + client secret), redirect
  `<SITE_URL>/auth/microsoft/callback`; it keeps only UUID and name and drops all tokens after sign-in.
- Microsoft token → Xbox Live → XSTS (`rp://api.minecraftservices.com/`) → `login_with_xbox` → **ownership check**
  (`/entitlements/mcstore`) → profile. Release builds start the game only for a signed-in account that owns
  Minecraft: Java Edition – there are no offline accounts. Refresh tokens are stored encrypted on the device; the
  Cubiq services verify players through Mojang's session server, so tokens never reach Cubiq servers.

Code: `src-tauri/crates/core/src/auth/microsoft.rs`, `client-mod/common/src/main/java/dev/pwiepixel/cubiqclient/core/account/MsAuth.java`,
website `api/server/lib/microsoft.ts`.

## 1. Directory (tenant)

App registrations need a Microsoft Entra directory. A personal Microsoft account alone is no longer enough; the usual
way is a free Azure account (https://azure.microsoft.com/free) – Microsoft verifies it with a phone number and a card
but charges nothing, and app registrations stay free. Use the same Microsoft account whose e-mail you give in the
review form.

## 2. App registration

1. https://portal.azure.com → **Microsoft Entra ID** → **App registrations** → **New registration**.
2. Name **Cubiq Launcher** (names must not contain Mojang, Minecraft, Microsoft, Live, Xbox, Discord or Hypixel).
   Supported account types: **Personal Microsoft accounts only**. Leave the redirect URI empty → **Register**.
3. **Authentication** → **Add a platform** → **Mobile and desktop applications** → custom redirect URI
   `http://localhost/login` → **Configure**.
4. **Authentication** → **Settings** → **Allow public client flows** → **Yes** (device code) → **Save**.
5. For the website (also later): **Add a platform** → **Web** → `https://<your-domain>/auth/microsoft/callback`, then
   **Certificates & secrets** → **New client secret** – copy the *Value* right away (it is shown once and expires;
   renew it in time).
6. Optional but helpful for the review: **Branding & properties** → logo, home page
   `https://github.com/PwiePixel/Cubiq-launcher`, privacy and terms links of your website.
7. **Overview**: copy **Application (client) ID** and **Directory (tenant) ID**.

No API permissions are needed; `XboxLive.signin` is requested at sign-in.

## 3. Review form

https://aka.ms/mce-reviewappid – one form per AppID, reviewed weekly; sending it twice does not help. Before approval
the launcher shows "not approved" at sign-in (`login_with_xbox` answers 403); after approval it can take up to a day.

| Question | Answer |
|---|---|
| 1. Read EULA + Usage Guidelines | **Yes** – after reading http://aka.ms/mcusageguidelines and the Minecraft EULA |
| 2. Contact | the e-mail of the Microsoft account that owns the app in the Azure portal |
| 3. Type | **New AppID for Approval** |
| 4. Application name | `Cubiq Launcher` |
| 5. Application ID | Application (client) ID from step 2.7 |
| 6. Tenant ID | Directory (tenant) ID from step 2.7 |
| 7. Website | `https://github.com/PwiePixel/Cubiq-launcher` (or your website once it is online) |
| 8. Justification | text below |
| 9. Other information | text below |

**8. Justification**

> Cubiq Launcher is a free, open-source (GPL-3.0) launcher for Minecraft: Java Edition on Windows and Linux, with
> Android and iOS versions in development. It downloads the official game files from Mojang, manages instances and
> modpacks, and ships an optional client-side mod (Cubiq Client). We need this AppID so players can sign in with their
> own Microsoft account: Microsoft identity platform (consumers) with Authorization Code + PKCE on a loopback redirect
> and device code as fallback, scope XboxLive.signin offline_access, then Xbox Live, XSTS and
> api.minecraftservices.com (login_with_xbox, entitlements/mcstore, minecraft/profile). The launcher verifies game
> ownership before every launch: only signed-in accounts that own Minecraft: Java Edition can play, there are no
> offline or cracked accounts, and authentication, ownership and license checks are never bypassed or disabled.

**9. Other information**

> The same AppID is used by our in-game mod for signing in inside the game (same public-client flow) and by our
> website (confidential web flow with client secret, only reads UUID and player name, discards all tokens after
> sign-in). Passwords are only entered on Microsoft's own sign-in page. Refresh tokens are stored encrypted on the
> player's device; access tokens are passed to the game like the official launcher does and are never sent to our
> servers – our online features verify players through Mojang's session server (join/hasJoined). Source code of the
> sign-in: https://github.com/PwiePixel/Cubiq-launcher/blob/main/src-tauri/crates/core/src/auth/microsoft.rs

## 4. After approval

- Launcher: GitHub → repository **Settings** → **Secrets and variables** → **Actions** → **Variables** →
  `CUBIQ_MSA_CLIENT_ID` = the Application (client) ID. The next release build has sign-in. Locally:
  `CUBIQ_MSA_CLIENT_ID=… pnpm app:build`.
- Cubiq Client: the same ID in `MsAuth.CLIENT_ID` (`client-mod/common/src/main/java/dev/pwiepixel/cubiqclient/core/account/MsAuth.java`).
  The ID is not secret and may be committed.
- Website: `MS_CLIENT_ID` (same ID) and `MS_CLIENT_SECRET` (the secret value) in the server's `.env`.

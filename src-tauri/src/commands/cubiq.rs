//! Cubiq API: Umhänge, Freunde, Präsenz, Verwaltung. Alle Anfragen laufen im
//! Kern; das Webview bekommt nur Daten (Texturen als Data-URL), nie den Token.

use std::path::PathBuf;

use tauri::ipc::Channel;
use tauri::{AppHandle, State};
use tauri_plugin_dialog::DialogExt;
use cubiq_core::featured::AdminFeaturedPack;
use cubiq_core::cubiq_api::featured::FeaturedPatch;
use cubiq_core::cubiq_api::achievements::{MyAchievements, PlayerAchievements, ReportKind};
use cubiq_core::cubiq_api::cape_import::{self, CapeSource};
use cubiq_core::cubiq_api::cape_video::{self, VideoInfo, VideoPicks, VideoRequest};
use cubiq_core::cubiq_api::sync::SyncStatus;
use cubiq_core::cubiq_api::types::{
    AdminCape, AdminStats, AdminUser, BlockedUser, CapeHolders, CapeItem, CapeOffers, CodeView, Friend,
    FriendRequestResult, FriendsView, Me, NewCodes, PlayerCape, RedeemResult, ReportReason, ReviewList,
    SettingsPatch, CubiqStatus, UserRef,
};

use crate::LauncherState;
use crate::dialog_text::{self, DialogText};
use crate::error::CommandResult;

#[tauri::command]
pub async fn cubiq_status(launcher: State<'_, LauncherState>) -> CommandResult<CubiqStatus> {
    Ok(launcher.cubiq_status().await?)
}

/// Stand der Cubiq-Synchronisation (kleiner Hinweis auf der Skins-Seite).
#[tauri::command]
pub async fn cubiq_sync_status(launcher: State<'_, LauncherState>) -> CommandResult<SyncStatus> {
    Ok(launcher.cubiq_sync_status().await)
}

/// Einwilligung erteilen oder zurückziehen (dann keine Anfragen mehr).
#[tauri::command]
pub async fn cubiq_set_consent(launcher: State<'_, LauncherState>, accepted: bool) -> CommandResult<CubiqStatus> {
    Ok(launcher.cubiq_set_consent(accepted).await?)
}

/// Eigene Erfolge: Katalog, freigeschaltete, Fortschritt, Punkte.
#[tauri::command]
pub async fn cubiq_achievements(launcher: State<'_, LauncherState>) -> CommandResult<MyAchievements> {
    Ok(launcher.cubiq_achievements().await?)
}

/// Meldung aus der Oberfläche (nur `crash_fixed`: Behebungen des Absturz-Helfers laufen dort).
#[tauri::command]
pub async fn cubiq_achievement_report(launcher: State<'_, LauncherState>, kind: String) -> CommandResult<()> {
    if let Some(kind) = ReportKind::from_ui(&kind) {
        launcher.cubiq_achievement_event(kind, None).await;
    }
    Ok(())
}

/// Erfolge für Freunde sichtbar oder privat.
#[tauri::command]
pub async fn cubiq_set_achievements_visible(launcher: State<'_, LauncherState>, visible: bool) -> CommandResult<bool> {
    Ok(launcher.cubiq_set_achievements_visible(visible).await?)
}

/// Punkte und freigeschaltete Erfolge eines Freundes.
#[tauri::command]
pub async fn cubiq_player_achievements(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<PlayerAchievements> {
    Ok(launcher.cubiq_player_achievements(&uuid).await?)
}

#[tauri::command]
pub async fn cubiq_me(launcher: State<'_, LauncherState>) -> CommandResult<Me> {
    Ok(launcher.cubiq_me().await?)
}

#[tauri::command]
pub async fn cubiq_update_me(launcher: State<'_, LauncherState>, patch: SettingsPatch) -> CommandResult<Me> {
    Ok(launcher.cubiq_update_me(patch).await?)
}

/// Löscht alle Cubiq-Daten des aktiven Accounts und schaltet die Dienste aus.
#[tauri::command]
pub async fn cubiq_delete_me(launcher: State<'_, LauncherState>) -> CommandResult<CubiqStatus> {
    Ok(launcher.cubiq_delete_me().await?)
}

#[tauri::command]
pub async fn cubiq_capes(launcher: State<'_, LauncherState>) -> CommandResult<Vec<CapeItem>> {
    Ok(launcher.cubiq_capes().await?)
}

#[tauri::command]
pub async fn cubiq_set_cape(launcher: State<'_, LauncherState>, cape_id: Option<String>) -> CommandResult<Option<String>> {
    Ok(launcher.cubiq_set_cape(cape_id).await?)
}

/// Bilder für den Umhang-Dialog wählen (auch mehrere = Frames). Der Kern erkennt
/// das Format an den Magic Bytes, prüft die Maße vor dem Dekodieren und zerlegt
/// GIFs; das Webview bekommt nur Data-URLs. `None` = abgebrochen. Der Pfad
/// verlässt Rust nie.
#[tauri::command]
pub async fn cubiq_pick_cape_sources(
    app: AppHandle,
    launcher: State<'_, LauncherState>,
) -> CommandResult<Option<Vec<CapeSource>>> {
    let lang = dialog_text::language(&launcher).await;
    let picked = tauri::async_runtime::spawn_blocking(move || {
        app.dialog()
            .file()
            .set_title(DialogText::PickCape.text(lang))
            .add_filter(DialogText::Cape.text(lang), &["png", "jpg", "jpeg", "webp", "gif", "json"])
            .blocking_pick_files()
    })
    .await
    .ok()
    .flatten();
    let Some(picked) = picked else { return Ok(None) };
    let files: Vec<PathBuf> = picked.into_iter().filter_map(|p| p.into_path().ok()).collect();
    if files.is_empty() {
        return Ok(None);
    }
    Ok(Some(cape_import::read_sources(files).await?))
}

/// Video als Umhang-Quelle wählen (nur mit geladenem FFmpeg, wie Clips nur unter
/// Windows). Der Kern liest Dauer und Größe; das Webview bekommt nur eine Kennung.
/// `None` = abgebrochen.
#[tauri::command]
pub async fn cubiq_pick_cape_video(
    app: AppHandle,
    launcher: State<'_, LauncherState>,
    picks: State<'_, VideoPicks>,
) -> CommandResult<Option<VideoInfo>> {
    cubiq_core::platform::desktop_only()?;
    cape_video::supported()?;
    let Some(exe) = launcher.clips().ffmpeg().ready().await else { return Err(cape_video::ffmpeg_missing().into()) };
    let lang = dialog_text::language(&launcher).await;
    let picked = tauri::async_runtime::spawn_blocking(move || {
        app.dialog()
            .file()
            .set_title(DialogText::PickCapeVideo.text(lang))
            .add_filter(DialogText::Videos.text(lang), &cape_video::VIDEO_EXTENSIONS)
            .blocking_pick_file()
    })
    .await
    .ok()
    .flatten()
    .and_then(|p| p.into_path().ok());
    let Some(path) = picked else { return Ok(None) };
    if !std::fs::metadata(&path).is_ok_and(|m| m.is_file()) {
        return Ok(None);
    }
    let stem = path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    let name = cubiq_core::cubiq_api::validate::text(stem.trim(), 64);
    let (duration_ms, width, height) = cape_video::probe(&exe, &path, &name).await?;
    Ok(Some(picks.insert(path, name, duration_ms, width, height)))
}

/// Bereich eines gewählten Videos in Frames zerlegen – als Aufgabe mit
/// Fortschritt (0–100) und Abbrechen (beendet FFmpeg).
#[tauri::command]
pub async fn cubiq_cape_video_frames(
    app: AppHandle,
    launcher: State<'_, LauncherState>,
    picks: State<'_, VideoPicks>,
    token: String,
    request: VideoRequest,
    on_progress: Channel<f64>,
    task_id: Option<String>,
) -> CommandResult<CapeSource> {
    cubiq_core::platform::desktop_only()?;
    cape_video::supported()?;
    let (path, info) = picks.get(&token)?;
    let Some(exe) = launcher.clips().ffmpeg().ready().await else { return Err(cape_video::ffmpeg_missing().into()) };
    let span_us = request.end_ms.min(info.duration_ms).saturating_sub(request.start_ms).max(1) * 1000;
    let last = std::sync::atomic::AtomicI32::new(-1);
    let progress: cubiq_core::clips::edit::Progress = std::sync::Arc::new(move |us: u64| {
        #[allow(clippy::cast_possible_truncation)]
        let percent = (us.min(span_us) * 100 / span_us) as i32;
        if last.swap(percent, std::sync::atomic::Ordering::Relaxed) != percent {
            let _ = on_progress.send(f64::from(percent));
        }
    });
    let dir = cape_video::work_dir(launcher.paths().root());
    let runner = cape_video::KillableRunner { exe };
    let work = cape_video::extract(&runner, &path, &dir, &info, &request, Some(progress));
    Ok(crate::commands::tasks::tracked(&app, task_id, work).await?)
}

/// Lädt den fertigen Streifen aus dem Zuschneide-Dialog hoch (PNG als Base64,
/// `frames` Frames untereinander). Der Kern prüft alles noch einmal.
#[tauri::command]
pub async fn cubiq_upload_cape(
    launcher: State<'_, LauncherState>,
    png: String,
    frames: u32,
    frame_time_ms: Option<u32>,
    name: Option<String>,
) -> CommandResult<CapeItem> {
    let bytes = cape_import::decode_upload(&png)?;
    Ok(launcher.cubiq_upload_cape(bytes, frames, frame_time_ms, name.as_deref()).await?)
}

#[tauri::command]
pub async fn cubiq_delete_cape(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_delete_cape(&id).await?)
}

#[tauri::command]
pub async fn cubiq_report_cape(
    launcher: State<'_, LauncherState>,
    id: String,
    reason: ReportReason,
    note: Option<String>,
) -> CommandResult<()> {
    Ok(launcher.cubiq_report_cape(&id, reason, note.as_deref()).await?)
}

#[tauri::command]
pub async fn cubiq_redeem(launcher: State<'_, LauncherState>, code: String) -> CommandResult<RedeemResult> {
    Ok(launcher.cubiq_redeem(&code).await?)
}

#[tauri::command]
pub async fn cubiq_hats(launcher: State<'_, LauncherState>) -> CommandResult<Vec<cubiq_core::cubiq_api::types::HatItem>> {
    Ok(launcher.cubiq_hats().await?)
}

/// Alle Kopf-Kosmetik-Teile (besessen + gesperrt) mit Vorschaubildern.
#[tauri::command]
pub async fn cubiq_head_cosmetics(
    launcher: State<'_, LauncherState>,
) -> CommandResult<Vec<cubiq_core::cubiq_api::types::HeadCosmetic>> {
    Ok(launcher.cubiq_head_cosmetics().await?)
}

/// v2-Modell + Texturen für die 3D-Vorschau.
#[tauri::command]
pub async fn cubiq_head_cosmetic_model(
    launcher: State<'_, LauncherState>,
    id: String,
) -> CommandResult<cubiq_core::cubiq_api::types::HeadCosmeticModel> {
    Ok(launcher.cubiq_head_cosmetic_model(&id).await?)
}

#[tauri::command]
pub async fn cubiq_set_hat(launcher: State<'_, LauncherState>, id: Option<String>) -> CommandResult<()> {
    Ok(launcher.cubiq_set_hat(id).await?)
}

#[tauri::command]
pub async fn cubiq_set_companion(launcher: State<'_, LauncherState>, id: Option<String>) -> CommandResult<()> {
    Ok(launcher.cubiq_set_companion(id).await?)
}

/// Begleiter am Boden (Pinguin …) hinstellen oder wegnehmen.
#[tauri::command]
pub async fn cubiq_set_pet(launcher: State<'_, LauncherState>, id: Option<String>) -> CommandResult<()> {
    Ok(launcher.cubiq_set_pet(id).await?)
}

/// Fach belegen/leeren, optional mit der Wahl der abschaltbaren Teile (`{ "beard": false }`).
#[tauri::command]
pub async fn cubiq_equip_cosmetic(
    launcher: State<'_, LauncherState>,
    slot: String,
    id: Option<String>,
    options: Option<std::collections::BTreeMap<String, bool>>,
) -> CommandResult<()> {
    Ok(launcher.cubiq_equip_cosmetic(&slot, id, options).await?)
}

/// Kosmetik-Teil eines laufenden Events gratis holen.
#[tauri::command]
pub async fn cubiq_claim_cosmetic(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_claim_cosmetic(&id).await?)
}

/// Event-Umhang gratis holen.
#[tauri::command]
pub async fn cubiq_claim_cape(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_claim_cape(&id).await?)
}

/// Events, die für den Spieler zuletzt aktiv waren (Zwischenspeicher, ohne Netz).
#[tauri::command]
pub fn cubiq_active_events(launcher: State<'_, LauncherState>) -> Vec<String> {
    launcher.cubiq_active_events()
}

/// Davon die Events, deren Look der Spieler sehen soll (Team-Sperre abgezogen; Zwischenspeicher).
#[tauri::command]
pub fn cubiq_look_events(launcher: State<'_, LauncherState>) -> Vec<String> {
    launcher.cubiq_look_events()
}

#[tauri::command]
pub async fn cubiq_admin_events(
    launcher: State<'_, LauncherState>,
) -> CommandResult<Vec<cubiq_core::cubiq_api::events::AdminEvent>> {
    Ok(launcher.cubiq_admin_events().await?)
}

#[tauri::command]
pub async fn cubiq_admin_set_event(launcher: State<'_, LauncherState>, id: String, enabled: bool) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_set_event(&id, enabled).await?)
}

#[tauri::command]
pub async fn cubiq_admin_add_event_player(launcher: State<'_, LauncherState>, id: String, player: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_add_event_player(&id, &player).await?)
}

#[tauri::command]
pub async fn cubiq_admin_remove_event_player(launcher: State<'_, LauncherState>, id: String, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_remove_event_player(&id, &uuid).await?)
}

#[tauri::command]
pub async fn cubiq_admin_add_event_look_block(launcher: State<'_, LauncherState>, id: String, player: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_add_event_look_block(&id, &player).await?)
}

#[tauri::command]
pub async fn cubiq_admin_remove_event_look_block(launcher: State<'_, LauncherState>, id: String, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_remove_event_look_block(&id, &uuid).await?)
}

// --- Empfohlene Modpacks (Team, featured.manage) ---

#[tauri::command]
pub async fn cubiq_admin_featured_packs(launcher: State<'_, LauncherState>) -> CommandResult<Vec<AdminFeaturedPack>> {
    Ok(launcher.cubiq_admin_featured_packs().await?)
}

#[tauri::command]
pub async fn cubiq_admin_add_featured_pack(launcher: State<'_, LauncherState>, url: String) -> CommandResult<AdminFeaturedPack> {
    Ok(launcher.cubiq_admin_add_featured_pack(&url).await?)
}

#[tauri::command]
pub async fn cubiq_admin_update_featured_pack(
    launcher: State<'_, LauncherState>,
    id: String,
    patch: FeaturedPatch,
) -> CommandResult<AdminFeaturedPack> {
    Ok(launcher.cubiq_admin_update_featured_pack(&id, &patch).await?)
}

#[tauri::command]
pub async fn cubiq_admin_delete_featured_pack(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_delete_featured_pack(&id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_order_featured_packs(
    launcher: State<'_, LauncherState>,
    ids: Vec<String>,
) -> CommandResult<Vec<AdminFeaturedPack>> {
    Ok(launcher.cubiq_admin_order_featured_packs(&ids).await?)
}

#[tauri::command]
pub async fn cubiq_admin_refresh_featured_pack(launcher: State<'_, LauncherState>, id: String) -> CommandResult<AdminFeaturedPack> {
    Ok(launcher.cubiq_admin_refresh_featured_pack(&id).await?)
}

#[tauri::command]
pub async fn cubiq_player_capes(launcher: State<'_, LauncherState>, uuids: Vec<String>) -> CommandResult<Vec<PlayerCape>> {
    Ok(launcher.cubiq_player_capes(&uuids).await?)
}

#[tauri::command]
pub async fn cubiq_cape_offers(launcher: State<'_, LauncherState>) -> CommandResult<CapeOffers> {
    Ok(launcher.cubiq_cape_offers().await?)
}

#[tauri::command]
pub async fn cubiq_offer_cape(launcher: State<'_, LauncherState>, cape_id: String, friend: String) -> CommandResult<()> {
    Ok(launcher.cubiq_offer_cape(&cape_id, &friend).await?)
}

#[tauri::command]
pub async fn cubiq_accept_cape_offer(launcher: State<'_, LauncherState>, cape_id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_accept_cape_offer(&cape_id).await?)
}

#[tauri::command]
pub async fn cubiq_decline_cape_offer(launcher: State<'_, LauncherState>, cape_id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_decline_cape_offer(&cape_id).await?)
}

#[tauri::command]
pub async fn cubiq_cape_holders(launcher: State<'_, LauncherState>, cape_id: String) -> CommandResult<CapeHolders> {
    Ok(launcher.cubiq_cape_holders(&cape_id).await?)
}

#[tauri::command]
pub async fn cubiq_revoke_cape_share(
    launcher: State<'_, LauncherState>,
    cape_id: String,
    holder: String,
) -> CommandResult<()> {
    Ok(launcher.cubiq_revoke_cape_share(&cape_id, &holder).await?)
}

#[tauri::command]
pub async fn cubiq_friends(launcher: State<'_, LauncherState>) -> CommandResult<FriendsView> {
    Ok(launcher.cubiq_friends().await?)
}

#[tauri::command]
pub async fn cubiq_blocks(launcher: State<'_, LauncherState>) -> CommandResult<Vec<BlockedUser>> {
    Ok(launcher.cubiq_blocks().await?)
}

#[tauri::command]
pub async fn cubiq_friend_request(launcher: State<'_, LauncherState>, target: String) -> CommandResult<FriendRequestResult> {
    Ok(launcher.cubiq_friend_request(&target).await?)
}

#[tauri::command]
pub async fn cubiq_friend_accept(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<Friend> {
    Ok(launcher.cubiq_friend_accept(&uuid).await?)
}

#[tauri::command]
pub async fn cubiq_friend_decline(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_friend_decline(&uuid).await?)
}

#[tauri::command]
pub async fn cubiq_friend_cancel(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_friend_cancel(&uuid).await?)
}

#[tauri::command]
pub async fn cubiq_friend_remove(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_friend_remove(&uuid).await?)
}

#[tauri::command]
pub async fn cubiq_block(launcher: State<'_, LauncherState>, target: String) -> CommandResult<UserRef> {
    Ok(launcher.cubiq_block(&target).await?)
}

#[tauri::command]
pub async fn cubiq_unblock(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_unblock(&uuid).await?)
}

// --- Verwaltung ------------------------------------------------------------------------

#[tauri::command]
pub async fn cubiq_admin_stats(launcher: State<'_, LauncherState>) -> CommandResult<AdminStats> {
    Ok(launcher.cubiq_admin_stats().await?)
}

#[tauri::command]
pub async fn cubiq_admin_capes(launcher: State<'_, LauncherState>, list: ReviewList) -> CommandResult<Vec<AdminCape>> {
    Ok(launcher.cubiq_admin_capes(list).await?)
}

#[tauri::command]
pub async fn cubiq_admin_approve(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_approve(&id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_reject(launcher: State<'_, LauncherState>, id: String, reason: Option<String>) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_reject(&id, reason.as_deref()).await?)
}

#[tauri::command]
pub async fn cubiq_admin_delete_cape(launcher: State<'_, LauncherState>, id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_delete_cape(&id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_codes(launcher: State<'_, LauncherState>) -> CommandResult<Vec<CodeView>> {
    Ok(launcher.cubiq_admin_codes().await?)
}

#[tauri::command]
pub async fn cubiq_admin_create_codes(launcher: State<'_, LauncherState>, request: NewCodes) -> CommandResult<Vec<CodeView>> {
    Ok(launcher.cubiq_admin_create_codes(request).await?)
}

#[tauri::command]
pub async fn cubiq_admin_revoke_code(launcher: State<'_, LauncherState>, id: u64) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_revoke_code(id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_user(launcher: State<'_, LauncherState>, query: String) -> CommandResult<AdminUser> {
    Ok(launcher.cubiq_admin_user(&query).await?)
}

/// Liefert `true`, wenn der Spieler den Umhang schon hatte.
#[tauri::command]
pub async fn cubiq_admin_grant(launcher: State<'_, LauncherState>, player: String, cape_id: String) -> CommandResult<bool> {
    Ok(launcher.cubiq_admin_grant(&player, &cape_id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_revoke_grant(launcher: State<'_, LauncherState>, uuid: String, cape_id: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_revoke_grant(&uuid, &cape_id).await?)
}

#[tauri::command]
pub async fn cubiq_admin_ban(
    launcher: State<'_, LauncherState>,
    player: String,
    reason: Option<String>,
) -> CommandResult<AdminUser> {
    Ok(launcher.cubiq_admin_ban(&player, reason.as_deref()).await?)
}

#[tauri::command]
pub async fn cubiq_admin_unban(launcher: State<'_, LauncherState>, uuid: String) -> CommandResult<()> {
    Ok(launcher.cubiq_admin_unban(&uuid).await?)
}

// --- Anmeldung auf der Website per Cubiq Launcher (API §29) ------------------------------------------------

/// Konten, mit denen eine Website-Anmeldung bestätigt werden kann (aktives zuerst).
#[tauri::command]
pub async fn cubiq_web_login_accounts(
    launcher: State<'_, LauncherState>,
) -> CommandResult<Vec<cubiq_core::cubiq_api::web_login::WebLoginAccount>> {
    Ok(launcher.cubiq_web_login_accounts().await?)
}

/// Anfrage nachschlagen – per Link-Token oder eingetipptem Code. Bestätigt wird damit noch nichts.
#[tauri::command]
pub async fn cubiq_web_login_lookup(
    launcher: State<'_, LauncherState>,
    account: Option<String>,
    token: Option<String>,
    code: Option<String>,
) -> CommandResult<cubiq_core::cubiq_api::web_login::WebLoginRequest> {
    Ok(launcher.cubiq_web_login_lookup(account.as_deref(), token.as_deref(), code.as_deref()).await?)
}

/// Bestätigen (`approve = true`) oder ablehnen – nur nach einem Klick im Dialog.
#[tauri::command]
pub async fn cubiq_web_login_decide(
    launcher: State<'_, LauncherState>,
    account: Option<String>,
    id: String,
    code: String,
    approve: bool,
) -> CommandResult<()> {
    Ok(launcher.cubiq_web_login_decide(account.as_deref(), &id, &code, approve).await?)
}

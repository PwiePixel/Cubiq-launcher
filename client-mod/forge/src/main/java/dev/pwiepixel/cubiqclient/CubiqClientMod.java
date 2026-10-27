package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
//? if <1.20.5 {
/*import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
*///?}

/**
 * Forge-Einstieg. Reine Client-Mod: auf einem dedizierten Server passiert nichts
 * (und es werden keine Client-Klassen geladen).
 * Forge-API wird nur für die Tastenbelegungen (RegisterKeyMappingsEvent) und bis 1.20.4 für das HUD genutzt.
 */
@Mod(CubiqClient.MOD_ID)
public final class CubiqClientMod {
	// Konstruktor-Injektion des Lade-Kontexts gibt es erst in neueren Forge-Builds; bis 1.20.4 daher
	// FMLJavaModLoadingContext.get() (funktioniert in allen 46.x–49.x-Builds).
	//? if >=1.20.5 {
	public CubiqClientMod(FMLJavaModLoadingContext context) {
	//?} else
	/*@SuppressWarnings("removal") public CubiqClientMod() {*/
		if (FMLEnvironment.dist != Dist.CLIENT) return;
		CubiqClient.init();

		// Tastenbelegungen: EventBus 7 (ab Forge 1.21.6) hat pro Event einen eigenen Bus –
		// bis 1.21.11 je Mod-Bus-Gruppe (getBus), ab 26.1 ein globaler (BUS).
		//? if >=26.1 {
		/*RegisterKeyMappingsEvent.BUS.addListener(CubiqKeys::register);
		*///?} elif >=1.21.6 {
		/*RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(CubiqKeys::register);
		*///?} elif >=1.20.5 {
		context.getModEventBus().addListener((RegisterKeyMappingsEvent e) -> CubiqKeys.register(e));
		//?} else
		/*FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> CubiqKeys.register(e));*/

		// "Konfigurieren" in Forges Mod-Liste öffnet das Cubiq-Menü.
		ConfigScreenHandler.ConfigScreenFactory configScreen =
				new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new CubiqMenuScreen(parent));
		// ModLoadingContext.get() gibt es in allen Versionen (1.21 hat keine Instanzmethode am Kontext).
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> configScreen);

		// HUD bis 1.20.4: ForgeGui ersetzt Gui#render komplett → Forge-Event nach dem HUD.
		// Ab 1.20.6 zeichnet GuiMixin am Ende von Gui#render.
		//? if <1.20.5
		/*MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> CubiqClient.get().hud().render(Gfx.of(e.getGuiGraphics())));*/
	}
}

package dev.pwiepixel.cubiqclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
//? if >=1.9 {
/*import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.BlockPos;
*///?} else {
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.util.BlockPos;
//?}
//? if >=1.10 {
/*import net.minecraft.world.GameType;
*///?}

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Alle Zugriffe auf Minecraft/Forge, die sich zwischen 1.8.9 und 1.12.2 unterscheiden (MCP-Namen,
 * Forge-Event-Felder → Getter ab 1.9, Ausrüstungs-Slots ab 1.9, ItemStack.EMPTY ab 1.11 …).
 * Der übrige Code ist versionsneutral und ruft nur diese Methoden auf.
 */
public final class Mc {
	private static String mcVersion;

	private Mc() {
	}

	public static Minecraft mc() {
		return Minecraft.getMinecraft();
	}

	/** Laufende Minecraft-Version (zur Laufzeit gelesen – die Konstante würde beim Kompilieren eingesetzt). */
	public static String version() {
		if (mcVersion == null) {
			try {
				Field f = Class.forName("net.minecraftforge.fml.common.Loader").getField("MC_VERSION");
				mcVersion = String.valueOf(f.get(null));
			} catch (ReflectiveOperationException | RuntimeException e) {
				mcVersion = "?";
			}
		}
		return mcVersion;
	}

	/** Spielverzeichnis (.minecraft bzw. Instanzordner). */
	public static java.io.File gameDir() {
		//? if >=1.12 {
		/*return mc().gameDir;
		*///?} else
		return mc().mcDataDir;
	}

	// --- Spieler, Welt, Schrift ---

	/** Aktuell offener Bildschirm (null = im Spiel). */
	public static GuiScreen screen() {
		return mc().currentScreen;
	}

	public static void setScreen(GuiScreen screen) {
		mc().displayGuiScreen(screen);
	}

	/** HUD per F1 ausgeblendet? */
	public static boolean hudHidden() {
		return mc().gameSettings.hideGUI;
	}

	public static boolean firstPerson() {
		return mc().gameSettings.thirdPersonView == 0;
	}

	public static WorldClient world() {
		//? if >=1.10 {
		/*return mc().world;
		*///?} else
		return mc().theWorld;
	}

	public static EntityPlayerSP player() {
		//? if >=1.10 {
		/*return mc().player;
		*///?} else
		return mc().thePlayer;
	}

	public static FontRenderer font() {
		//? if >=1.11 {
		/*return mc().fontRenderer;
		*///?} else
		return mc().fontRendererObj;
	}

	public static NetHandlerPlayClient connection() {
		//? if >=1.9 {
		/*return mc().getConnection();
		*///?} else
		return mc().getNetHandler();
	}

	/** Kurzer Hinweis über der Hotbar. */
	public static void actionBar(String message) {
		if (mc().ingameGUI == null) return;
		//? if >=1.10 {
		/*mc().ingameGUI.setOverlayMessage(message, false);
		*///?} else
		mc().ingameGUI.setRecordPlaying(message, false);
	}

	/** Klick-Geräusch der Vanilla-Knöpfe. */
	public static void clickSound() {
		//? if >=1.9 {
		/*mc().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		*///?} else
		mc().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
	}

	/** Leiser, etwas hellerer Klick für Sozial-Benachrichtigungen (Master-Lautstärke wie der Knopfklick). */
	public static void toastSound() {
		//? if >=1.9 {
		/*mc().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.4F));
		*///?} else
		mc().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.4F));
	}

	/** Läuft das Spiel im Vollbild? */
	public static boolean fullscreen() {
		return mc().isFullScreen();
	}

	/**
	 * Aktuelle Welt verlassen (Server trennen bzw. Einzelspieler speichern und beenden) – wie „Trennen“ im
	 * Pausenmenü, ohne einen Bildschirm zu öffnen.
	 */
	public static void leaveWorld() {
		WorldClient w = world();
		if (w == null) return;
		w.sendQuittingDisconnectingPacket();
		mc().loadWorld((WorldClient) null);
	}

	/** Mit einem Server verbinden (Einladung „Beitreten“); zurück führt in die Serverliste. */
	public static void connect(String address, String label) {
		net.minecraft.client.multiplayer.ServerData data = new net.minecraft.client.multiplayer.ServerData(
				label == null || label.isEmpty() ? address : label, address, false);
		GuiScreen back = new net.minecraft.client.gui.GuiMultiplayer(new net.minecraft.client.gui.GuiMainMenu());
		mc().displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(back, mc(), data));
	}

	public static ScaledResolution scaledResolution() {
		return new ScaledResolution(mc());
	}

	// --- Forge-Events (bis 1.8.9 öffentliche Felder, ab 1.9 Getter/Setter) ---

	public static RenderGameOverlayEvent.ElementType overlayType(RenderGameOverlayEvent event) {
		//? if >=1.9 {
		/*return event.getType();
		*///?} else
		return event.type;
	}

	public static ScaledResolution resolution(RenderGameOverlayEvent event) {
		//? if >=1.9 {
		/*return event.getResolution();
		*///?} else
		return event.resolution;
	}

	public static float partialTicks(RenderGameOverlayEvent event) {
		//? if >=1.9 {
		/*return event.getPartialTicks();
		*///?} else
		return event.partialTicks;
	}

	/** Bildschirm, zu dem ein {@code GuiScreenEvent} gehört. */
	public static GuiScreen eventGui(net.minecraftforge.client.event.GuiScreenEvent event) {
		//? if >=1.9 {
		/*return event.getGui();
		*///?} else
		return event.gui;
	}

	/** Maustaste des Ereignisses (-1 = nur Bewegung/Mausrad). */
	public static int mouseButton(MouseEvent event) {
		//? if >=1.9 {
		/*return event.getButton();
		*///?} else
		return event.button;
	}

	public static boolean mouseButtonDown(MouseEvent event) {
		//? if >=1.9 {
		/*return event.isButtonstate();
		*///?} else
		return event.buttonstate;
	}

	public static int mouseWheel(MouseEvent event) {
		//? if >=1.9 {
		/*return event.getDwheel();
		*///?} else
		return event.dwheel;
	}

	public static GuiScreen openedGui(GuiOpenEvent event) {
		//? if >=1.9 {
		/*return event.getGui();
		*///?} else
		return event.gui;
	}

	public static void setOpenedGui(GuiOpenEvent event, GuiScreen screen) {
		//? if >=1.9 {
		/*event.setGui(screen);
		*///?} else
		event.gui = screen;
	}

	/** Blickwinkel der Kamera überschreiben (Freelook). */
	public static void setCamera(EntityViewRenderEvent.CameraSetup event, float yaw, float pitch) {
		//? if >=1.9 {
		/*event.setYaw(yaw);
		event.setPitch(pitch);
		*///?} else {
		event.yaw = yaw;
		event.pitch = pitch;
		//?}
	}

	// --- Spielinhalte ---

	/**
	 * Ausrüstung: 0 Helm, 1 Brustpanzer, 2 Hose, 3 Stiefel, 4 Haupthand.
	 * Liefert null für einen leeren Slot (ab 1.11 gibt Minecraft ItemStack.EMPTY statt null zurück).
	 */
	public static ItemStack equipment(EntityPlayer player, int slot) {
		ItemStack stack;
		//? if >=1.9 {
		/*EntityEquipmentSlot[] slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
				EntityEquipmentSlot.FEET, EntityEquipmentSlot.MAINHAND};
		stack = player.getItemStackFromSlot(slots[slot]);
		*///?} else
		stack = slot == 4 ? player.getHeldItem() : player.getCurrentArmor(3 - slot);
		return isEmpty(stack) ? null : stack;
	}

	public static boolean isEmpty(ItemStack stack) {
		//? if >=1.11 {
		/*return stack == null || stack.isEmpty();
		*///?} else
		return stack == null || stack.stackSize <= 0;
	}

	/** Gegenstand über seinen Registry-Namen (z. B. "iron_helmet"), null wenn unbekannt. */
	public static Item item(String name) {
		return Item.getByNameOrId("minecraft:" + name);
	}

	@SuppressWarnings("unchecked")
	public static Collection<PotionEffect> effects(EntityPlayer player) {
		return player.getActivePotionEffects();
	}

	public static Potion potion(PotionEffect effect) {
		//? if >=1.9 {
		/*return effect.getPotion();
		*///?} else
		return Potion.potionTypes[effect.getPotionID()];
	}

	/** Block unter den Füßen einer Entity (BlockPos liegt ab 1.9 in util.math). */
	public static BlockPos blockPos(net.minecraft.entity.Entity entity) {
		return new BlockPos(entity);
	}

	/** Übersetzter Name des Bioms an einer Position. */
	public static String biomeName(World world, BlockPos pos) {
		//? if >=1.9.4 {
		/*return world.getBiome(pos).getBiomeName();
		*///?} elif >=1.9 {
		/*return world.getBiomeGenForCoords(pos).getBiomeName();
		*///?} else
		return world.getBiomeGenForCoords(pos).biomeName;
	}

	/** Abklingzeit-Anzeige am Fadenkreuz eingeschaltet? (Erst ab 1.9 gibt es die Angriffs-Abklingzeit.) */
	public static boolean attackIndicatorAtCrosshair() {
		//? if >=1.9 {
		/*return mc().gameSettings.attackIndicator == 1;
		*///?} else
		return false;
	}

	/** Aufgeladene Angriffsstärke 0..1 (vor 1.9 immer 1). */
	public static float attackStrength(EntityPlayer player) {
		//? if >=1.9 {
		/*return player.getCooledAttackStrength(0.0F);
		*///?} else
		return 1.0F;
	}

	public static GuiScreen worldSelectScreen(GuiScreen parent) {
		//? if >=1.9 {
		/*return new GuiWorldSelection(parent);
		*///?} else
		return new GuiSelectWorld(parent);
	}

	// --- PvP-Anzeigen / Optik ---

	/**
	 * Entfernung vom Auge des Spielers zum getroffenen Punkt (reine Anzeige).
	 * Fällt auf die Mitte des Ziels zurück, wenn gerade kein Treffer anliegt.
	 */
	public static double hitDistance(EntityPlayer player, net.minecraft.entity.Entity target) {
		//? if >=1.9 {
		/*net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1.0F);
		net.minecraft.util.math.RayTraceResult hit = mc().objectMouseOver;
		if (hit != null && hit.entityHit == target && hit.hitVec != null) return eye.distanceTo(hit.hitVec);
		return eye.distanceTo(new net.minecraft.util.math.Vec3d(target.posX, target.posY + target.height / 2.0, target.posZ));
		*///?} else {
		net.minecraft.util.Vec3 eye = player.getPositionEyes(1.0F);
		net.minecraft.util.MovingObjectPosition hit = mc().objectMouseOver;
		if (hit != null && hit.entityHit == target && hit.hitVec != null) return eye.distanceTo(hit.hitVec);
		return eye.distanceTo(new net.minecraft.util.Vec3(target.posX, target.posY + target.height / 2.0, target.posZ));
		//?}
	}

	/**
	 * Entfernung vom Auge des Spielers bis zu Minecrafts aktuellem Treffer (Block oder Wesen unter dem Fadenkreuz),
	 * unendlich ohne Treffer.
	 */
	public static double aimDistance(EntityPlayer player) {
		//? if >=1.9 {
		/*net.minecraft.util.math.RayTraceResult hit = mc().objectMouseOver;
		if (hit == null || hit.hitVec == null || hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		*///?} else {
		net.minecraft.util.MovingObjectPosition hit = mc().objectMouseOver;
		if (hit == null || hit.hitVec == null || hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		//?}
		return player.getPositionEyes(1.0F).distanceTo(hit.hitVec);
	}

	/** Zelle des Blocks unter dem Fadenkreuz (Minecrafts aktueller Treffer) oder null: kein Block (Wesen, nichts). */
	public static BlockPos aimBlock() {
		//? if >=1.9 {
		/*net.minecraft.util.math.RayTraceResult hit = mc().objectMouseOver;
		if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) return null;
		*///?} else {
		net.minecraft.util.MovingObjectPosition hit = mc().objectMouseOver;
		if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) return null;
		//?}
		return hit.getBlockPos();
	}

	/**
	 * Durchsichtig für den Klick-Test der Begleiter ({@code PetSight#seeThrough})? Pflanzen ({@code BlockBush}: Getreide,
	 * kurzes und hohes Gras, Farn, Blumen, Setzlinge, toter Busch, Pilze …) ohne Kollisions-Box und eine einzelne
	 * Schneeschicht (ihre Box ist flach). Die Seerose hat eine Box und hält den Strahl auf.
	 */
	public static boolean seeThrough(World w, BlockPos pos) {
		net.minecraft.block.state.IBlockState state = w.getBlockState(pos);
		net.minecraft.block.Block b = state.getBlock();
		//? if >=1.9 {
		/*net.minecraft.util.math.AxisAlignedBB box = state.getCollisionBoundingBox(w, pos);
		*///?} else {
		b.setBlockBoundsBasedOnState(w, pos);
		net.minecraft.util.AxisAlignedBB box = b.getCollisionBoundingBox(w, pos, state);
		//?}
		int snow = b instanceof net.minecraft.block.BlockSnow ? state.getValue(net.minecraft.block.BlockSnow.LAYERS) : 0;
		return dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSight.seeThrough(b instanceof net.minecraft.block.BlockBush, false,
				box == null || box.maxY <= box.minY, snow);
	}

	/**
	 * Erster Block, dessen Auswahl-Umriss der Strahl vom Auge ({@code ex, ey, ez}) in Richtung ({@code dx, dy, dz},
	 * normiert) zwischen {@code from} und {@code to} Blöcken trifft – wie Minecrafts Fadenkreuz, Flüssigkeiten zählen nicht.
	 * Ergebnis: Entfernung ab dem Auge, NaN = keiner; die Zelle in {@code cell}.
	 */
	public static double clipBlock(World w, double ex, double ey, double ez, double dx, double dy, double dz, double from,
			double to, int[] cell) {
		//? if >=1.9 {
		/*net.minecraft.util.math.Vec3d eye = new net.minecraft.util.math.Vec3d(ex, ey, ez);
		net.minecraft.util.math.RayTraceResult hit = w.rayTraceBlocks(
				new net.minecraft.util.math.Vec3d(ex + dx * from, ey + dy * from, ez + dz * from),
				new net.minecraft.util.math.Vec3d(ex + dx * to, ey + dy * to, ez + dz * to), false, false, true);
		if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK || hit.hitVec == null) {
			return Double.NaN;
		}
		*///?} else {
		net.minecraft.util.Vec3 eye = new net.minecraft.util.Vec3(ex, ey, ez);
		net.minecraft.util.MovingObjectPosition hit = w.rayTraceBlocks(
				new net.minecraft.util.Vec3(ex + dx * from, ey + dy * from, ez + dz * from),
				new net.minecraft.util.Vec3(ex + dx * to, ey + dy * to, ez + dz * to), false, false, true);
		if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec == null) {
			return Double.NaN;
		}
		//?}
		BlockPos pos = hit.getBlockPos();
		if (pos == null) return Double.NaN;
		cell[0] = pos.getX();
		cell[1] = pos.getY();
		cell[2] = pos.getZ();
		return eye.distanceTo(hit.hitVec);
	}

	/**
	 * Entfernung vom Auge bis zum ersten Wesen auf dem Strahl (bis {@code to} Blöcke), das Minecrafts Fadenkreuz träfe:
	 * anklickbar, kein Zuschauer, nicht das eigene Reittier; sein Kasten um den Rand vergrößert wie dort. Unendlich = keins.
	 */
	public static double aimEntity(World w, EntityPlayer player, double ex, double ey, double ez, double dx, double dy, double dz,
			double to) {
		double x1 = ex + dx * to, y1 = ey + dy * to, z1 = ez + dz * to;
		//? if >=1.9 {
		/*net.minecraft.util.math.AxisAlignedBB area = new net.minecraft.util.math.AxisAlignedBB(Math.min(ex, x1) - 1,
				Math.min(ey, y1) - 1, Math.min(ez, z1) - 1, Math.max(ex, x1) + 1, Math.max(ey, y1) + 1, Math.max(ez, z1) + 1);
		*///?} else {
		net.minecraft.util.AxisAlignedBB area = new net.minecraft.util.AxisAlignedBB(Math.min(ex, x1) - 1, Math.min(ey, y1) - 1,
				Math.min(ez, z1) - 1, Math.max(ex, x1) + 1, Math.max(ey, y1) + 1, Math.max(ez, z1) + 1);
		//?}
		double best = Double.POSITIVE_INFINITY;
		for (net.minecraft.entity.Entity e : w.getEntitiesWithinAABBExcludingEntity(player, area)) {
			if (!e.canBeCollidedWith() || mount(player, e)) continue;
			if (e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator()) continue;
			double r = e.getCollisionBorderSize();
			double t = dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSight.enter(ex, ey, ez, dx, dy, dz,
					e.getEntityBoundingBox().minX - r, e.getEntityBoundingBox().minY - r, e.getEntityBoundingBox().minZ - r,
					e.getEntityBoundingBox().maxX + r, e.getEntityBoundingBox().maxY + r, e.getEntityBoundingBox().maxZ + r);
			if (t < best) best = t;
		}
		return best <= to ? best : Double.POSITIVE_INFINITY;
	}

	/** Reitet der Spieler auf {@code e} (ab 1.9: gehören beide zum selben Reit-Stapel)? Das Fadenkreuz übergeht es. */
	private static boolean mount(EntityPlayer player, net.minecraft.entity.Entity e) {
		//? if >=1.9 {
		/*return e.getLowestRidingEntity() == player.getLowestRidingEntity();
		*///?} else
		return e == player.ridingEntity;
	}

	/** Gegenstand in der Zweithand (ab 1.9) oder null: leer bzw. vor 1.9 (keine Zweithand). */
	public static ItemStack offhand(EntityPlayer player) {
		//? if >=1.9 {
		/*ItemStack stack = player.getHeldItemOffhand();
		return isEmpty(stack) ? null : stack;
		*///?} else
		return null;
	}

	/** Benutzt der Spieler gerade einen Gegenstand (Bogen, Essen, Block …)? */
	public static boolean usingItem(EntityPlayer player) {
		//? if >=1.9 {
		/*return player.isHandActive();
		*///?} else
		return player.isUsingItem();
	}

	/** Startet die Schlaganimation nur als Anzeige (kein Paket, kein Angriff). */
	public static void startSwingAnimation(EntityPlayer player) {
		player.isSwingInProgress = true;
		player.swingProgressInt = -1;
		//? if >=1.9 {
		/*player.swingingHand = net.minecraft.util.EnumHand.MAIN_HAND;
		*///?}
	}

	/** Trefferboxen wie F3+B ein-/ausschalten. */
	public static void setDebugHitboxes(boolean on) {
		if (mc().getRenderManager() != null) mc().getRenderManager().setDebugBoundingBox(on);
	}

	// --- Chat, Welt, Kamera ---

	/** Sendet eine Chat-Nachricht bzw. einen Befehl (mit "/") als Spieler. */
	public static void sendChat(String text) {
		EntityPlayerSP p = player();
		if (p != null && text != null && !text.isEmpty()) p.sendChatMessage(text);
	}

	public static void setClipboard(String text) {
		GuiScreen.setClipboardString(text);
	}

	public static String clipboard() {
		return GuiScreen.getClipboardString();
	}

	/** Adresse des Servers (null = Einzelspieler). */
	public static String serverAddress() {
		net.minecraft.client.multiplayer.ServerData data = mc().getCurrentServerData();
		return data == null ? null : data.serverIP;
	}

	/** Ordnername der Einzelspielerwelt (null = kein integrierter Server). */
	public static String levelName() {
		net.minecraft.server.integrated.IntegratedServer server = mc().getIntegratedServer();
		return server == null ? null : server.getFolderName();
	}

	/** Kennung der Dimension ("dim0", "dim-1" …) – Wegpunkte gelten nur in ihrer Dimension. */
	public static String dimensionId() {
		World w = world();
		if (w == null || w.provider == null) return "";
		//? if >=1.9 {
		/*return "dim" + w.provider.getDimension();
		*///?} else
		return "dim" + w.provider.getDimensionId();
	}

	/** Entity, aus deren Sicht gerendert wird (normalerweise der Spieler). */
	public static net.minecraft.entity.Entity viewEntity() {
		net.minecraft.entity.Entity e = mc().getRenderViewEntity();
		return e == null ? player() : e;
	}

	/** Zwischenwert einer Position für das aktuelle Teilbild. */
	public static double lerp(double last, double now, float partialTicks) {
		return last + (now - last) * partialTicks;
	}

	/** Welt-Einstellungen für die Kreativ-Testwelt des Selbsttests. */
	public static WorldSettings creativeWorld(long seed) {
		//? if >=1.10 {
		/*return new WorldSettings(seed, GameType.CREATIVE, true, false, WorldType.DEFAULT);
		*///?} else
		return new WorldSettings(seed, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT);
	}
}

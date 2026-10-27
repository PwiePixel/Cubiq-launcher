package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.ui.wheel.EmoteWheel;
import dev.pwiepixel.cubiqclient.online.EmoteHooks;
import dev.pwiepixel.cubiqclient.ui.Gfx;

/**
 * Emote-Rad (Taste halten, Maus zeigt, Loslassen spielt ab). Aussehen und Bedienung stehen in {@link EmoteWheel};
 * hier nur die Rad-Taste, Hinweise und Ton. Kein Vanilla-Hintergrund – die Welt bleibt sichtbar.
 */
public final class EmoteWheelScreen extends CubiqUiScreen {
	private final EmoteWheel wheel;

	public EmoteWheelScreen() {
		this(new EmoteWheel(EmoteHooks.emotes(), new Host()));
	}

	private EmoteWheelScreen(EmoteWheel wheel) {
		super(I18n.tr("wheel.title"), wheel);
		this.wheel = wheel;
	}

	public EmoteWheel wheel() {
		return wheel;
	}

	@Override
	protected boolean customBackground() {
		return true;
	}

	@Override
	protected void drawBackground(Gfx g, float partialTick) {
		// Das Rad dunkelt selbst ab.
	}

	private static final class Host implements EmoteWheel.Host {
		@Override
		public Boolean keyHeld() {
			return EmoteHooks.keyHeld(CubiqKeys.emoteWheel);
		}

		@Override
		public String keyName() {
			return EmoteHooks.keyName(CubiqKeys.emoteWheel);
		}

		@Override
		public void close() {
			EmoteHooks.closeScreen();
		}

		@Override
		public void actionBar(String text) {
			EmoteHooks.actionBar(text);
		}

		@Override
		public void click() {
			new CubiqMenuHost(null).playClick();
		}
	}
}

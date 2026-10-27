package dev.pwiepixel.cubiqclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Zugriff auf die Chat-Zeilen (Zusammenfassen gleicher Nachrichten, Kopieren per Strg+Klick).
 * Die drei Felder heißen von 1.14.4 bis 1.19.4 gleich, nur ihr Inhaltstyp ändert sich
 * (siehe {@link dev.pwiepixel.cubiqclient.compat.ChatLines}).
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<?> cubiqclient$allMessages();

	@Accessor("trimmedMessages")
	List<?> cubiqclient$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int cubiqclient$chatScrollbarPos();
}

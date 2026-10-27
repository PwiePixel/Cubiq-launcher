package dev.pwiepixel.cubiqclient.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Welt-Hosting: Veröffentlichung zurücknehmen (bis 26.1 gibt es kein {@code unpublishServer}). */
@Mixin(IntegratedServer.class)
public interface HostingServerAccessor {
	@Accessor("publishedPort")
	void cubiqclient$setPublishedPort(int port);
}

package dev.pwiepixel.cubiqrules.fabric;

import dev.pwiepixel.cubiqrules.common.Protocol;
import dev.pwiepixel.cubiqrules.mod.Mc;
import dev.pwiepixel.cubiqrules.mod.ModServerRules;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Networking for Minecraft 1.20.5+ (payload types). Both payloads carry the raw UTF-8 JSON bytes without a length
 * prefix (binding protocol). The global receiver makes Fabric API announce {@code cubiqclient:hello} to clients
 * (minecraft:register); {@link #canReceive} honours the client's own minecraft:register for {@code cubiqclient:rules}.
 */
final class FabricNet {
	/** Client → server. */
	record Hello(byte[] data) implements CustomPacketPayload {
		static final Type<Hello> TYPE = new Type<>(Mc.id("cubiqclient", "hello"));
		static final StreamCodec<FriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(
				(value, buf) -> buf.writeBytes(value.data), buf -> new Hello(readAll(buf)));

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}

	/** Server → client. */
	record Rules(byte[] data) implements CustomPacketPayload {
		static final Type<Rules> TYPE = new Type<>(Mc.id("cubiqclient", "rules"));
		static final StreamCodec<FriendlyByteBuf, Rules> CODEC = CustomPacketPayload.codec(
				(value, buf) -> buf.writeBytes(value.data), buf -> new Rules(readAll(buf)));

		@Override
		public Type<Rules> type() {
			return TYPE;
		}
	}

	private FabricNet() {
	}

	private static byte[] readAll(FriendlyByteBuf buf) {
		int n = buf.readableBytes();
		if (n > Protocol.MAX_PAYLOAD) {
			buf.skipBytes(n);
			return new byte[0];
		}
		byte[] data = new byte[n];
		buf.readBytes(data);
		return data;
	}

	/**
	 * Registers the payload types and the hello receiver. Returns false (mod stays inactive) if another mod already
	 * registered the same ids – e.g. the Cubiq Client, which can only happen if someone forces this mod onto a client.
	 */
	static boolean register(ModServerRules mod) {
		try {
			PayloadTypeRegistry.playC2S().register(Hello.TYPE, Hello.CODEC);
			PayloadTypeRegistry.playS2C().register(Rules.TYPE, Rules.CODEC);
		} catch (IllegalArgumentException e) {
			ModServerRules.log().warn("cubiqclient:hello / cubiqclient:rules are already registered by another mod ({}) – "
					+ "Cubiq Server Rules stays inactive.", e.getMessage());
			return false;
		}
		ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (payload, context) -> {
			if (payload.data().length > 0) mod.onHello(context.player(), payload.data());
		});
		return true;
	}

	static boolean canReceive(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Rules.TYPE);
	}

	static void send(ServerPlayer player, byte[] payload) {
		ServerPlayNetworking.send(player, new Rules(payload));
	}
}

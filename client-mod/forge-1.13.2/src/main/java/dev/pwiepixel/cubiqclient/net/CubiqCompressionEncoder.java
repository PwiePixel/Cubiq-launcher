package dev.pwiepixel.cubiqclient.net;

import dev.pwiepixel.cubiqclient.core.net.NetCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NettyCompressionEncoder;

/**
 * Vanillas Packer (Forge 1.13.2) ohne {@code new byte[]} je Paket – gleiche Schwelle, gleiche zlib-Stufe, gleiche
 * Bytes auf der Leitung. Unterklasse, damit Vanillas {@code instanceof} weiter greift.
 */
public final class CubiqCompressionEncoder extends NettyCompressionEncoder {
	private final NetCodecs.Deflate deflate = new NetCodecs.Deflate();
	private int threshold;

	public CubiqCompressionEncoder(int threshold) {
		super(threshold);
		this.threshold = threshold;
	}

	@Override
	public void setCompressionThreshold(int threshold) {
		super.setCompressionThreshold(threshold);
		this.threshold = threshold;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
		int size = in.readableBytes();
		if (size < threshold) {
			NetCodecs.writeVarInt(out, 0);
			out.writeBytes(in);
		} else {
			NetCodecs.writeVarInt(out, size);
			deflate.deflate(in, out);
		}
	}
}

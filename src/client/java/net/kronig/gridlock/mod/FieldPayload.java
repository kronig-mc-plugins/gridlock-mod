package net.kronig.gridlock.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: one raw field message, decoded in {@link FieldState#handle}. */
public record FieldPayload(byte[] data) implements CustomPacketPayload {

	public static final Type<FieldPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("gridlock", "field"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FieldPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBytes(payload.data()),
			buf -> {
				byte[] data = new byte[buf.readableBytes()];
				buf.readBytes(data);
				return new FieldPayload(data);
			});

	@Override
	public Type<FieldPayload> type() {
		return TYPE;
	}
}

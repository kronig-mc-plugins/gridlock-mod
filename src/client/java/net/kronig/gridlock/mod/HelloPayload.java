package net.kronig.gridlock.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: this client runs the mod, with the protocol version it speaks. */
public record HelloPayload(int protocol) implements CustomPacketPayload {

	public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("gridlock", "hello"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeInt(payload.protocol()),
			buf -> new HelloPayload(buf.readInt()));

	@Override
	public Type<HelloPayload> type() {
		return TYPE;
	}
}

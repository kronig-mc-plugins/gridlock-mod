package net.kronig.gridlock.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client side of the GridLock plugin. The server sends the unlocked field; this mod makes the local player
 * collide with its edges (hard stop without any server correction) and draws the border itself. On servers
 * without the plugin the mod does nothing.
 */
public final class GridLockMod implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("GridLock");

	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FieldPayload.TYPE, FieldPayload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(FieldPayload.TYPE, (payload, context) -> {
			byte[] data = payload.data();
			context.client().execute(() -> {
				try {
					FieldState.handle(data);
				} catch (RuntimeException e) {
					LOGGER.error("Unlesbare Feld-Nachricht vom Server, Mod-Funktionen aus", e);
					FieldState.clear();
					// Tell the server, so it gives us the normal server-side border back.
					if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
						ClientPlayNetworking.send(new HelloPayload(FieldState.SIGN_OFF));
					}
				}
			});
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			FieldState.clear();
			if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				ClientPlayNetworking.send(new HelloPayload(FieldState.PROTOCOL));
				LOGGER.info("GridLock-Server erkannt, Mod angemeldet (Protokoll {})", FieldState.PROTOCOL);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FieldState.clear());

		BorderRenderer renderer = new BorderRenderer();
		LevelRenderEvents.COLLECT_SUBMITS.register(renderer::render);
	}
}

package net.kronig.gridlock.mod.mixin;

import net.kronig.gridlock.mod.FieldState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any block change on the client (own digging included) redraws the border in the same frame. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void gridlock$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
		if (FieldState.active() && oldState != newState) {
			FieldState.markDirty();
		}
	}
}

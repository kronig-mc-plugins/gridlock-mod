package net.kronig.gridlock.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.kronig.gridlock.mod.FieldState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * The movement code collects "hard" entity colliders (boats, shulkers) before resolving a move. For the local
 * player we add a full-height wall for every locked column next to them. These are pure collision shapes: no
 * blocks and no entities, so nothing can get in the way of clicking, breaking or placing.
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {

	@ModifyExpressionValue(method = "collide", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> gridlock$addBorderWalls(List<VoxelShape> original, @Local(argsOnly = true) Vec3 movement) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof LocalPlayer) || !FieldState.active() || self.isSpectator()) {
			return original;
		}
		List<VoxelShape> walls = FieldState.walls(self.getBoundingBox().expandTowards(movement).inflate(1.0E-4));
		if (walls.isEmpty()) {
			return original;
		}
		List<VoxelShape> combined = new ArrayList<>(original.size() + walls.size());
		combined.addAll(original);
		combined.addAll(walls);
		return combined;
	}
}

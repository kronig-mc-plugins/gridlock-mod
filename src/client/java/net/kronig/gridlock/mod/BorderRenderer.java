package net.kronig.gridlock.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the field border on the client: a soft curtain on the boundary plane that is strongest at the ground and
 * fades out upwards, plus thin crisp lines along the terrain profile (top edge, floor edge, ends). The wall always
 * reaches up to the surface, so it is visible from inside a hole and from above.
 */
public final class BorderRenderer {

	private static final int RADIUS = 28;
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** One quad: 4 corners (x, y, z) and an alpha per corner; {@code line} quads are fully opaque. */
	private record Quad(double[] xyz, float[] alpha) {
	}

	private final List<Quad> quads = new ArrayList<>();
	private int builtRevision = -1;
	private long builtAt;
	private BlockPos builtFor = BlockPos.ZERO;
	private int loggedRevision = -1;

	public void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || !FieldState.active()) {
			return;
		}
		rebuildIfNeeded(player, level);
		if (quads.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int color = FieldState.color();
		int red = (color >> 16) & 255;
		int green = (color >> 8) & 255;
		int blue = color & 255;
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			for (Quad quad : quads) {
				for (int corner = 0; corner < 4; corner++) {
					vertex(buffer, pose, quad, corner, camera, red, green, blue);
				}
			}
		});
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Quad quad, int corner, Vec3 camera,
							   int red, int green, int blue) {
		// Subtract the camera in double precision, far from the origin floats are too coarse.
		float x = (float) (quad.xyz()[corner * 3] - camera.x);
		float y = (float) (quad.xyz()[corner * 3 + 1] - camera.y);
		float z = (float) (quad.xyz()[corner * 3 + 2] - camera.z);
		buffer.addVertex(pose, x, y, z).setColor(red, green, blue, Math.round(quad.alpha()[corner] * 255f));
	}

	// ------------------------------------------------------------------ geometry

	private void rebuildIfNeeded(LocalPlayer player, ClientLevel level) {
		BlockPos at = player.blockPosition();
		long now = System.currentTimeMillis();
		boolean stale = builtRevision != FieldState.revision() || !at.equals(builtFor) || now - builtAt > 1000;
		if (!stale) {
			return;
		}
		builtRevision = FieldState.revision();
		builtFor = at;
		builtAt = now;
		quads.clear();

		boolean ceiling = level.dimensionType().hasCeiling();
		int refY = at.getY();
		int revisionBefore = loggedRevision;
		loggedRevision = FieldState.revision();
		for (int x = at.getX() - RADIUS; x <= at.getX() + RADIUS; x++) {
			for (int z = at.getZ() - RADIUS; z <= at.getZ() + RADIUS; z++) {
				if (!FieldState.isAllowed(x, z)) {
					continue;
				}
				for (int[] dir : DIRECTIONS) {
					if (!FieldState.isAllowed(x + dir[0], z + dir[1])) {
						addEdge(level, x, z, dir, refY, ceiling);
					}
				}
			}
		}
		logOnce(revisionBefore, at);
	}

	private void logOnce(int revisionBefore, BlockPos at) {
		if (revisionBefore != loggedRevision) {
			GridLockMod.LOGGER.debug("Border-Geometrie: {} Quads um {}", quads.size(), at.toShortString());
		}
	}

	private void addEdge(ClientLevel level, int x, int z, int[] dir, int refY, boolean ceiling) {
		int[] profile = profile(level, x, z, dir, refY, ceiling);
		if (profile == null) {
			return;
		}
		int low = profile[0];
		int high = profile[1];
		boolean alongZ = dir[0] != 0;
		// A hair inside the field, so the curtain never z-fights with block faces lying in the plane.
		double plane = (alongZ ? (dir[0] > 0 ? x + 1 : x) : (dir[1] > 0 ? z + 1 : z)) - (dir[0] + dir[1]) * 0.004;
		double from = alongZ ? z : x;
		double to = from + 1;

		float strength = FieldState.glowStrength();
		float glowHeight = FieldState.glowHeight();
		if (strength > 0f) {
			if (high > low) {
				wall(alongZ, plane, from, to, low, high, strength * 0.6f, strength * 0.6f);
			}
			if (glowHeight > 0f) {
				wall(alongZ, plane, from, to, high, high + glowHeight, strength, 0f);
			}
		}

		float width = FieldState.lineWidth();
		wall(alongZ, plane, from, to, high, high + width, 1f, 1f);
		ground(alongZ, plane, from, to, high + 0.003, -(dir[0] + dir[1]) * width);
		if (high > low) {
			wall(alongZ, plane, from, to, low, low + width, 1f, 1f);
			ground(alongZ, plane, from, to, low + 0.003, -(dir[0] + dir[1]) * width);
		}
		// Vertical ends where the outline turns a corner or the profile changes.
		for (int end = 0; end <= 1; end++) {
			int step = end == 0 ? -1 : 1;
			int nx = alongZ ? x : x + step;
			int nz = alongZ ? z + step : z;
			int[] next = FieldState.isAllowed(nx, nz) && !FieldState.isAllowed(nx + dir[0], nz + dir[1])
					? profile(level, nx, nz, dir, refY, ceiling) : null;
			if (next != null && next[0] == low && next[1] == high) {
				continue;
			}
			double position = end == 0 ? from : to - width;
			wall(alongZ, plane, position, position + width, low, high, 1f, 1f);
		}
	}

	/** {floor of the field column, top of the terrain in the plane}, or null over the void. */
	private static int[] profile(ClientLevel level, int x, int z, int[] dir, int refY, boolean ceiling) {
		int inner = groundBelow(level, x, z, refY);
		if (inner == Integer.MIN_VALUE) {
			return null;
		}
		int outer = groundBelow(level, x + dir[0], z + dir[1], refY + 2);
		int low = inner + 1;
		int high = Math.max(low, outer == Integer.MIN_VALUE ? low : outer + 1);
		if (!ceiling) {
			high = Math.max(high, Math.max(
					level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
					level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dir[0], z + dir[1])));
		}
		return new int[]{low, high};
	}

	/** Y of the highest block with collision at or below {@code fromY}, MIN_VALUE if there is none nearby. */
	private static int groundBelow(ClientLevel level, int x, int z, int fromY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int stop = Math.max(level.getMinY(), fromY - 48);
		for (int y = fromY; y >= stop; y--) {
			pos.set(x, y, z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Vertical quad on the boundary plane, alpha fading from bottom to top. */
	private void wall(boolean alongZ, double plane, double from, double to, double bottom, double top,
					  float alphaBottom, float alphaTop) {
		double[] xyz = alongZ
				? new double[]{plane, bottom, from, plane, bottom, to, plane, top, to, plane, top, from}
				: new double[]{from, bottom, plane, to, bottom, plane, to, top, plane, from, top, plane};
		quads.add(new Quad(xyz, new float[]{alphaBottom, alphaBottom, alphaTop, alphaTop}));
	}

	/** Thin horizontal strip lying on the ground next to the plane (towards the field). */
	private void ground(boolean alongZ, double plane, double from, double to, double y, double offset) {
		double other = plane + offset;
		double[] xyz = alongZ
				? new double[]{plane, y, from, plane, y, to, other, y, to, other, y, from}
				: new double[]{from, y, plane, to, y, plane, to, y, other, from, y, other};
		quads.add(new Quad(xyz, new float[]{1f, 1f, 1f, 1f}));
	}
}

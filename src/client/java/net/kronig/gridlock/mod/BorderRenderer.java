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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the field border on the client.
 *
 * <p>For every boundary edge the renderer looks at the real air spaces of the field column next to it (tunnel,
 * staircase, shaft, cave, surface) over the whole height around the player, not just at the player's own level.
 * Each air space gets a thin line along its floor, one along its ceiling if it has one, and lines on ledges of
 * the terrain outside. Every one of those lines carries a soft glow that fades out upwards. Corners get exactly
 * one vertical post, shared by all edges meeting there, with overlapping ranges merged.
 */
public final class BorderRenderer {

	private static final int RADIUS = 40;
	/** Blocks scanned below and above the player for air spaces. */
	private static final int SCAN = 40;
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** One quad: 4 corners (x, y, z) and an alpha per corner. */
	private record Quad(double[] xyz, float[] alpha) {
	}

	/** An air space in a column: from {@code bottom} (first free block) to {@code top} (exclusive). */
	private record Gap(int bottom, int top, boolean capped) {
	}

	private final List<Quad> quads = new ArrayList<>();
	private final Map<Long, List<Gap>> gapCache = new HashMap<>();
	/** Vertex (packed x,z) -> vertical post ranges {from, to}. */
	private final Map<Long, List<int[]>> posts = new HashMap<>();
	private int builtRevision = -1;
	private long builtAt;
	private BlockPos builtFor = BlockPos.ZERO;

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
		posts.clear();
		gapCache.clear();

		for (int x = at.getX() - RADIUS; x <= at.getX() + RADIUS; x++) {
			for (int z = at.getZ() - RADIUS; z <= at.getZ() + RADIUS; z++) {
				if (!FieldState.isAllowed(x, z)) {
					continue;
				}
				for (int[] dir : DIRECTIONS) {
					if (!FieldState.isAllowed(x + dir[0], z + dir[1])) {
						addEdge(level, x, z, dir, at.getY());
					}
				}
			}
		}
		double h = FieldState.lineWidth() / 2.0;
		posts.forEach((vertex, ranges) -> {
			int vx = (int) (vertex >> 32);
			int vz = (int) (long) vertex;
			for (int[] range : merge(ranges)) {
				box(vx - h, range[0] - h, vz - h, vx + h, range[1] + h, vz + h);
			}
		});
		GridLockMod.LOGGER.debug("Border-Geometrie: {} Quads um {}", quads.size(), at.toShortString());
	}

	private void addEdge(ClientLevel level, int x, int z, int[] dir, int refY) {
		boolean alongZ = dir[0] != 0;
		int plane = alongZ ? (dir[0] > 0 ? x + 1 : x) : (dir[1] > 0 ? z + 1 : z);
		int from = alongZ ? z : x;
		int outerX = x + dir[0];
		int outerZ = z + dir[1];
		// A hair inside the field, so the glow never z-fights with block faces lying in the plane.
		double glowPlane = plane - (dir[0] + dir[1]) * 0.004;
		int outerSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outerX, outerZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (Gap gap : gaps(level, x, z, refY)) {
			line(alongZ, plane, glowPlane, from, gap.bottom(), gap.top());
			if (gap.capped()) {
				rod(alongZ, plane, from, from + 1, gap.top());
			}
			// Ledges of the terrain outside: solid below, free above, within this air space.
			int ledgeLimit = Math.min(gap.top(), gap.bottom() + SCAN);
			for (int y = gap.bottom() + 1; y < ledgeLimit; y++) {
				if (solid(level, pos.set(outerX, y - 1, outerZ)) && !solid(level, pos.set(outerX, y, outerZ))) {
					line(alongZ, plane, glowPlane, from, y, gap.top());
				}
			}
			// Posts at both ends, unless the outline simply continues there with the same air space.
			int postTop = gap.capped() ? gap.top() : Math.max(gap.bottom(), Math.min(gap.top(), outerSurface));
			if (postTop <= gap.bottom()) {
				continue;
			}
			for (int end = 0; end <= 1; end++) {
				int step = end == 0 ? -1 : 1;
				int nx = alongZ ? x : x + step;
				int nz = alongZ ? z + step : z;
				boolean continues = FieldState.isAllowed(nx, nz) && !FieldState.isAllowed(nx + dir[0], nz + dir[1])
						&& gaps(level, nx, nz, refY).contains(gap);
				if (continues) {
					continue;
				}
				int along = from + end;
				long vertex = FieldState.pack(alongZ ? plane : along, alongZ ? along : plane);
				posts.computeIfAbsent(vertex, key -> new ArrayList<>()).add(new int[]{gap.bottom(), postTop});
			}
		}
	}

	/** A crisp line at height {@code y} plus its glow, which fades out upwards but never leaves the air space. */
	private void line(boolean alongZ, int plane, double glowPlane, int from, int y, int ceiling) {
		rod(alongZ, plane, from, from + 1, y);
		float strength = FieldState.glowStrength();
		float glowHeight = FieldState.glowHeight();
		if (strength <= 0f || glowHeight <= 0f) {
			return;
		}
		double top = Math.min(y + glowHeight, ceiling);
		if (top <= y) {
			return;
		}
		// Cut off by a ceiling: end with the alpha the fade has reached there instead of jumping to zero.
		float alphaTop = (float) (strength * (1.0 - (top - y) / glowHeight));
		wall(alongZ, glowPlane, from, from + 1, y, top, strength, Math.max(0f, alphaTop));
	}

	/** All air spaces of a column within the scan range around the player. */
	private List<Gap> gaps(ClientLevel level, int x, int z, int refY) {
		return gapCache.computeIfAbsent(FieldState.pack(x, z), key -> {
			List<Gap> gaps = new ArrayList<>();
			int min = Math.max(level.getMinY(), refY - SCAN);
			int max = Math.min(level.getMaxY(), refY + SCAN);
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int y = min;
			// Start on solid ground: an air space cut off by the lower scan limit has no floor to draw.
			while (y <= max && !solid(level, pos.set(x, y, z))) {
				y++;
			}
			while (y <= max) {
				while (y <= max && solid(level, pos.set(x, y, z))) {
					y++;
				}
				if (y > max) {
					break;
				}
				int bottom = y;
				while (y <= max && !solid(level, pos.set(x, y, z))) {
					y++;
				}
				gaps.add(new Gap(bottom, y, y <= max));
			}
			return gaps;
		});
	}

	private static boolean solid(ClientLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Merges overlapping or touching ranges, so a post is one clean piece instead of stacked duplicates. */
	private static List<int[]> merge(List<int[]> ranges) {
		ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
		List<int[]> merged = new ArrayList<>();
		for (int[] range : ranges) {
			if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
				int[] last = merged.get(merged.size() - 1);
				last[1] = Math.max(last[1], range[1]);
			} else {
				merged.add(new int[]{range[0], range[1]});
			}
		}
		return merged;
	}

	/** Horizontal rod along an edge at height {@code y}, centred exactly on the boundary plane. */
	private void rod(boolean alongZ, double plane, double from, double to, double y) {
		double h = FieldState.lineWidth() / 2.0;
		// Slightly longer than the edge, so rods of neighbouring edges and the posts close up.
		if (alongZ) {
			box(plane - h, y - h, from - h, plane + h, y + h, to + h);
		} else {
			box(from - h, y - h, plane - h, to + h, y + h, plane + h);
		}
	}

	/** Opaque box: all six faces (the render type does not cull). */
	private void box(double x0, double y0, double z0, double x1, double y1, double z1) {
		float[] opaque = {1f, 1f, 1f, 1f};
		quads.add(new Quad(new double[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, opaque));
		quads.add(new Quad(new double[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, opaque));
		quads.add(new Quad(new double[]{x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, opaque));
		quads.add(new Quad(new double[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, opaque));
		quads.add(new Quad(new double[]{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, opaque));
		quads.add(new Quad(new double[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, opaque));
	}

	/** Vertical quad on the boundary plane, alpha fading from bottom to top. */
	private void wall(boolean alongZ, double plane, double from, double to, double bottom, double top,
					  float alphaBottom, float alphaTop) {
		double[] xyz = alongZ
				? new double[]{plane, bottom, from, plane, bottom, to, plane, top, to, plane, top, from}
				: new double[]{from, bottom, plane, to, bottom, plane, to, top, plane, from, top, plane};
		quads.add(new Quad(xyz, new float[]{alphaBottom, alphaBottom, alphaTop, alphaTop}));
	}
}

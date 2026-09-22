package net.kronig.gridlock.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the field border on the client.
 *
 * <p>For every boundary edge the renderer looks at the real air spaces of the field column next to it (tunnel,
 * staircase, shaft, cave, surface) over the whole height around the player. The result is one continuous frame
 * per walkable level: a line along the boundary on the higher of the two sides (the floor of the field, or the
 * top of the blocks standing right outside), with vertical pieces where that height steps up or down. Heights come from the real collision shapes, so slabs, farmland, paths and the like are
 * followed where they actually end.
 *
 * <p>Lines are real lines with a fixed width in pixels (like the block selection outline), so they stay thin no
 * matter how close the camera gets. A soft glow fades out above every floor line, and a much fainter curtain fills
 * the whole height of the air space (up to the terrain surface under open sky), so the border stays readable on
 * tall staircases and in shafts.
 */
public final class BorderRenderer {

	private static final int RADIUS = 40;
	/** Blocks scanned below and above the player for air spaces. */
	private static final int SCAN = 40;
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** A glow quad: 4 corners (x, y, z) and an alpha per corner. */
	private record Quad(double[] xyz, float[] alpha) {
	}

	private record Line(double x1, double y1, double z1, double x2, double y2, double z2) {
	}

	/** An air space in a column: from the top of its floor to the underside of its ceiling. */
	private record Gap(double bottom, double top, boolean capped) {
	}

	/** Where the frame line of one edge runs within an air space. */
	private record Level(Gap gap, double y) {
	}

	private final List<Quad> quads = new ArrayList<>();
	private final List<Line> lines = new ArrayList<>();
	private final Map<Long, List<Gap>> gapCache = new HashMap<>();
	/** Corner (packed x,z) -> the line levels of every boundary edge ending there. */
	private final Map<Long, List<List<Level>>> corners = new HashMap<>();
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
		if (lines.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int color = FieldState.color();
		int red = (color >> 16) & 255;
		int green = (color >> 8) & 255;
		int blue = color & 255;
		// The server setting is in 1/100 blocks (for its entity rods); as a pixel width 3 means 3 px.
		float pixels = Math.max(1f, FieldState.lineWidth() * 100f);
		PoseStack poseStack = context.poseStack();

		if (!quads.isEmpty()) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
				for (Quad quad : quads) {
					for (int corner = 0; corner < 4; corner++) {
						float x = (float) (quad.xyz()[corner * 3] - camera.x);
						float y = (float) (quad.xyz()[corner * 3 + 1] - camera.y);
						float z = (float) (quad.xyz()[corner * 3 + 2] - camera.z);
						buffer.addVertex(pose, x, y, z).setColor(red, green, blue, Math.round(quad.alpha()[corner] * 255f));
					}
				}
			});
		}
		// Depth bias keeps the lines from flickering against the block edges they lie on.
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesDepthBias(), (pose, buffer) -> {
			for (Line line : lines) {
				// Subtract the camera in double precision, far from the origin floats are too coarse.
				float x1 = (float) (line.x1() - camera.x);
				float y1 = (float) (line.y1() - camera.y);
				float z1 = (float) (line.z1() - camera.z);
				float x2 = (float) (line.x2() - camera.x);
				float y2 = (float) (line.y2() - camera.y);
				float z2 = (float) (line.z2() - camera.z);
				float nx = x2 - x1;
				float ny = y2 - y1;
				float nz = z2 - z1;
				float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (length < 1.0E-6f) {
					continue;
				}
				nx /= length;
				ny /= length;
				nz /= length;
				lineVertex(buffer, pose, x1, y1, z1, nx, ny, nz, red, green, blue, pixels);
				lineVertex(buffer, pose, x2, y2, z2, nx, ny, nz, red, green, blue, pixels);
			}
		});
	}

	private static void lineVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
								   float nx, float ny, float nz, int red, int green, int blue, float pixels) {
		buffer.addVertex(pose, x, y, z).setColor(red, green, blue, 255).setNormal(pose, nx, ny, nz).setLineWidth(pixels);
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
		lines.clear();
		corners.clear();
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
		connectCorners();
		GridLockMod.LOGGER.debug("Border-Geometrie: {} Linien, {} Glow-Quads um {}", lines.size(), quads.size(),
				at.toShortString());
	}

	/**
	 * One continuous frame: along every boundary edge one line per air space of the field column. Where its height
	 * changes between neighbouring edges, a vertical piece at the shared corner joins the two, so the frame climbs
	 * steps instead of outlining single blocks.
	 */
	private void addEdge(ClientLevel level, int x, int z, int[] dir, int refY) {
		boolean alongZ = dir[0] != 0;
		int plane = alongZ ? (dir[0] > 0 ? x + 1 : x) : (dir[1] > 0 ? z + 1 : z);
		int from = alongZ ? z : x;
		// A hair inside the field, so the glow never z-fights with block faces lying in the plane.
		double glowPlane = plane - (dir[0] + dir[1]) * 0.004;
		List<Gap> gaps = gaps(level, x, z, refY);
		// Under open sky the curtain ends at the terrain surface on either side of the boundary.
		double surface = Math.max(
				level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
				level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dir[0], z + dir[1]));
		List<Level> levels = new ArrayList<>(gaps.size());
		for (Gap gap : gaps) {
			double y = lineHeight(level, x + dir[0], z + dir[1], gap);
			levels.add(new Level(gap, y));
			horizontal(alongZ, plane, glowPlane, from, y, gap.top());
			curtain(alongZ, glowPlane, from, gap, surface);
		}
		for (int end = 0; end <= 1; end++) {
			int along = from + end;
			long vertex = FieldState.pack(alongZ ? plane : along, alongZ ? along : plane);
			corners.computeIfAbsent(vertex, key -> new ArrayList<>()).add(levels);
		}
	}

	/**
	 * The frame runs along the higher side of the boundary: the floor of the field, or the top of the stack of
	 * blocks standing right outside it. A wall that fills the whole air space (a tunnel) has no top to run along,
	 * so the line stays on the floor there.
	 */
	private static double lineHeight(ClientLevel level, int outerX, int outerZ, Gap gap) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double height = gap.bottom();
		int firstY = (int) Math.floor(gap.bottom() - 1.0E-6);
		for (int y = firstY; y <= firstY + SCAN; y++) {
			double bottom = bottomOf(level, pos.set(outerX, y, outerZ));
			if (Double.isNaN(bottom)) {
				if (y >= (int) Math.floor(height)) {
					break; // free above the stack
				}
				continue; // still below the floor of the field
			}
			if (bottom > height + 1.0E-6) {
				break; // something floating above, not part of the stack
			}
			height = Math.max(height, topOf(level, pos));
		}
		// Walls higher than the limit (or filling the whole air space) keep the line on the floor.
		return height >= gap.top() - 1.0E-6 || height - gap.bottom() > FieldState.climbLimit() + 1.0E-6
				? gap.bottom() : height;
	}

	/** Joins the lines of different edges meeting at a corner, wherever their air spaces touch. */
	private void connectCorners() {
		corners.forEach((vertex, edges) -> {
			List<double[]> ranges = new ArrayList<>();
			for (int i = 0; i < edges.size(); i++) {
				for (int j = i + 1; j < edges.size(); j++) {
					for (Level a : edges.get(i)) {
						for (Level b : edges.get(j)) {
							boolean touching = a.gap().bottom() < b.gap().top() && b.gap().bottom() < a.gap().top();
							if (touching && Math.abs(a.y() - b.y()) > 1.0E-6) {
								ranges.add(new double[]{Math.min(a.y(), b.y()), Math.max(a.y(), b.y())});
							}
						}
					}
				}
			}
			int vx = (int) (vertex >> 32);
			int vz = (int) (long) vertex;
			for (double[] range : merge(ranges)) {
				lines.add(new Line(vx, range[0], vz, vx, range[1], vz));
			}
		});
	}

	/** A horizontal line plus its glow, which fades out upwards but never leaves the air space. */
	private void horizontal(boolean alongZ, int plane, double glowPlane, int from, double y, double ceiling) {
		edgeLine(alongZ, plane, from, y);
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
		float alphaTop = (float) Math.max(0.0, strength * (1.0 - (top - y) / glowHeight));
		double[] xyz = alongZ
				? new double[]{glowPlane, y, from, glowPlane, y, from + 1, glowPlane, top, from + 1, glowPlane, top, from}
				: new double[]{from, y, glowPlane, from + 1, y, glowPlane, from + 1, top, glowPlane, from, top, glowPlane};
		quads.add(new Quad(xyz, new float[]{strength, strength, alphaTop, alphaTop}));
	}

	/**
	 * Faint curtain over the whole height of an air space, so the border stays visible between the floor glow and
	 * whatever is above (a tall staircase, a shaft). Much weaker than the floor glow. Under open sky it reaches the
	 * terrain surface and fades out above it instead of ending in a hard edge.
	 */
	private void curtain(boolean alongZ, double glowPlane, int from, Gap gap, double surface) {
		float strength = FieldState.glowStrength() * FieldState.curtainShare();
		if (strength <= 0f) {
			return;
		}
		double top = gap.capped() ? gap.top() : Math.min(gap.top(), Math.max(gap.bottom(), surface));
		if (top > gap.bottom()) {
			glowQuad(alongZ, glowPlane, from, gap.bottom(), top, strength, strength);
		}
		if (!gap.capped()) {
			double fadeTop = Math.min(gap.top(), top + Math.max(1.0, FieldState.glowHeight()));
			if (fadeTop > top) {
				glowQuad(alongZ, glowPlane, from, top, fadeTop, strength, 0f);
			}
		}
	}

	private void glowQuad(boolean alongZ, double glowPlane, int from, double bottom, double top,
						  float alphaBottom, float alphaTop) {
		double[] xyz = alongZ
				? new double[]{glowPlane, bottom, from, glowPlane, bottom, from + 1, glowPlane, top, from + 1, glowPlane, top, from}
				: new double[]{from, bottom, glowPlane, from + 1, bottom, glowPlane, from + 1, top, glowPlane, from, top, glowPlane};
		quads.add(new Quad(xyz, new float[]{alphaBottom, alphaBottom, alphaTop, alphaTop}));
	}

	private void edgeLine(boolean alongZ, int plane, int from, double y) {
		lines.add(alongZ
				? new Line(plane, y, from, plane, y, from + 1)
				: new Line(from, y, plane, from + 1, y, plane));
	}

	/** All air spaces of a column within the scan range around the player, with real floor and ceiling heights. */
	private List<Gap> gaps(ClientLevel level, int x, int z, int refY) {
		return gapCache.computeIfAbsent(FieldState.pack(x, z), key -> {
			List<Gap> gaps = new ArrayList<>();
			int min = Math.max(level.getMinY(), refY - SCAN);
			int max = Math.min(level.getMaxY(), refY + SCAN);
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int y = min;
			// Start on solid ground: an air space cut off by the lower scan limit has no floor to draw.
			while (y <= max && Double.isNaN(topOf(level, pos.set(x, y, z)))) {
				y++;
			}
			while (y <= max) {
				double floor = Double.NaN;
				while (y <= max) {
					double top = topOf(level, pos.set(x, y, z));
					if (Double.isNaN(top)) {
						break;
					}
					floor = top;
					y++;
				}
				if (y > max || Double.isNaN(floor)) {
					break;
				}
				double ceiling = Double.NaN;
				while (y <= max) {
					ceiling = bottomOf(level, pos.set(x, y, z));
					if (!Double.isNaN(ceiling)) {
						break;
					}
					y++;
				}
				boolean capped = !Double.isNaN(ceiling);
				double top = capped ? ceiling : max + 1;
				if (top - floor > 1.0E-6) {
					gaps.add(new Gap(floor, top, capped));
				}
			}
			return gaps;
		});
	}

	/** World Y of the top of the collision shape at {@code pos}, NaN if nothing collides there. */
	private static double topOf(ClientLevel level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() ? Double.NaN : pos.getY() + shape.max(Direction.Axis.Y);
	}

	/** World Y of the underside of the collision shape at {@code pos}, NaN if nothing collides there. */
	private static double bottomOf(ClientLevel level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() ? Double.NaN : pos.getY() + shape.min(Direction.Axis.Y);
	}

	/** Merges overlapping or touching ranges, so a corner line is one clean piece instead of stacked duplicates. */
	private static List<double[]> merge(List<double[]> ranges) {
		ranges.sort((a, b) -> Double.compare(a[0], b[0]));
		List<double[]> merged = new ArrayList<>();
		for (double[] range : ranges) {
			if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1] + 1.0E-6) {
				double[] last = merged.get(merged.size() - 1);
				last[1] = Math.max(last[1], range[1]);
			} else {
				merged.add(new double[]{range[0], range[1]});
			}
		}
		return merged;
	}
}

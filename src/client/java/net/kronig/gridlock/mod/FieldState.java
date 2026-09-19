package net.kronig.gridlock.mod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** What the server told us about the field of the current world. Only touched on the client thread. */
public final class FieldState {

	public static final int PROTOCOL = 1;
	/** Sent instead of the protocol version when the mod has to switch itself off. */
	public static final int SIGN_OFF = -1;

	private static final LongSet COLUMNS = new LongOpenHashSet();
	private static boolean active;
	private static int endFreeRadius;
	private static int baseColor = 0xFF2828;
	private static float glowHeight = 2.6f;
	private static float glowStrength = 0.28f;
	private static float lineWidth = 0.03f;
	private static float progress;
	private static long flashUntil;
	private static int revision;

	private FieldState() {
	}

	static long pack(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public static void clear() {
		COLUMNS.clear();
		active = false;
		progress = 0f;
		revision++;
	}

	/** Decodes one message of the gridlock:field channel (format: see ModLink in the plugin). */
	public static void handle(byte[] data) {
		ByteBuffer in = ByteBuffer.wrap(data);
		switch (in.get()) {
			case 0 -> {
				COLUMNS.clear();
				active = in.get() != 0;
				endFreeRadius = in.getInt();
				baseColor = in.getInt();
				glowHeight = in.getInt() / 10f;
				glowStrength = in.getInt() / 100f;
				lineWidth = in.getInt() / 100f;
				readColumns(in, true);
				GridLockMod.LOGGER.debug("Feld empfangen: aktiv={}, Bloecke={}", active, COLUMNS.size());
			}
			case 1 -> readColumns(in, true);
			case 2 -> readColumns(in, false);
			case 3 -> progress = in.getFloat();
			case 4 -> flashUntil = System.currentTimeMillis() + 900;
			default -> {
			}
		}
		revision++;
	}

	private static void readColumns(ByteBuffer in, boolean add) {
		int count = in.getInt();
		for (int i = 0; i < count; i++) {
			long column = pack(in.getInt(), in.getInt());
			if (add) {
				COLUMNS.add(column);
			} else {
				COLUMNS.remove(column);
			}
		}
	}

	public static boolean active() {
		return active;
	}

	public static int columnCount() {
		return COLUMNS.size();
	}

	/** Changes whenever anything that affects the geometry changed. */
	public static int revision() {
		return revision;
	}

	public static boolean isAllowed(int x, int z) {
		if (endFreeRadius > 0) {
			double cx = x + 0.5;
			double cz = z + 0.5;
			if (cx * cx + cz * cz <= (double) endFreeRadius * endFreeRadius) {
				return true;
			}
		}
		return COLUMNS.contains(pack(x, z));
	}

	/** Full-height collision walls for every locked column the box would touch. */
	public static List<VoxelShape> walls(AABB box) {
		int minX = (int) Math.floor(box.minX);
		int maxX = (int) Math.floor(box.maxX);
		int minZ = (int) Math.floor(box.minZ);
		int maxZ = (int) Math.floor(box.maxZ);
		if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > 256) {
			return List.of(); // teleports and the like: leave it to the server
		}
		List<VoxelShape> walls = null;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (!isAllowed(x, z)) {
					if (walls == null) {
						walls = new ArrayList<>();
					}
					walls.add(Shapes.create(x, -2048, z, x + 1, 2048, z + 1));
				}
			}
		}
		return walls == null ? List.of() : walls;
	}

	public static float glowHeight() {
		return glowHeight;
	}

	public static float glowStrength() {
		return glowStrength;
	}

	public static float lineWidth() {
		return lineWidth;
	}

	/** Current border colour (0xRRGGBB): the base colour, shifting towards green while a block is being bought. */
	public static int color() {
		long now = System.currentTimeMillis();
		if (now < flashUntil) {
			float fade = Math.min(1f, (flashUntil - now) / 300f); // the last 300 ms blend back
			return lerp(baseColor, 0x46FF5A, fade);
		}
		if (progress <= 0f) {
			return baseColor;
		}
		float scaled = Math.min(1f, progress) * 3f;
		if (scaled < 1f) {
			return lerp(baseColor, 0xFF8C00, scaled);
		}
		if (scaled < 2f) {
			return lerp(0xFF8C00, 0xFFE128, scaled - 1f);
		}
		return lerp(0xFFE128, 0x46EB46, scaled - 2f);
	}

	private static int lerp(int from, int to, float t) {
		int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
		int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
		int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
		return (r << 16) | (g << 8) | b;
	}
}

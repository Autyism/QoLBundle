package io.github.autyism.qolbundle.modules;

import com.google.gson.JsonObject;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.module.setting.StringSetting;
import io.github.autyism.qolbundle.render.HighlightColor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Highlights slime chunks on the ground. Which chunks are slime chunks follows from the world
 * seed alone, so the seed has to be typed in (servers do not send it). In single-player the seed
 * of the open world is used automatically when the box is left empty.
 */
public class SlimeChunksModule extends Module {
	private static final String SECTION = "slime_chunks";
	private static final long SLIME_SCRAMBLER = 987234911L;
	private static final int REBUILD_TICKS = 20;
	private static final double LIFT = 0.03;

	private final StringSetting seed = add(new StringSetting("seed", "", 64));
	private final IntSetting radius = add(new IntSetting("radius", 4, 1, 8));
	private final EnumSetting<HighlightColor> color = add(new EnumSetting<>("color", HighlightColor.GREEN));
	private final IntSetting opacity = add(new IntSetting("opacity", 35, 10, 80, "%"));
	private final BoolSetting showHud = add(new BoolSetting("show_hud", true));
	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));

	/** Ground patches of all slime chunks in range: pairs of (north-west-low corner, south-east-high corner). */
	private final List<Vec3[]> patches = new ArrayList<>();
	/** Corners of slime chunks in range, as x/z block coordinates. */
	private final List<int[]> slimeChunks = new ArrayList<>();
	private OptionalLong activeSeed = OptionalLong.empty();
	private boolean inOverworld;
	private boolean standingInSlimeChunk;
	private int ticks;
	@Nullable
	private String loadedWorldId;
	private String storedSeed = "";

	public SlimeChunksModule() {
		super("slime_chunks", ModuleCategory.TECHNICAL, false);
	}

	public StringSetting seedSetting() {
		return seed;
	}

	/** The seed in use, empty when none is known (for the self-test). */
	public OptionalLong getActiveSeed() {
		return activeSeed;
	}

	public int getHighlightedChunkCount() {
		return slimeChunks.size();
	}

	public boolean isStandingInSlimeChunk() {
		return standingInSlimeChunk;
	}

	/** The game's own rule: one chunk in ten, picked by a random number seeded from the world seed and the chunk position. */
	public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
		return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, worldSeed, SLIME_SCRAMBLER).nextInt(10) == 0;
	}

	/** Same reading of the seed box as the "create world" screen: a number is used as is, other text is hashed. */
	private static long parseSeed(String text) {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return text.hashCode();
		}
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		patches.clear();
		slimeChunks.clear();
		ticks = 0;
	}

	@Override
	public void onTick(Minecraft client) {
		syncWorldData();
		if (client.player == null || client.level == null) {
			activeSeed = OptionalLong.empty();
			return;
		}
		String text = seed.get().trim();
		if (!text.isEmpty()) {
			activeSeed = OptionalLong.of(parseSeed(text));
		} else if (client.getSingleplayerServer() != null) {
			activeSeed = OptionalLong.of(client.getSingleplayerServer().overworld().getSeed());
		} else {
			activeSeed = OptionalLong.empty();
		}
		inOverworld = client.level.dimension() == Level.OVERWORLD;
		if (activeSeed.isEmpty() || !inOverworld) {
			patches.clear();
			slimeChunks.clear();
			standingInSlimeChunk = false;
			return;
		}
		int chunkX = client.player.getBlockX() >> 4;
		int chunkZ = client.player.getBlockZ() >> 4;
		standingInSlimeChunk = isSlimeChunk(activeSeed.getAsLong(), chunkX, chunkZ);
		if (ticks++ % REBUILD_TICKS == 0) {
			rebuild(client.level, chunkX, chunkZ);
		}
	}

	/** Works out the slime chunks around the player and the ground patches to draw on them. */
	private void rebuild(ClientLevel world, int centerX, int centerZ) {
		patches.clear();
		slimeChunks.clear();
		long worldSeed = activeSeed.getAsLong();
		int r = radius.get();
		for (int cx = centerX - r; cx <= centerX + r; cx++) {
			for (int cz = centerZ - r; cz <= centerZ + r; cz++) {
				if (!isSlimeChunk(worldSeed, cx, cz)) {
					continue;
				}
				slimeChunks.add(new int[] {cx << 4, cz << 4});
				if (world.getChunkSource().hasChunk(cx, cz)) {
					addGroundPatches(world, cx << 4, cz << 4);
				}
			}
		}
	}

	/**
	 * Covers the surface of one chunk. Neighbouring columns of the same height in a row are merged
	 * into one strip, so flat ground needs 16 strips instead of 256 squares.
	 */
	private void addGroundPatches(ClientLevel world, int baseX, int baseZ) {
		for (int dz = 0; dz < 16; dz++) {
			int runStart = 0;
			int runHeight = world.getHeight(Heightmap.Types.MOTION_BLOCKING, baseX, baseZ + dz);
			for (int dx = 1; dx <= 16; dx++) {
				int height = dx < 16 ? world.getHeight(Heightmap.Types.MOTION_BLOCKING, baseX + dx, baseZ + dz) : Integer.MIN_VALUE;
				if (height != runHeight) {
					double y = runHeight + LIFT;
					patches.add(new Vec3[] {new Vec3(baseX + runStart, y, baseZ + dz), new Vec3(baseX + dx, y, baseZ + dz + 1)});
					runStart = dx;
					runHeight = height;
				}
			}
		}
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || slimeChunks.isEmpty()) {
			return;
		}
		GizmoStyle fill = GizmoStyle.fill(color.get().withOpacity(opacity.get()));
		for (Vec3[] patch : patches) {
			Gizmos.rect(patch[0], patch[1], Direction.UP, fill);
		}
		// Corner posts through the whole world height, so the chunk can also be found from underground.
		int line = color.get().withOpacity(70);
		double bottom = client.level.getMinY();
		double top = client.level.getMaxY() + 1;
		for (int[] chunk : slimeChunks) {
			for (int i = 0; i <= 1; i++) {
				for (int j = 0; j <= 1; j++) {
					double x = chunk[0] + i * 16;
					double z = chunk[1] + j * 16;
					Gizmos.line(new Vec3(x, bottom, z), new Vec3(x, top, z), line, 2.0F);
				}
			}
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (!showHud.get()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		Component line;
		if (activeSeed.isEmpty()) {
			line = Component.translatable(key + "no_seed").withColor(0xAAAAAA);
		} else if (!inOverworld) {
			return; // slimes only use slime chunks in the Overworld
		} else if (standingInSlimeChunk) {
			line = Component.translatable(key + "yes").withColor(0x55FF55);
		} else {
			line = Component.translatable(key + "no");
		}
		layout.drawLines(context, client.font, position.get(), List.of(line));
	}

	// ---- the seed is remembered per world -------------------------------------------------------

	private void syncWorldData() {
		String worldId = WorldData.getWorldId();
		if (!Objects.equals(worldId, loadedWorldId)) {
			loadedWorldId = worldId;
			JsonObject section = WorldData.section(SECTION);
			if (section != null) {
				storedSeed = section.has("seed") ? section.get("seed").getAsString() : "";
				seed.set(storedSeed);
			}
			return;
		}
		if (worldId != null && !seed.get().equals(storedSeed)) {
			JsonObject section = WorldData.section(SECTION);
			if (section != null) {
				storedSeed = seed.get();
				section.addProperty("seed", storedSeed);
				WorldData.markDirty();
			}
		}
	}
}

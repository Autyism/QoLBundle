package io.github.autyi6969.qolbundle.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.autyi6969.qolbundle.data.WorldData;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Look at a nether portal and see where it leads: the matching coordinates in the other dimension
 * (no more dividing by 8 in your head), which portal it will link to, and whether the way back
 * returns to the same portal.
 *
 * <p>The game only tells the client about the dimension it is in, so the link prediction uses the
 * portals this client has already seen (remembered per world in {@link WorldData}).
 */
public class PortalCalculatorModule extends Module {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String SECTION = "portals";
	private static final int MAX_PORTAL_SIZE = 21;
	private static final int RESCAN_TICKS = 40;

	private final IntSetting range = add(new IntSetting("range", 24, 5, 64));
	private final BoolSetting showLink = add(new BoolSetting("show_link", true));
	private final BoolSetting showReturn = add(new BoolSetting("show_return", true));

	/** Dimension id -> portals seen there, for the current world. */
	private final Map<String, List<Portal>> known = new HashMap<>();
	@Nullable
	private String loadedWorldId;
	@Nullable
	private Info info;
	private int ticks;

	public PortalCalculatorModule() {
		super("portal_calculator", ModuleCategory.TECHNICAL, true);
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (isEnabled()) {
				syncWorldData();
				scanChunk(world, chunk);
			}
		});
	}

	/** A rectangular sheet of portal blocks, as inclusive block coordinates. */
	public record Portal(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		BlockPos nearestTo(BlockPos pos) {
			return new BlockPos(MathHelper.clamp(pos.getX(), minX, maxX), MathHelper.clamp(pos.getY(), minY, maxY),
					MathHelper.clamp(pos.getZ(), minZ, maxZ));
		}

		boolean contains(BlockPos pos) {
			return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
					&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
		}

		boolean intersects(Portal other) {
			return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
					&& minZ <= other.maxZ && maxZ >= other.minZ;
		}

		/** The middle of the bottom row: roughly where a player walks in. */
		double centerX() {
			return (minX + maxX + 1) / 2.0;
		}

		double centerZ() {
			return (minZ + maxZ + 1) / 2.0;
		}
	}

	/**
	 * What the HUD shows for the portal being looked at.
	 *
	 * @param target       matching block position in the other dimension
	 * @param link         known portal the game would pick there, or null if none is known in range
	 * @param linkDistance horizontal blocks between target and that portal
	 * @param returnPortal where the trip back from {@code link} ends, or null if unknown
	 */
	public record Info(Portal portal, boolean inNether, BlockPos target, @Nullable Portal link, int linkDistance,
			int searchRadius, @Nullable Portal returnPortal) {
		public boolean returnsHere() {
			return returnPortal != null && returnPortal.equals(portal);
		}
	}

	@Nullable
	public Info getInfo() {
		return info;
	}

	public int getKnownCount(String dimension) {
		List<Portal> list = known.get(dimension);
		return list == null ? 0 : list.size();
	}

	/**
	 * Where a new Nether portal whose Overworld-side target is the given position would come out:
	 * the nearest block of a known Overworld portal within 128 blocks, or null if none is known.
	 */
	@Nullable
	public BlockPos predictOverworldExit(BlockPos overworldTarget) {
		Portal link = findLink(known.get(OVERWORLD), overworldTarget, 128);
		return link == null ? null : link.nearestTo(overworldTarget);
	}

	/** Forgets every remembered portal of the current world (used by the self-test). */
	public void forgetAll() {
		known.clear();
		store();
	}

	@Override
	public void onTick(MinecraftClient client) {
		syncWorldData();
		info = null;
		if (client.player == null || client.world == null) {
			return;
		}
		ClientWorld world = client.world;
		if (++ticks % RESCAN_TICKS == 0) {
			rescanNearby(client, world);
		}

		HitResult hit = client.player.raycast(range.get(), 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos lookedAt = ((BlockHitResult) hit).getBlockPos();
		BlockPos portalPos = portalAt(world, lookedAt);
		if (portalPos == null) {
			return;
		}
		String dimension = dimensionId(world);
		boolean inNether = NETHER.equals(dimension);
		if (!inNether && !OVERWORLD.equals(dimension)) {
			return; // nether portals do nothing in other dimensions
		}
		Portal portal = remember(dimension, measure(world, portalPos));
		info = compute(portal, inNether);
	}

	/** The looked-at block if it is a portal block, or a portal block touching the looked-at obsidian. */
	@Nullable
	private static BlockPos portalAt(ClientWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isOf(Blocks.NETHER_PORTAL)) {
			return pos;
		}
		if (state.isOf(Blocks.OBSIDIAN)) {
			for (Direction direction : Direction.values()) {
				BlockPos neighbor = pos.offset(direction);
				if (world.getBlockState(neighbor).isOf(Blocks.NETHER_PORTAL)) {
					return neighbor;
				}
			}
		}
		return null;
	}

	private Info compute(Portal portal, boolean inNether) {
		// Nether -> Overworld multiplies by 8, Overworld -> Nether divides by 8. Y stays.
		double scale = inNether ? 8.0 : 0.125;
		BlockPos target = BlockPos.ofFloored(portal.centerX() * scale, portal.minY(), portal.centerZ() * scale);
		// The game searches 16 blocks around the target in the Nether, 128 in the Overworld.
		int radius = inNether ? 128 : 16;
		String here = inNether ? NETHER : OVERWORLD;
		String there = inNether ? OVERWORLD : NETHER;

		Portal link = findLink(known.get(there), target, radius);
		int distance = 0;
		Portal returnPortal = null;
		if (link != null) {
			// Shown to the player as the horizontal offset: that is the part worth fixing when lining portals up.
			BlockPos nearest = link.nearestTo(target);
			distance = (int) Math.round(Math.hypot(nearest.getX() - target.getX(), nearest.getZ() - target.getZ()));
			double back = inNether ? 0.125 : 8.0;
			BlockPos backTarget = BlockPos.ofFloored(link.centerX() * back, link.minY(), link.centerZ() * back);
			returnPortal = findLink(known.get(here), backTarget, inNether ? 16 : 128);
		}
		return new Info(portal, inNether, target, link, distance, radius, returnPortal);
	}

	/** Same rule as the game: closest portal block within a square of the given radius, lower Y wins ties. */
	@Nullable
	private static Portal findLink(@Nullable List<Portal> candidates, BlockPos target, int radius) {
		if (candidates == null) {
			return null;
		}
		Portal best = null;
		double bestDistance = Double.MAX_VALUE;
		int bestY = Integer.MAX_VALUE;
		for (Portal portal : candidates) {
			BlockPos nearest = portal.nearestTo(target);
			if (Math.abs(nearest.getX() - target.getX()) > radius || Math.abs(nearest.getZ() - target.getZ()) > radius) {
				continue;
			}
			double distance = nearest.getSquaredDistance(target);
			if (distance < bestDistance || distance == bestDistance && nearest.getY() < bestY) {
				best = portal;
				bestDistance = distance;
				bestY = nearest.getY();
			}
		}
		return best;
	}

	// ---- finding and remembering portals -------------------------------------------------------

	private static String dimensionId(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	/** Grows from one portal block to the whole rectangular sheet. */
	private static Portal measure(ClientWorld world, BlockPos start) {
		BlockState state = world.getBlockState(start);
		Direction.Axis axis = state.get(NetherPortalBlock.AXIS);
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		BlockPos min = extend(world, state, extend(world, state, start, Direction.DOWN), along.getOpposite());
		BlockPos max = extend(world, state, extend(world, state, start, Direction.UP), along);
		return new Portal(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()),
				Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
	}

	private static BlockPos extend(ClientWorld world, BlockState state, BlockPos from, Direction direction) {
		BlockPos.Mutable pos = from.mutableCopy();
		for (int i = 0; i < MAX_PORTAL_SIZE; i++) {
			pos.move(direction);
			if (world.getBlockState(pos) != state) {
				pos.move(direction.getOpposite());
				break;
			}
		}
		return pos.toImmutable();
	}

	/** Adds a portal, replacing any older overlapping record. Returns the stored instance. */
	private Portal remember(String dimension, Portal portal) {
		List<Portal> list = known.computeIfAbsent(dimension, key -> new ArrayList<>());
		if (list.contains(portal)) {
			return portal;
		}
		list.removeIf(portal::intersects);
		list.add(portal);
		store();
		return portal;
	}

	private void scanChunk(ClientWorld world, WorldChunk chunk) {
		ChunkSection[] sections = chunk.getSectionArray();
		String dimension = dimensionId(world);
		for (int i = 0; i < sections.length; i++) {
			ChunkSection section = sections[i];
			// hasAny looks at the section's block palette first, so portal-free sections cost almost nothing.
			if (section == null || section.isEmpty() || !section.hasAny(state -> state.isOf(Blocks.NETHER_PORTAL))) {
				continue;
			}
			int baseX = chunk.getPos().getStartX();
			int baseY = chunk.sectionIndexToCoord(i) << 4;
			int baseZ = chunk.getPos().getStartZ();
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!section.getBlockState(x, y, z).isOf(Blocks.NETHER_PORTAL)) {
							continue;
						}
						BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
						if (!isKnown(dimension, pos)) {
							remember(dimension, measure(world, pos));
						}
					}
				}
			}
		}
	}

	private boolean isKnown(String dimension, BlockPos pos) {
		List<Portal> list = known.get(dimension);
		if (list == null) {
			return false;
		}
		for (Portal portal : list) {
			if (portal.contains(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Every two seconds: pick up portals lit near the player and drop remembered ones that are gone. */
	private void rescanNearby(MinecraftClient client, ClientWorld world) {
		String dimension = dimensionId(world);
		int chunkX = client.player.getBlockX() >> 4;
		int chunkZ = client.player.getBlockZ() >> 4;
		List<Portal> list = known.get(dimension);
		if (list != null) {
			boolean removed = list.removeIf(portal -> {
				int cx = portal.minX() >> 4;
				int cz = portal.minZ() >> 4;
				boolean near = Math.abs(cx - chunkX) <= 1 && Math.abs(cz - chunkZ) <= 1;
				return near && world.getChunkManager().isChunkLoaded(cx, cz)
						&& !world.getBlockState(new BlockPos(portal.minX(), portal.minY(), portal.minZ())).isOf(Blocks.NETHER_PORTAL);
			});
			if (removed) {
				store();
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX + dx, chunkZ + dz);
				if (chunk != null) {
					scanChunk(world, chunk);
				}
			}
		}
	}

	// ---- saving / loading ----------------------------------------------------------------------

	private void syncWorldData() {
		String worldId = WorldData.getWorldId();
		if (Objects.equals(worldId, loadedWorldId)) {
			return;
		}
		loadedWorldId = worldId;
		known.clear();
		JsonObject section = WorldData.section(SECTION);
		if (section == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				continue;
			}
			List<Portal> list = new ArrayList<>();
			for (JsonElement element : entry.getValue().getAsJsonArray()) {
				if (element.isJsonArray() && element.getAsJsonArray().size() == 6) {
					JsonArray a = element.getAsJsonArray();
					try {
						list.add(new Portal(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
								a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt()));
					} catch (RuntimeException ignored) {
						// skip a damaged entry
					}
				}
			}
			known.put(entry.getKey(), list);
		}
	}

	private void store() {
		JsonObject section = WorldData.section(SECTION);
		if (section == null) {
			return;
		}
		for (String key : new ArrayList<>(section.keySet())) {
			section.remove(key);
		}
		for (Map.Entry<String, List<Portal>> entry : known.entrySet()) {
			JsonArray array = new JsonArray();
			for (Portal portal : entry.getValue()) {
				JsonArray a = new JsonArray();
				a.add(portal.minX());
				a.add(portal.minY());
				a.add(portal.minZ());
				a.add(portal.maxX());
				a.add(portal.maxY());
				a.add(portal.maxZ());
				array.add(a);
			}
			section.add(entry.getKey(), array);
		}
		WorldData.markDirty();
	}

	// ---- HUD -----------------------------------------------------------------------------------

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Info shown = info;
		if (shown == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		List<Text> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		BlockPos target = shown.target();
		lines.add(Text.translatable(key + (shown.inNether() ? "target_overworld" : "target_nether"),
				target.getX(), target.getY(), target.getZ()));
		colors.add(HudLayout.WHITE);

		if (showLink.get()) {
			Portal link = shown.link();
			if (link != null) {
				BlockPos at = link.nearestTo(target);
				lines.add(Text.translatable(key + "link_known", at.getX(), at.getY(), at.getZ(), shown.linkDistance()));
				colors.add(0xFF55FF55);
				if (showReturn.get()) {
					Portal back = shown.returnPortal();
					if (shown.returnsHere()) {
						lines.add(Text.translatable(key + "return_ok"));
						colors.add(0xFF55FF55);
					} else if (back != null) {
						lines.add(Text.translatable(key + "return_other", back.minX(), back.minY(), back.minZ()));
						colors.add(0xFFFF5555);
					}
				}
			} else {
				lines.add(Text.translatable(key + "link_new", shown.searchRadius()));
				colors.add(0xFFFFFF55);
				lines.add(Text.translatable(key + "note"));
				colors.add(0xFFAAAAAA);
			}
		}

		int width = 0;
		for (Text line : lines) {
			width = Math.max(width, client.textRenderer.getWidth(line));
		}
		// Centred a little below the crosshair, so the crosshair itself stays visible.
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 + 30;
		context.fill(x - 3, y - 3, x + width + 3, y + lines.size() * HudLayout.LINE_HEIGHT + 1, 0x90000000);
		for (int i = 0; i < lines.size(); i++) {
			context.drawTextWithShadow(client.textRenderer, lines.get(i), x, y + i * HudLayout.LINE_HEIGHT, colors.get(i));
		}
	}
}

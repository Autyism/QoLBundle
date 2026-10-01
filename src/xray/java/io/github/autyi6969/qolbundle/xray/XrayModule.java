package io.github.autyi6969.qolbundle.xray;

import io.github.autyi6969.qolbundle.hud.HudAnchor;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import io.github.autyi6969.qolbundle.module.setting.StringSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>X-ray as an overlay: ores (and any extra blocks you name) near you get a coloured outline
 * that is visible through walls. Nothing about how the world itself is drawn is changed, so it
 * works with any renderer; it only looks at chunks the client already has.
 */
public class XrayModule extends Module {
	/** How many chunk sections (16x16x16) are examined per tick; a full sweep takes a second or two. */
	private static final int SECTIONS_PER_TICK = 48;
	private static final int REFRESH_VISIBLE_TICKS = 5;

	private final IntSetting radius = add(new IntSetting("radius", 32, 8, 64));
	private final IntSetting maxBoxes = add(new IntSetting("max_boxes", 400, 50, 2000));
	private final BoolSetting diamond = add(new BoolSetting("diamond", true));
	private final BoolSetting ancientDebris = add(new BoolSetting("ancient_debris", true));
	private final BoolSetting emerald = add(new BoolSetting("emerald", true));
	private final BoolSetting gold = add(new BoolSetting("gold", true));
	private final BoolSetting iron = add(new BoolSetting("iron", true));
	private final BoolSetting redstone = add(new BoolSetting("redstone", true));
	private final BoolSetting lapis = add(new BoolSetting("lapis", true));
	private final BoolSetting coal = add(new BoolSetting("coal", false));
	private final BoolSetting copper = add(new BoolSetting("copper", false));
	private final BoolSetting quartz = add(new BoolSetting("quartz", false));
	private final StringSetting extraBlocks = add(new StringSetting("extra_blocks", "", 200));
	private final BoolSetting showCount = add(new BoolSetting("show_count", true));
	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));

	/** One highlighted block. */
	public record Found(BlockPos pos, int color) {
	}

	private final Map<Block, Integer> targets = new HashMap<>();
	private String targetsSignature = "";
	/** Results per chunk section (key: ChunkSectionPos as long). */
	private final Map<Long, List<Found>> bySection = new HashMap<>();
	/** The nearest blocks, the ones actually drawn. */
	private List<Found> visible = List.of();
	private int sweepIndex;
	private int ticks;

	public XrayModule() {
		super("xray", ModuleCategory.GREY, false);
	}

	/** The blocks currently outlined (for the self-test). */
	public List<Found> getVisible() {
		return visible;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		bySection.clear();
		visible = List.of();
		sweepIndex = 0;
	}

	// ---- which blocks to look for ----------------------------------------------------------------

	private void refreshTargets() {
		String signature = diamond.get() + "|" + ancientDebris.get() + "|" + emerald.get() + "|" + gold.get() + "|" + iron.get()
				+ "|" + redstone.get() + "|" + lapis.get() + "|" + coal.get() + "|" + copper.get() + "|" + quartz.get()
				+ "|" + extraBlocks.get();
		if (signature.equals(targetsSignature)) {
			return;
		}
		targetsSignature = signature;
		targets.clear();
		bySection.clear();
		addGroup(diamond, 0x55FFFF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
		addGroup(ancientDebris, 0xC8783C, Blocks.ANCIENT_DEBRIS);
		addGroup(emerald, 0x55FF55, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
		addGroup(gold, 0xFFD700, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
		addGroup(iron, 0xE0B090, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
		addGroup(redstone, 0xFF3030, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
		addGroup(lapis, 0x3060FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
		addGroup(coal, 0xAAAAAA, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
		addGroup(copper, 0xE07840, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
		addGroup(quartz, 0xFFFFFF, Blocks.NETHER_QUARTZ_ORE);
		for (String name : extraBlocks.get().split(",")) {
			Identifier id = Identifier.tryParse(name.trim());
			if (id != null && !name.isBlank() && Registries.BLOCK.containsId(id)) {
				Block block = Registries.BLOCK.get(id);
				if (block != Blocks.AIR) {
					targets.put(block, 0xFF55FF);
				}
			}
		}
	}

	private void addGroup(BoolSetting enabled, int color, Block... blocks) {
		if (enabled.get()) {
			for (Block block : blocks) {
				targets.put(block, color);
			}
		}
	}

	// ---- scanning ---------------------------------------------------------------------------------

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			bySection.clear();
			visible = List.of();
			return;
		}
		refreshTargets();
		ClientWorld world = client.world;
		BlockPos center = client.player.getBlockPos();
		int reach = radius.get();
		int chunkReach = (reach >> 4) + 1;
		int side = chunkReach * 2 + 1;
		int minSection = ChunkSectionPos.getSectionCoord(Math.max(world.getBottomY(), center.getY() - reach));
		int maxSection = ChunkSectionPos.getSectionCoord(Math.min(world.getTopYInclusive(), center.getY() + reach));
		int layers = maxSection - minSection + 1;
		int total = side * side * layers;

		// A rolling sweep over the cube of sections around the player, a few per tick.
		for (int i = 0; i < SECTIONS_PER_TICK && total > 0; i++) {
			int index = sweepIndex++ % total;
			int chunkX = (center.getX() >> 4) - chunkReach + index % side;
			int chunkZ = (center.getZ() >> 4) - chunkReach + index / side % side;
			int sectionY = minSection + index / (side * side);
			scanSection(world, chunkX, sectionY, chunkZ);
		}

		if (ticks++ % REFRESH_VISIBLE_TICKS == 0) {
			refreshVisible(center, reach);
		}
	}

	private void scanSection(ClientWorld world, int chunkX, int sectionY, int chunkZ) {
		long key = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ);
		WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
		if (chunk == null) {
			bySection.remove(key);
			return;
		}
		int index = chunk.sectionCoordToIndex(sectionY);
		ChunkSection[] sections = chunk.getSectionArray();
		if (index < 0 || index >= sections.length) {
			return;
		}
		ChunkSection section = sections[index];
		// hasAny checks the section's short list of block kinds first: sections without ore cost next to nothing.
		if (section == null || section.isEmpty() || !section.hasAny(state -> targets.containsKey(state.getBlock()))) {
			bySection.remove(key);
			return;
		}
		List<Found> found = new ArrayList<>();
		int baseX = chunkX << 4;
		int baseY = sectionY << 4;
		int baseZ = chunkZ << 4;
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					Integer color = targets.get(section.getBlockState(x, y, z).getBlock());
					if (color != null) {
						found.add(new Found(new BlockPos(baseX + x, baseY + y, baseZ + z), color));
					}
				}
			}
		}
		bySection.put(key, found);
	}

	/** Picks the nearest blocks within range; those are what gets drawn until the next refresh. */
	private void refreshVisible(BlockPos center, int reach) {
		long reachSquared = (long) reach * reach;
		List<Found> near = new ArrayList<>();
		bySection.entrySet().removeIf(entry -> {
			ChunkSectionPos section = ChunkSectionPos.from(entry.getKey());
			// Forget sections the player has walked far away from.
			return Math.abs(section.getMinX() + 8 - center.getX()) > reach + 48
					|| Math.abs(section.getMinZ() + 8 - center.getZ()) > reach + 48;
		});
		for (List<Found> list : bySection.values()) {
			for (Found found : list) {
				if (found.pos.getSquaredDistance(center) <= reachSquared) {
					near.add(found);
				}
			}
		}
		int limit = maxBoxes.get();
		if (near.size() > limit) {
			near.sort(Comparator.comparingDouble(found -> found.pos.getSquaredDistance(center)));
			near = new ArrayList<>(near.subList(0, limit));
		}
		visible = near;
	}

	// ---- drawing ----------------------------------------------------------------------------------

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		for (Found found : visible) {
			GizmoDrawing.box(new Box(found.pos), DrawStyle.stroked(0xFF000000 | found.color, 2.0F)).ignoreOcclusion();
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!showCount.get()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		layout.drawLines(context, client.textRenderer, position.get(),
				List.of(Text.translatable(getTranslationKey() + ".hud", visible.size()).withColor(0xFFAA00)));
	}
}

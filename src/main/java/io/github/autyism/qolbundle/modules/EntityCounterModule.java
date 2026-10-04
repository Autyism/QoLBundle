package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Counts the entities the client currently has loaded, by kind, and warns when dropped items pile
 * up (the usual cause of lag at farms). Also says where the biggest pile of dropped items is.
 *
 * <p>Only counts what the server has already sent to this client. The location hint is for dropped
 * items only, never for mobs or players.
 */
public class EntityCounterModule extends Module {
	public enum Category {
		ITEM,
		XP_ORB,
		HOSTILE,
		ANIMAL,
		OTHER_MOB,
		PLAYER,
		PROJECTILE,
		VEHICLE,
		OTHER
	}

	private static final int RECOUNT_TICKS = 10;
	/** Items within the same 8x8x8 block cell count as one pile. */
	private static final int PILE_CELL_SHIFT = 3;
	private static final int MIN_PILE = 5;
	private static final String[] COMPASS = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_RIGHT));
	private final BoolSetting showCategories = add(new BoolSetting("show_categories", true));
	private final IntSetting topTypes = add(new IntSetting("top_types", 3, 0, 10));
	private final IntSetting itemWarning = add(new IntSetting("item_warning", 100, 10, 1000));
	private final BoolSetting showItemPile = add(new BoolSetting("show_item_pile", true));

	private final int[] counts = new int[Category.values().length];
	private final List<Map.Entry<EntityType<?>, Integer>> top = new ArrayList<>();
	private int total;
	@Nullable
	private BlockPos pilePos;
	private int pileCount;
	private int ticks;

	public EntityCounterModule() {
		super("entity_counter", ModuleCategory.TECHNICAL, false);
	}

	public int getCount(Category category) {
		return counts[category.ordinal()];
	}

	public int getTotal() {
		return total;
	}

	@Nullable
	public BlockPos getPilePos() {
		return pilePos;
	}

	public IntSetting itemWarningSetting() {
		return itemWarning;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		ticks = 0;
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		if (ticks++ % RECOUNT_TICKS != 0) {
			return;
		}
		java.util.Arrays.fill(counts, 0);
		total = 0;
		Map<EntityType<?>, Integer> byType = new HashMap<>();
		Map<Long, int[]> itemCells = new HashMap<>();
		for (Entity entity : client.world.getEntities()) {
			if (entity == client.player) {
				continue;
			}
			total++;
			Category category = categorize(entity);
			counts[category.ordinal()]++;
			byType.merge(entity.getType(), 1, Integer::sum);
			if (category == Category.ITEM) {
				BlockPos at = entity.getBlockPos();
				long cell = BlockPos.asLong(at.getX() >> PILE_CELL_SHIFT, at.getY() >> PILE_CELL_SHIFT, at.getZ() >> PILE_CELL_SHIFT);
				// count, sum x, sum y, sum z: to place the hint in the middle of the pile
				int[] sums = itemCells.computeIfAbsent(cell, key -> new int[4]);
				sums[0]++;
				sums[1] += at.getX();
				sums[2] += at.getY();
				sums[3] += at.getZ();
			}
		}

		top.clear();
		top.addAll(byType.entrySet());
		top.sort((a, b) -> b.getValue() - a.getValue());

		pilePos = null;
		pileCount = 0;
		for (int[] sums : itemCells.values()) {
			if (sums[0] > pileCount) {
				pileCount = sums[0];
				pilePos = new BlockPos(Math.floorDiv(sums[1], sums[0]), Math.floorDiv(sums[2], sums[0]), Math.floorDiv(sums[3], sums[0]));
			}
		}
		if (pileCount < MIN_PILE) {
			pilePos = null;
		}
	}

	private static Category categorize(Entity entity) {
		if (entity instanceof ItemEntity) {
			return Category.ITEM;
		}
		if (entity instanceof ExperienceOrbEntity) {
			return Category.XP_ORB;
		}
		if (entity instanceof PlayerEntity) {
			return Category.PLAYER;
		}
		if (entity instanceof ProjectileEntity) {
			return Category.PROJECTILE;
		}
		if (entity instanceof VehicleEntity) {
			return Category.VEHICLE;
		}
		SpawnGroup group = entity.getType().getSpawnGroup();
		if (group == SpawnGroup.MONSTER) {
			return Category.HOSTILE;
		}
		if (group != SpawnGroup.MISC) {
			return Category.ANIMAL;
		}
		// Villagers, golems and the like are "misc" for the game but still mobs.
		return entity instanceof MobEntity ? Category.OTHER_MOB : Category.OTHER;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable(key + "total", total));

		int items = getCount(Category.ITEM);
		int warnAt = itemWarning.get();
		if (showCategories.get()) {
			for (Category category : Category.values()) {
				int count = getCount(category);
				if (count == 0) {
					continue;
				}
				MutableText line = Text.translatable(key + "category." + category.name().toLowerCase(Locale.ROOT), count);
				if (category == Category.ITEM) {
					if (items >= warnAt) {
						line = line.append(Text.translatable(key + "lag_warning")).withColor(0xFF5555);
					} else if (items * 2 >= warnAt) {
						line = line.withColor(0xFFFF55);
					}
				}
				lines.add(line);
			}
		} else if (items >= warnAt) {
			lines.add(Text.translatable(key + "category.item", items).append(Text.translatable(key + "lag_warning")).withColor(0xFF5555));
		}

		int shown = Math.min(topTypes.get(), top.size());
		for (int i = 0; i < shown; i++) {
			Map.Entry<EntityType<?>, Integer> entry = top.get(i);
			lines.add(Text.translatable(key + "top", entry.getKey().getName(), entry.getValue()).withColor(0xAAAAAA));
		}

		if (showItemPile.get() && pilePos != null) {
			double dx = pilePos.getX() + 0.5 - client.player.getX();
			double dz = pilePos.getZ() + 0.5 - client.player.getZ();
			int distance = (int) Math.round(Math.sqrt(client.player.getBlockPos().getSquaredDistance(pilePos)));
			// atan2 with +X = east and +Z = south gives 0 = east, 90 = south.
			int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0), 8);
			lines.add(Text.translatable(key + "pile", pileCount, pilePos.getX(), pilePos.getY(), pilePos.getZ(), distance,
					Text.translatable("qolbundle.compass." + COMPASS[sector])).withColor(0xFFAA00));
		}
		layout.drawLines(context, client.textRenderer, position.get(), lines);
	}
}

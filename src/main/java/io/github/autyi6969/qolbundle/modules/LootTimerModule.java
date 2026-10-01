package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loot timer: a countdown over dropped items until they disappear (items lie for 5 minutes).
 *
 * <p>The client is not told how old an item is, so the clock starts when the item first shows up
 * for this client. For loot that drops while you are there (somebody dies near you) that is exact;
 * for items that were already lying around when you arrived it is an upper limit, hence the "≤".
 * Only items in plain sight get a label.
 */
public class LootTimerModule extends Module {
	private static final int LIFETIME_TICKS = 6000;
	private static final int MAX_LABELS = 12;

	/** One label: the items lying in the same block. */
	public record Pile(Vec3d pos, int stacks, int secondsLeft) {
	}

	private final IntSetting radius = add(new IntSetting("radius", 16, 4, 32));
	private final IntSetting minStacks = add(new IntSetting("min_stacks", 1, 1, 10));

	/** Entity id to the tick it was first seen. */
	private final Map<Integer, Long> firstSeen = new HashMap<>();
	private final List<Pile> piles = new ArrayList<>();
	private long tick;

	public LootTimerModule() {
		super("loot_timer", ModuleCategory.PVP, true);
	}

	public List<Pile> getPiles() {
		return piles;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		firstSeen.clear();
		piles.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			firstSeen.clear();
			piles.clear();
			return;
		}
		if (tick % 5 != 0) {
			return;
		}
		// Every item the client has gets its clock started, labelled or not, so that walking closer
		// later does not restart it.
		Set<Integer> present = new HashSet<>();
		Map<BlockPos, List<ItemEntity>> byBlock = new HashMap<>();
		double range = radius.get();
		for (ItemEntity item : client.world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(64.0), entity -> true)) {
			present.add(item.getId());
			firstSeen.putIfAbsent(item.getId(), tick);
			if (item.squaredDistanceTo(player) <= range * range && player.canSee(item)) {
				byBlock.computeIfAbsent(item.getBlockPos(), pos -> new ArrayList<>()).add(item);
			}
		}
		firstSeen.keySet().retainAll(present);

		piles.clear();
		for (List<ItemEntity> items : byBlock.values()) {
			if (items.size() < minStacks.get()) {
				continue;
			}
			long oldest = tick;
			Vec3d middle = Vec3d.ZERO;
			for (ItemEntity item : items) {
				oldest = Math.min(oldest, firstSeen.get(item.getId()));
				middle = middle.add(item.getEntityPos());
			}
			int left = (int) Math.max(0, (LIFETIME_TICKS - (tick - oldest)) / 20);
			piles.add(new Pile(middle.multiply(1.0 / items.size()), items.size(), left));
		}
		piles.sort(Comparator.comparingDouble(pile -> pile.pos.squaredDistanceTo(player.getEntityPos())));
		while (piles.size() > MAX_LABELS) {
			piles.remove(piles.size() - 1);
		}
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		for (Pile pile : piles) {
			int seconds = pile.secondsLeft;
			String time = "≤" + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
			String text = pile.stacks > 1 ? "×" + pile.stacks + "  " + time : time;
			int color = seconds <= 20 ? 0xFFFF5555 : seconds <= 60 ? 0xFFFFFF55 : 0xFFFFFFFF;
			GizmoDrawing.text(text, pile.pos.add(0, 0.9, 0), TextGizmo.Style.centered(color).scaled(0.7F));
		}
	}
}

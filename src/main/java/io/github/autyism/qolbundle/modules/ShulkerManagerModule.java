package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.mixin.HandledScreenAccessor;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.util.ItemNames;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes shulker boxes readable at a glance and searchable:
 * every box in an inventory screen gets a coloured tag for the kind of things in it and a small
 * picture of what it holds most of, and when you search (the item search box, or the chest memory
 * screen) the boxes that contain the item are pointed out.
 */
public class ShulkerManagerModule extends Module {
	/** What a box mostly holds. The colour is the tag drawn on the box. */
	public enum BoxCategory {
		BUILDING(0xFFB0B0B0),
		MATERIALS(0xFF55FFFF),
		REDSTONE(0xFFFF4040),
		FOOD(0xFFFFAA00),
		GEAR(0xFFB060FF),
		MAGIC(0xFFFF7ADB),
		MIXED(0xFFFFFFFF);

		public final int color;

		BoxCategory(int color) {
			this.color = color;
		}
	}

	/** One box in the player's inventory that holds something matching a search. */
	public record InventoryHit(int slot, ItemStack box, Component name, int count) {
	}

	private static final String[] REDSTONE_WORDS = {"redstone", "repeater", "comparator", "piston", "hopper", "observer",
		"dropper", "dispenser", "rail", "lever", "button", "pressure_plate", "tripwire", "daylight", "target", "sculk_sensor", "tnt", "note_block"};
	private static final String[] MATERIAL_WORDS = {"ingot", "nugget", "raw_", "diamond", "emerald", "coal", "lapis", "quartz",
		"netherite_scrap", "amethyst_shard", "_ore", "ancient_debris", "copper"};
	private static final String[] MAGIC_WORDS = {"potion", "enchanted_book", "experience_bottle", "ender_pearl", "ender_eye", "blaze", "ghast_tear", "nether_wart"};

	private final BoolSetting showTag = add(new BoolSetting("show_tag", true));
	private final BoolSetting showIcon = add(new BoolSetting("show_icon", true));
	private final BoolSetting searchInside = add(new BoolSetting("search_inside", true));

	public ShulkerManagerModule() {
		super("shulker_manager", ModuleCategory.TOOLS, true);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (isEnabled() && screen instanceof AbstractContainerScreen<?> handled) {
				ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> decorate(context, handled));
			}
		});
	}

	private static boolean isBox(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.SHULKER_BOXES);
	}

	/** The stacks inside a shulker box item (empty list for an empty box or anything else). */
	public static List<ItemStack> contentsOf(ItemStack box) {
		List<ItemStack> contents = new ArrayList<>();
		if (isBox(box)) {
			ItemContainerContents container = box.get(DataComponents.CONTAINER);
			if (container != null) {
				container.nonEmptyItems().forEach(contents::add);
			}
		}
		return contents;
	}

	private static BoxCategory kindOf(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String word : REDSTONE_WORDS) {
			if (id.contains(word)) {
				return BoxCategory.REDSTONE;
			}
		}
		for (String word : MAGIC_WORDS) {
			if (id.contains(word)) {
				return BoxCategory.MAGIC;
			}
		}
		if (stack.has(DataComponents.FOOD)) {
			return BoxCategory.FOOD;
		}
		if (stack.isDamageableItem() || stack.is(Items.ARROW) || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.FIREWORK_ROCKET)) {
			return BoxCategory.GEAR;
		}
		for (String word : MATERIAL_WORDS) {
			if (id.contains(word)) {
				return BoxCategory.MATERIALS;
			}
		}
		return stack.getItem() instanceof BlockItem ? BoxCategory.BUILDING : BoxCategory.MIXED;
	}

	/** The kind most of the box's stacks belong to; MIXED when no kind fills at least 60 %. Null for an empty box. */
	@Nullable
	public static BoxCategory categoryOf(ItemStack box) {
		List<ItemStack> contents = contentsOf(box);
		if (contents.isEmpty()) {
			return null;
		}
		Map<BoxCategory, Integer> votes = new EnumMap<>(BoxCategory.class);
		for (ItemStack stack : contents) {
			votes.merge(kindOf(stack), 1, Integer::sum);
		}
		BoxCategory best = BoxCategory.MIXED;
		int bestVotes = 0;
		for (Map.Entry<BoxCategory, Integer> entry : votes.entrySet()) {
			if (entry.getValue() > bestVotes) {
				best = entry.getKey();
				bestVotes = entry.getValue();
			}
		}
		return bestVotes * 10 >= contents.size() * 6 ? best : BoxCategory.MIXED;
	}

	/** The item the box holds most of (by count). */
	public static ItemStack mainItemOf(ItemStack box) {
		Map<String, Integer> counts = new HashMap<>();
		Map<String, ItemStack> samples = new HashMap<>();
		for (ItemStack stack : contentsOf(box)) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			counts.merge(id, stack.getCount(), Integer::sum);
			samples.putIfAbsent(id, stack);
		}
		String best = null;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (best == null || entry.getValue() > counts.get(best)) {
				best = entry.getKey();
			}
		}
		return best == null ? ItemStack.EMPTY : samples.get(best);
	}

	/** The first stack inside the box that matches the search, or EMPTY. */
	public static ItemStack firstMatchInside(ItemStack box, String query) {
		if (query.isBlank()) {
			return ItemStack.EMPTY;
		}
		for (ItemStack stack : contentsOf(box)) {
			if (ItemNames.matches(stack, query, true)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Boxes carried by the player that hold something matching the search. */
	public List<InventoryHit> searchInventory(@Nullable LocalPlayer player, String query) {
		List<InventoryHit> hits = new ArrayList<>();
		if (player == null || query.isBlank() || !isEnabled()) {
			return hits;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack box = player.getInventory().getItem(slot);
			int count = 0;
			Component name = null;
			for (ItemStack stack : contentsOf(box)) {
				if (ItemNames.matches(stack, query, true)) {
					count += stack.getCount();
					if (name == null) {
						name = stack.getHoverName();
					}
				}
			}
			if (count > 0) {
				hits.add(new InventoryHit(slot, box, name, count));
			}
		}
		return hits;
	}

	private void decorate(GuiGraphics context, AbstractContainerScreen<?> screen) {
		if (!isEnabled()) {
			return;
		}
		String query = "";
		if (searchInside.get() && ModuleRegistry.get("item_search") instanceof ItemSearchModule search && search.isEnabled()) {
			query = search.getQuery();
		}
		HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
		int left = accessor.qolbundle$getX();
		int top = accessor.qolbundle$getY();
		//? if <1.21.6
		/*io.github.autyism.qolbundle.hud.GuiDepth.push(context, io.github.autyism.qolbundle.hud.GuiDepth.OVER_SLOTS);*/
		for (Slot slot : screen.getMenu().slots) {
			ItemStack box = slot.getItem();
			if (!slot.isActive() || !isBox(box)) {
				continue;
			}
			BoxCategory category = categoryOf(box);
			if (category == null) {
				continue;
			}
			int x = left + slot.x;
			int y = top + slot.y;
			ItemStack match = firstMatchInside(box, query);
			if (!match.isEmpty()) {
				// The searched item is inside this box.
				context.fill(x, y, x + 16, y + 16, 0x30FFAA00);
				context.renderOutline(x - 1, y - 1, 18, 18, 0xFFFFAA00);
			}
			if (showTag.get()) {
				context.fill(x, y, x + 5, y + 5, 0xFF000000);
				context.fill(x, y, x + 4, y + 4, category.color);
			}
			if (showIcon.get()) {
				ItemStack shown = match.isEmpty() ? mainItemOf(box) : match;
				if (!shown.isEmpty()) {
					context.pose().pushMatrix();
					context.pose().translate(x + 8, y);
					context.pose().scale(0.5F, 0.5F);
					//? if >=1.21.6 {
					context.renderItem(shown, 0, 0);
					//?} else
					/*io.github.autyism.qolbundle.hud.GuiDepth.item(context, shown, 0, 0);*/
					context.pose().popMatrix();
				}
			}
		}
		//? if <1.21.6
		/*io.github.autyism.qolbundle.hud.GuiDepth.pop(context);*/
	}
}

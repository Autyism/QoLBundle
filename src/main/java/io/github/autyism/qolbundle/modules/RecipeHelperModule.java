package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.gui.ChestMemoryScreen;
import io.github.autyism.qolbundle.mixin.CyclingItemAccessor;
import io.github.autyism.qolbundle.mixin.GhostRecipeAccessor;
import io.github.autyism.qolbundle.mixin.HandledScreenAccessor;
import io.github.autyism.qolbundle.mixin.RecipeBookScreenAccessor;
import io.github.autyism.qolbundle.mixin.RecipeBookWidgetAccessor;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recipe book helper. When you pick a recipe you cannot craft, the game shows it as a faint
 * preview in the crafting grid. This module adds what the preview does not say: which of the
 * ingredients you are actually missing (and how many), and where to get them: a shulker box you
 * carry, or a chest that Chest Memory has seen them in. Clicking a missing ingredient points the
 * way to that chest.
 *
 * <p>Reads the preview the game already shows and the player's own inventory; nothing is sent.
 */
public class RecipeHelperModule extends Module {
	private static final int CHIP_HEIGHT = 20;
	private static final long REFRESH_MS = 200;
	private static final int MAX_SOURCE_LINES = 4;
	private static final int GREEN = 0xFF55FF55;
	private static final int AQUA = 0xFF55FFFF;
	private static final int RED = 0xFFFF5555;

	/** A place where a missing ingredient lies. chest is null for a shulker box the player carries (in slot). */
	public record Source(ChestMemoryModule.@Nullable Chest chest, int slot, int count) {
		public boolean isCarried() {
			return chest == null;
		}
	}

	/** One ingredient the player does not have enough of. */
	public static final class Missing {
		/** Every item the recipe accepts in this place (several for "any planks"). */
		public final List<ItemStack> accepts;
		public int count;
		public List<Source> sources = List.of();
		private int x;
		private int y;
		private int width;

		Missing(List<ItemStack> accepts) {
			this.accepts = accepts;
		}
	}

	private final BoolSetting slotFrames = add(new BoolSetting("slot_frames", true));

	private final List<Missing> missing = new ArrayList<>();
	private final Map<Slot, Boolean> slotOk = new LinkedHashMap<>();
	private long analysedMs;
	private int analysedSize = -1;
	/** Entry whose details stay open without the mouse over it; the self-test uses it in place of a real hover. */
	private int pinnedDetails = -1;

	public RecipeHelperModule() {
		super("recipe_helper", ModuleCategory.TOOLS, true);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (isEnabled() && screen instanceof AbstractRecipeBookScreen<?> book) {
				reset();
				ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> render(context, book, mouseX, mouseY));
				//? if >=1.21.9 {
				ScreenMouseEvents.allowMouseClick(screen).register((current, click) ->
						!(click.button() == 0 && activate(chipAt(click.x(), click.y()))));
				//?} else {
				/*ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) ->
						!(button == 0 && activate(chipAt(mouseX, mouseY))));
				*///?}
				ScreenEvents.remove(screen).register(current -> reset());
			}
		});
	}

	/** What is missing for the recipe currently previewed (empty when there is no preview). */
	public List<Missing> getMissing() {
		return missing;
	}

	public void showDetailsFor(int index) {
		pinnedDetails = index;
	}

	private void reset() {
		missing.clear();
		slotOk.clear();
		analysedSize = -1;
		pinnedDetails = -1;
	}

	private static Map<Slot, ?> previewOf(AbstractRecipeBookScreen<?> screen) {
		Object widget = ((RecipeBookScreenAccessor) screen).qolbundle$getRecipeBook();
		Object ghost = ((RecipeBookWidgetAccessor) widget).qolbundle$getGhostRecipe();
		return ((GhostRecipeAccessor) ghost).qolbundle$getItems();
	}

	// ---- working out what is missing -----------------------------------------------------------

	private void analyse(Minecraft client, Map<Slot, ?> preview) {
		missing.clear();
		slotOk.clear();
		LocalPlayer player = client.player;

		// What the player has to craft with: hotbar + backpack, plus anything already lying in the grid.
		Map<Item, Integer> have = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < 36; slot++) {
			count(have, inventory.getItem(slot));
		}
		List<Map.Entry<Slot, List<ItemStack>>> needs = new ArrayList<>();
		for (Map.Entry<Slot, ?> entry : preview.entrySet()) {
			CyclingItemAccessor item = (CyclingItemAccessor) entry.getValue();
			if (!item.qolbundle$isResultSlot() && !item.qolbundle$getItems().isEmpty()) {
				count(have, entry.getKey().getItem());
				needs.add(Map.entry(entry.getKey(), item.qolbundle$getItems()));
			}
		}
		// Places that accept only one item choose first, so "any planks" does not take the oak a
		// "must be oak" place needed.
		needs.sort(Comparator.comparingInt(need -> need.getValue().size()));

		Map<String, Missing> groups = new LinkedHashMap<>();
		for (Map.Entry<Slot, List<ItemStack>> need : needs) {
			Item best = null;
			for (ItemStack option : need.getValue()) {
				int owned = have.getOrDefault(option.getItem(), 0);
				if (owned > 0 && (best == null || owned > have.get(best))) {
					best = option.getItem();
				}
			}
			if (best != null) {
				have.merge(best, -1, Integer::sum);
				slotOk.put(need.getKey(), true);
				continue;
			}
			slotOk.put(need.getKey(), false);
			StringBuilder key = new StringBuilder();
			for (ItemStack option : need.getValue()) {
				key.append(BuiltInRegistries.ITEM.getKey(option.getItem())).append(';');
			}
			groups.computeIfAbsent(key.toString(), unused -> new Missing(need.getValue())).count++;
		}

		String dimension = client.level.dimension().identifier().toString();
		ChestMemoryModule chests = ModuleRegistry.get("chest_memory") instanceof ChestMemoryModule module && module.isEnabled() ? module : null;
		for (Missing entry : groups.values()) {
			Set<Item> wanted = new HashSet<>();
			Set<String> wantedIds = new HashSet<>();
			for (ItemStack option : entry.accepts) {
				wanted.add(option.getItem());
				wantedIds.add(BuiltInRegistries.ITEM.getKey(option.getItem()).toString());
			}
			List<Source> sources = new ArrayList<>();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				int inside = countInBox(inventory.getItem(slot), wanted);
				if (inside > 0) {
					sources.add(new Source(null, slot, inside));
				}
			}
			if (chests != null) {
				for (ChestMemoryModule.Hit hit : chests.findItems(wantedIds, player, dimension)) {
					sources.add(new Source(hit.chest(), -1, hit.count()));
				}
			}
			entry.sources = sources;
			missing.add(entry);
		}
	}

	private static void count(Map<Item, Integer> into, ItemStack stack) {
		if (!stack.isEmpty()) {
			into.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
	}

	private static int countInBox(ItemStack box, Set<Item> wanted) {
		if (box.isEmpty() || !box.is(ItemTags.SHULKER_BOXES)) {
			return 0;
		}
		ItemContainerContents inside = box.get(DataComponents.CONTAINER);
		int total = 0;
		if (inside != null) {
			for (ItemStack stack : inside.nonEmptyItems()) {
				if (wanted.contains(stack.getItem())) {
					total += stack.getCount();
				}
			}
		}
		return total;
	}

	// ---- drawing -------------------------------------------------------------------------------

	private void render(GuiGraphics context, AbstractRecipeBookScreen<?> screen, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || client.player == null || client.level == null) {
			return;
		}
		Map<Slot, ?> preview = previewOf(screen);
		if (preview.isEmpty()) {
			if (analysedSize != 0) {
				missing.clear();
				slotOk.clear();
				analysedSize = 0;
			}
			return;
		}
		long now = Util.getMillis();
		if (preview.size() != analysedSize || now - analysedMs > REFRESH_MS) {
			analyse(client, preview);
			analysedSize = preview.size();
			analysedMs = now;
		}

		int left = ((HandledScreenAccessor) screen).qolbundle$getX();
		int top = ((HandledScreenAccessor) screen).qolbundle$getY();
		if (slotFrames.get()) {
			// Green: you have this one. Red: this is what is missing.
			for (Map.Entry<Slot, Boolean> entry : slotOk.entrySet()) {
				Slot slot = entry.getKey();
				context.renderOutline(left + slot.x - 1, top + slot.y - 1, 18, 18, entry.getValue() ? GREEN : RED);
			}
		}
		if (missing.isEmpty()) {
			return;
		}

		// One small box per missing ingredient, in a row above the window.
		Font textRenderer = client.font;
		Component label = Component.translatable(getTranslationKey() + ".label");
		int labelWidth = textRenderer.width(label);
		int y = Math.max(2, top - CHIP_HEIGHT - 3);
		int x = left;
		context.fill(x, y, x + labelWidth + 6, y + CHIP_HEIGHT, 0xC0000000);
		context.drawString(textRenderer, label, x + 3, y + 6, RED);
		x += labelWidth + 8;
		int icon = (int) (now / 1000L);
		for (Missing entry : missing) {
			String amount = "×" + entry.count;
			entry.width = 2 + 16 + 2 + textRenderer.width(amount) + 3;
			entry.x = x;
			entry.y = y;
			context.fill(x, y, x + entry.width, y + CHIP_HEIGHT, 0xC0000000);
			context.renderOutline(x, y, entry.width, CHIP_HEIGHT, colorOf(entry));
			context.renderItem(entry.accepts.get(icon % entry.accepts.size()), x + 2, y + 2);
			context.drawString(textRenderer, amount, x + 20, y + 6, 0xFFFFFFFF);
			x += entry.width + 2;
		}

		int details = pinnedDetails >= 0 && pinnedDetails < missing.size() ? pinnedDetails : missing.indexOf(chipAt(mouseX, mouseY));
		if (details >= 0) {
			Missing entry = missing.get(details);
			boolean pinned = details == pinnedDetails;
			drawDetails(context, textRenderer, detailsOf(client, entry, icon),
					pinned ? entry.x : mouseX + 10, pinned ? entry.y + CHIP_HEIGHT + 2 : mouseY + 6);
		}
	}

	/** Green: a chest you know has it. Aqua: a shulker box you carry has it. Red: nowhere known. */
	private static int colorOf(Missing entry) {
		if (entry.sources.isEmpty()) {
			return RED;
		}
		return entry.sources.get(0).isCarried() ? AQUA : GREEN;
	}

	@Nullable
	private Missing chipAt(double mouseX, double mouseY) {
		for (Missing entry : missing) {
			if (mouseX >= entry.x && mouseX < entry.x + entry.width && mouseY >= entry.y && mouseY < entry.y + CHIP_HEIGHT) {
				return entry;
			}
		}
		return null;
	}

	private List<Component> detailsOf(Minecraft client, Missing entry, int icon) {
		String key = getTranslationKey() + ".";
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(key + "missing", entry.count, entry.accepts.get(icon % entry.accepts.size()).getHoverName()));
		if (entry.accepts.size() > 1) {
			lines.add(Component.translatable(key + "alternatives", entry.accepts.size()).withStyle(ChatFormatting.GRAY));
		}
		if (entry.sources.isEmpty()) {
			lines.add(Component.translatable(key + "nowhere").withStyle(ChatFormatting.RED));
			if (!(ModuleRegistry.get("chest_memory") instanceof ChestMemoryModule chests && chests.isEnabled())) {
				lines.add(Component.translatable(key + "no_memory").withStyle(ChatFormatting.GRAY));
			}
			return lines;
		}
		String here = client.level.dimension().identifier().toString();
		for (int i = 0; i < entry.sources.size() && i < MAX_SOURCE_LINES; i++) {
			lines.add(describe(client, entry.sources.get(i), here));
		}
		if (entry.sources.size() > MAX_SOURCE_LINES) {
			lines.add(Component.translatable(key + "more", entry.sources.size() - MAX_SOURCE_LINES).withStyle(ChatFormatting.GRAY));
		}
		if (pointable(client, entry) != null) {
			lines.add(Component.translatable(key + "click").withStyle(ChatFormatting.YELLOW));
		}
		return lines;
	}

	private Component describe(Minecraft client, Source source, String here) {
		String key = getTranslationKey() + ".";
		if (source.isCarried()) {
			return Component.translatable(key + "in_carried_box", ChestMemoryScreen.slotPlace(source.slot), source.count).withStyle(ChatFormatting.AQUA);
		}
		ChestMemoryModule.Chest chest = source.chest;
		MutableComponent text;
		if (chest.isEnderChest()) {
			text = Component.translatable(key + "in_ender_chest", source.count);
		} else {
			//~ if >=26.1 '.getName()' -> '.components().getOrDefault(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.network.chat.CommonComponents.EMPTY)'
			Component block = ChestMemoryModule.itemOf(chest.blockId).getName();
			String position = chest.pos.getX() + ", " + chest.pos.getY() + ", " + chest.pos.getZ();
			if (chest.dimension.equals(here)) {
				int distance = (int) Math.round(Math.sqrt(chest.pos.distSqr(client.player.blockPosition())));
				text = Component.translatable(key + "in_chest", block, position, distance, source.count);
			} else {
				text = Component.translatable(key + "in_chest_other", block, ChestMemoryScreen.dimensionName(chest.dimension), position, source.count);
			}
		}
		if (ModuleRegistry.get("chest_memory") instanceof ChestMemoryModule chests && chests.isStale(chest)) {
			return text.append(Component.literal(" ")).append(Component.translatable("qolbundle.module.chest_memory.screen.stale")).withStyle(ChatFormatting.GOLD);
		}
		return text.withStyle(ChatFormatting.GREEN);
	}

	private static void drawDetails(GuiGraphics context, Font textRenderer, List<Component> lines, int x, int y) {
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, textRenderer.width(line));
		}
		int height = lines.size() * 10 + 5;
		x = Math.max(2, Math.min(x, context.guiWidth() - width - 8));
		y = Math.max(2, Math.min(y, context.guiHeight() - height - 2));
		context.fill(x, y, x + width + 6, y + height, 0xF0100010);
		context.renderOutline(x, y, width + 6, height, 0xFF5000A0);
		for (int i = 0; i < lines.size(); i++) {
			context.drawString(textRenderer, lines.get(i), x + 3, y + 3 + i * 10, 0xFFFFFFFF);
		}
	}

	// ---- pointing the way ----------------------------------------------------------------------

	/** The nearest known chest in this dimension that holds the ingredient, if any. */
	private static ChestMemoryModule.@Nullable Chest pointable(Minecraft client, Missing entry) {
		String here = client.level.dimension().identifier().toString();
		for (Source source : entry.sources) {
			if (!source.isCarried() && !source.chest.isEnderChest() && source.chest.dimension.equals(here)) {
				return source.chest;
			}
		}
		return null;
	}

	/** Closes the crafting screen and lets Chest Memory point at the chest holding this ingredient. */
	public boolean activate(@Nullable Missing entry) {
		Minecraft client = Minecraft.getInstance();
		if (entry == null || client.player == null || client.level == null) {
			return false;
		}
		ChestMemoryModule.Chest chest = pointable(client, entry);
		if (chest != null && ModuleRegistry.get("chest_memory") instanceof ChestMemoryModule chests) {
			chests.setTarget(chest);
			client.player.closeContainer();
		}
		return true; // a click on the box never falls through to whatever lies under it
	}
}

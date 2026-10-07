package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.modules.ChestMemoryModule;
import io.github.autyism.qolbundle.modules.ShulkerManagerModule;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Search the remembered containers for an item. Each result is one container; clicking it makes
 * the HUD point to that container.
 */
public class ChestMemoryScreen extends Screen {
	private static final String KEY = "qolbundle.module.chest_memory.screen.";
	private static final int LIST_TOP = 62;
	private static final int FOOTER_HEIGHT = 34;
	private static final int ROW_HEIGHT = 20;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;

	@Nullable
	private final Screen parent;
	private final ChestMemoryModule module;
	private List<ChestMemoryModule.Hit> hits = new ArrayList<>();
	/** Shulker boxes the player carries that hold the item; listed before the chests. */
	private List<ShulkerManagerModule.InventoryHit> carried = new ArrayList<>();
	private EditBox queryField;
	private String query = "";
	private int scroll;

	public ChestMemoryScreen(@Nullable Screen parent, ChestMemoryModule module) {
		super(Component.translatable(KEY + "title"));
		this.parent = parent;
		this.module = module;
	}

	public List<ChestMemoryModule.Hit> getHits() {
		return hits;
	}

	public List<ShulkerManagerModule.InventoryHit> getCarried() {
		return carried;
	}

	public void setQuery(String text) {
		if (queryField != null) {
			queryField.setValue(text);
		} else {
			query = text;
		}
		runSearch(text);
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(this.width - 40, 300);
		queryField = new EditBox(this.font, (this.width - fieldWidth) / 2, 28, fieldWidth, 18, Component.translatable(KEY + "hint"));
		queryField.setMaxLength(60);
		queryField.setHint(Component.translatable(KEY + "hint"));
		queryField.setValue(query);
		queryField.setResponder(this::runSearch);
		addRenderableWidget(queryField);
		setInitialFocus(queryField);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 - 75, this.height - 27, 150, 20).build());
		runSearch(query);
	}

	private void runSearch(String text) {
		query = text;
		scroll = 0;
		String dimension = this.minecraft != null && this.minecraft.level != null
				? this.minecraft.level.dimension().identifier().toString() : "";
		hits = text.isBlank() ? new ArrayList<>() : module.search(text, this.minecraft == null ? null : this.minecraft.player, dimension);
		carried = new ArrayList<>();
		if (this.minecraft != null && ModuleRegistry.get("shulker_manager") instanceof ShulkerManagerModule shulkers) {
			carried = shulkers.searchInventory(this.minecraft.player, text);
		}
	}

	private int rowCount() {
		return carried.size() + hits.size();
	}

	private int visibleRows() {
		return Math.max(1, (this.height - FOOTER_HEIGHT - LIST_TOP) / ROW_HEIGHT);
	}

	private int rowLeft() {
		return Math.max(10, this.width / 2 - 200);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int max = Math.max(0, rowCount() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
		return true;
	}

	//? if >=1.21.9 {
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		int index = rowAt(click.x(), click.y());
	//?} else {
	/*@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int index = rowAt(mouseX, mouseY);
	*///?}
		if (index >= 0) {
			choose(index);
			return true;
		}
		return false;
	}

	private int rowAt(double mouseX, double mouseY) {
		if (mouseY < LIST_TOP || mouseX < rowLeft() || mouseX > this.width - rowLeft()) {
			return -1;
		}
		int index = scroll + (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
		return index < rowCount() && index < scroll + visibleRows() ? index : -1;
	}

	/** Makes the HUD point at the container of a result and closes the screen. */
	public void choose(int index) {
		if (index >= carried.size()) {
			module.setTarget(hits.get(index - carried.size()).chest());
		}
		// A box you are carrying needs no pointer: its place in the backpack is written in the row.
		this.minecraft.setScreen(null);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	*///?} else {
	public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
	//?}
		context.drawCenteredString(this.font, this.title, this.width / 2, 11, WHITE);
		Component status;
		if (module.getChests().isEmpty() && carried.isEmpty()) {
			status = Component.translatable(KEY + "nothing_remembered");
		} else if (query.isBlank()) {
			status = Component.translatable(KEY + "remembered", module.getChests().size());
		} else {
			status = carried.isEmpty()
					? Component.translatable(KEY + "found", hits.size())
					: Component.translatable(KEY + "found_carried", carried.size(), hits.size());
		}
		context.drawCenteredString(this.font, status, this.width / 2, LIST_TOP - 12, GRAY);

		int left = rowLeft();
		int hovered = rowAt(mouseX, mouseY);
		for (int i = scroll; i < rowCount() && i < scroll + visibleRows(); i++) {
			int y = LIST_TOP + (i - scroll) * ROW_HEIGHT;
			if (i == hovered) {
				context.fill(left - 2, y, this.width - left + 2, y + ROW_HEIGHT - 1, 0x40FFFFFF);
			}
			if (i < carried.size()) {
				ShulkerManagerModule.InventoryHit box = carried.get(i);
				context.renderItem(box.box(), left, y + 1);
				context.drawString(this.font, Component.translatable(KEY + "what", box.name(), box.count()), left + 20, y + 1, WHITE);
				context.drawString(this.font, carriedWhere(box), left + 20, y + 10, 0xFF55FF55);
				continue;
			}
			ChestMemoryModule.Hit hit = hits.get(i - carried.size());
			ChestMemoryModule.Chest chest = hit.chest();
			context.renderItem(new ItemStack(ChestMemoryModule.itemOf(chest.blockId)), left, y + 1);
			MutableComponent what = Component.translatable(KEY + (hit.inShulkerBox() ? "what_in_box" : "what"), hit.name(), hit.count());
			context.drawString(this.font, what, left + 20, y + 1, WHITE);
			context.drawString(this.font, where(chest), left + 20, y + 10, module.isStale(chest) ? 0xFFFFAA00 : GRAY);
		}
	}

	/** "in the shulker box in your hotbar, slot 5". */
	private static Component carriedWhere(ShulkerManagerModule.InventoryHit box) {
		return Component.translatable(KEY + "carried", box.box().getHoverName(), slotPlace(box.slot()));
	}

	/** An inventory slot in words: "hotbar slot 5", "backpack row 2, column 3", "off hand". */
	public static Component slotPlace(int slot) {
		if (slot < 9) {
			return Component.translatable(KEY + "slot.hotbar", slot + 1);
		} else if (slot < 36) {
			return Component.translatable(KEY + "slot.backpack", (slot - 9) / 9 + 1, (slot - 9) % 9 + 1);
		}
		return Component.translatable(KEY + "slot.offhand");
	}

	/** "at 12, 64, -30 (35 blocks, 2 hours ago)" plus a note when the record is old. */
	private Component where(ChestMemoryModule.Chest chest) {
		MutableComponent text;
		if (chest.isEnderChest()) {
			text = Component.translatable(KEY + "ender_chest");
		} else {
			String here = this.minecraft.level == null ? "" : this.minecraft.level.dimension().identifier().toString();
			String position = chest.pos.getX() + ", " + chest.pos.getY() + ", " + chest.pos.getZ();
			if (chest.dimension.equals(here) && this.minecraft.player != null) {
				int distance = (int) Math.round(Math.sqrt(chest.pos.distSqr(this.minecraft.player.blockPosition())));
				text = Component.translatable(KEY + "where", position, distance);
			} else {
				text = Component.translatable(KEY + "where_other", dimensionName(chest.dimension), position);
			}
		}
		text.append(Component.literal("  ")).append(age(chest.seenMs));
		if (module.isStale(chest)) {
			text.append(Component.literal("  ")).append(Component.translatable(KEY + "stale"));
		}
		return text;
	}

	private static Component age(long seenMs) {
		long minutes = Math.max(0, (System.currentTimeMillis() - seenMs) / 60000L);
		if (minutes < 1) {
			return Component.translatable(KEY + "age.now");
		}
		if (minutes < 60) {
			return Component.translatable(KEY + "age.minutes", minutes);
		}
		if (minutes < 60 * 24) {
			return Component.translatable(KEY + "age.hours", minutes / 60);
		}
		return Component.translatable(KEY + "age.days", minutes / (60 * 24));
	}

	public static Component dimensionName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String key = "qolbundle.dimension." + path.toLowerCase(Locale.ROOT);
		return I18n.exists(key) ? Component.translatable(key) : Component.literal(id);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

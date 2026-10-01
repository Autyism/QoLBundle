package io.github.autyi6969.qolbundle.gui;

import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.ChestMemoryModule;
import io.github.autyi6969.qolbundle.modules.ShulkerManagerModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
	private TextFieldWidget queryField;
	private String query = "";
	private int scroll;

	public ChestMemoryScreen(@Nullable Screen parent, ChestMemoryModule module) {
		super(Text.translatable(KEY + "title"));
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
			queryField.setText(text);
		} else {
			query = text;
		}
		runSearch(text);
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(this.width - 40, 300);
		queryField = new TextFieldWidget(this.textRenderer, (this.width - fieldWidth) / 2, 28, fieldWidth, 18, Text.translatable(KEY + "hint"));
		queryField.setMaxLength(60);
		queryField.setPlaceholder(Text.translatable(KEY + "hint"));
		queryField.setText(query);
		queryField.setChangedListener(this::runSearch);
		addDrawableChild(queryField);
		setInitialFocus(queryField);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 - 75, this.height - 27, 150, 20).build());
		runSearch(query);
	}

	private void runSearch(String text) {
		query = text;
		scroll = 0;
		String dimension = this.client != null && this.client.world != null
				? this.client.world.getRegistryKey().getValue().toString() : "";
		hits = text.isBlank() ? new ArrayList<>() : module.search(text, this.client == null ? null : this.client.player, dimension);
		carried = new ArrayList<>();
		if (this.client != null && ModuleRegistry.get("shulker_manager") instanceof ShulkerManagerModule shulkers) {
			carried = shulkers.searchInventory(this.client.player, text);
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

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		int index = rowAt(click.x(), click.y());
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
		this.client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 11, WHITE);
		Text status;
		if (module.getChests().isEmpty() && carried.isEmpty()) {
			status = Text.translatable(KEY + "nothing_remembered");
		} else if (query.isBlank()) {
			status = Text.translatable(KEY + "remembered", module.getChests().size());
		} else {
			status = carried.isEmpty()
					? Text.translatable(KEY + "found", hits.size())
					: Text.translatable(KEY + "found_carried", carried.size(), hits.size());
		}
		context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, LIST_TOP - 12, GRAY);

		int left = rowLeft();
		int hovered = rowAt(mouseX, mouseY);
		for (int i = scroll; i < rowCount() && i < scroll + visibleRows(); i++) {
			int y = LIST_TOP + (i - scroll) * ROW_HEIGHT;
			if (i == hovered) {
				context.fill(left - 2, y, this.width - left + 2, y + ROW_HEIGHT - 1, 0x40FFFFFF);
			}
			if (i < carried.size()) {
				ShulkerManagerModule.InventoryHit box = carried.get(i);
				context.drawItem(box.box(), left, y + 1);
				context.drawTextWithShadow(this.textRenderer, Text.translatable(KEY + "what", box.name(), box.count()), left + 20, y + 1, WHITE);
				context.drawTextWithShadow(this.textRenderer, carriedWhere(box), left + 20, y + 10, 0xFF55FF55);
				continue;
			}
			ChestMemoryModule.Hit hit = hits.get(i - carried.size());
			ChestMemoryModule.Chest chest = hit.chest();
			context.drawItem(new ItemStack(ChestMemoryModule.itemOf(chest.blockId)), left, y + 1);
			MutableText what = Text.translatable(KEY + (hit.inShulkerBox() ? "what_in_box" : "what"), hit.name(), hit.count());
			context.drawTextWithShadow(this.textRenderer, what, left + 20, y + 1, WHITE);
			context.drawTextWithShadow(this.textRenderer, where(chest), left + 20, y + 10, module.isStale(chest) ? 0xFFFFAA00 : GRAY);
		}
	}

	/** "in the shulker box in your hotbar, slot 5". */
	private static Text carriedWhere(ShulkerManagerModule.InventoryHit box) {
		int slot = box.slot();
		Text place;
		if (slot < 9) {
			place = Text.translatable(KEY + "slot.hotbar", slot + 1);
		} else if (slot < 36) {
			place = Text.translatable(KEY + "slot.backpack", (slot - 9) / 9 + 1, (slot - 9) % 9 + 1);
		} else {
			place = Text.translatable(KEY + "slot.offhand");
		}
		return Text.translatable(KEY + "carried", box.box().getName(), place);
	}

	/** "at 12, 64, -30 (35 blocks, 2 hours ago)" plus a note when the record is old. */
	private Text where(ChestMemoryModule.Chest chest) {
		MutableText text;
		if (chest.isEnderChest()) {
			text = Text.translatable(KEY + "ender_chest");
		} else {
			String here = this.client.world == null ? "" : this.client.world.getRegistryKey().getValue().toString();
			String position = chest.pos.getX() + ", " + chest.pos.getY() + ", " + chest.pos.getZ();
			if (chest.dimension.equals(here) && this.client.player != null) {
				int distance = (int) Math.round(Math.sqrt(chest.pos.getSquaredDistance(this.client.player.getBlockPos())));
				text = Text.translatable(KEY + "where", position, distance);
			} else {
				text = Text.translatable(KEY + "where_other", dimensionName(chest.dimension), position);
			}
		}
		text.append(Text.literal("  ")).append(age(chest.seenMs));
		if (module.isStale(chest)) {
			text.append(Text.literal("  ")).append(Text.translatable(KEY + "stale"));
		}
		return text;
	}

	private static Text age(long seenMs) {
		long minutes = Math.max(0, (System.currentTimeMillis() - seenMs) / 60000L);
		if (minutes < 1) {
			return Text.translatable(KEY + "age.now");
		}
		if (minutes < 60) {
			return Text.translatable(KEY + "age.minutes", minutes);
		}
		if (minutes < 60 * 24) {
			return Text.translatable(KEY + "age.hours", minutes / 60);
		}
		return Text.translatable(KEY + "age.days", minutes / (60 * 24));
	}

	private static Text dimensionName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String key = "qolbundle.dimension." + path.toLowerCase(Locale.ROOT);
		return I18n.hasTranslation(key) ? Text.translatable(key) : Text.literal(id);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}

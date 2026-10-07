package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.modules.HotbarLayoutsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** One row per saved hotbar layout: its name, the nine items, "save current hotbar" and "apply". */
public class HotbarLayoutScreen extends Screen {
	private static final int ROW_HEIGHT = 26;
	private static final int TOP = 36;
	private static final int NAME_WIDTH = 80;
	private static final int ICONS_WIDTH = 9 * 18;
	private static final int BUTTON_WIDTH = 60;
	private static final int ROW_WIDTH = NAME_WIDTH + 6 + ICONS_WIDTH + 6 + BUTTON_WIDTH * 2 + 4;

	@Nullable
	private final Screen parent;
	private final HotbarLayoutsModule module;

	public HotbarLayoutScreen(@Nullable Screen parent, HotbarLayoutsModule module) {
		super(Component.translatable("qolbundle.module.hotbar_layouts.screen.title"));
		this.parent = parent;
		this.module = module;
	}

	private int left() {
		return (this.width - ROW_WIDTH) / 2;
	}

	@Override
	protected void init() {
		int left = left();
		for (int i = 0; i < HotbarLayoutsModule.LAYOUT_COUNT; i++) {
			int index = i;
			int y = TOP + i * ROW_HEIGHT;
			EditBox name = new EditBox(this.font, left, y + 3, NAME_WIDTH, 18,
					Component.translatable("qolbundle.module.hotbar_layouts.screen.name", i + 1));
			name.setMaxLength(24);
			name.setHint(Component.translatable("qolbundle.module.hotbar_layouts.screen.name", i + 1));
			name.setValue(module.getLayoutName(i));
			name.setResponder(text -> module.setLayoutName(index, text));
			addRenderableWidget(name);

			int buttonsLeft = left + NAME_WIDTH + 6 + ICONS_WIDTH + 6;
			addRenderableWidget(Button.builder(Component.translatable("qolbundle.module.hotbar_layouts.screen.save"), button -> {
				module.saveCurrent(this.minecraft, index);
				ConfigManager.save();
			}).bounds(buttonsLeft, y + 2, BUTTON_WIDTH, 20).build());
			Button apply = addRenderableWidget(Button.builder(Component.translatable("qolbundle.module.hotbar_layouts.screen.apply"), button -> {
				module.apply(this.minecraft, index);
				onClose();
			}).bounds(buttonsLeft + BUTTON_WIDTH + 4, y + 2, BUTTON_WIDTH, 20).build());
			apply.active = this.minecraft.player != null;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 - 75, this.height - 27, 150, 20).build());
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	*///?} else {
	public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
	//?}
		context.drawCenteredString(this.font, this.title, this.width / 2, 11, 0xFFFFFFFF);
		context.drawCenteredString(this.font, Component.translatable("qolbundle.module.hotbar_layouts.screen.hint"),
				this.width / 2, 23, 0xFFA0A0A0);
		int iconsLeft = left() + NAME_WIDTH + 6;
		for (int i = 0; i < HotbarLayoutsModule.LAYOUT_COUNT; i++) {
			int y = TOP + i * ROW_HEIGHT + 3;
			String[] wanted = module.getLayout(i);
			for (int slot = 0; slot < wanted.length; slot++) {
				int x = iconsLeft + slot * 18;
				context.fill(x, y, x + 17, y + 17, 0x60000000);
				Identifier id = wanted[slot].isEmpty() ? null : Identifier.tryParse(wanted[slot]);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					Item item = BuiltInRegistries.ITEM.getValue(id);
					if (item != Items.AIR) {
						context.renderItem(new ItemStack(item), x, y);
					}
				}
			}
		}
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

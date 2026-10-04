package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.modules.HotbarLayoutsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
		super(Text.translatable("qolbundle.module.hotbar_layouts.screen.title"));
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
			TextFieldWidget name = new TextFieldWidget(this.textRenderer, left, y + 3, NAME_WIDTH, 18,
					Text.translatable("qolbundle.module.hotbar_layouts.screen.name", i + 1));
			name.setMaxLength(24);
			name.setPlaceholder(Text.translatable("qolbundle.module.hotbar_layouts.screen.name", i + 1));
			name.setText(module.getLayoutName(i));
			name.setChangedListener(text -> module.setLayoutName(index, text));
			addDrawableChild(name);

			int buttonsLeft = left + NAME_WIDTH + 6 + ICONS_WIDTH + 6;
			addDrawableChild(ButtonWidget.builder(Text.translatable("qolbundle.module.hotbar_layouts.screen.save"), button -> {
				module.saveCurrent(this.client, index);
				ConfigManager.save();
			}).dimensions(buttonsLeft, y + 2, BUTTON_WIDTH, 20).build());
			ButtonWidget apply = addDrawableChild(ButtonWidget.builder(Text.translatable("qolbundle.module.hotbar_layouts.screen.apply"), button -> {
				module.apply(this.client, index);
				close();
			}).dimensions(buttonsLeft + BUTTON_WIDTH + 4, y + 2, BUTTON_WIDTH, 20).build());
			apply.active = this.client.player != null;
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 - 75, this.height - 27, 150, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 11, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("qolbundle.module.hotbar_layouts.screen.hint"),
				this.width / 2, 23, 0xFFA0A0A0);
		int iconsLeft = left() + NAME_WIDTH + 6;
		for (int i = 0; i < HotbarLayoutsModule.LAYOUT_COUNT; i++) {
			int y = TOP + i * ROW_HEIGHT + 3;
			String[] wanted = module.getLayout(i);
			for (int slot = 0; slot < wanted.length; slot++) {
				int x = iconsLeft + slot * 18;
				context.fill(x, y, x + 17, y + 17, 0x60000000);
				Identifier id = wanted[slot].isEmpty() ? null : Identifier.tryParse(wanted[slot]);
				if (id != null && Registries.ITEM.containsId(id)) {
					Item item = Registries.ITEM.get(id);
					if (item != Items.AIR) {
						context.drawItem(new ItemStack(item), x, y);
					}
				}
			}
		}
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}

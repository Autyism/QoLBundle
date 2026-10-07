package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.mixin.HandledScreenAccessor;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.util.ItemNames;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;


/**
 * A search box in every inventory and container screen. Type part of an item's name in English,
 * in Chinese, or the pinyin initials of its Chinese name ("zs" for 钻石), and the matching items
 * are framed while the rest is dimmed. Works whatever language the game is set to.
 */
public class ItemSearchModule extends Module {
	private final BoolSetting dimOthers = add(new BoolSetting("dim_others", true));
	private final BoolSetting pinyin = add(new BoolSetting("pinyin", true));

	private String query = "";
	@Nullable
	private EditBox field;

	public ItemSearchModule() {
		super("item_search", ModuleCategory.TOOLS, true);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (isEnabled() && screen instanceof AbstractContainerScreen<?> handled && !(screen instanceof CreativeModeInventoryScreen)) {
				attach(client, handled, height);
			}
		});
	}

	/** What is typed in the search box right now ("" when there is none). */
	public String getQuery() {
		return field == null ? "" : query;
	}

	public void setQuery(String text) {
		query = text;
		if (field != null) {
			field.setValue(text);
		}
	}

	/** How many slots of the screen hold a matching item (for the self-test). */
	public int countMatches(AbstractContainerScreen<?> screen) {
		int count = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.getItem().isEmpty() && matches(slot.getItem(), query)) {
				count++;
			}
		}
		return count;
	}

	private void attach(Minecraft client, AbstractContainerScreen<?> screen, int height) {
		query = "";
		EditBox box = new EditBox(client.font, 4, height - 20, 110, 16,
				Component.translatable(getTranslationKey() + ".placeholder"));
		box.setHint(Component.translatable(getTranslationKey() + ".placeholder"));
		box.setMaxLength(40);
		box.setResponder(text -> query = text);
		field = box;
		Screens.getButtons(screen).add(box);

		// While typing, keys must not reach the inventory ("e" would close it, numbers would move items).
		//? if >=1.21.9 {
		ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> {
			if (!box.isFocused() || input.isEscape()) {
				return true;
			}
			if (input.isConfirmation()) {
				box.setFocused(false);
				current.setFocused(null);
			} else {
				box.keyPressed(input);
			}
			return false;
		});
		//?} else {
		/*ScreenKeyboardEvents.allowKeyPress(screen).register((current, key, scancode, modifiers) -> {
			if (!box.isFocused() || key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
				return true;
			}
			if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
				box.setFocused(false);
				current.setFocused(null);
			} else {
				box.keyPressed(key, scancode, modifiers);
			}
			return false;
		});
		*///?}
		ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> highlight(context, screen));
		ScreenEvents.remove(screen).register(current -> {
			if (field == box) {
				field = null;
			}
		});
	}

	private void highlight(GuiGraphics context, AbstractContainerScreen<?> screen) {
		if (query.isBlank() || !isEnabled()) {
			return;
		}
		HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
		int left = accessor.qolbundle$getX();
		int top = accessor.qolbundle$getY();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.isActive()) {
				continue;
			}
			int x = left + slot.x;
			int y = top + slot.y;
			if (!slot.getItem().isEmpty() && matches(slot.getItem(), query)) {
				context.renderOutline(x - 1, y - 1, 18, 18, 0xFF55FF55);
			} else if (ModuleRegistry.get("shulker_manager") instanceof ShulkerManagerModule shulkers && shulkers.isEnabled()
					&& !ShulkerManagerModule.firstMatchInside(slot.getItem(), query).isEmpty()) {
				// A shulker box with a match inside: the Shulker Box Manager frames it, so leave it bright.
				continue;
			} else if (dimOthers.get()) {
				context.fill(x, y, x + 16, y + 16, 0xB0101010);
			}
		}
	}

	/** True when every word of the query is found in one of the item's names. */
	public boolean matches(ItemStack stack, String text) {
		return ItemNames.matches(stack, text, pinyin.get());
	}
}

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
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
	private TextFieldWidget field;

	public ItemSearchModule() {
		super("item_search", ModuleCategory.TOOLS, true);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (isEnabled() && screen instanceof HandledScreen<?> handled && !(screen instanceof CreativeInventoryScreen)) {
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
			field.setText(text);
		}
	}

	/** How many slots of the screen hold a matching item (for the self-test). */
	public int countMatches(HandledScreen<?> screen) {
		int count = 0;
		for (Slot slot : screen.getScreenHandler().slots) {
			if (!slot.getStack().isEmpty() && matches(slot.getStack(), query)) {
				count++;
			}
		}
		return count;
	}

	private void attach(MinecraftClient client, HandledScreen<?> screen, int height) {
		query = "";
		TextFieldWidget box = new TextFieldWidget(client.textRenderer, 4, height - 20, 110, 16,
				Text.translatable(getTranslationKey() + ".placeholder"));
		box.setPlaceholder(Text.translatable(getTranslationKey() + ".placeholder"));
		box.setMaxLength(40);
		box.setChangedListener(text -> query = text);
		field = box;
		Screens.getButtons(screen).add(box);

		// While typing, keys must not reach the inventory ("e" would close it, numbers would move items).
		ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> {
			if (!box.isFocused() || input.isEscape()) {
				return true;
			}
			if (input.isEnter()) {
				box.setFocused(false);
				current.setFocused(null);
			} else {
				box.keyPressed(input);
			}
			return false;
		});
		ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> highlight(context, screen));
		ScreenEvents.remove(screen).register(current -> {
			if (field == box) {
				field = null;
			}
		});
	}

	private void highlight(DrawContext context, HandledScreen<?> screen) {
		if (query.isBlank() || !isEnabled()) {
			return;
		}
		HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
		int left = accessor.qolbundle$getX();
		int top = accessor.qolbundle$getY();
		for (Slot slot : screen.getScreenHandler().slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			int x = left + slot.x;
			int y = top + slot.y;
			if (!slot.getStack().isEmpty() && matches(slot.getStack(), query)) {
				context.drawStrokedRectangle(x - 1, y - 1, 18, 18, 0xFF55FF55);
			} else if (ModuleRegistry.get("shulker_manager") instanceof ShulkerManagerModule shulkers && shulkers.isEnabled()
					&& !ShulkerManagerModule.firstMatchInside(slot.getStack(), query).isEmpty()) {
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

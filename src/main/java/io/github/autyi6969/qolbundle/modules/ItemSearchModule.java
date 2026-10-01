package io.github.autyi6969.qolbundle.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.mixin.HandledScreenAccessor;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A search box in every inventory and container screen. Type part of an item's name in English,
 * in Chinese, or the pinyin initials of its Chinese name ("zs" for 钻石), and the matching items
 * are framed while the rest is dimmed. Works whatever language the game is set to.
 */
public class ItemSearchModule extends Module {
	private static final int CJK_FIRST = 0x4E00;
	private static final int CJK_LAST = 0x9FFF;

	private final BoolSetting dimOthers = add(new BoolSetting("dim_others", true));
	private final BoolSetting pinyin = add(new BoolSetting("pinyin", true));

	/** Item translation key -> name, for the two languages that are always searched. */
	@Nullable
	private Map<String, String> english;
	@Nullable
	private Map<String, String> chinese;
	/** Pinyin initial of every character from U+4E00 on; loaded on first use. */
	@Nullable
	private String initials;

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
			} else if (dimOthers.get()) {
				context.fill(x, y, x + 16, y + 16, 0xB0101010);
			}
		}
	}

	/** True when every word of the query is found in one of the item's names. */
	public boolean matches(ItemStack stack, String text) {
		String wanted = text.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return false;
		}
		loadTables();
		String key = stack.getItem().getTranslationKey();
		String shown = stack.getName().getString();
		String zh = chinese.getOrDefault(key, "");
		StringBuilder haystack = new StringBuilder();
		haystack.append(shown).append('\n').append(english.getOrDefault(key, "")).append('\n').append(zh);
		if (pinyin.get()) {
			haystack.append('\n').append(initialsOf(zh)).append('\n').append(initialsOf(shown));
		}
		String all = haystack.toString().toLowerCase(Locale.ROOT);
		for (String word : wanted.split("\\s+")) {
			if (!all.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** "钻石剑" -> "zsj". Letters and digits are kept, anything else is dropped. */
	private String initialsOf(String name) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c >= CJK_FIRST && c <= CJK_LAST) {
				char letter = initials.charAt(c - CJK_FIRST);
				if (letter != '?') {
					out.append(letter);
				}
			} else if (Character.isLetterOrDigit(c)) {
				out.append(Character.toLowerCase(c));
			}
		}
		return out.toString();
	}

	private void loadTables() {
		if (english != null) {
			return;
		}
		ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
		english = readLanguage(resources, "en_us");
		chinese = readLanguage(resources, "zh_cn");
		String table = "";
		try (InputStream in = ItemSearchModule.class.getResourceAsStream("/assets/qolbundle/pinyin_initials.txt")) {
			if (in != null) {
				table = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
			}
		} catch (Exception e) {
			QoLBundleClient.LOGGER.error("Could not read the pinyin table", e);
		}
		// Too short or missing: fall back to "no initials known" rather than failing on lookups.
		initials = table.length() == CJK_LAST - CJK_FIRST + 1 ? table : "?".repeat(CJK_LAST - CJK_FIRST + 1);
	}

	/** Item and block names of one language, from every mod's language file. */
	private static Map<String, String> readLanguage(ResourceManager resources, String code) {
		Map<String, String> names = new HashMap<>();
		for (String namespace : resources.getAllNamespaces()) {
			Optional<Resource> resource = resources.getResource(Identifier.of(namespace, "lang/" + code + ".json"));
			if (resource.isEmpty()) {
				continue;
			}
			try (Reader reader = resource.get().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					String key = entry.getKey();
					if ((key.startsWith("item.") || key.startsWith("block.")) && entry.getValue().isJsonPrimitive()) {
						names.put(key, entry.getValue().getAsString());
					}
				}
			} catch (Exception e) {
				QoLBundleClient.LOGGER.warn("Could not read {} names of {}", code, namespace);
			}
		}
		return names;
	}
}

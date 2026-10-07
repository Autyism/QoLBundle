package io.github.autyism.qolbundle.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.qolbundle.QoLBundleClient;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

/**
 * Finds items by name in several ways at once: the name as shown, the English name, the Chinese
 * name, and the pinyin initials of the Chinese name ("zs" for 钻石). Works whatever language the
 * game is set to. Shared by every module that has an item search box.
 */
public final class ItemNames {
	private static final int CJK_FIRST = 0x4E00;
	private static final int CJK_LAST = 0x9FFF;

	/** Item translation key -> name, for the two languages that are always searched. */
	private static Map<String, String> english;
	private static Map<String, String> chinese;
	/** Pinyin initial of every character from U+4E00 on; loaded on first use. */
	private static String initials;

	private ItemNames() {
	}

	/** Everything an item can be found by, lower case, one name per line. */
	public static String searchText(String translationKey, String shownName, boolean withPinyin) {
		load();
		String zh = chinese.getOrDefault(translationKey, "");
		StringBuilder text = new StringBuilder();
		text.append(shownName).append('\n').append(english.getOrDefault(translationKey, "")).append('\n').append(zh);
		if (withPinyin) {
			text.append('\n').append(initialsOf(zh)).append('\n').append(initialsOf(shownName));
		}
		return text.toString().toLowerCase(Locale.ROOT);
	}

	public static String searchText(ItemStack stack, boolean withPinyin) {
		return searchText(stack.getItem().getDescriptionId(), stack.getHoverName().getString(), withPinyin);
	}

	/** True when every word of the query occurs in the search text. An empty query matches nothing. */
	public static boolean matches(String searchText, String query) {
		String wanted = query.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return false;
		}
		for (String word : wanted.split("\\s+")) {
			if (!searchText.contains(word)) {
				return false;
			}
		}
		return true;
	}

	public static boolean matches(ItemStack stack, String query, boolean withPinyin) {
		return !query.isBlank() && matches(searchText(stack, withPinyin), query);
	}

	/** "钻石剑" -> "zsj". Letters and digits are kept, anything else is dropped. */
	public static String initialsOf(String name) {
		load();
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

	private static void load() {
		if (english != null) {
			return;
		}
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		english = readLanguage(resources, "en_us");
		chinese = readLanguage(resources, "zh_cn");
		String table = "";
		try (InputStream in = ItemNames.class.getResourceAsStream("/assets/qolbundle/pinyin_initials.txt")) {
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
		for (String namespace : resources.getNamespaces()) {
			Optional<Resource> resource = resources.getResource(Identifier.fromNamespaceAndPath(namespace, "lang/" + code + ".json"));
			if (resource.isEmpty()) {
				continue;
			}
			try (Reader reader = resource.get().openAsReader()) {
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

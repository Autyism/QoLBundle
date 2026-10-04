package io.github.autyism.qolbundle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes config/qolbundle.json.
 *
 * <pre>
 * { "version": 1,
 *   "modules": { "durability_alert": { "enabled": true, "threshold": 5 } } }
 * </pre>
 * Unknown keys are ignored and missing keys keep their defaults, so old config files keep working
 * when modules or settings are added.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String ENABLED_KEY = "enabled";
	private static final int VERSION = 1;

	/** While true, save() does nothing. Used by the self-test so it never touches the user's config. */
	private static boolean savingSuppressed;

	private ConfigManager() {
	}

	public static Path getPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(QoLBundleClient.MOD_ID + ".json");
	}

	public static void setSavingSuppressed(boolean suppressed) {
		savingSuppressed = suppressed;
	}

	public static void load() {
		Path path = getPath();
		if (!Files.exists(path)) {
			save();
			return;
		}
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			JsonElement root = JsonParser.parseString(text);
			if (!root.isJsonObject()) {
				throw new IllegalStateException("root is not an object");
			}
			apply(root.getAsJsonObject());
		} catch (Exception e) {
			QoLBundleClient.LOGGER.error("Could not read {}, keeping defaults. The broken file is kept as {}.broken",
					path, path.getFileName(), e);
			try {
				Files.copy(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// best effort only
			}
		}
	}

	/** Takes over every module found in the given config tree; returns how many modules it covered. */
	private static int apply(JsonObject root) {
		JsonElement modules = root.get("modules");
		if (modules == null || !modules.isJsonObject()) {
			return 0;
		}
		int count = 0;
		for (Module module : ModuleRegistry.all()) {
			JsonElement entry = modules.getAsJsonObject().get(module.getId());
			if (entry != null && entry.isJsonObject()) {
				readModule(module, entry.getAsJsonObject());
				count++;
			}
		}
		return count;
	}

	private static void readModule(Module module, JsonObject json) {
		for (Setting<?> setting : module.getSettings()) {
			JsonElement value = json.get(setting.getId());
			if (value != null && !value.isJsonNull()) {
				try {
					setting.fromJson(value);
				} catch (RuntimeException e) {
					QoLBundleClient.LOGGER.warn("Bad value for {}.{} in config, keeping default", module.getId(), setting.getId());
				}
			}
		}
		// Settings first, then the switch, so a module that reacts to being enabled sees its real settings.
		JsonElement enabled = json.get(ENABLED_KEY);
		if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
			module.setEnabled(enabled.getAsBoolean());
		}
	}

	// ---- share codes ---------------------------------------------------------------------------

	private static final String CODE_PREFIX = "QOL1:";

	/** Everything in the config as one line of text that can be pasted into a chat. */
	public static String exportCode() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(new Gson().toJson(toJson()).getBytes(StandardCharsets.UTF_8));
			}
			return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e); // writing to memory cannot fail
		}
	}

	/** The settings inside a share code, or null when the text is not a (readable) share code. */
	public static JsonObject parseCode(String code) {
		String text = code == null ? "" : code.trim();
		if (!text.startsWith(CODE_PREFIX)) {
			return null;
		}
		try {
			byte[] packed = Base64.getUrlDecoder().decode(text.substring(CODE_PREFIX.length()));
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
				// A share code is a few kilobytes; refuse anything that unpacks to more than 1 MB.
				byte[] json = gzip.readNBytes(1 << 20);
				if (gzip.read() != -1) {
					return null;
				}
				JsonElement root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
				return root.isJsonObject() && root.getAsJsonObject().has("modules") ? root.getAsJsonObject() : null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	/** How many modules a parsed share code has settings for. */
	public static int countModules(JsonObject parsed) {
		JsonElement modules = parsed.get("modules");
		return modules != null && modules.isJsonObject() ? modules.getAsJsonObject().size() : 0;
	}

	/** Applies a share code. Returns false, changing nothing, when the text is not a share code. */
	public static boolean importCode(String code) {
		JsonObject parsed = parseCode(code);
		if (parsed == null) {
			return false;
		}
		apply(parsed);
		save();
		return true;
	}

	private static JsonObject toJson() {
		JsonObject modules = new JsonObject();
		for (Module module : ModuleRegistry.all()) {
			JsonObject entry = new JsonObject();
			entry.addProperty(ENABLED_KEY, module.isEnabled());
			for (Setting<?> setting : module.getSettings()) {
				entry.add(setting.getId(), setting.toJson());
			}
			modules.add(module.getId(), entry);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("modules", modules);
		return root;
	}

	public static void save() {
		if (savingSuppressed) {
			return;
		}
		JsonObject root = toJson();

		Path path = getPath();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			QoLBundleClient.LOGGER.error("Could not write {}", path, e);
		}
	}
}

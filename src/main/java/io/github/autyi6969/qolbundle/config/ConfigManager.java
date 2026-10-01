package io.github.autyi6969.qolbundle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
			JsonElement modules = root.getAsJsonObject().get("modules");
			if (modules == null || !modules.isJsonObject()) {
				return;
			}
			for (Module module : ModuleRegistry.all()) {
				JsonElement entry = modules.getAsJsonObject().get(module.getId());
				if (entry == null || !entry.isJsonObject()) {
					continue;
				}
				readModule(module, entry.getAsJsonObject());
			}
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

	public static void save() {
		if (savingSuppressed) {
			return;
		}
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

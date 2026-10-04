package io.github.autyism.qolbundle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.qolbundle.QoLBundleClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.WorldSavePath;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Things modules remember about one particular world or server (portals seen, the bed you slept
 * in, ...). One JSON file per world in config/qolbundle/worlds/, so single-player saves and
 * servers never mix. Each module gets its own section of the file.
 *
 * <p>This is client-side memory only: it holds what the player has already seen, nothing more.
 */
public final class WorldData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int AUTOSAVE_TICKS = 20 * 60;

	@Nullable
	private static String worldId;
	private static JsonObject root = new JsonObject();
	private static boolean dirty;
	private static int ticksSinceSave;

	private WorldData() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> load(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> unload());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
	}

	/** Identifies the current world/server; null while not in a world. Changes when the player switches worlds. */
	@Nullable
	public static String getWorldId() {
		return worldId;
	}

	/** The part of the current world's file that belongs to one module; created empty when missing. */
	@Nullable
	public static JsonObject section(String name) {
		if (worldId == null) {
			return null;
		}
		JsonElement existing = root.get(name);
		if (existing != null && existing.isJsonObject()) {
			return existing.getAsJsonObject();
		}
		JsonObject created = new JsonObject();
		root.add(name, created);
		return created;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void tick() {
		if (worldId != null && dirty && ++ticksSinceSave >= AUTOSAVE_TICKS) {
			save();
		}
	}

	private static Path fileFor(String id) {
		return FabricLoader.getInstance().getConfigDir().resolve(QoLBundleClient.MOD_ID).resolve("worlds").resolve(id + ".json");
	}

	private static String computeId(MinecraftClient client) {
		String raw;
		if (client.isIntegratedServerRunning() && client.getServer() != null) {
			Path folder = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
			raw = "local_" + folder.getFileName();
		} else {
			ServerInfo server = client.getCurrentServerEntry();
			raw = "server_" + (server != null ? server.address : "unknown");
		}
		return raw.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static void load(MinecraftClient client) {
		save();
		worldId = computeId(client);
		root = new JsonObject();
		dirty = false;
		ticksSinceSave = 0;
		Path path = fileFor(worldId);
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) {
				root = parsed.getAsJsonObject();
			}
		} catch (Exception e) {
			QoLBundleClient.LOGGER.error("Could not read world data {}, starting empty", path, e);
		}
	}

	private static void unload() {
		save();
		worldId = null;
		root = new JsonObject();
	}

	public static void save() {
		if (worldId == null || !dirty) {
			return;
		}
		dirty = false;
		ticksSinceSave = 0;
		Path path = fileFor(worldId);
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			QoLBundleClient.LOGGER.error("Could not write world data {}", path, e);
		}
	}
}

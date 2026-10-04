package io.github.autyism.qolbundle.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.gui.ChestMemoryScreen;
import io.github.autyism.qolbundle.gui.MouseOnlyButton;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.util.ItemNames;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Remembers what was in every chest, barrel, shulker box, hopper, dispenser and your ender chest
 * the last time you opened it, per world, and lets you search for an item and be pointed to the
 * chest that holds it. Shulker boxes lying inside a chest are looked into as well.
 *
 * <p>Only what you saw when you had the container open is known; nothing is read from containers
 * you never opened.
 */
public class ChestMemoryModule extends Module {
	private static final String SECTION = "chest_memory";
	/** A container screen that opens this soon after a block was right-clicked belongs to that block. */
	private static final long USE_WINDOW_MS = 3000;
	private static final int SNAPSHOT_TICKS = 10;
	private static final int PRUNE_TICKS = 40;
	private static final long DAY_MS = 24L * 60 * 60 * 1000;

	/** Some amount of one item. label is the shown name when it differs from the item's normal name. */
	public record StoredItem(String id, int count, String label) {
	}

	/** A shulker box seen inside a container, with what it held. */
	public record StoredBox(String boxId, String label, List<StoredItem> items) {
	}

	/** One remembered container. pos is null for the ender chest (the same wherever you open it). */
	public static final class Chest {
		public final String dimension;
		@Nullable
		public final BlockPos pos;
		public final String blockId;
		public long seenMs;
		public List<StoredItem> items = List.of();
		public List<StoredBox> boxes = List.of();

		Chest(String dimension, @Nullable BlockPos pos, String blockId) {
			this.dimension = dimension;
			this.pos = pos;
			this.blockId = blockId;
		}

		public boolean isEnderChest() {
			return pos == null;
		}
	}

	/** A search result: this chest holds count matching items; name is the first one found. */
	public record Hit(Chest chest, int count, Text name, boolean inShulkerBox) {
	}

	private final IntSetting staleDays = add(new IntSetting("stale_days", 7, 1, 60));
	private final IntSetting pointerSeconds = add(new IntSetting("pointer_seconds", 120, 10, 600));
	private final BoolSetting inventoryButton = add(new BoolSetting("inventory_button", true));

	private final KeyBinding searchKey;
	private final List<Chest> chests = new ArrayList<>();
	@Nullable
	private String loadedWorldId;

	@Nullable
	private BlockPos lastUsedPos;
	private String lastUsedDimension = "";
	private long lastUsedMs;
	@Nullable
	private ScreenHandler openHandler;
	@Nullable
	private Chest openChest;

	@Nullable
	private Chest target;
	private long targetUntilMs;
	private int ticks;

	public ChestMemoryModule() {
		super("chest_memory", ModuleCategory.TOOLS, true);
		searchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.chest_memory",
				InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), QoLBundleClient.KEY_CATEGORY));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient() && isEnabled()) {
				lastUsedPos = hit.getBlockPos().toImmutable();
				lastUsedDimension = world.getRegistryKey().getValue().toString();
				lastUsedMs = Util.getMeasuringTimeMs();
			}
			return ActionResult.PASS; // only watching
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen && isEnabled() && inventoryButton.get()) {
				Screens.getButtons(screen).add(new MouseOnlyButton(width - 104, height - 48, 100, 20,
						Text.translatable(getTranslationKey() + ".button"), button -> client.setScreen(new ChestMemoryScreen(null, this))));
			}
		});
	}

	@Override
	public boolean hasCustomScreen() {
		return true;
	}

	@Override
	public Screen createCustomScreen(Screen parent) {
		return new ChestMemoryScreen(parent, this);
	}

	public List<Chest> getChests() {
		syncWorldData();
		return chests;
	}

	public int getStaleDays() {
		return staleDays.get();
	}

	public boolean isStale(Chest chest) {
		return System.currentTimeMillis() - chest.seenMs > staleDays.get() * DAY_MS;
	}

	@Nullable
	public Chest getTarget() {
		return target;
	}

	/** Points the HUD arrow at a chest for a while (null switches it off). */
	public void setTarget(@Nullable Chest chest) {
		target = chest != null && !chest.isEnderChest() ? chest : null;
		targetUntilMs = Util.getMeasuringTimeMs() + pointerSeconds.get() * 1000L;
	}

	/** Forgets every container of the current world (used by the self-test). */
	public void forgetAll() {
		syncWorldData();
		chests.clear();
		target = null;
		store();
	}

	// ---- watching containers -------------------------------------------------------------------

	private static boolean isStorage(ScreenHandler handler) {
		return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler
				|| handler instanceof HopperScreenHandler || handler instanceof Generic3x3ContainerScreenHandler;
	}

	@Override
	public void onTick(MinecraftClient client) {
		syncWorldData();
		while (searchKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ChestMemoryScreen(null, this));
			}
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			openHandler = null;
			openChest = null;
			return;
		}
		ticks++;
		ScreenHandler handler = player.currentScreenHandler;
		if (handler != player.playerScreenHandler && isStorage(handler)) {
			if (handler != openHandler) {
				openHandler = handler;
				openChest = identify(client.world);
			}
			if (openChest != null && ticks % SNAPSHOT_TICKS == 0) {
				snapshot(player, handler, openChest);
			}
		} else if (openHandler != null) {
			// Just closed: the handler still holds what was last shown, take the final picture.
			if (openChest != null) {
				snapshot(player, openHandler, openChest);
			}
			openHandler = null;
			openChest = null;
		}

		if (ticks % PRUNE_TICKS == 0) {
			prune(client.world, player);
		}
		if (target != null && Util.getMeasuringTimeMs() > targetUntilMs) {
			target = null;
		}
	}

	/** Which remembered container the screen that just opened belongs to (creating the record if new). */
	@Nullable
	private Chest identify(ClientWorld world) {
		if (lastUsedPos == null || Util.getMeasuringTimeMs() - lastUsedMs > USE_WINDOW_MS) {
			return null; // opened some other way (minecart, command): there is no block to remember it by
		}
		BlockState state = world.getBlockState(lastUsedPos);
		String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
		if (state.isOf(Blocks.ENDER_CHEST)) {
			for (Chest chest : chests) {
				if (chest.isEnderChest()) {
					return chest;
				}
			}
			Chest ender = new Chest("", null, blockId);
			chests.add(ender);
			return ender;
		}
		BlockPos pos = lastUsedPos;
		// Both halves of a double chest are one container: always file it under the same half.
		if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.offset(ChestBlock.getFacing(state));
			if (other.compareTo(pos) < 0) {
				pos = other;
			}
		}
		for (Chest chest : chests) {
			if (!chest.isEnderChest() && chest.pos.equals(pos) && chest.dimension.equals(lastUsedDimension)) {
				return chest;
			}
		}
		Chest created = new Chest(lastUsedDimension, pos, blockId);
		chests.add(created);
		return created;
	}

	private void snapshot(ClientPlayerEntity player, ScreenHandler handler, Chest chest) {
		Map<String, StoredItem> items = new LinkedHashMap<>();
		List<StoredBox> boxes = new ArrayList<>();
		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (slot.inventory == player.getInventory() || stack.isEmpty()) {
				continue;
			}
			add(items, stack);
			if (stack.isIn(ItemTags.SHULKER_BOXES)) {
				ContainerComponent inside = stack.get(DataComponentTypes.CONTAINER);
				if (inside != null) {
					Map<String, StoredItem> boxItems = new LinkedHashMap<>();
					for (ItemStack inner : inside.iterateNonEmpty()) {
						add(boxItems, inner);
					}
					if (!boxItems.isEmpty()) {
						boxes.add(new StoredBox(idOf(stack), labelOf(stack), List.copyOf(boxItems.values())));
					}
				}
			}
		}
		chest.items = List.copyOf(items.values());
		chest.boxes = boxes;
		chest.seenMs = System.currentTimeMillis();
		if (chest == target) {
			target = null; // found it
		}
		store();
	}

	private static void add(Map<String, StoredItem> into, ItemStack stack) {
		String id = idOf(stack);
		String label = labelOf(stack);
		into.merge(id + "\n" + label, new StoredItem(id, stack.getCount(), label),
				(a, b) -> new StoredItem(a.id, a.count + b.count, a.label));
	}

	private static String idOf(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()).toString();
	}

	/** The shown name when it says more than the plain item name (renamed items, enchanted books, potions). */
	private static String labelOf(ItemStack stack) {
		String shown = stack.getName().getString();
		return shown.equals(stack.getItem().getName().getString()) ? "" : shown;
	}

	/** Forgets containers that are plainly gone: the block at their place is something else now. */
	private void prune(ClientWorld world, ClientPlayerEntity player) {
		String dimension = world.getRegistryKey().getValue().toString();
		boolean removed = chests.removeIf(chest -> !chest.isEnderChest() && chest != openChest
				&& chest.dimension.equals(dimension)
				&& chest.pos.getSquaredDistance(player.getBlockPos()) <= 12 * 12
				&& world.getChunkManager().isChunkLoaded(chest.pos.getX() >> 4, chest.pos.getZ() >> 4)
				&& !Registries.BLOCK.getId(world.getBlockState(chest.pos).getBlock()).toString().equals(chest.blockId));
		if (removed) {
			if (target != null && !chests.contains(target)) {
				target = null;
			}
			store();
		}
	}

	// ---- searching -----------------------------------------------------------------------------

	/** Containers holding something that matches, nearest first (other dimensions and the ender chest last). */
	public List<Hit> search(String query, @Nullable ClientPlayerEntity player, String currentDimension) {
		return find(item -> matches(item, query), player, currentDimension);
	}

	/** Containers holding any of these items (ids like "minecraft:iron_ingot"), in the same order as {@link #search}. */
	public List<Hit> findItems(Set<String> itemIds, @Nullable ClientPlayerEntity player, String currentDimension) {
		return find(item -> itemIds.contains(item.id), player, currentDimension);
	}

	private List<Hit> find(Predicate<StoredItem> wanted, @Nullable ClientPlayerEntity player, String currentDimension) {
		syncWorldData();
		List<Hit> hits = new ArrayList<>();
		for (Chest chest : chests) {
			int count = 0;
			Text name = null;
			boolean inBox = false;
			for (StoredItem item : chest.items) {
				if (wanted.test(item)) {
					count += item.count;
					if (name == null) {
						name = displayName(item);
					}
				}
			}
			for (StoredBox box : chest.boxes) {
				for (StoredItem item : box.items) {
					if (wanted.test(item)) {
						count += item.count;
						inBox = true;
						if (name == null) {
							name = displayName(item);
						}
					}
				}
			}
			if (count > 0) {
				hits.add(new Hit(chest, count, name, inBox));
			}
		}
		hits.sort(Comparator.comparingDouble(hit -> sortKey(hit.chest, player, currentDimension)));
		return hits;
	}

	private static double sortKey(Chest chest, @Nullable ClientPlayerEntity player, String currentDimension) {
		if (chest.isEnderChest()) {
			return Double.MAX_VALUE / 2;
		}
		if (player == null || !chest.dimension.equals(currentDimension)) {
			return Double.MAX_VALUE / 4;
		}
		return chest.pos.getSquaredDistance(player.getBlockPos());
	}

	private static boolean matches(StoredItem item, String query) {
		Item type = itemOf(item.id);
		String shown = item.label.isEmpty() ? type.getName().getString() : item.label;
		return ItemNames.matches(ItemNames.searchText(type.getTranslationKey(), shown, true), query);
	}

	public static Item itemOf(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? net.minecraft.item.Items.AIR : Registries.ITEM.get(identifier);
	}

	public static Text displayName(StoredItem item) {
		return item.label.isEmpty() ? itemOf(item.id).getName() : Text.literal(item.label);
	}

	// ---- saving / loading ----------------------------------------------------------------------

	private void syncWorldData() {
		String worldId = WorldData.getWorldId();
		if (Objects.equals(worldId, loadedWorldId)) {
			return;
		}
		loadedWorldId = worldId;
		chests.clear();
		target = null;
		openHandler = null;
		openChest = null;
		JsonObject section = WorldData.section(SECTION);
		if (section == null || !section.has("chests") || !section.get("chests").isJsonArray()) {
			return;
		}
		for (JsonElement element : section.getAsJsonArray("chests")) {
			try {
				JsonObject json = element.getAsJsonObject();
				BlockPos pos = json.has("x")
						? new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()) : null;
				Chest chest = new Chest(json.get("dimension").getAsString(), pos, json.get("block").getAsString());
				chest.seenMs = json.get("seen").getAsLong();
				chest.items = readItems(json.getAsJsonArray("items"));
				List<StoredBox> boxes = new ArrayList<>();
				if (json.has("boxes")) {
					for (JsonElement boxElement : json.getAsJsonArray("boxes")) {
						JsonObject box = boxElement.getAsJsonObject();
						boxes.add(new StoredBox(box.get("id").getAsString(), box.get("label").getAsString(), readItems(box.getAsJsonArray("items"))));
					}
				}
				chest.boxes = boxes;
				chests.add(chest);
			} catch (RuntimeException ignored) {
				// skip a damaged entry
			}
		}
	}

	private static List<StoredItem> readItems(JsonArray array) {
		List<StoredItem> items = new ArrayList<>();
		for (JsonElement element : array) {
			JsonArray entry = element.getAsJsonArray();
			items.add(new StoredItem(entry.get(0).getAsString(), entry.get(1).getAsInt(), entry.size() > 2 ? entry.get(2).getAsString() : ""));
		}
		return items;
	}

	private static JsonArray writeItems(List<StoredItem> items) {
		JsonArray array = new JsonArray();
		for (StoredItem item : items) {
			JsonArray entry = new JsonArray();
			entry.add(item.id);
			entry.add(item.count);
			if (!item.label.isEmpty()) {
				entry.add(item.label);
			}
			array.add(entry);
		}
		return array;
	}

	private void store() {
		JsonObject section = WorldData.section(SECTION);
		if (section == null) {
			return;
		}
		JsonArray array = new JsonArray();
		for (Chest chest : chests) {
			JsonObject json = new JsonObject();
			json.addProperty("dimension", chest.dimension);
			if (chest.pos != null) {
				json.addProperty("x", chest.pos.getX());
				json.addProperty("y", chest.pos.getY());
				json.addProperty("z", chest.pos.getZ());
			}
			json.addProperty("block", chest.blockId);
			json.addProperty("seen", chest.seenMs);
			json.add("items", writeItems(chest.items));
			if (!chest.boxes.isEmpty()) {
				JsonArray boxes = new JsonArray();
				for (StoredBox box : chest.boxes) {
					JsonObject boxJson = new JsonObject();
					boxJson.addProperty("id", box.boxId);
					boxJson.addProperty("label", box.label);
					boxJson.add("items", writeItems(box.items));
					boxes.add(boxJson);
				}
				json.add("boxes", boxes);
			}
			array.add(json);
		}
		section.add("chests", array);
		WorldData.markDirty();
	}

	// ---- pointing at the chosen chest ----------------------------------------------------------

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Chest chosen = target;
		MinecraftClient client = MinecraftClient.getInstance();
		if (chosen == null || client.world == null || !chosen.dimension.equals(client.world.getRegistryKey().getValue().toString())) {
			return;
		}
		// Your own chest, which you asked to be led to: its frame may show through walls.
		GizmoDrawing.box(new Box(chosen.pos).expand(0.02), DrawStyle.stroked(0xFF55FF55, 3.0F)).ignoreOcclusion();
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Chest chosen = target;
		if (chosen == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		Text text;
		if (!chosen.dimension.equals(client.world.getRegistryKey().getValue().toString())) {
			text = Text.translatable(key + "other_dimension", chosen.pos.getX(), chosen.pos.getY(), chosen.pos.getZ());
		} else {
			Vec3d spot = Vec3d.ofCenter(chosen.pos);
			float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(client.gameRenderer.getCamera().getCameraPos(),
					client.gameRenderer.getCamera().getYaw(), spot));
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(centerX, centerY);
			context.getMatrices().rotate(angle);
			for (int row = 0; row < 9; row++) {
				context.fill(-row, -40 + row, row + 1, -39 + row, 0xFF55FF55);
			}
			context.getMatrices().popMatrix();
			int distance = (int) Math.round(Math.sqrt(chosen.pos.getSquaredDistance(client.player.getBlockPos())));
			text = Text.translatable(key + "pointer", chosen.pos.getX(), chosen.pos.getY(), chosen.pos.getZ(), distance);
		}
		int width = client.textRenderer.getWidth(text);
		context.fill(centerX - width / 2 - 3, centerY + 44, centerX + width / 2 + 3, centerY + 56, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, text, centerX - width / 2, centerY + 46, 0xFF55FF55);
	}
}

package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.gui.HotbarLayoutScreen;
import io.github.autyism.qolbundle.gui.MouseOnlyButton;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.StringSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;

/**
 * Saved hotbar layouts ("building", "fighting", "mining" ...). Applying one fetches the wanted
 * items from the backpack into the right hotbar slots; items you do not have are simply skipped.
 *
 * <p>The items are moved with the ordinary "swap with hotbar slot" inventory click, one per tick,
 * the same thing pressing a number key over an item does.
 */
public class HotbarLayoutsModule extends Module {
	public static final int LAYOUT_COUNT = 5;
	private static final int HOTBAR = Inventory.SELECTION_SIZE;
	private static final int IDLE = -1;

	private final BoolSetting inventoryButton = add(new BoolSetting("inventory_button", true));
	private final StringSetting[] names = new StringSetting[LAYOUT_COUNT];
	/** Nine item ids separated by commas; an empty entry means "leave this slot alone". */
	private final StringSetting[] items = new StringSetting[LAYOUT_COUNT];

	private final KeyMapping openKey;
	private final KeyMapping[] applyKeys = new KeyMapping[LAYOUT_COUNT];

	private int applying = IDLE;
	private int nextSlot;
	private int missing;
	private int moved;

	public HotbarLayoutsModule() {
		super("hotbar_layouts", ModuleCategory.TOOLS, true);
		for (int i = 0; i < LAYOUT_COUNT; i++) {
			names[i] = add(new StringSetting("name_" + (i + 1), "", 24));
			names[i].setHidden(true);
			items[i] = add(new StringSetting("items_" + (i + 1), "", 600));
			items[i].setHidden(true);
		}
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.hotbar_layouts",
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), QoLBundleClient.KEY_CATEGORY));
		for (int i = 0; i < LAYOUT_COUNT; i++) {
			applyKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.hotbar_layout_" + (i + 1),
					InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), QoLBundleClient.KEY_CATEGORY));
		}
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen && isEnabled() && inventoryButton.get()) {
				Screens.getButtons(screen).add(new MouseOnlyButton(width - 104, height - 24, 100, 20,
						Component.translatable(getTranslationKey() + ".button"), button -> client.setScreen(new HotbarLayoutScreen(null, this))));
			}
		});
	}

	@Override
	public boolean hasCustomScreen() {
		return true;
	}

	@Override
	public Screen createCustomScreen(Screen parent) {
		return new HotbarLayoutScreen(parent, this);
	}

	public String getLayoutName(int index) {
		return names[index].get();
	}

	public void setLayoutName(int index, String name) {
		names[index].set(name);
	}

	/** The nine wanted item ids of a layout ("" = leave the slot alone). */
	public String[] getLayout(int index) {
		String[] wanted = new String[HOTBAR];
		Arrays.fill(wanted, "");
		String[] stored = items[index].get().split(",", -1);
		for (int i = 0; i < HOTBAR && i < stored.length; i++) {
			wanted[i] = stored[i].trim();
		}
		return wanted;
	}

	public void setLayout(int index, String[] wanted) {
		items[index].set(String.join(",", wanted));
	}

	public boolean isLayoutEmpty(int index) {
		for (String id : getLayout(index)) {
			if (!id.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Remembers what is in the hotbar right now as layout number index. */
	public void saveCurrent(Minecraft client, int index) {
		if (client.player == null) {
			return;
		}
		String[] wanted = new String[HOTBAR];
		for (int i = 0; i < HOTBAR; i++) {
			ItemStack stack = client.player.getInventory().getItem(i);
			wanted[i] = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}
		setLayout(index, wanted);
	}

	public boolean isApplying() {
		return applying != IDLE;
	}

	/** Items of the last applied layout that were not in the backpack (for the self-test). */
	public int getMissing() {
		return missing;
	}

	public void apply(Minecraft client, int index) {
		if (client.player == null || applying != IDLE) {
			return;
		}
		if (isLayoutEmpty(index)) {
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".empty", index + 1).withStyle(ChatFormatting.RED), false);
			return;
		}
		applying = index;
		nextSlot = 0;
		missing = 0;
		moved = 0;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		applying = IDLE;
	}

	@Override
	public void onTick(Minecraft client) {
		while (openKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new HotbarLayoutScreen(null, this));
			}
		}
		for (int i = 0; i < LAYOUT_COUNT; i++) {
			while (applyKeys[i].consumeClick()) {
				apply(client, i);
			}
		}
		if (applying == IDLE) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || player.containerMenu != player.inventoryMenu) {
			applying = IDLE; // a chest or similar is open: inventory clicks would go to the wrong window
			return;
		}
		String[] wanted = getLayout(applying);
		Inventory inventory = player.getInventory();
		// One swap per tick. Slots that are already right, or whose item is not there, cost no time.
		while (nextSlot < HOTBAR) {
			int slot = nextSlot++;
			String id = wanted[slot];
			if (id.isEmpty() || idOf(inventory.getItem(slot)).equals(id)) {
				continue;
			}
			int source = findSource(inventory, wanted, slot, id);
			if (source < 0) {
				missing++;
				continue;
			}
			client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, source, slot, ClickType.SWAP, player);
			moved++;
			return;
		}
		Component name = getLayoutName(applying).isEmpty() ? Component.literal(String.valueOf(applying + 1)) : Component.literal(getLayoutName(applying));
		client.gui.setOverlayMessage(missing == 0
				? Component.translatable(getTranslationKey() + ".applied", name, moved)
				: Component.translatable(getTranslationKey() + ".applied_missing", name, moved, missing), false);
		applying = IDLE;
	}

	private static String idOf(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/**
	 * Where to take the item from, as a slot number of the player's inventory window:
	 * the backpack first, then a later hotbar slot that does not need it itself. -1 if nowhere.
	 */
	private static int findSource(Inventory inventory, String[] wanted, int target, String id) {
		for (int i = HOTBAR; i < 36; i++) {
			if (idOf(inventory.getItem(i)).equals(id)) {
				return i; // backpack slots have the same number in the window
			}
		}
		for (int i = target + 1; i < HOTBAR; i++) {
			if (idOf(inventory.getItem(i)).equals(id) && !wanted[i].equals(id)) {
				return InventoryMenu.USE_ROW_SLOT_START + i;
			}
		}
		return -1;
	}
}

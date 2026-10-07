package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Shows worn armor (and optionally held tools) as icons with their remaining durability. */
public class ArmorHudModule extends Module {
	public enum DurabilityDisplay {
		REMAINING,
		PERCENT,
		BOTH
	}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final int ROW_HEIGHT = 17;

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.BOTTOM_RIGHT));
	private final EnumSetting<DurabilityDisplay> display = add(new EnumSetting<>("display", DurabilityDisplay.REMAINING));
	private final BoolSetting showHands = add(new BoolSetting("show_hands", true));
	private final BoolSetting showBar = add(new BoolSetting("show_bar", true));
	private final BoolSetting showFreeSlots = add(new BoolSetting("show_free_slots", true));
	private static final ItemStack CHEST_ICON = new ItemStack(net.minecraft.world.item.Items.CHEST);
	private int freeSlots;

	private int shownLastFrame;

	public ArmorHudModule() {
		super("armor_hud", ModuleCategory.TECHNICAL, true);
	}

	/** Empty slots among the 36 of hotbar and backpack, as counted in the last frame (for the self-test). */
	public int getFreeSlots() {
		return freeSlots;
	}

	/** How many items were drawn in the last frame (for the self-test). */
	public int getShownCount() {
		return shownLastFrame;
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Minecraft client = Minecraft.getInstance();
		List<ItemStack> stacks = new ArrayList<>();
		collect(client, ARMOR, stacks);
		if (showHands.get()) {
			collect(client, HANDS, stacks);
		}
		shownLastFrame = stacks.size();
		HudAnchor anchor = position.get();
		freeSlots = 0;
		for (int slot = 0; slot < 36; slot++) {
			if (client.player.getInventory().getItem(slot).isEmpty()) {
				freeSlots++;
			}
		}
		boolean slotsRow = showFreeSlots.get();
		if (stacks.isEmpty() && !slotsRow || layout.isBlocked(anchor)) {
			return;
		}

		int rows = stacks.size() + (slotsRow ? 1 : 0);
		int top = layout.reserve(anchor, rows * ROW_HEIGHT);
		if (slotsRow) {
			// Last row: a chest and how many of the 36 inventory slots are still empty.
			String text = Integer.toString(freeSlots);
			int rowWidth = 16 + 3 + client.font.width(text);
			int x = layout.xFor(anchor, rowWidth);
			int y = top + stacks.size() * ROW_HEIGHT;
			context.renderItem(CHEST_ICON, anchor.right ? x + rowWidth - 16 : x, y);
			int color = freeSlots <= 3 ? 0xFFFF5555 : freeSlots <= 9 ? 0xFFFFFF55 : 0xFF55FF55;
			context.drawString(client.font, text, anchor.right ? x : x + 19, y + 4, color);
		}
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			int max = stack.getMaxDamage();
			int remaining = max - stack.getDamageValue();
			float fraction = remaining / (float) max;
			String text = switch (display.get()) {
				case REMAINING -> Integer.toString(remaining);
				case PERCENT -> Math.round(fraction * 100F) + "%";
				case BOTH -> remaining + " (" + Math.round(fraction * 100F) + "%)";
			};
			int textWidth = client.font.width(text);
			int rowWidth = 16 + 3 + textWidth;
			int x = layout.xFor(anchor, rowWidth);
			int y = top + i * ROW_HEIGHT;
			// Icon on the outer side, number towards the middle of the screen.
			int iconX = anchor.right ? x + rowWidth - 16 : x;
			int textX = anchor.right ? x : x + 19;
			context.renderItem(stack, iconX, y);
			if (showBar.get()) {
				// The same coloured bar the inventory shows under a damaged item.
				context.renderItemDecorations(client.font, stack, iconX, y);
			}
			context.drawString(client.font, text, textX, y + 4, colorFor(fraction));
		}
	}

	private static void collect(Minecraft client, EquipmentSlot[] slots, List<ItemStack> out) {
		for (EquipmentSlot slot : slots) {
			ItemStack stack = client.player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				out.add(stack);
			}
		}
	}

	/** Green when new, through yellow, to red when nearly broken. */
	private static int colorFor(float fraction) {
		float f = Math.max(0F, Math.min(1F, fraction));
		int red = f > 0.5F ? (int) (255 * (1F - f) * 2F) : 255;
		int green = f > 0.5F ? 255 : (int) (255 * f * 2F);
		return 0xFF000000 | red << 16 | green << 8 | 0x40;
	}
}

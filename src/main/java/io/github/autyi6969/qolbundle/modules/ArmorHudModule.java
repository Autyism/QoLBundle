package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudAnchor;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

	private int shownLastFrame;

	public ArmorHudModule() {
		super("armor_hud", ModuleCategory.TECHNICAL, true);
	}

	/** How many items were drawn in the last frame (for the self-test). */
	public int getShownCount() {
		return shownLastFrame;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		List<ItemStack> stacks = new ArrayList<>();
		collect(client, ARMOR, stacks);
		if (showHands.get()) {
			collect(client, HANDS, stacks);
		}
		shownLastFrame = stacks.size();
		HudAnchor anchor = position.get();
		if (stacks.isEmpty() || layout.isBlocked(anchor)) {
			return;
		}

		int top = layout.reserve(anchor, stacks.size() * ROW_HEIGHT);
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			int max = stack.getMaxDamage();
			int remaining = max - stack.getDamage();
			float fraction = remaining / (float) max;
			String text = switch (display.get()) {
				case REMAINING -> Integer.toString(remaining);
				case PERCENT -> Math.round(fraction * 100F) + "%";
				case BOTH -> remaining + " (" + Math.round(fraction * 100F) + "%)";
			};
			int textWidth = client.textRenderer.getWidth(text);
			int rowWidth = 16 + 3 + textWidth;
			int x = layout.xFor(anchor, rowWidth);
			int y = top + i * ROW_HEIGHT;
			// Icon on the outer side, number towards the middle of the screen.
			int iconX = anchor.right ? x + rowWidth - 16 : x;
			int textX = anchor.right ? x : x + 19;
			context.drawItem(stack, iconX, y);
			if (showBar.get()) {
				// The same coloured bar the inventory shows under a damaged item.
				context.drawStackOverlay(client.textRenderer, stack, iconX, y);
			}
			context.drawTextWithShadow(client.textRenderer, text, textX, y + 4, colorFor(fraction));
		}
	}

	private static void collect(MinecraftClient client, EquipmentSlot[] slots, List<ItemStack> out) {
		for (EquipmentSlot slot : slots) {
			ItemStack stack = client.player.getEquippedStack(slot);
			if (!stack.isEmpty() && stack.isDamageable()) {
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

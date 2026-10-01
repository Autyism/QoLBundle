package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns when a held tool or worn armor piece is about to break: the screen edges flash red,
 * a sound plays and a line of text names the item and how many uses are left.
 */
public class DurabilityAlertModule extends Module {
	private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final long FLASH_MS = 2500;
	private static final long SOUND_COOLDOWN_MS = 1500;
	private static final int EDGE_DEPTH = 28;

	private final IntSetting threshold = add(new IntSetting("threshold", 5, 1, 50, "%"));
	private final BoolSetting checkArmor = add(new BoolSetting("check_armor", true));
	private final BoolSetting flash = add(new BoolSetting("flash", true));
	private final BoolSetting sound = add(new BoolSetting("sound", true));
	private final BoolSetting showText = add(new BoolSetting("show_text", true));

	/** What each slot held last tick, to notice "just became low" and "lost more durability". */
	private final Item[] lastItem = new Item[EquipmentSlot.values().length];
	private final int[] lastRemaining = new int[EquipmentSlot.values().length];
	private final boolean[] wasLow = new boolean[EquipmentSlot.values().length];

	private final List<Text> warnings = new ArrayList<>();
	private boolean handItemLow;
	private long flashUntilMs;
	private long lastSoundMs;

	public DurabilityAlertModule() {
		super("durability_alert", ModuleCategory.TOOLS, true);
	}

	/** True while at least one watched item is at or below the threshold. */
	public boolean isAlertActive() {
		return !warnings.isEmpty();
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		forget();
	}

	private void forget() {
		warnings.clear();
		handItemLow = false;
		flashUntilMs = 0;
		java.util.Arrays.fill(wasLow, false);
		java.util.Arrays.fill(lastItem, null);
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) {
			forget();
			return;
		}
		warnings.clear();
		handItemLow = false;
		boolean triggered = false;
		for (EquipmentSlot slot : HANDS) {
			triggered |= checkSlot(client, slot, true);
		}
		if (checkArmor.get()) {
			for (EquipmentSlot slot : ARMOR) {
				triggered |= checkSlot(client, slot, false);
			}
		}
		if (triggered) {
			long now = Util.getMeasuringTimeMs();
			flashUntilMs = now + FLASH_MS;
			if (sound.get() && now - lastSoundMs > SOUND_COOLDOWN_MS) {
				lastSoundMs = now;
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.6F));
			}
		}
	}

	/** @return true when this slot should set off the flash and sound right now */
	private boolean checkSlot(MinecraftClient client, EquipmentSlot slot, boolean hand) {
		int i = slot.ordinal();
		ItemStack stack = client.player.getEquippedStack(slot);
		if (stack.isEmpty() || !stack.isDamageable()) {
			wasLow[i] = false;
			lastItem[i] = null;
			return false;
		}
		int max = stack.getMaxDamage();
		int remaining = max - stack.getDamage();
		boolean low = remaining * 100 <= threshold.get() * max;
		boolean sameItem = lastItem[i] == stack.getItem();
		// Fires when the item first becomes low, when a different low item is taken into this slot,
		// and every time a low item loses more durability.
		boolean trigger = low && (!wasLow[i] || !sameItem || remaining < lastRemaining[i]);
		wasLow[i] = low;
		lastItem[i] = stack.getItem();
		lastRemaining[i] = remaining;
		if (low) {
			handItemLow |= hand;
			int percent = Math.max(0, Math.round(remaining * 100F / max));
			warnings.add(Text.translatable(getTranslationKey() + ".warning", stack.getName(), remaining, percent));
		}
		return trigger;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (warnings.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		long now = Util.getMeasuringTimeMs();

		if (flash.get()) {
			float strength;
			if (now < flashUntilMs) {
				// Pulse about twice a second, never fully fading out.
				strength = 0.55F + 0.45F * (float) Math.sin(now / 80.0);
			} else {
				// After the flash: a faint steady edge while the low item is still in hand.
				strength = handItemLow ? 0.3F : 0F;
			}
			if (strength > 0) {
				drawRedEdges(context, layout.getScreenWidth(), layout.getScreenHeight(), strength);
			}
		}

		if (showText.get()) {
			int y = layout.getScreenHeight() - 72 - warnings.size() * HudLayout.LINE_HEIGHT;
			for (Text warning : warnings) {
				int width = client.textRenderer.getWidth(warning);
				int x = (layout.getScreenWidth() - width) / 2;
				context.fill(x - 3, y - 2, x + width + 3, y + 9, 0x90000000);
				context.drawTextWithShadow(client.textRenderer, warning, x, y, 0xFFFF5555);
				y += HudLayout.LINE_HEIGHT + 1;
			}
		}
	}

	/** Red glow fading from the screen border towards the middle, built from one-pixel frames. */
	private static void drawRedEdges(DrawContext context, int width, int height, float strength) {
		for (int i = 0; i < EDGE_DEPTH; i++) {
			float fade = 1F - i / (float) EDGE_DEPTH;
			int alpha = (int) (170 * strength * fade * fade);
			if (alpha <= 0) {
				continue;
			}
			int color = alpha << 24 | 0xFF0000;
			context.fill(i, i, width - i, i + 1, color);
			context.fill(i, height - i - 1, width - i, height - i, color);
			context.fill(i, i + 1, i + 1, height - i - 1, color);
			context.fill(width - i - 1, i + 1, width - i, height - i - 1, color);
		}
	}
}

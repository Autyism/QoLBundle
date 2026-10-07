package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Warns when a held tool or worn armor piece is about to break: the screen edges flash red,
 * a sound plays and a line of text names the item and how many uses are left.
 * Each item is announced once; it is announced again only after it has been repaired.
 */
public class DurabilityAlertModule extends Module {
	private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final long FLASH_MS = 2500;
	private static final long TEXT_MS = 6000;
	private static final long SOUND_COOLDOWN_MS = 1500;
	private static final int EDGE_DEPTH = 28;

	private final IntSetting threshold = add(new IntSetting("threshold", 5, 1, 50, "%"));
	private final BoolSetting checkArmor = add(new BoolSetting("check_armor", true));
	private final BoolSetting flash = add(new BoolSetting("flash", true));
	private final BoolSetting sound = add(new BoolSetting("sound", true));
	private final BoolSetting showText = add(new BoolSetting("show_text", true));
	private final BoolSetting remindAgain = add(new BoolSetting("remind_again", false));

	/**
	 * Items already announced, with the durability they had when last seen. The key describes the
	 * item (kind, name, enchantments) because the game gives items no id of their own.
	 */
	private final Map<String, Integer> announced = new HashMap<>();
	private final List<Component> warnings = new ArrayList<>();
	private long flashUntilMs;
	private long textUntilMs;
	private long lastSoundMs;
	private int alerts;

	public DurabilityAlertModule() {
		super("durability_alert", ModuleCategory.TOOLS, true);
	}

	/** True while at least one watched item is at or below the threshold. */
	public boolean isAlertActive() {
		return !warnings.isEmpty();
	}

	/** How many times the alert has gone off since the game started (for the self-test). */
	public int getAlertCount() {
		return alerts;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		forget();
	}

	private void forget() {
		warnings.clear();
		announced.clear();
		flashUntilMs = 0;
		textUntilMs = 0;
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			forget();
			return;
		}
		warnings.clear();
		boolean triggered = false;
		for (EquipmentSlot slot : HANDS) {
			triggered |= checkSlot(client, slot);
		}
		if (checkArmor.get()) {
			for (EquipmentSlot slot : ARMOR) {
				triggered |= checkSlot(client, slot);
			}
		}
		if (triggered) {
			alerts++;
			long now = Util.getMillis();
			flashUntilMs = now + FLASH_MS;
			textUntilMs = now + TEXT_MS;
			if (sound.get() && now - lastSoundMs > SOUND_COOLDOWN_MS) {
				lastSoundMs = now;
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.6F));
			}
		}
	}

	/** @return true when this slot should set off the flash and sound right now */
	private boolean checkSlot(Minecraft client, EquipmentSlot slot) {
		ItemStack stack = client.player.getItemBySlot(slot);
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return false;
		}
		int max = stack.getMaxDamage();
		int remaining = max - stack.getDamageValue();
		String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getHoverName().getString() + "|" + stack.getEnchantments();
		if (remaining * 100 > threshold.get() * max) {
			announced.remove(key); // healthy (again): the next time it runs low it is announced anew
			return false;
		}
		Integer before = announced.put(key, remaining);
		int percent = Math.max(0, Math.round(remaining * 100F / max));
		warnings.add(Component.translatable(getTranslationKey() + ".warning", stack.getHoverName(), remaining, percent));
		// Once per item. With "remind again" on, also every time it loses more durability.
		return before == null || remindAgain.get() && remaining < before;
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		long now = Util.getMillis();
		if (warnings.isEmpty() || now >= Math.max(flashUntilMs, textUntilMs)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();

		if (flash.get() && now < flashUntilMs) {
			// Pulse about twice a second, never fully fading out.
			float strength = 0.55F + 0.45F * (float) Math.sin(now / 80.0);
			drawRedEdges(context, layout.getScreenWidth(), layout.getScreenHeight(), strength);
		}

		if (showText.get() && now < textUntilMs) {
			int y = layout.getScreenHeight() - 72 - warnings.size() * HudLayout.LINE_HEIGHT;
			for (Component warning : warnings) {
				int width = client.font.width(warning);
				int x = (layout.getScreenWidth() - width) / 2;
				context.fill(x - 3, y - 2, x + width + 3, y + 9, 0x90000000);
				context.drawString(client.font, warning, x, y, 0xFFFF5555);
				y += HudLayout.LINE_HEIGHT + 1;
			}
		}
	}

	/** Red glow fading from the screen border towards the middle, built from one-pixel frames. */
	private static void drawRedEdges(GuiGraphics context, int width, int height, float strength) {
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

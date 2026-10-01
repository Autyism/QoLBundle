package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/**
 * Attack cooldown, to the tick: a bar under the crosshair that fills while the weapon recharges,
 * the number of ticks left next to it, and a soft click the moment it is full again.
 *
 * <p>The same number the game uses for its own small indicator, read from the local player.
 */
public class AttackCooldownModule extends Module {
	private static final int WIDTH = 40;
	private static final int FLASH_TICKS = 6;

	private final BoolSetting sound = add(new BoolSetting("sound", true));
	private final IntSetting volume = add(new IntSetting("volume", 30, 5, 100, "%"));
	private final BoolSetting showTicks = add(new BoolSetting("show_ticks", true));

	private boolean charging;
	private int flash;
	private int readyCount;

	public AttackCooldownModule() {
		super("attack_cooldown", ModuleCategory.PVP, true);
	}

	/** True while the weapon is recharging (the bar is on screen). */
	public boolean isCharging() {
		return charging;
	}

	/** How many times the "ready again" moment has been announced (for the self-test). */
	public int getReadyCount() {
		return readyCount;
	}

	/** Whole ticks until the next hit does full damage; 0 when ready. */
	public int ticksLeft(ClientPlayerEntity player) {
		float progress = player.getAttackCooldownProgress(0F);
		return progress >= 1F ? 0 : (int) Math.ceil((1F - progress) * player.getAttackCooldownProgressPerTick());
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		charging = false;
		flash = 0;
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			charging = false;
			return;
		}
		boolean now = player.getAttackCooldownProgress(0F) < 1F;
		if (charging && !now) {
			readyCount++;
			flash = FLASH_TICKS;
			if (sound.get()) {
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6F, volume.get() / 100F));
			}
		} else if (flash > 0) {
			flash--;
		}
		charging = now;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || !charging && flash == 0) {
			return;
		}
		float progress = player.getAttackCooldownProgress(tickCounter.getTickProgress(false));
		int left = layout.getScreenWidth() / 2 - WIDTH / 2;
		int top = layout.getScreenHeight() / 2 + 26;
		context.fill(left - 1, top - 1, left + WIDTH + 1, top + 4, 0xA0000000);
		if (charging) {
			// Red while useless, through yellow, towards green.
			int filled = Math.round(WIDTH * progress);
			int color = progress < 0.5F ? 0xFFFF5555 : progress < 0.9F ? 0xFFFFFF55 : 0xFFAAFF55;
			context.fill(left, top, left + filled, top + 3, color);
			if (showTicks.get()) {
				String text = ticksLeft(player) + "t";
				context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, left + WIDTH + 4, top - 3, 0xFFFFFFFF);
			}
		} else {
			context.fill(left, top, left + WIDTH, top + 3, 0xFF55FF55); // full: a short green flash
		}
	}
}

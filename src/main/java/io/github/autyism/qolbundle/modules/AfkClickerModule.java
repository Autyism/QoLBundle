package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.input.ViewHooks;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>An AFK auto-clicker: locks the view and clicks (or holds) a mouse button at a set pace until
 * the toggle key is pressed again or the player gets hurt. It presses the same key bindings the
 * player would, so everything it does goes through the normal game code.
 */
public class AfkClickerModule extends Module {
	public enum AfkAction {
		LEFT_CLICK(false, false),
		RIGHT_CLICK(true, false),
		HOLD_LEFT(false, true),
		HOLD_RIGHT(true, true);

		final boolean useKey;
		final boolean hold;

		AfkAction(boolean useKey, boolean hold) {
			this.useKey = useKey;
			this.hold = hold;
		}
	}

	/** Ready-made setups for common farms. CUSTOM uses the action and pace chosen below it. */
	public enum AfkPreset {
		CUSTOM(null, 0),
		MOB_FARM_SWORD(AfkAction.LEFT_CLICK, 14),
		HOLD_USE(AfkAction.HOLD_RIGHT, 0),
		HOLD_MINE(AfkAction.HOLD_LEFT, 0),
		FAST_USE(AfkAction.RIGHT_CLICK, 4),
		SLOW_USE(AfkAction.RIGHT_CLICK, 20);

		final AfkAction action;
		final int interval;

		AfkPreset(AfkAction action, int interval) {
			this.action = action;
			this.interval = interval;
		}
	}

	private final EnumSetting<AfkPreset> preset = add(new EnumSetting<>("preset", AfkPreset.CUSTOM));
	private final EnumSetting<AfkAction> action = add(new EnumSetting<>("action", AfkAction.RIGHT_CLICK));
	private final IntSetting interval = add(new IntSetting("interval", 10, 1, 200));
	private final BoolSetting lockView = add(new BoolSetting("lock_view", true));
	private final BoolSetting stopOnDamage = add(new BoolSetting("stop_on_damage", true));
	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));

	private final KeyMapping toggleKey;
	private boolean running;
	private boolean stoppedByDamage;
	private float lockedYaw;
	private float lockedPitch;
	private float lastHealth;
	private int ticksUntilClick;
	private int clicks;
	/** The key this run is holding down, to release exactly that one when it ends. */
	private KeyMapping heldKey;

	public AfkClickerModule() {
		super("afk_clicker", ModuleCategory.GREY, false);
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.afk_toggle",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, QoLBundleClient.KEY_CATEGORY));
		ViewHooks.register((deltaX, deltaY) -> running && lockView.get());
	}

	public boolean isRunning() {
		return running;
	}

	public boolean wasStoppedByDamage() {
		return stoppedByDamage;
	}

	public int getClicks() {
		return clicks;
	}

	public EnumSetting<AfkPreset> presetSetting() {
		return preset;
	}

	public EnumSetting<AfkAction> actionSetting() {
		return action;
	}

	public IntSetting intervalSetting() {
		return interval;
	}

	private AfkAction currentAction() {
		return preset.get() == AfkPreset.CUSTOM ? action.get() : preset.get().action;
	}

	private int currentInterval() {
		return preset.get() == AfkPreset.CUSTOM ? interval.get() : preset.get().interval;
	}

	public void start(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || running) {
			return;
		}
		running = true;
		stoppedByDamage = false;
		lockedYaw = player.getYRot();
		lockedPitch = player.getXRot();
		lastHealth = player.getHealth();
		ticksUntilClick = 0;
		clicks = 0;
		client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".started"), false);
	}

	public void stop(Minecraft client, boolean becauseOfDamage) {
		if (!running) {
			return;
		}
		running = false;
		stoppedByDamage = becauseOfDamage;
		if (heldKey != null) {
			heldKey.setDown(false);
			heldKey = null;
		}
		if (becauseOfDamage) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.5F));
			client.gui.setOverlayMessage(
					Component.translatable(getTranslationKey() + ".stopped_damage").withStyle(ChatFormatting.RED), false);
		} else {
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".stopped"), false);
		}
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled) {
			stop(Minecraft.getInstance(), false);
		}
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		while (toggleKey.consumeClick()) {
			if (running) {
				stop(client, false);
			} else {
				start(client);
			}
		}
		if (!running) {
			return;
		}
		if (player == null || client.level == null || player.isDeadOrDying()) {
			stop(client, false);
			return;
		}
		if (stopOnDamage.get() && player.getHealth() < lastHealth - 0.001F) {
			stop(client, true);
			return;
		}
		lastHealth = player.getHealth();

		if (lockView.get()) {
			player.setYRot(lockedYaw);
			player.setXRot(lockedPitch);
		}

		AfkAction current = currentAction();
		KeyMapping key = current.useKey ? client.options.keyUse : client.options.keyAttack;
		if (current.hold) {
			if (heldKey != null && heldKey != key) {
				heldKey.setDown(false);
			}
			heldKey = key;
			key.setDown(true);
			return;
		}
		if (heldKey != null) {
			heldKey.setDown(false);
			heldKey = null;
		}
		if (--ticksUntilClick <= 0) {
			ticksUntilClick = currentInterval();
			// One press of the bound key, exactly what a click produces; the game acts on it next tick.
			KeyMapping.click(KeyBindingHelper.getBoundKeyOf(key));
			clicks++;
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (!running) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		AfkAction current = currentAction();
		Component what = Component.translatable("qolbundle.option.afkaction." + current.name().toLowerCase(Locale.ROOT));
		Component line = current.hold
				? Component.translatable(getTranslationKey() + ".hud.holding", what)
				: Component.translatable(getTranslationKey() + ".hud.clicking", what, currentInterval(), clicks);
		layout.drawLines(context, client.font, position.get(), List.of(line.copy().withColor(0x55FF55)));
	}
}

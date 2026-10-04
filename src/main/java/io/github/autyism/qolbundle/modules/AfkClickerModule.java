package io.github.autyism.qolbundle.modules;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

	private final KeyBinding toggleKey;
	private boolean running;
	private boolean stoppedByDamage;
	private float lockedYaw;
	private float lockedPitch;
	private float lastHealth;
	private int ticksUntilClick;
	private int clicks;
	/** The key this run is holding down, to release exactly that one when it ends. */
	private KeyBinding heldKey;

	public AfkClickerModule() {
		super("afk_clicker", ModuleCategory.GREY, false);
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.afk_toggle",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, QoLBundleClient.KEY_CATEGORY));
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

	public void start(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || running) {
			return;
		}
		running = true;
		stoppedByDamage = false;
		lockedYaw = player.getYaw();
		lockedPitch = player.getPitch();
		lastHealth = player.getHealth();
		ticksUntilClick = 0;
		clicks = 0;
		client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".started"), false);
	}

	public void stop(MinecraftClient client, boolean becauseOfDamage) {
		if (!running) {
			return;
		}
		running = false;
		stoppedByDamage = becauseOfDamage;
		if (heldKey != null) {
			heldKey.setPressed(false);
			heldKey = null;
		}
		if (becauseOfDamage) {
			client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5F));
			client.inGameHud.setOverlayMessage(
					Text.translatable(getTranslationKey() + ".stopped_damage").formatted(Formatting.RED), false);
		} else {
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".stopped"), false);
		}
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled) {
			stop(MinecraftClient.getInstance(), false);
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		while (toggleKey.wasPressed()) {
			if (running) {
				stop(client, false);
			} else {
				start(client);
			}
		}
		if (!running) {
			return;
		}
		if (player == null || client.world == null || player.isDead()) {
			stop(client, false);
			return;
		}
		if (stopOnDamage.get() && player.getHealth() < lastHealth - 0.001F) {
			stop(client, true);
			return;
		}
		lastHealth = player.getHealth();

		if (lockView.get()) {
			player.setYaw(lockedYaw);
			player.setPitch(lockedPitch);
		}

		AfkAction current = currentAction();
		KeyBinding key = current.useKey ? client.options.useKey : client.options.attackKey;
		if (current.hold) {
			if (heldKey != null && heldKey != key) {
				heldKey.setPressed(false);
			}
			heldKey = key;
			key.setPressed(true);
			return;
		}
		if (heldKey != null) {
			heldKey.setPressed(false);
			heldKey = null;
		}
		if (--ticksUntilClick <= 0) {
			ticksUntilClick = currentInterval();
			// One press of the bound key, exactly what a click produces; the game acts on it next tick.
			KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(key));
			clicks++;
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!running) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		AfkAction current = currentAction();
		Text what = Text.translatable("qolbundle.option.afkaction." + current.name().toLowerCase(Locale.ROOT));
		Text line = current.hold
				? Text.translatable(getTranslationKey() + ".hud.holding", what)
				: Text.translatable(getTranslationKey() + ".hud.clicking", what, currentInterval(), clicks);
		layout.drawLines(context, client.textRenderer, position.get(), List.of(line.copy().withColor(0x55FF55)));
	}
}

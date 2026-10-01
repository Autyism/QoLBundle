package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.input.ViewHooks;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.DoubleSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>Freecam: the camera leaves the body and flies around freely while the player stands still.
 * Purely a different viewpoint on what the client already has loaded: the player does not move,
 * nothing extra is requested from the server, and clicking does nothing while it is on.
 * The camera itself is moved in {@link io.github.autyi6969.qolbundle.mixin.CameraMixin}.
 */
public class FreecamModule extends Module {
	private static final double SPRINT_FACTOR = 3.0;

	@Nullable
	private static FreecamModule instance;

	private final DoubleSetting speed = add(new DoubleSetting("speed", 0.5, 0.1, 3.0, 0.1, ""));
	private final BoolSetting exitOnDamage = add(new BoolSetting("exit_on_damage", true));
	private final BoolSetting showHud = add(new BoolSetting("show_hud", true));

	private final KeyBinding toggleKey;
	private boolean active;
	private Vec3d pos = Vec3d.ZERO;
	private Vec3d lastPos = Vec3d.ZERO;
	private float yaw;
	private float pitch;
	private float lastHealth;
	@Nullable
	private ClientWorld world;

	public FreecamModule() {
		super("freecam", ModuleCategory.GREY, false);
		instance = this;
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.freecam_toggle",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, QoLBundleClient.KEY_CATEGORY));
		ViewHooks.register(this::turnCamera);
	}

	/** The module while the free camera is in use, otherwise null. Used by the mixins. */
	@Nullable
	public static FreecamModule current() {
		return instance != null && instance.active ? instance : null;
	}

	public boolean isActive() {
		return active;
	}

	/** Camera position for this frame, smoothed between two ticks. */
	public Vec3d getCameraPos(float tickProgress) {
		return lastPos.lerp(pos, tickProgress);
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public void setActive(MinecraftClient client, boolean on) {
		ClientPlayerEntity player = client.player;
		if (on == active || (on && (player == null || client.world == null))) {
			return;
		}
		active = on;
		if (on) {
			pos = player.getEyePos();
			lastPos = pos;
			yaw = player.getYaw();
			pitch = player.getPitch();
			lastHealth = player.getHealth();
			world = client.world;
		} else {
			world = null;
		}
		client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + (on ? ".on" : ".off")), false);
	}

	private boolean turnCamera(double deltaX, double deltaY) {
		if (!active) {
			return false;
		}
		// Same sensitivity scaling as the game uses for the player.
		yaw += (float) deltaX * 0.15F;
		pitch = MathHelper.clamp(pitch + (float) deltaY * 0.15F, -90F, 90F);
		return true;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled) {
			setActive(MinecraftClient.getInstance(), false);
		}
	}

	@Override
	public void onStartTick(MinecraftClient client) {
		if (!active) {
			return;
		}
		// Out of body: clicks must not hit, place or pick anything where the body happens to look.
		GameOptions options = client.options;
		for (KeyBinding key : new KeyBinding[] {options.attackKey, options.useKey, options.pickItemKey}) {
			while (key.wasPressed()) {
				// discard
			}
			key.setPressed(false);
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		while (toggleKey.wasPressed()) {
			setActive(client, !active);
		}
		if (!active) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world != world || player.isDead()) {
			setActive(client, false);
			return;
		}
		if (exitOnDamage.get() && player.getHealth() < lastHealth - 0.001F) {
			setActive(client, false);
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".off_damage").formatted(Formatting.RED), false);
			return;
		}
		lastHealth = player.getHealth();

		lastPos = pos;
		if (client.currentScreen != null) {
			return; // typing in chat or a menu: keys are not movement
		}
		GameOptions options = client.options;
		double forward = (options.forwardKey.isPressed() ? 1 : 0) - (options.backKey.isPressed() ? 1 : 0);
		double sideways = (options.rightKey.isPressed() ? 1 : 0) - (options.leftKey.isPressed() ? 1 : 0);
		double up = (options.jumpKey.isPressed() ? 1 : 0) - (options.sneakKey.isPressed() ? 1 : 0);
		if (forward == 0 && sideways == 0 && up == 0) {
			return;
		}
		// Yaw 0 looks towards +Z; "right" of that is -X.
		double yawRadians = Math.toRadians(yaw);
		Vec3d move = new Vec3d(-Math.sin(yawRadians) * forward - Math.cos(yawRadians) * sideways,
				up,
				Math.cos(yawRadians) * forward - Math.sin(yawRadians) * sideways);
		if (move.lengthSquared() > 1.0) {
			move = move.normalize();
		}
		double pace = speed.get() * (options.sprintKey.isPressed() ? SPRINT_FACTOR : 1.0);
		pos = pos.add(move.multiply(pace));
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!active || !showHud.get()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int distance = (int) Math.round(pos.distanceTo(client.player.getEyePos()));
		Text text = Text.translatable(getTranslationKey() + ".hud", toggleKey.getBoundKeyLocalizedText(), distance);
		int width = client.textRenderer.getWidth(text);
		int x = (layout.getScreenWidth() - width) / 2;
		context.fill(x - 3, 4, x + width + 3, 16, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, text, x, 6, 0xFFFFAA00);
	}
}

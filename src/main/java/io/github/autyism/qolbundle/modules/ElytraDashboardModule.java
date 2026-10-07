package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flight instruments while gliding with an elytra: speed, pitch, height above ground, rockets
 * left, elytra durability as flying time, and where you will touch down if you keep looking the
 * way you look now.
 */
public class ElytraDashboardModule extends Module {
	public enum DashboardPosition {
		BELOW_CROSSHAIR,
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT
	}

	private static final int PREDICT_EVERY_TICKS = 4;
	/** Stop predicting after one minute of simulated flight. */
	private static final int MAX_SIMULATED_TICKS = 20 * 60;

	private final EnumSetting<DashboardPosition> position = add(new EnumSetting<>("position", DashboardPosition.BELOW_CROSSHAIR));
	private final BoolSetting showSpeed = add(new BoolSetting("show_speed", true));
	private final BoolSetting showPitch = add(new BoolSetting("show_pitch", true));
	private final BoolSetting showHeight = add(new BoolSetting("show_height", true));
	private final BoolSetting showRockets = add(new BoolSetting("show_rockets", true));
	private final IntSetting rocketWarning = add(new IntSetting("rocket_warning", 8, 0, 64));
	private final BoolSetting showElytra = add(new BoolSetting("show_elytra", true));
	private final BoolSetting showLanding = add(new BoolSetting("show_landing", true));
	private final BoolSetting landingMarker = add(new BoolSetting("landing_marker", true));

	private boolean active;
	private double speed;
	private double verticalSpeed;
	private float pitch;
	private int heightAboveGround;
	private int rockets;
	private int elytraSeconds;
	@Nullable
	private Vec3 landing;
	private int landingTicks;
	private boolean landingOutOfRange;
	private int ticks;

	public ElytraDashboardModule() {
		super("elytra_dashboard", ModuleCategory.TOOLS, true);
	}

	/** True while the player is gliding and the dashboard is shown (for the self-test). */
	public boolean isActive() {
		return active;
	}

	/** Blocks per second. */
	public double getSpeed() {
		return speed;
	}

	public int getRockets() {
		return rockets;
	}

	@Nullable
	public Vec3 getLanding() {
		return landing;
	}

	/** True when the flight path leaves the loaded chunks before touching down. */
	public boolean isLandingOutOfRange() {
		return landingOutOfRange;
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		active = player != null && client.level != null && player.isFallFlying();
		if (!active) {
			landing = null;
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		speed = velocity.length() * 20.0;
		verticalSpeed = velocity.y * 20.0;
		pitch = player.getXRot();
		int ground = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
		heightAboveGround = Math.max(0, Mth.floor(player.getY()) - ground);
		rockets = countRockets(player.getInventory());
		elytraSeconds = elytraSecondsLeft(player.getItemBySlot(EquipmentSlot.CHEST));
		if (ticks++ % PREDICT_EVERY_TICKS == 0) {
			predictLanding(client.level, player);
		}
	}

	private static int countRockets(Inventory inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.FIREWORK_ROCKET)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** An elytra loses one durability point per second of flight and stops working at 1 left. */
	private static int elytraSecondsLeft(ItemStack chest) {
		if (!chest.is(Items.ELYTRA) || !chest.isDamageableItem()) {
			return -1;
		}
		return Math.max(0, chest.getMaxDamage() - chest.getDamageValue() - 1);
	}

	/**
	 * Flies an imaginary copy of the player forward with the game's own gliding formula
	 * (LivingEntity.calcGlidingVelocity), keeping the current look direction, until it hits something.
	 */
	private void predictLanding(ClientLevel world, LocalPlayer player) {
		landing = null;
		landingOutOfRange = false;
		Vec3 look = player.getLookAngle();
		float pitchRadians = player.getXRot() * (float) (Math.PI / 180.0);
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double cosSquared = Mth.square(Math.cos(pitchRadians));
		boolean slowFalling = player.hasEffect(MobEffects.SLOW_FALLING);
		double fullGravity = player.getGravity();

		Vec3 pos = player.position();
		Vec3 velocity = player.getDeltaMovement();
		for (int tick = 1; tick <= MAX_SIMULATED_TICKS; tick++) {
			double horizontal = velocity.horizontalDistance();
			double gravity = slowFalling && velocity.y <= 0.0 ? Math.min(fullGravity, 0.01) : fullGravity;
			velocity = velocity.add(0.0, gravity * (-1.0 + cosSquared * 0.75), 0.0);
			if (velocity.y < 0.0 && lookHorizontal > 0.0) {
				double lift = velocity.y * -0.1 * cosSquared;
				velocity = velocity.add(look.x * lift / lookHorizontal, lift, look.z * lift / lookHorizontal);
			}
			if (pitchRadians < 0.0F && lookHorizontal > 0.0) {
				double climb = horizontal * -Mth.sin(pitchRadians) * 0.04;
				velocity = velocity.add(-look.x * climb / lookHorizontal, climb * 3.2, -look.z * climb / lookHorizontal);
			}
			if (lookHorizontal > 0.0) {
				velocity = velocity.add((look.x / lookHorizontal * horizontal - velocity.x) * 0.1, 0.0,
						(look.z / lookHorizontal * horizontal - velocity.z) * 0.1);
			}
			velocity = velocity.multiply(0.99F, 0.98F, 0.99F);

			Vec3 next = pos.add(velocity);
			if (next.y < world.getMinY() - 8
					|| !world.getChunkSource().hasChunk(Mth.floor(next.x) >> 4, Mth.floor(next.z) >> 4)) {
				// Past what the client has loaded: all that can be said is "further than this".
				landing = pos;
				landingTicks = tick;
				landingOutOfRange = true;
				return;
			}
			BlockHitResult hit = world.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.ANY, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				landing = hit.getLocation();
				landingTicks = tick;
				return;
			}
			pos = next;
		}
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Vec3 target = landing;
		if (!active || target == null || landingOutOfRange || !showLanding.get() || !landingMarker.get()) {
			return;
		}
		// A ring on the ground with a short post, so the spot is easy to pick out from the air.
		int color = 0xFFFF5555;
		Gizmos.circle(target.add(0, 0.1, 0), 1.5F, GizmoStyle.stroke(color, 3.0F));
		Gizmos.line(target, target.add(0, 4, 0), color, 3.0F);
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (!active) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		List<Component> lines = new ArrayList<>();

		List<Component> flight = new ArrayList<>();
		if (showSpeed.get()) {
			flight.add(Component.translatable(key + "speed", String.format(Locale.ROOT, "%.1f", speed),
					String.format(Locale.ROOT, "%+.1f", verticalSpeed)));
		}
		if (showPitch.get()) {
			// The game counts looking down as positive pitch; pilots expect "up" to be positive.
			flight.add(Component.translatable(key + "pitch", String.format(Locale.ROOT, "%+.0f", -pitch)));
		}
		if (showHeight.get()) {
			flight.add(Component.translatable(key + "height", heightAboveGround));
		}
		addJoined(lines, flight);

		List<Component> supplies = new ArrayList<>();
		if (showRockets.get()) {
			MutableComponent text = Component.translatable(key + "rockets", rockets);
			if (rockets == 0) {
				text = text.withColor(0xFF5555);
			} else if (rockets <= rocketWarning.get()) {
				text = text.withColor(0xFFFF55);
			}
			supplies.add(text);
		}
		if (showElytra.get() && elytraSeconds >= 0) {
			MutableComponent text = Component.translatable(key + "elytra", String.format(Locale.ROOT, "%d:%02d", elytraSeconds / 60, elytraSeconds % 60));
			if (elytraSeconds <= 30) {
				text = text.withColor(0xFF5555);
			} else if (elytraSeconds <= 90) {
				text = text.withColor(0xFFFF55);
			}
			supplies.add(text);
		}
		addJoined(lines, supplies);

		Vec3 target = landing;
		if (showLanding.get() && target != null) {
			int distance = (int) Math.round(Math.hypot(target.x - client.player.getX(), target.z - client.player.getZ()));
			String seconds = String.format(Locale.ROOT, "%.0f", landingTicks / 20.0);
			if (landingOutOfRange) {
				lines.add(Component.translatable(key + "landing_far", distance).withColor(0xAAAAAA));
			} else {
				lines.add(Component.translatable(key + "landing", Mth.floor(target.x), Mth.floor(target.y),
						Mth.floor(target.z), distance, seconds));
			}
		}
		if (lines.isEmpty()) {
			return;
		}

		DashboardPosition where = position.get();
		if (where != DashboardPosition.BELOW_CROSSHAIR) {
			layout.drawLines(context, client.font, HudAnchor.valueOf(where.name()), lines);
			return;
		}
		int y = layout.getScreenHeight() / 2 + 44;
		for (Component line : lines) {
			int width = client.font.width(line);
			int x = (layout.getScreenWidth() - width) / 2;
			context.fill(x - 3, y - 1, x + width + 3, y + 9, 0x80000000);
			context.drawString(client.font, line, x, y, HudLayout.WHITE);
			y += HudLayout.LINE_HEIGHT;
		}
	}

	/** Puts several short readings on one line, separated by spaces. */
	private static void addJoined(List<Component> lines, List<Component> parts) {
		if (parts.isEmpty()) {
			return;
		}
		MutableComponent joined = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				joined.append("   ");
			}
			joined.append(parts.get(i));
		}
		lines.add(joined);
	}
}

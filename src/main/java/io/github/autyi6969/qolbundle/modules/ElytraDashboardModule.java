package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudAnchor;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
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
	private Vec3d landing;
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
	public Vec3d getLanding() {
		return landing;
	}

	/** True when the flight path leaves the loaded chunks before touching down. */
	public boolean isLandingOutOfRange() {
		return landingOutOfRange;
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		active = player != null && client.world != null && player.isGliding();
		if (!active) {
			landing = null;
			return;
		}
		Vec3d velocity = player.getVelocity();
		speed = velocity.length() * 20.0;
		verticalSpeed = velocity.y * 20.0;
		pitch = player.getPitch();
		int ground = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
		heightAboveGround = Math.max(0, MathHelper.floor(player.getY()) - ground);
		rockets = countRockets(player.getInventory());
		elytraSeconds = elytraSecondsLeft(player.getEquippedStack(EquipmentSlot.CHEST));
		if (ticks++ % PREDICT_EVERY_TICKS == 0) {
			predictLanding(client.world, player);
		}
	}

	private static int countRockets(PlayerInventory inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isOf(Items.FIREWORK_ROCKET)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** An elytra loses one durability point per second of flight and stops working at 1 left. */
	private static int elytraSecondsLeft(ItemStack chest) {
		if (!chest.isOf(Items.ELYTRA) || !chest.isDamageable()) {
			return -1;
		}
		return Math.max(0, chest.getMaxDamage() - chest.getDamage() - 1);
	}

	/**
	 * Flies an imaginary copy of the player forward with the game's own gliding formula
	 * (LivingEntity.calcGlidingVelocity), keeping the current look direction, until it hits something.
	 */
	private void predictLanding(ClientWorld world, ClientPlayerEntity player) {
		landing = null;
		landingOutOfRange = false;
		Vec3d look = player.getRotationVector();
		float pitchRadians = player.getPitch() * (float) (Math.PI / 180.0);
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double cosSquared = MathHelper.square(Math.cos(pitchRadians));
		boolean slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING);
		double fullGravity = player.getFinalGravity();

		Vec3d pos = player.getEntityPos();
		Vec3d velocity = player.getVelocity();
		for (int tick = 1; tick <= MAX_SIMULATED_TICKS; tick++) {
			double horizontal = velocity.horizontalLength();
			double gravity = slowFalling && velocity.y <= 0.0 ? Math.min(fullGravity, 0.01) : fullGravity;
			velocity = velocity.add(0.0, gravity * (-1.0 + cosSquared * 0.75), 0.0);
			if (velocity.y < 0.0 && lookHorizontal > 0.0) {
				double lift = velocity.y * -0.1 * cosSquared;
				velocity = velocity.add(look.x * lift / lookHorizontal, lift, look.z * lift / lookHorizontal);
			}
			if (pitchRadians < 0.0F && lookHorizontal > 0.0) {
				double climb = horizontal * -MathHelper.sin(pitchRadians) * 0.04;
				velocity = velocity.add(-look.x * climb / lookHorizontal, climb * 3.2, -look.z * climb / lookHorizontal);
			}
			if (lookHorizontal > 0.0) {
				velocity = velocity.add((look.x / lookHorizontal * horizontal - velocity.x) * 0.1, 0.0,
						(look.z / lookHorizontal * horizontal - velocity.z) * 0.1);
			}
			velocity = velocity.multiply(0.99F, 0.98F, 0.99F);

			Vec3d next = pos.add(velocity);
			if (next.y < world.getBottomY() - 8
					|| !world.getChunkManager().isChunkLoaded(MathHelper.floor(next.x) >> 4, MathHelper.floor(next.z) >> 4)) {
				// Past what the client has loaded: all that can be said is "further than this".
				landing = pos;
				landingTicks = tick;
				landingOutOfRange = true;
				return;
			}
			BlockHitResult hit = world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.ANY, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				landing = hit.getPos();
				landingTicks = tick;
				return;
			}
			pos = next;
		}
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Vec3d target = landing;
		if (!active || target == null || landingOutOfRange || !showLanding.get() || !landingMarker.get()) {
			return;
		}
		// A ring on the ground with a short post, so the spot is easy to pick out from the air.
		int color = 0xFFFF5555;
		GizmoDrawing.circle(target.add(0, 0.1, 0), 1.5F, DrawStyle.stroked(color, 3.0F));
		GizmoDrawing.line(target, target.add(0, 4, 0), color, 3.0F);
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!active) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		List<Text> lines = new ArrayList<>();

		List<Text> flight = new ArrayList<>();
		if (showSpeed.get()) {
			flight.add(Text.translatable(key + "speed", String.format(Locale.ROOT, "%.1f", speed),
					String.format(Locale.ROOT, "%+.1f", verticalSpeed)));
		}
		if (showPitch.get()) {
			// The game counts looking down as positive pitch; pilots expect "up" to be positive.
			flight.add(Text.translatable(key + "pitch", String.format(Locale.ROOT, "%+.0f", -pitch)));
		}
		if (showHeight.get()) {
			flight.add(Text.translatable(key + "height", heightAboveGround));
		}
		addJoined(lines, flight);

		List<Text> supplies = new ArrayList<>();
		if (showRockets.get()) {
			MutableText text = Text.translatable(key + "rockets", rockets);
			if (rockets == 0) {
				text = text.withColor(0xFF5555);
			} else if (rockets <= rocketWarning.get()) {
				text = text.withColor(0xFFFF55);
			}
			supplies.add(text);
		}
		if (showElytra.get() && elytraSeconds >= 0) {
			MutableText text = Text.translatable(key + "elytra", String.format(Locale.ROOT, "%d:%02d", elytraSeconds / 60, elytraSeconds % 60));
			if (elytraSeconds <= 30) {
				text = text.withColor(0xFF5555);
			} else if (elytraSeconds <= 90) {
				text = text.withColor(0xFFFF55);
			}
			supplies.add(text);
		}
		addJoined(lines, supplies);

		Vec3d target = landing;
		if (showLanding.get() && target != null) {
			int distance = (int) Math.round(Math.hypot(target.x - client.player.getX(), target.z - client.player.getZ()));
			String seconds = String.format(Locale.ROOT, "%.0f", landingTicks / 20.0);
			if (landingOutOfRange) {
				lines.add(Text.translatable(key + "landing_far", distance).withColor(0xAAAAAA));
			} else {
				lines.add(Text.translatable(key + "landing", MathHelper.floor(target.x), MathHelper.floor(target.y),
						MathHelper.floor(target.z), distance, seconds));
			}
		}
		if (lines.isEmpty()) {
			return;
		}

		DashboardPosition where = position.get();
		if (where != DashboardPosition.BELOW_CROSSHAIR) {
			layout.drawLines(context, client.textRenderer, HudAnchor.valueOf(where.name()), lines);
			return;
		}
		int y = layout.getScreenHeight() / 2 + 44;
		for (Text line : lines) {
			int width = client.textRenderer.getWidth(line);
			int x = (layout.getScreenWidth() - width) / 2;
			context.fill(x - 3, y - 1, x + width + 3, y + 9, 0x80000000);
			context.drawTextWithShadow(client.textRenderer, line, x, y, HudLayout.WHITE);
			y += HudLayout.LINE_HEIGHT;
		}
	}

	/** Puts several short readings on one line, separated by spaces. */
	private static void addJoined(List<Text> lines, List<Text> parts) {
		if (parts.isEmpty()) {
			return;
		}
		MutableText joined = Text.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				joined.append("   ");
			}
			joined.append(parts.get(i));
		}
		lines.add(joined);
	}
}

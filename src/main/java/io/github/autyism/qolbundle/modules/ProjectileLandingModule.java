package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * While you hold something throwable (ender pearl, snowball, egg, splash / lingering potion,
 * bottle o' enchanting): draws the arc it will fly and where it lands. Potions also show how far
 * their effect reaches; pearls get a traffic light that tells whether the throw is a good one.
 * Uses the game's own numbers for throw speed, gravity and drag.
 */
public class ProjectileLandingModule extends Module {
	public enum Light {
		/** Lands on top of a block. */
		GREEN(0x55FF55),
		/** Hits a wall. */
		YELLOW(0xFFFF55),
		/** Hits a ceiling, or drops right at your feet. */
		RED(0xFF5555);

		final int rgb;

		Light(int rgb) {
			this.rgb = rgb;
		}
	}

	/** How one kind of throwable flies: power, upward tilt in degrees, gravity per tick, effect radius. */
	private record Kind(float power, float tilt, double gravity, float effectRadius, boolean pearl) {
	}

	private static final Kind PEARL = new Kind(1.5F, 0F, 0.03, 0F, true);
	private static final Kind SMALL = new Kind(1.5F, 0F, 0.03, 0F, false);
	private static final Kind SPLASH = new Kind(0.5F, -20F, 0.05, 4F, false);
	private static final Kind LINGERING = new Kind(0.5F, -20F, 0.05, 3F, false);
	private static final Kind XP_BOTTLE = new Kind(0.7F, -20F, 0.07, 0F, false);
	private static final int MAX_TICKS = 300;
	private static final double TOO_CLOSE = 2.5;

	private final BoolSetting showArc = add(new BoolSetting("show_arc", true));
	private final BoolSetting showLanding = add(new BoolSetting("show_landing", true));
	private final BoolSetting showRadius = add(new BoolSetting("show_radius", true));
	private final BoolSetting showHud = add(new BoolSetting("show_hud", true));

	private final List<Vec3> path = new ArrayList<>();
	@Nullable
	private Kind kind;
	@Nullable
	private Vec3 landing;
	private Light light = Light.GREEN;

	public ProjectileLandingModule() {
		super("projectile_landing", ModuleCategory.INFO, true);
	}

	/** Predicted landing point, null when nothing throwable is held or it flies out of the loaded world. */
	@Nullable
	public Vec3 getLanding() {
		return landing;
	}

	public Light getLight() {
		return light;
	}

	public boolean isHoldingThrowable() {
		return kind != null;
	}

	@Nullable
	private static Kind kindOf(ItemStack stack) {
		if (stack.is(Items.ENDER_PEARL)) {
			return PEARL;
		}
		if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.BLUE_EGG) || stack.is(Items.BROWN_EGG)) {
			return SMALL;
		}
		if (stack.is(Items.SPLASH_POTION)) {
			return SPLASH;
		}
		if (stack.is(Items.LINGERING_POTION)) {
			return LINGERING;
		}
		if (stack.is(Items.EXPERIENCE_BOTTLE)) {
			return XP_BOTTLE;
		}
		return null;
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		path.clear();
		landing = null;
		kind = null;
		if (player == null || client.level == null) {
			return;
		}
		kind = kindOf(player.getMainHandItem());
		if (kind == null) {
			kind = kindOf(player.getOffhandItem());
		}
		if (kind != null) {
			simulate(client.level, player, kind);
		}
	}

	/** The same steps the game takes for a thrown item each tick: gravity, drag, then move. */
	private void simulate(ClientLevel world, LocalPlayer player, Kind thrown) {
		float yaw = player.getYRot() * (float) (Math.PI / 180.0);
		float pitch = player.getXRot() * (float) (Math.PI / 180.0);
		float tilted = (player.getXRot() + thrown.tilt) * (float) (Math.PI / 180.0);
		Vec3 velocity = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(tilted),
				Mth.cos(yaw) * Mth.cos(pitch)).normalize().scale(thrown.power);
		Vec3 own = player.getKnownMovement();
		velocity = velocity.add(own.x, player.onGround() ? 0.0 : own.y, own.z);

		Vec3 pos = new Vec3(player.getX(), player.getEyeY() - 0.1, player.getZ());
		Vec3 start = pos;
		path.add(pos);
		Direction side = null;
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			velocity = velocity.add(0.0, -thrown.gravity, 0.0);
			boolean inWater = world.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
			velocity = velocity.scale(inWater ? 0.8F : 0.99F);
			Vec3 next = pos.add(velocity);
			if (next.y < world.getMinY() - 8
					|| !world.getChunkSource().hasChunk(Mth.floor(next.x) >> 4, Mth.floor(next.z) >> 4)) {
				return; // leaves the loaded world: no landing point to show
			}
			BlockHitResult hit = world.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				landing = hit.getLocation();
				side = hit.getDirection();
				path.add(landing);
				break;
			}
			path.add(next);
			pos = next;
		}
		if (landing == null) {
			return;
		}
		double horizontal = Math.hypot(landing.x - start.x, landing.z - start.z);
		if (side == Direction.DOWN || horizontal < TOO_CLOSE) {
			light = Light.RED;
		} else if (side != Direction.UP) {
			light = Light.YELLOW;
		} else {
			light = Light.GREEN;
		}
	}

	private int color() {
		return 0xFF000000 | (kind != null && kind.pearl ? light.rgb : 0xFFFFFF);
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Kind thrown = kind;
		if (thrown == null || path.size() < 3) {
			return;
		}
		int color = color();
		if (showArc.get()) {
			// The first stretch starts inside the player's head; skip it so it does not cover the view.
			for (int i = 2; i < path.size(); i++) {
				Gizmos.line(path.get(i - 1), path.get(i), color, 2.0F);
			}
		}
		Vec3 target = landing;
		if (target == null) {
			return;
		}
		if (showLanding.get()) {
			Gizmos.cuboid(AABB.ofSize(target, 0.4, 0.4, 0.4), GizmoStyle.stroke(color, 2.5F));
		}
		if (showRadius.get() && thrown.effectRadius > 0F) {
			Gizmos.circle(target.add(0, 0.1, 0), thrown.effectRadius, GizmoStyle.stroke(0xFFFF55FF, 2.5F));
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Kind thrown = kind;
		Vec3 target = landing;
		if (thrown == null || !showHud.get()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		Component text;
		int color = HudLayout.WHITE;
		if (target == null) {
			text = Component.translatable(key + "out_of_range");
			color = 0xFFAAAAAA;
		} else {
			int distance = (int) Math.round(Math.hypot(target.x - client.player.getX(), target.z - client.player.getZ()));
			if (thrown.pearl) {
				text = Component.translatable(key + "pearl_" + light.name().toLowerCase(java.util.Locale.ROOT), distance);
				color = 0xFF000000 | light.rgb;
			} else {
				text = Component.translatable(key + "distance", distance);
			}
		}
		int width = client.font.width(text);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 + 16;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawString(client.font, text, x, y, color);
	}
}

package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where was I shot from? The moment an arrow (or trident, snowball, fireball ...) hits the player,
 * a red arrow at the crosshair points to where it came from, for a few seconds.
 *
 * <p>Worked out from the projectile that was flying at the player just before the hit: back along
 * its flight path. Nothing is known about a shooter who is not in view; only the direction of the
 * shot is shown.
 */
public class ProjectileDirectionModule extends Module {
	/** A projectile that was near the player, as last seen. */
	private record Seen(Vec3 pos, Vec3 velocity, Component name, long tick) {
	}

	private static final double WATCH_RANGE = 8.0;
	private static final int RECENT_TICKS = 5;
	private static final int RING = 50;
	private static final int COLOR = 0xFFFF3030;

	private final IntSetting seconds = add(new IntSetting("seconds", 3, 1, 10, " s"));

	private final Map<Integer, Seen> seen = new HashMap<>();
	private long tick;
	private float lastHealth;
	@Nullable
	private Vec3 source;
	@Nullable
	private Component sourceName;
	private long showUntil;
	private int hits;

	public ProjectileDirectionModule() {
		super("projectile_direction", ModuleCategory.PVP, true);
	}

	/** A point in the direction the last shot came from; null when nothing is shown. */
	@Nullable
	public Vec3 getSource() {
		return tick < showUntil ? source : null;
	}

	public int getHits() {
		return hits;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		seen.clear();
		source = null;
		showUntil = 0;
	}

	@Override
	public void onTick(Minecraft client) {
		tick++;
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			seen.clear();
			return;
		}
		AABB near = player.getBoundingBox().inflate(WATCH_RANGE);
		for (Projectile projectile : client.level.getEntitiesOfClass(Projectile.class, near, entity -> true)) {
			Entity owner = projectile.getOwner();
			Vec3 velocity = projectile.getDeltaMovement();
			if (owner == player || velocity.lengthSqr() < 0.01) {
				continue; // my own, or lying / stuck somewhere
			}
			// Keep the velocity it had in full flight: on hitting something the client's copy of an
			// arrow bounces back slowly, and that last velocity would point the wrong way.
			Seen before = seen.get(projectile.getId());
			if (before != null && before.velocity.lengthSqr() > velocity.lengthSqr()) {
				velocity = before.velocity;
			}
			seen.put(projectile.getId(), new Seen(projectile.position(), velocity, projectile.getName(), tick));
		}
		seen.values().removeIf(entry -> tick - entry.tick > RECENT_TICKS);

		if (player.getHealth() < lastHealth - 0.001F && !seen.isEmpty()) {
			// Hurt while something was flying at me: the closest of them is what hit.
			Seen closest = null;
			for (Seen entry : seen.values()) {
				if (closest == null || entry.pos.distanceToSqr(player.getEyePosition()) < closest.pos.distanceToSqr(player.getEyePosition())) {
					closest = entry;
				}
			}
			if (closest.pos.distanceToSqr(player.getEyePosition()) < 5.0 * 5.0) {
				// Back along the way it flew.
				source = closest.pos.subtract(closest.velocity.normalize().scale(24.0));
				sourceName = closest.name;
				showUntil = tick + seconds.get() * 20L;
				hits++;
				seen.clear();
			}
		}
		lastHealth = player.getHealth();
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Vec3 from = getSource();
		if (from == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(client.gameRenderer.getMainCamera().position(),
				client.gameRenderer.getMainCamera().yRot(), from));
		context.pose().pushMatrix();
		context.pose().translate(centerX, centerY);
		context.pose().rotate(angle);
		for (int row = 0; row < 10; row++) {
			context.fill(-row, -RING + row, row + 1, -RING + row + 1, COLOR);
		}
		context.pose().popMatrix();

		Component label = Component.translatable(getTranslationKey() + ".hud", sourceName);
		int width = client.font.width(label);
		int labelRadius = RING + 12;
		int x = centerX + Math.round(Mth.sin(angle) * (labelRadius + width / 2F)) - width / 2;
		int y = centerY - Math.round(Mth.cos(angle) * labelRadius) - 4;
		x = Mth.clamp(x, 2, layout.getScreenWidth() - width - 2);
		context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
		context.drawString(client.font, label, x, y, COLOR);
	}
}

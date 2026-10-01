package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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
	private record Seen(Vec3d pos, Vec3d velocity, Text name, long tick) {
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
	private Vec3d source;
	@Nullable
	private Text sourceName;
	private long showUntil;
	private int hits;

	public ProjectileDirectionModule() {
		super("projectile_direction", ModuleCategory.PVP, true);
	}

	/** A point in the direction the last shot came from; null when nothing is shown. */
	@Nullable
	public Vec3d getSource() {
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
	public void onTick(MinecraftClient client) {
		tick++;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			seen.clear();
			return;
		}
		Box near = player.getBoundingBox().expand(WATCH_RANGE);
		for (ProjectileEntity projectile : client.world.getEntitiesByClass(ProjectileEntity.class, near, entity -> true)) {
			Entity owner = projectile.getOwner();
			Vec3d velocity = projectile.getVelocity();
			if (owner == player || velocity.lengthSquared() < 0.01) {
				continue; // my own, or lying / stuck somewhere
			}
			// Keep the velocity it had in full flight: on hitting something the client's copy of an
			// arrow bounces back slowly, and that last velocity would point the wrong way.
			Seen before = seen.get(projectile.getId());
			if (before != null && before.velocity.lengthSquared() > velocity.lengthSquared()) {
				velocity = before.velocity;
			}
			seen.put(projectile.getId(), new Seen(projectile.getEntityPos(), velocity, projectile.getName(), tick));
		}
		seen.values().removeIf(entry -> tick - entry.tick > RECENT_TICKS);

		if (player.getHealth() < lastHealth - 0.001F && !seen.isEmpty()) {
			// Hurt while something was flying at me: the closest of them is what hit.
			Seen closest = null;
			for (Seen entry : seen.values()) {
				if (closest == null || entry.pos.squaredDistanceTo(player.getEyePos()) < closest.pos.squaredDistanceTo(player.getEyePos())) {
					closest = entry;
				}
			}
			if (closest.pos.squaredDistanceTo(player.getEyePos()) < 5.0 * 5.0) {
				// Back along the way it flew.
				source = closest.pos.subtract(closest.velocity.normalize().multiply(24.0));
				sourceName = closest.name;
				showUntil = tick + seconds.get() * 20L;
				hits++;
				seen.clear();
			}
		}
		lastHealth = player.getHealth();
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Vec3d from = getSource();
		if (from == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(client.gameRenderer.getCamera().getCameraPos(),
				client.gameRenderer.getCamera().getYaw(), from));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(centerX, centerY);
		context.getMatrices().rotate(angle);
		for (int row = 0; row < 10; row++) {
			context.fill(-row, -RING + row, row + 1, -RING + row + 1, COLOR);
		}
		context.getMatrices().popMatrix();

		Text label = Text.translatable(getTranslationKey() + ".hud", sourceName);
		int width = client.textRenderer.getWidth(label);
		int labelRadius = RING + 12;
		int x = centerX + Math.round(MathHelper.sin(angle) * (labelRadius + width / 2F)) - width / 2;
		int y = centerY - Math.round(MathHelper.cos(angle) * labelRadius) - 4;
		x = MathHelper.clamp(x, 2, layout.getScreenWidth() - width - 2);
		context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
		context.drawTextWithShadow(client.textRenderer, label, x, y, COLOR);
	}
}

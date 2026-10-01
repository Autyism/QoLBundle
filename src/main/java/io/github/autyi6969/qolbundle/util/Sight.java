package io.github.autyi6969.qolbundle.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * "Who can I see?" for the PvP modules. Everything they report is about players the local player
 * has an unobstructed line of sight to; a player behind a wall does not exist for them. That is the
 * line between reading what is in front of you and a wall hack, and it lives here in one place.
 *
 * <p>"Players" means players and the game's player-shaped figures (mannequins).
 */
public final class Sight {
	/** Half the angle of the cone that counts as "in front of the camera". */
	private static final double FIELD_OF_VIEW_HALF_ANGLE = 60.0;

	private Sight() {
	}

	/** Other players within range that the local player could see by turning the head: nothing solid in between. */
	public static List<PlayerLikeEntity> visiblePlayers(MinecraftClient client, double range) {
		List<PlayerLikeEntity> result = new ArrayList<>();
		ClientPlayerEntity self = client.player;
		if (self == null || client.world == null) {
			return result;
		}
		for (Entity entity : client.world.getEntities()) {
			if (entity instanceof PlayerLikeEntity other && other != self && other.isAlive() && !other.isSpectator()
					&& !other.isInvisibleTo(self) && other.squaredDistanceTo(self) <= range * range && self.canSee(other)) {
				result.add(other);
			}
		}
		return result;
	}

	/** Degrees between where the camera looks and the direction to the entity's chest. */
	public static double angleFromCrosshair(MinecraftClient client, Entity entity) {
		Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
		Vec3d to = entity.getBoundingBox().getCenter().subtract(eye);
		if (to.lengthSquared() < 1.0E-6) {
			return 0;
		}
		Vec3d look = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw());
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(to.normalize())))));
	}

	/** True when the entity is in front of the camera (roughly: on the screen). */
	public static boolean inFieldOfView(MinecraftClient client, Entity entity) {
		return angleFromCrosshair(client, entity) <= FIELD_OF_VIEW_HALF_ANGLE;
	}
}

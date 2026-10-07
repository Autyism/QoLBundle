package io.github.autyism.qolbundle.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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
	public static List<Avatar> visiblePlayers(Minecraft client, double range) {
		List<Avatar> result = new ArrayList<>();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			return result;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Avatar other && other != self && other.isAlive() && !other.isSpectator()
					&& !other.isInvisibleTo(self) && other.distanceToSqr(self) <= range * range && self.hasLineOfSight(other)) {
				result.add(other);
			}
		}
		return result;
	}

	/** Degrees between where the camera looks and the direction to the entity's chest. */
	public static double angleFromCrosshair(Minecraft client, Entity entity) {
		Vec3 eye = client.gameRenderer.getMainCamera().position();
		Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
		if (to.lengthSqr() < 1.0E-6) {
			return 0;
		}
		Vec3 look = Vec3.directionFromRotation(client.gameRenderer.getMainCamera().xRot(), client.gameRenderer.getMainCamera().yRot());
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())))));
	}

	/** True when the entity is in front of the camera (roughly: on the screen). */
	public static boolean inFieldOfView(Minecraft client, Entity entity) {
		return angleFromCrosshair(client, entity) <= FIELD_OF_VIEW_HALF_ANGLE;
	}
}

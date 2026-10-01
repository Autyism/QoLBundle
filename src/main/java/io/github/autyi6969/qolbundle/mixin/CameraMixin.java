package io.github.autyi6969.qolbundle.mixin;

import io.github.autyi6969.qolbundle.modules.FreecamModule;
import io.github.autyi6969.qolbundle.modules.RearMirrorModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: after the game has placed the camera at the player's eyes, move it to the free
 * camera's position instead. Marking it "third person" makes the game draw the player's own body;
 * that waits until the camera is a block away, so you never look at the inside of your own head.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("TAIL"))
	private void qolbundle$freecam(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		FreecamModule freecam = FreecamModule.current();
		if (freecam != null) {
			Vec3d pos = freecam.getCameraPos(tickProgress);
			this.setRotation(freecam.getYaw(), freecam.getPitch());
			this.setPos(pos.x, pos.y, pos.z);
			this.thirdPerson = pos.squaredDistanceTo(focusedEntity.getCameraPosVec(tickProgress)) > 1.0;
		}
		if (RearMirrorModule.isDrawingRear()) {
			// The view for the rear mirror: turned right round, and kept near the horizon so the
			// mirror does not show only floor when the player looks down.
			this.setRotation(this.getYaw() + 180F, MathHelper.clamp(this.getPitch(), -25F, 25F));
		}
	}
}

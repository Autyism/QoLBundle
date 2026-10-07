package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FreecamModule;
import io.github.autyism.qolbundle.modules.RearMirrorModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
	private boolean detached;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void qolbundle$freecam(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		FreecamModule freecam = FreecamModule.current();
		if (freecam != null) {
			Vec3 pos = freecam.getCameraPos(tickProgress);
			this.setRotation(freecam.getYaw(), freecam.getPitch());
			this.setPosition(pos.x, pos.y, pos.z);
			this.detached = pos.distanceToSqr(focusedEntity.getEyePosition(tickProgress)) > 1.0;
		}
		if (RearMirrorModule.isDrawingRear()) {
			// The view for the rear mirror: turned right round, and kept near the horizon so the
			// mirror does not show only floor when the player looks down.
			this.setRotation(this.yRot() + 180F, Mth.clamp(this.xRot(), -25F, 25F));
		}
	}
}

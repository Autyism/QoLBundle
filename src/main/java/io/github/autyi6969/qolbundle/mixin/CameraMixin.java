package io.github.autyi6969.qolbundle.mixin;

import io.github.autyi6969.qolbundle.modules.FreecamModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: after the game has placed the camera at the player's eyes, move it to the free
 * camera's position instead. Marking it "third person" makes the game draw the player's own body
 * and hide the first-person hand.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean thirdPerson;

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At("TAIL"))
	private void qolbundle$freecam(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		FreecamModule freecam = FreecamModule.current();
		if (freecam != null) {
			Vec3d pos = freecam.getCameraPos(tickProgress);
			this.setRotation(freecam.getYaw(), freecam.getPitch());
			this.setPos(pos.x, pos.y, pos.z);
			this.thirdPerson = true;
		}
	}
}

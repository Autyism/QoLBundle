package io.github.autyism.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.autyism.qolbundle.modules.FreecamModule;
import io.github.autyism.qolbundle.modules.RearMirrorModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/**
	 * Freecam and rear mirror: the first-person hand belongs to the body looking forward, so it is
	 * not drawn for the free camera or in the view backwards.
	 */
	@ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean qolbundle$noHandOutOfBody(boolean firstPerson) {
		return firstPerson && FreecamModule.current() == null && !RearMirrorModule.isDrawingRear();
	}

	/**
	 * Rear mirror: before the world is drawn for this frame, draw it once looking backwards and
	 * keep that picture. The normal drawing afterwards overwrites the screen as always.
	 */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
	private void qolbundle$rearMirror(GameRenderer renderer, DeltaTracker tickCounter, Operation<Void> original) {
		RearMirrorModule mirror = RearMirrorModule.current();
		if (mirror != null && mirror.wantsPicture()) {
			mirror.beginRear();
			try {
				//? if >=26.1 {
				/*qolbundle$extractView(renderer, tickCounter); // turns the camera round, see CameraMixin
				*///?} else
				renderer.updateCamera(tickCounter); // turns the camera round, see CameraMixin
				original.call(renderer, tickCounter);
				mirror.keepPicture();
			} finally {
				mirror.endRear();
				//? if >=26.1 {
				/*qolbundle$extractView(renderer, tickCounter); // and back
				*///?} else
				renderer.updateCamera(tickCounter); // and back
			}
		}
		original.call(renderer, tickCounter);
	}

	//? if >=26.1 {
	/*@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	private net.minecraft.client.Minecraft minecraft;

	@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	private net.minecraft.client.Camera mainCamera;

	@org.spongepowered.asm.mixin.Shadow
	private void extractCamera(DeltaTracker deltaTracker, float worldPartialTicks, float cameraEntityPartialTicks) {
		throw new AssertionError();
	}

	/^* The camera and what it sees are worked out before drawing starts; do that again for another view. ^/
	@org.spongepowered.asm.mixin.Unique
	private void qolbundle$extractView(GameRenderer renderer, DeltaTracker tickCounter) {
		renderer.update(tickCounter, true);
		float worldPartialTicks = tickCounter.getGameTimeDeltaPartialTick(false);
		extractCamera(tickCounter, worldPartialTicks, mainCamera.getCameraEntityPartialTicks(tickCounter));
		minecraft.levelRenderer.extractLevel(tickCounter, mainCamera, worldPartialTicks);
	}
	*///?}
}

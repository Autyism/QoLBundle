package io.github.autyi6969.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.autyi6969.qolbundle.modules.FreecamModule;
import io.github.autyi6969.qolbundle.modules.RearMirrorModule;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/**
	 * Freecam and rear mirror: the first-person hand belongs to the body looking forward, so it is
	 * not drawn for the free camera or in the view backwards.
	 */
	@ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
	private boolean qolbundle$noHandOutOfBody(boolean firstPerson) {
		return firstPerson && FreecamModule.current() == null && !RearMirrorModule.isDrawingRear();
	}

	/**
	 * Rear mirror: before the world is drawn for this frame, draw it once looking backwards and
	 * keep that picture. The normal drawing afterwards overwrites the screen as always.
	 */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"))
	private void qolbundle$rearMirror(GameRenderer renderer, RenderTickCounter tickCounter, Operation<Void> original) {
		RearMirrorModule mirror = RearMirrorModule.current();
		if (mirror != null && mirror.wantsPicture()) {
			mirror.beginRear();
			try {
				renderer.updateCamera(tickCounter); // turns the camera round, see CameraMixin
				original.call(renderer, tickCounter);
				mirror.keepPicture();
			} finally {
				mirror.endRear();
				renderer.updateCamera(tickCounter); // and back
			}
		}
		original.call(renderer, tickCounter);
	}
}

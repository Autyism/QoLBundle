package io.github.autyi6969.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.autyi6969.qolbundle.modules.FreecamModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Freecam: the first-person hand belongs to the body, not to the free camera, so do not draw it. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
	private boolean qolbundle$noHandInFreecam(boolean firstPerson) {
		return firstPerson && FreecamModule.current() == null;
	}
}

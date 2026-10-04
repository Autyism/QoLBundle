package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FreecamModule;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: while the free camera is flying, the movement keys steer the camera, so the player's
 * body must receive "no keys pressed".
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
	@Inject(method = "tick", at = @At("TAIL"))
	private void qolbundle$standStillDuringFreecam(CallbackInfo ci) {
		if (FreecamModule.current() != null) {
			this.playerInput = PlayerInput.DEFAULT;
			this.movementVector = Vec2f.ZERO;
		}
	}
}

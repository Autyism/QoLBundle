package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.input.ViewHooks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse movement turns the player through this one method. The AFK clicker (view lock) and
 * Freecam (turn the free camera instead) need to step in before that happens; there is no
 * Fabric API event for it.
 */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void qolbundle$interceptLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity && ViewHooks.interceptLook(cursorDeltaX, cursorDeltaY)) {
			ci.cancel();
		}
	}
}

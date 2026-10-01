package io.github.autyi6969.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.autyi6969.qolbundle.modules.FreecamModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freecam: the game skips drawing chunks it thinks are hidden behind solid blocks, worked out
 * from where the camera is. That is only right for a camera that cannot be inside a wall; a free
 * camera inside rock would see almost nothing, and caves would pop in and out while it moves.
 * So while the free camera flies, every loaded chunk in view is drawn.
 *
 * <p>Both injections are optional (require = 0): renderer mods such as Sodium replace this code,
 * and then the second one still tells them "treat the camera like a spectator's".
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@ModifyExpressionValue(method = "updateCamera", require = 0, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/MinecraftClient;chunkCullingEnabled:Z"))
	private boolean qolbundle$noHiddenChunkSkippingInFreecam(boolean cullingEnabled) {
		return cullingEnabled && FreecamModule.current() == null;
	}

	@ModifyExpressionValue(method = "render", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSpectator()Z"))
	private boolean qolbundle$freecamSeesLikeSpectator(boolean spectator) {
		return spectator || FreecamModule.current() != null;
	}
}

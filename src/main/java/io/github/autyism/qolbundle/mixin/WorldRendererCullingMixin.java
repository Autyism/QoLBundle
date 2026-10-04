package io.github.autyism.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.autyism.qolbundle.modules.FreecamModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freecam, vanilla renderer only: the game skips drawing chunks it thinks are hidden behind solid
 * blocks, worked out from where the camera is. That is only right for a camera that cannot be
 * inside a wall; a free camera inside rock would see almost nothing, and caves would pop in and out
 * while it moves. So while the free camera flies, every loaded chunk in view is drawn.
 *
 * <p>Sodium overwrites {@code updateCamera}, and Mixin refuses (with a crash, even with
 * {@code require = 0}) to inject into a method another mod has overwritten. {@link QoLBundleMixinPlugin}
 * therefore skips this mixin when Sodium is installed; Sodium gets the spectator flag from
 * {@link WorldRendererMixin} instead.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererCullingMixin {
	@ModifyExpressionValue(method = "updateCamera", require = 0, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/MinecraftClient;chunkCullingEnabled:Z"))
	private boolean qolbundle$noHiddenChunkSkippingInFreecam(boolean cullingEnabled) {
		return cullingEnabled && FreecamModule.current() == null;
	}
}

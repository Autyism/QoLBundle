package io.github.autyi6969.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.autyi6969.qolbundle.modules.FreecamModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freecam: tells the terrain renderer to treat the free camera like a spectator's, so chunks are
 * not hidden just because the camera sits inside rock. Renderer mods such as Sodium read this same
 * flag. The vanilla-only part lives in {@link WorldRendererCullingMixin}.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@ModifyExpressionValue(method = "render", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSpectator()Z"))
	private boolean qolbundle$freecamSeesLikeSpectator(boolean spectator) {
		return spectator || FreecamModule.current() != null;
	}
}

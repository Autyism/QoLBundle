package io.github.autyism.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.autyism.qolbundle.modules.FreecamModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freecam: tells the terrain renderer to treat the free camera like a spectator's, so chunks are
 * not hidden just because the camera sits inside rock. Renderer mods such as Sodium read this same
 * flag. The vanilla-only part lives in {@link WorldRendererCullingMixin}.
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
	@ModifyExpressionValue(method = "renderLevel", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
	private boolean qolbundle$freecamSeesLikeSpectator(boolean spectator) {
		return spectator || FreecamModule.current() != null;
	}
}

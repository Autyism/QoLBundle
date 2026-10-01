package io.github.autyi6969.qolbundle.mixin;

import io.github.autyi6969.qolbundle.modules.FluidVisionModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water / Lava Vision: lets the module thin out the fog inside lava after the game has computed it. */
@Mixin(LavaFogModifier.class)
public class LavaFogModifierMixin {
	@Inject(method = "applyStartEndModifier", at = @At("TAIL"))
	private void qolbundle$thinLavaFog(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
		FluidVisionModule.adjustLavaFog(data);
	}
}

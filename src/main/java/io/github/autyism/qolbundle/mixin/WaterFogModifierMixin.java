package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FluidVisionModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water / Lava Vision: lets the module widen the underwater fog after the game has computed it. */
@Mixin(WaterFogModifier.class)
public class WaterFogModifierMixin {
	@Inject(method = "applyStartEndModifier", at = @At("TAIL"))
	private void qolbundle$clearWater(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
		FluidVisionModule.adjustWaterFog(data, viewDistance);
	}
}

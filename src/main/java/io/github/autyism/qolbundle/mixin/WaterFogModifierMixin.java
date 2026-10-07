package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FluidVisionModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water / Lava Vision: lets the module widen the underwater fog after the game has computed it. */
@Mixin(WaterFogEnvironment.class)
public class WaterFogModifierMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void qolbundle$clearWater(FogData data, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
		FluidVisionModule.adjustWaterFog(data, viewDistance);
	}
}

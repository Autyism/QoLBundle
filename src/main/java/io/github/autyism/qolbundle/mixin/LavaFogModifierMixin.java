package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FluidVisionModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water / Lava Vision: lets the module thin out the fog inside lava after the game has computed it. */
@Mixin(LavaFogEnvironment.class)
public class LavaFogModifierMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	//? if >=1.21.11 {
	private void qolbundle$thinLavaFog(FogData data, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
	//?} else {
	/*private void qolbundle$thinLavaFog(FogData data, net.minecraft.world.entity.Entity entity, net.minecraft.core.BlockPos pos, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
	*///?}
		FluidVisionModule.adjustLavaFog(data);
	}
}

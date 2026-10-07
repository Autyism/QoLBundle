package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.FluidVisionModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.6 {
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water / Lava Vision: lets the module thin out the fog inside lava after the game has computed it. */
//? if >=1.21.6 {
@Mixin(LavaFogEnvironment.class)
//?} else
/*@Mixin(FogRenderer.class)*/
public class LavaFogModifierMixin {
	//? if >=1.21.11 {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void qolbundle$thinLavaFog(FogData data, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
		FluidVisionModule.adjustLavaFog(data);
	}
	//?} elif >=1.21.6 {
	/*@Inject(method = "setupFog", at = @At("TAIL"))
	private void qolbundle$thinLavaFog(FogData data, net.minecraft.world.entity.Entity entity, net.minecraft.core.BlockPos pos, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
		FluidVisionModule.adjustLavaFog(data);
	}
	*///?} else {
	/*// Before 1.21.6 one call works out the whole fog (for the sky or for the terrain) and returns it.
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private static FogParameters qolbundle$thinLavaFog(FogParameters fog, Camera camera, FogRenderer.FogMode mode, Vector4f color, float viewDistance, boolean thickFog, float tickDelta) {
		return FluidVisionModule.adjustLavaFog(fog, camera);
	}
	*///?}
}

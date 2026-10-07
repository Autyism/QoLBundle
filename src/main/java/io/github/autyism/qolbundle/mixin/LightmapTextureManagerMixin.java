package io.github.autyism.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.autyism.qolbundle.modules.FullbrightModule;
//? if >=26.1 {
/*import net.minecraft.client.renderer.LightmapRenderStateExtractor;
*///?} else
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright. The vanilla light map already has a "night vision" input; while the Fullbright
 * module is on we feed it a fixed strength instead of asking the (absent) potion effect.
 * There is no Fabric API event for the light map, hence a Mixin.
 */
//? if >=26.1 {
/*@Mixin(LightmapRenderStateExtractor.class)
*///?} else
@Mixin(LightTexture.class)
public class LightmapTextureManagerMixin {
	/** First hasEffect call in updateLightTexture() is the Night Vision check. */
	//? if >=26.1 {
	/*@ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE",
	*///?} else
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z",
			ordinal = 0))
	private boolean qolbundle$pretendNightVision(boolean original) {
		return original || FullbrightModule.isActive();
	}

	//? if >=26.1 {
	/*@WrapOperation(method = "extract", at = @At(value = "INVOKE",
	*///?} else
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
			//? if >=26.2 {
			/*target = "Lnet/minecraft/client/renderer/GameRenderer;nightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
			*///?} else
			target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float qolbundle$nightVisionStrength(LivingEntity entity, float tickProgress, Operation<Float> original) {
		if (FullbrightModule.isActive()) {
			// The real method would crash without the potion effect, so never call it here.
			return FullbrightModule.getStrength();
		}
		return original.call(entity, tickProgress);
	}
}

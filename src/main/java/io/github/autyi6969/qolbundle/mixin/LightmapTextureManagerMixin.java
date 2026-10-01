package io.github.autyi6969.qolbundle.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.autyi6969.qolbundle.modules.FullbrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright. The vanilla light map already has a "night vision" input; while the Fullbright
 * module is on we feed it a fixed strength instead of asking the (absent) potion effect.
 * There is no Fabric API event for the light map, hence a Mixin.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
	/** First hasStatusEffect call in update() is the Night Vision check. */
	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
			ordinal = 0))
	private boolean qolbundle$pretendNightVision(boolean original) {
		return original || FullbrightModule.isActive();
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))
	private float qolbundle$nightVisionStrength(LivingEntity entity, float tickProgress, Operation<Float> original) {
		if (FullbrightModule.isActive()) {
			// The real method would crash without the potion effect, so never call it here.
			return FullbrightModule.getStrength();
		}
		return original.call(entity, tickProgress);
	}
}

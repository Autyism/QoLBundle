//? if >=1.21.9 <1.21.11 {
/*package io.github.autyism.qolbundle.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.autyism.qolbundle.render.legacy.WorldRenderEvents;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* 1.21.9-1.21.10: fires {@link WorldRenderEvents#BEFORE_ENTITIES} where Fabric fires its own on 1.21.10. ^/
@Mixin(LevelRenderer.class)
public class WorldRenderEventsMixin {
	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void qolbundle$beforeEntities(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
		WorldRenderEvents.fireBeforeEntities(poseStack, collector);
	}
}
*///?}

//? if <1.21.9 {
/*package io.github.autyism.qolbundle.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.autyism.qolbundle.render.legacy.GizmoRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Before 1.21.9: draws the shapes of {@link io.github.autyism.qolbundle.render.legacy.Gizmos} when the game draws its
 * own debug shapes, and the "always on top" ones after its last debug drawing in the frame.
 ^/
@Mixin(DebugRenderer.class)
public class LegacyDebugRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void qolbundle$drawShapes(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource buffers,
			double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
		GizmoRenderer.drawStandard(poseStack, buffers, cameraX, cameraY, cameraZ);
	}

	@Inject(method = "renderAfterTranslucents", at = @At("TAIL"))
	private void qolbundle$drawShapesOnTop(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
			double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
		GizmoRenderer.drawOnTop(poseStack, buffers, cameraX, cameraY, cameraZ);
	}
}
*///?}

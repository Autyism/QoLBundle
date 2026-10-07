//? if >=1.21.9 <1.21.11 {
/*package io.github.autyism.qolbundle.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.autyism.qolbundle.render.legacy.GizmoRenderer;
import io.github.autyism.qolbundle.render.legacy.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Before 1.21.11 (here 1.21.9 and 1.21.10): lets the modules add their shapes ({@link WorldRenderEvents#BEFORE_DEBUG_RENDER})
 * and draws the shapes of {@link io.github.autyism.qolbundle.render.legacy.Gizmos} when the game draws its own debug
 * shapes, and the "always on top" ones after its last debug drawing in the frame.
 ^/
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void qolbundle$drawShapes(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource buffers,
			double cameraX, double cameraY, double cameraZ, boolean late, CallbackInfo ci) {
		if (!late) {
			WorldRenderEvents.fireBeforeDebugRender();
			GizmoRenderer.drawStandard(poseStack, buffers, cameraX, cameraY, cameraZ);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void qolbundle$drawShapesOnTop(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource buffers,
			double cameraX, double cameraY, double cameraZ, boolean late, CallbackInfo ci) {
		if (late) {
			GizmoRenderer.drawOnTop(poseStack, buffers, cameraX, cameraY, cameraZ);
		}
	}
}
*///?}

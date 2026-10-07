//? if <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.autyism.qolbundle.QoLBundleClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/^*
 * Draws the shapes made with {@link Gizmos} on the versions before 1.21.11, the way 1.21.11 draws its own:
 * ordinary shapes together with the game's debug shapes, "always on top" shapes last in the frame over a
 * cleared depth buffer. Within each, the fully opaque pieces go first, then the see-through ones, and in each
 * of those filled faces, then fans, then lines, then text.
 ^/
public final class GizmoRenderer {
	/^* 1.21.11's line pipelines: the opaque one writes depth, the see-through one does not. ^/
	private static final RenderPipeline TRANSLUCENT_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(QoLBundleClient.id("pipeline/gizmo_lines_translucent")).withDepthWrite(false).build();
	/^* 1.21.11's filled shapes: blended, no depth writes, faces drawn from the front only. ^/
	private static final RenderPipeline QUADS_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(QoLBundleClient.id("pipeline/gizmo_quads")).withDepthWrite(false).build();
	private static final RenderPipeline FAN_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(QoLBundleClient.id("pipeline/gizmo_triangle_fan")).withCull(false).withDepthWrite(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN).build();
	private static final RenderType QUADS = RenderType.create("qolbundle_gizmo_quads", 1536, false, true, QUADS_PIPELINE,
			RenderType.CompositeState.builder().setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING).createCompositeState(false));
	private static final RenderType FAN = RenderType.create("qolbundle_gizmo_triangle_fan", 1536, false, true, FAN_PIPELINE,
			RenderType.CompositeState.builder().createCompositeState(false));
	/^* Line width is part of a render type before 1.21.11, so there is one per width (and see-through or not). ^/
	private static final Map<Float, RenderType> OPAQUE_LINES = new HashMap<>();
	private static final Map<Float, RenderType> TRANSLUCENT_LINES = new HashMap<>();
	/^* 1.21.11's lines move this close to the camera at most; whatever is nearer is cut off. ^/
	private static final float NEAR_LIMIT = -0.05F;

	private static final List<Shape> SHAPES = new ArrayList<>();

	private GizmoRenderer() {
	}

	/^* Forgets the shapes of the previous frame; the modules add this frame's right after. ^/
	public static void beginFrame() {
		SHAPES.clear();
	}

	static Shape newShape() {
		Shape shape = new Shape();
		SHAPES.add(shape);
		return shape;
	}

	/^* Ordinary shapes, drawn when the game draws its own debug shapes. ^/
	public static void drawStandard(PoseStack poseStack, MultiBufferSource.BufferSource buffers, double cameraX, double cameraY, double cameraZ) {
		draw(false, poseStack, buffers, new Vec3(cameraX, cameraY, cameraZ));
	}

	/^* "Always on top" shapes, last in the frame: like 1.21.11, the depth buffer is cleared for them first. ^/
	public static void drawOnTop(PoseStack poseStack, MultiBufferSource.BufferSource buffers, double cameraX, double cameraY, double cameraZ) {
		boolean any = false;
		for (Shape shape : SHAPES) {
			any |= shape.onTop;
		}
		if (any) {
			RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(Minecraft.getInstance().getMainRenderTarget().getDepthTexture(), 1.0);
			draw(true, poseStack, buffers, new Vec3(cameraX, cameraY, cameraZ));
		}
		SHAPES.clear();
	}

	private static void draw(boolean onTop, PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
		Group opaque = new Group(true);
		Group translucent = new Group(false);
		for (Shape shape : SHAPES) {
			if (shape.onTop == onTop) {
				shape.sortInto(opaque, translucent);
			}
		}
		if (opaque.isEmpty() && translucent.isEmpty()) {
			return;
		}
		Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
		Matrix4f viewRotation = new Matrix4f().rotation(view.rotation().conjugate(new Quaternionf()));
		opaque.render(poseStack, buffers, camera, viewRotation, view.rotation());
		translucent.render(poseStack, buffers, camera, viewRotation, view.rotation());
		buffers.endLastBatch();
	}

	private static RenderType lines(float width, boolean opaque) {
		Map<Float, RenderType> types = opaque ? OPAQUE_LINES : TRANSLUCENT_LINES;
		return types.computeIfAbsent(width, w -> RenderType.create("qolbundle_gizmo_lines_" + (opaque ? "" : "translucent_") + w, 1536,
				opaque ? RenderPipelines.LINES : TRANSLUCENT_LINES_PIPELINE,
				RenderType.CompositeState.builder()
						.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(w)))
						.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
						.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
						.createCompositeState(false)));
	}

	private static boolean isOpaque(int color) {
		return (color >>> 24) == 255;
	}

	record Line(Vec3 start, Vec3 end, int color, float width) {
	}

	record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
	}

	record Fan(Vec3[] points, int color) {
	}

	record Text(Vec3 pos, String text, TextGizmo.Style style) {
	}

	/^* One shape as the modules see it: its pieces, and whether it is drawn on top. ^/
	static final class Shape implements GizmoProperties {
		private final List<Line> lines = new ArrayList<>();
		private final List<Quad> quads = new ArrayList<>();
		private final List<Fan> fans = new ArrayList<>();
		private final List<Text> texts = new ArrayList<>();
		private boolean onTop;

		@Override
		public GizmoProperties setAlwaysOnTop() {
			onTop = true;
			return this;
		}

		void line(Vec3 start, Vec3 end, int color, float width) {
			lines.add(new Line(start, end, color, width));
		}

		void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
			quads.add(new Quad(a, b, c, d, color));
		}

		void fan(Vec3[] points, int color) {
			fans.add(new Fan(points, color));
		}

		void text(Vec3 pos, String text, TextGizmo.Style style) {
			texts.add(new Text(pos, text, style));
		}

		void sortInto(Group opaque, Group translucent) {
			for (Line line : lines) {
				(isOpaque(line.color) ? opaque : translucent).lines.add(line);
			}
			for (Quad quad : quads) {
				(isOpaque(quad.color) ? opaque : translucent).quads.add(quad);
			}
			for (Fan fan : fans) {
				(isOpaque(fan.color) ? opaque : translucent).fans.add(fan);
			}
			for (Text text : texts) {
				(isOpaque(text.style.color()) ? opaque : translucent).texts.add(text);
			}
		}
	}

	static final class Group {
		private final boolean opaque;
		private final List<Line> lines = new ArrayList<>();
		private final List<Quad> quads = new ArrayList<>();
		private final List<Fan> fans = new ArrayList<>();
		private final List<Text> texts = new ArrayList<>();

		Group(boolean opaque) {
			this.opaque = opaque;
		}

		boolean isEmpty() {
			return lines.isEmpty() && quads.isEmpty() && fans.isEmpty() && texts.isEmpty();
		}

		void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera, Matrix4f viewRotation, Quaternionf orientation) {
			PoseStack.Pose pose = poseStack.last();
			if (!quads.isEmpty()) {
				VertexConsumer consumer = buffers.getBuffer(QUADS);
				for (Quad quad : quads) {
					vertex(consumer, pose, quad.a, camera, quad.color);
					vertex(consumer, pose, quad.b, camera, quad.color);
					vertex(consumer, pose, quad.c, camera, quad.color);
					vertex(consumer, pose, quad.d, camera, quad.color);
				}
			}
			for (Fan fan : fans) {
				VertexConsumer consumer = buffers.getBuffer(FAN);
				for (Vec3 point : fan.points) {
					vertex(consumer, pose, point, camera, fan.color);
				}
			}
			renderLines(buffers, pose, camera, viewRotation);
			if (!texts.isEmpty()) {
				renderTexts(poseStack, buffers, camera, orientation);
			}
		}

		private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, Vec3 camera, int color) {
			consumer.addVertex(pose, (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z)).setColor(color);
		}

		private void renderLines(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose, Vec3 camera, Matrix4f viewRotation) {
			// Grouped by width (each width is its own render type here), widths in a fixed order.
			Map<Float, List<Line>> byWidth = new TreeMap<>();
			for (Line line : lines) {
				byWidth.computeIfAbsent(line.width, w -> new ArrayList<>()).add(line);
			}
			Vector4f start = new Vector4f();
			Vector4f end = new Vector4f();
			Vector4f startInView = new Vector4f();
			Vector4f endInView = new Vector4f();
			Vector4f cut = new Vector4f();
			for (Map.Entry<Float, List<Line>> entry : byWidth.entrySet()) {
				VertexConsumer consumer = buffers.getBuffer(lines(entry.getKey(), opaque));
				for (Line line : entry.getValue()) {
					start.set(line.start.x - camera.x, line.start.y - camera.y, line.start.z - camera.z, 1.0);
					end.set(line.end.x - camera.x, line.end.y - camera.y, line.end.z - camera.z, 1.0);
					start.mul(viewRotation, startInView);
					end.mul(viewRotation, endInView);
					boolean startTooNear = startInView.z > NEAR_LIMIT;
					boolean endTooNear = endInView.z > NEAR_LIMIT;
					if (startTooNear && endTooNear) {
						continue;
					}
					if (startTooNear || endTooNear) {
						// One end is behind the camera (or nearly): keep only the part in front of it.
						float span = endInView.z - startInView.z;
						if (Math.abs(span) < 1.0E-9F) {
							continue;
						}
						float t = Math.max(0.0F, Math.min(1.0F, (NEAR_LIMIT - startInView.z) / span));
						start.lerp(end, t, cut);
						if (startTooNear) {
							start.set(cut);
						} else {
							end.set(cut);
						}
					}
					float dx = end.x - start.x;
					float dy = end.y - start.y;
					float dz = end.z - start.z;
					consumer.addVertex(pose, start.x, start.y, start.z).setColor(line.color).setNormal(pose, dx, dy, dz);
					consumer.addVertex(pose, end.x, end.y, end.z).setColor(line.color).setNormal(pose, dx, dy, dz);
				}
			}
		}

		private void renderTexts(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera, Quaternionf orientation) {
			Font font = Minecraft.getInstance().font;
			for (Text text : texts) {
				poseStack.pushPose();
				poseStack.translate((float) (text.pos.x - camera.x), (float) (text.pos.y - camera.y), (float) (text.pos.z - camera.z));
				poseStack.mulPose(orientation);
				float scale = text.style.scale();
				poseStack.scale(scale / 16.0F, -scale / 16.0F, scale / 16.0F);
				float x = text.style.adjustLeft().isEmpty()
						? -font.width(text.text) / 2.0F
						: (float) -text.style.adjustLeft().getAsDouble() / scale;
				font.drawInBatch(text.text, x, 0.0F, text.style.color(), false, poseStack.last().pose(), buffers,
						Font.DisplayMode.NORMAL, 0, 0xF000F0);
				poseStack.popPose();
			}
		}
	}
}
*///?}

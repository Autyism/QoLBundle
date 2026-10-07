//? if <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/^*
 * Minecraft 1.21.11 added "gizmos": simple shapes drawn in the world, the way the F3 debug views draw.
 * The modules draw with them. For the versions before 1.21.11 this class offers the same calls, so the
 * modules read the same on every version, and {@link GizmoRenderer} draws the shapes the same way.
 ^/
public final class Gizmos {
	private static final int CIRCLE_POINTS = 20;

	private Gizmos() {
	}

	public static GizmoProperties line(Vec3 start, Vec3 end, int color, float width) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		shape.line(start, end, color, width);
		return shape;
	}

	/^* A line with a small four-pronged head at the end. The head is a tenth of the length (0.1 to 1 block). ^/
	public static GizmoProperties arrow(Vec3 start, Vec3 end, int color, float width) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		shape.line(start, end, color, width);
		Vec3 along = end.subtract(start);
		float size = (float) Math.max(0.1, Math.min(1.0, along.length() * 0.1F));
		Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(1.0F, 0.0F, 0.0F), along.toVector3f().normalize());
		Vector3f[] prongs = {new Vector3f(-size, size, 0.0F), new Vector3f(-size, 0.0F, size),
				new Vector3f(-size, -size, 0.0F), new Vector3f(-size, 0.0F, -size)};
		for (Vector3f prong : prongs) {
			turn.transform(prong);
			shape.line(end.add(prong.x, prong.y, prong.z), end, color, width);
		}
		return shape;
	}

	public static GizmoProperties cuboid(AABB box, GizmoStyle style) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		if (style.hasFill()) {
			for (Direction side : Direction.values()) {
				Vec3[] corners = face(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), side);
				shape.quad(corners[0], corners[1], corners[2], corners[3], style.fill());
			}
		}
		if (style.hasStroke()) {
			double[] xs = {box.minX, box.maxX};
			double[] ys = {box.minY, box.maxY};
			double[] zs = {box.minZ, box.maxZ};
			for (double y : ys) {
				for (double z : zs) {
					shape.line(new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z), style.stroke(), style.strokeWidth());
				}
			}
			for (double x : xs) {
				for (double z : zs) {
					shape.line(new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z), style.stroke(), style.strokeWidth());
				}
				for (double y : ys) {
					shape.line(new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ), style.stroke(), style.strokeWidth());
				}
			}
		}
		return shape;
	}

	/^* One face of the box between {@code min} and {@code max}: the face on the given side. ^/
	public static GizmoProperties rect(Vec3 min, Vec3 max, Direction side, GizmoStyle style) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		Vec3[] corners = face(min, max, side);
		if (style.hasFill()) {
			shape.quad(corners[0], corners[1], corners[2], corners[3], style.fill());
		}
		if (style.hasStroke()) {
			for (int i = 0; i < 4; i++) {
				shape.line(corners[i], corners[(i + 1) % 4], style.stroke(), style.strokeWidth());
			}
		}
		return shape;
	}

	/^* A flat circle lying in the horizontal plane, drawn with 20 straight pieces. ^/
	public static GizmoProperties circle(Vec3 center, float radius, GizmoStyle style) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		Vec3[] points = new Vec3[CIRCLE_POINTS + 1];
		for (int i = 0; i < CIRCLE_POINTS; i++) {
			float angle = i * (float) (Math.PI * 2.0 / CIRCLE_POINTS);
			points[i] = center.add((float) (radius * Math.cos(angle)), 0.0, (float) (radius * Math.sin(angle)));
		}
		points[CIRCLE_POINTS] = points[0];
		if (style.hasFill()) {
			shape.fan(points, style.fill());
		}
		if (style.hasStroke()) {
			for (int i = 0; i < CIRCLE_POINTS; i++) {
				shape.line(points[i], points[i + 1], style.stroke(), style.strokeWidth());
			}
		}
		return shape;
	}

	/^* Text that always faces the camera. ^/
	public static GizmoProperties billboardText(String text, Vec3 pos, TextGizmo.Style style) {
		GizmoRenderer.Shape shape = GizmoRenderer.newShape();
		shape.text(pos, text, style);
		return shape;
	}

	/^*
	 * The four corners of a box face, in the order that makes the face's front point outwards
	 * (counter-clockwise seen from outside). Filled faces are only drawn from the front.
	 ^/
	private static Vec3[] face(Vec3 min, Vec3 max, Direction side) {
		return switch (side) {
			case DOWN -> new Vec3[] {new Vec3(min.x, min.y, min.z), new Vec3(max.x, min.y, min.z), new Vec3(max.x, min.y, max.z), new Vec3(min.x, min.y, max.z)};
			case UP -> new Vec3[] {new Vec3(min.x, max.y, min.z), new Vec3(min.x, max.y, max.z), new Vec3(max.x, max.y, max.z), new Vec3(max.x, max.y, min.z)};
			case NORTH -> new Vec3[] {new Vec3(min.x, min.y, min.z), new Vec3(min.x, max.y, min.z), new Vec3(max.x, max.y, min.z), new Vec3(max.x, min.y, min.z)};
			case SOUTH -> new Vec3[] {new Vec3(min.x, min.y, max.z), new Vec3(max.x, min.y, max.z), new Vec3(max.x, max.y, max.z), new Vec3(min.x, max.y, max.z)};
			case WEST -> new Vec3[] {new Vec3(min.x, min.y, min.z), new Vec3(min.x, min.y, max.z), new Vec3(min.x, max.y, max.z), new Vec3(min.x, max.y, min.z)};
			case EAST -> new Vec3[] {new Vec3(max.x, min.y, min.z), new Vec3(max.x, max.y, min.z), new Vec3(max.x, max.y, max.z), new Vec3(max.x, min.y, max.z)};
		};
	}
}
*///?}

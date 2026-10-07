//? if <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

/^* How a shape is drawn: an outline colour and width, a fill colour, or both (the 1.21.11 calls, see {@link Gizmos}). ^/
public record GizmoStyle(int stroke, float strokeWidth, int fill) {
	public static GizmoStyle stroke(int color) {
		return new GizmoStyle(color, 2.5F, 0);
	}

	public static GizmoStyle stroke(int color, float width) {
		return new GizmoStyle(color, width, 0);
	}

	public static GizmoStyle fill(int color) {
		return new GizmoStyle(0, 0.0F, color);
	}

	public static GizmoStyle strokeAndFill(int color, float width, int fillColor) {
		return new GizmoStyle(color, width, fillColor);
	}

	public boolean hasFill() {
		return fill != 0;
	}

	public boolean hasStroke() {
		return stroke != 0 && strokeWidth > 0.0F;
	}
}
*///?}

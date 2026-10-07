//? if <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

import java.util.OptionalDouble;

/^* Text shapes (the 1.21.11 calls, see {@link Gizmos}). ^/
public final class TextGizmo {
	private TextGizmo() {
	}

	/^*
	 * Colour, size and where the text starts: centred on the point when {@code adjustLeft} is empty,
	 * otherwise starting that far to the left of it.
	 ^/
	public record Style(int color, float scale, OptionalDouble adjustLeft) {
		public static final float DEFAULT_SCALE = 0.32F;

		public static Style whiteAndCentered() {
			return new Style(0xFFFFFFFF, DEFAULT_SCALE, OptionalDouble.empty());
		}

		public static Style forColorAndCentered(int color) {
			return new Style(color, DEFAULT_SCALE, OptionalDouble.empty());
		}

		public static Style forColor(int color) {
			return new Style(color, DEFAULT_SCALE, OptionalDouble.of(0.0));
		}

		public Style withScale(float newScale) {
			return new Style(color, newScale, adjustLeft);
		}

		public Style withLeftAlignment(float left) {
			return new Style(color, scale, OptionalDouble.of(left));
		}
	}
}
*///?}

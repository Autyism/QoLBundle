package io.github.autyism.qolbundle.render;

/** The colors offered for things drawn in the world (walls, highlights). */
public enum HighlightColor {
	RED(0xFF5555),
	ORANGE(0xFFAA00),
	YELLOW(0xFFFF55),
	GREEN(0x55FF55),
	CYAN(0x55FFFF),
	BLUE(0x5577FF),
	PURPLE(0xFF55FF),
	WHITE(0xFFFFFF);

	public final int rgb;

	HighlightColor(int rgb) {
		this.rgb = rgb;
	}

	/** ARGB color with the given opacity in percent (0 = invisible, 100 = solid). */
	public int withOpacity(int percent) {
		int alpha = Math.max(0, Math.min(255, percent * 255 / 100));
		return alpha << 24 | rgb;
	}
}

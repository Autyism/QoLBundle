package io.github.autyi6969.qolbundle.hud;

/** Screen corner a HUD block is attached to. */
public enum HudAnchor {
	TOP_LEFT(false, false),
	TOP_RIGHT(true, false),
	BOTTOM_LEFT(false, true),
	BOTTOM_RIGHT(true, true);

	public final boolean right;
	public final boolean bottom;

	HudAnchor(boolean right, boolean bottom) {
		this.right = right;
		this.bottom = bottom;
	}
}

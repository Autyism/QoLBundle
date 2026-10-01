package io.github.autyi6969.qolbundle.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Hands out screen space in the four corners so that several HUD modules never draw on top of each
 * other. A new instance is created for every frame; modules are asked in registry order.
 */
public final class HudLayout {
	public static final int MARGIN = 4;
	public static final int LINE_HEIGHT = 10;
	private static final int GAP = 2;
	private static final int BACKGROUND = 0x70000000;
	public static final int WHITE = 0xFFFFFFFF;

	private final int screenWidth;
	private final int screenHeight;
	private final boolean debugHudVisible;
	private final int[] used = new int[HudAnchor.values().length];

	public HudLayout(int screenWidth, int screenHeight, boolean debugHudVisible) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.debugHudVisible = debugHudVisible;
	}

	/** True while the F3 screen is open. It fills both top corners, so corner text there is skipped. */
	public boolean isBlocked(HudAnchor anchor) {
		return debugHudVisible && !anchor.bottom;
	}

	public int getScreenWidth() {
		return screenWidth;
	}

	public int getScreenHeight() {
		return screenHeight;
	}

	/** Reserves a block of the given height in a corner and returns the y of its top edge. */
	public int reserve(HudAnchor anchor, int height) {
		int offset = used[anchor.ordinal()];
		used[anchor.ordinal()] = offset + height + GAP;
		return anchor.bottom ? screenHeight - MARGIN - offset - height : MARGIN + offset;
	}

	/** x of the left edge of a block of the given width in a corner. */
	public int xFor(HudAnchor anchor, int width) {
		return anchor.right ? screenWidth - MARGIN - width : MARGIN;
	}

	/** Draws a block of text lines (each with a dark backdrop, like F3) in a corner. */
	public void drawLines(DrawContext context, TextRenderer textRenderer, HudAnchor anchor, List<Text> lines) {
		drawLines(context, textRenderer, anchor, lines, 1F);
	}

	/** @param scale font size, 1 = the normal size */
	public void drawLines(DrawContext context, TextRenderer textRenderer, HudAnchor anchor, List<Text> lines, float scale) {
		if (lines.isEmpty() || isBlocked(anchor)) {
			return;
		}
		int lineHeight = Math.max(1, Math.round(LINE_HEIGHT * scale));
		int top = reserve(anchor, lines.size() * lineHeight);
		for (int i = 0; i < lines.size(); i++) {
			Text line = lines.get(i);
			int width = textRenderer.getWidth(line);
			int x = xFor(anchor, Math.round(width * scale));
			int y = top + i * lineHeight;
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(x, y);
			context.getMatrices().scale(scale, scale);
			context.fill(-2, -1, width + 2, LINE_HEIGHT - 1, BACKGROUND);
			context.drawTextWithShadow(textRenderer, line, 0, 0, WHITE);
			context.getMatrices().popMatrix();
		}
	}
}

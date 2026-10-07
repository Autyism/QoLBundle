package io.github.autyism.qolbundle.gui;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A plain screen with a title, a vertically scrolling list of rows and a footer.
 * Each row has a label on the left and any number of widgets lined up on the right.
 */
public abstract class ScrollListScreen extends Screen {
	protected static final int LIST_TOP = 30;
	protected static final int FOOTER_HEIGHT = 34;
	protected static final int WHITE = 0xFFFFFFFF;
	protected static final int GRAY = 0xFFA0A0A0;
	protected static final int GOLD = 0xFFFFD75E;
	private static final int SCROLL_STEP = 20;

	@Nullable
	protected final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private int scroll;

	protected ScrollListScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Fill the list with {@link #addRow} / {@link #addHeader}. Called every time the screen is (re)built. */
	protected abstract void buildRows();

	/** Buttons below the list. The default is a single "Done" button. */
	protected void addFooter() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 - 75, this.height - 27, 150, 20).build());
	}

	protected int listBottom() {
		return this.height - FOOTER_HEIGHT;
	}

	protected int rowWidth() {
		return Math.min(this.width - 32, 400);
	}

	protected int rowLeft() {
		return (this.width - rowWidth()) / 2;
	}

	protected Row addRow(int height, Component label, @Nullable Component subLabel) {
		Row row = new Row(height, label, subLabel, false);
		rows.add(row);
		return row;
	}

	protected Row addHeader(Component label) {
		Row row = new Row(18, label, null, true);
		rows.add(row);
		return row;
	}

	@Override
	protected void init() {
		rows.clear();
		buildRows();
		addFooter();
		layout();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private int maxScroll() {
		int content = 0;
		for (Row row : rows) {
			content += row.height;
		}
		return Math.max(0, content - (listBottom() - LIST_TOP));
	}

	private void layout() {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
		int y = LIST_TOP - scroll;
		for (Row row : rows) {
			row.y = y;
			boolean fullyVisible = y >= LIST_TOP && y + row.height <= listBottom();
			int right = rowLeft() + rowWidth();
			for (Placed placed : row.widgets) {
				placed.widget.setX(right - placed.fromRight);
				placed.widget.setY(y + (row.height - placed.widget.getHeight()) / 2);
				placed.widget.visible = fullyVisible;
			}
			y += row.height;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScroll() > 0) {
			scroll -= (int) Math.round(verticalAmount * SCROLL_STEP);
			layout();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	*///?} else {
	public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
	//?}
		context.drawCenteredString(this.font, this.title, this.width / 2, 11, WHITE);

		int left = rowLeft();
		int width = rowWidth();
		context.enableScissor(0, LIST_TOP, this.width, listBottom());
		for (Row row : rows) {
			if (row.y + row.height < LIST_TOP || row.y > listBottom()) {
				continue;
			}
			if (row.header) {
				context.drawCenteredString(this.font, row.label, this.width / 2, row.y + 6, GOLD);
				int lineY = row.y + row.height - 2;
				context.fill(left, lineY, left + width, lineY + 1, 0x40FFFFFF);
				continue;
			}
			int labelWidth = width - row.widgetsWidth() - 8;
			if (row.subLabel == null) {
				context.drawString(this.font, trim(row.label, labelWidth), left, row.y + (row.height - 8) / 2, WHITE);
			} else {
				context.drawString(this.font, trim(row.label, labelWidth), left, row.y + 3, WHITE);
				context.drawString(this.font, trim(row.subLabel, labelWidth), left, row.y + 14, GRAY);
			}
		}
		context.disableScissor();

		int max = maxScroll();
		if (max > 0) {
			int trackHeight = listBottom() - LIST_TOP;
			int barHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + max));
			int barY = LIST_TOP + (trackHeight - barHeight) * scroll / max;
			int barX = left + width + 6;
			context.fill(barX, LIST_TOP, barX + 3, listBottom(), 0x40000000);
			context.fill(barX, barY, barX + 3, barY + barHeight, 0xFFC0C0C0);
		}
	}

	private String trim(Component text, int maxWidth) {
		String full = text.getString();
		if (this.font.width(full) <= maxWidth) {
			return full;
		}
		return this.font.plainSubstrByWidth(full, Math.max(0, maxWidth - this.font.width("..."))) + "...";
	}

	private record Placed(AbstractWidget widget, int fromRight) {
	}

	protected final class Row {
		private final int height;
		private final Component label;
		@Nullable
		private final Component subLabel;
		private final boolean header;
		private final List<Placed> widgets = new ArrayList<>();
		private int y;

		private Row(int height, Component label, @Nullable Component subLabel, boolean header) {
			this.height = height;
			this.label = label;
			this.subLabel = subLabel;
			this.header = header;
		}

		/** Adds a widget; widgets are placed right to left in the order they are added. */
		public <W extends AbstractWidget> W add(W widget) {
			int fromRight = widgetsWidth() + (widgets.isEmpty() ? 0 : 4) + widget.getWidth();
			widgets.add(new Placed(widget, fromRight));
			ScrollListScreen.this.addRenderableWidget(widget);
			return widget;
		}

		private int widgetsWidth() {
			return widgets.isEmpty() ? 0 : widgets.get(widgets.size() - 1).fromRight;
		}
	}
}

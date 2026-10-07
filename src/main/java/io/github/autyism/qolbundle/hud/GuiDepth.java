//? if <1.21.6 {
/*package io.github.autyism.qolbundle.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/^*
 * Before 1.21.6 the screen had depth: something drawn later but further back stays hidden behind what is in front.
 * In a container the items are at depth 250 (their numbers at 300), the item on the mouse at 382 and tooltips at 400
 * (higher is closer). What the mod draws over a container is put between those, so it looks as it does from 1.21.6 on.
 ^/
public final class GuiDepth {
	/^* Over the items in the slots and their numbers, under the item on the mouse and the tooltips. ^/
	public static final float OVER_SLOTS = 310F;
	/^* A box of text over all that, including items the mod drew itself ({@link #item}), still under the tooltips. ^/
	public static final float PANEL = 350F;
	/^* How far in front {@link GuiGraphics#renderItem} puts an item by itself. ^/
	private static final float ITEM_DEPTH = 150F;
	/^* An item's own thickness reaches 8 to the front and back; this keeps it clear of what lies under it. ^/
	private static final float ITEM_CLEARANCE = 20F;

	private GuiDepth() {
	}

	public static void push(GuiGraphics context, float depth) {
		PoseStack pose = context.pose();
		pose.pushPose();
		pose.translate(0F, 0F, depth);
	}

	public static void pop(GuiGraphics context) {
		context.pose().popPose();
	}

	/^*
	 * An item just in front of what the mod drew at the current depth (inside {@link #push}), instead of 150 in
	 * front of it, which would put it over the item on the mouse and the tooltips.
	 ^/
	public static void item(GuiGraphics context, ItemStack stack, int x, int y) {
		push(context, ITEM_CLEARANCE - ITEM_DEPTH);
		context.renderItem(stack, x, y);
		pop(context);
	}
}
*///?}

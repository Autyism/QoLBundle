package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.mixin.ClientPlayerInteractionManagerAccessor;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** While mining a block: a small bar under the crosshair with the percentage and the time left. */
public class BreakProgressModule extends Module {
	private static final int BAR_WIDTH = 40;

	private final BoolSetting showBar = add(new BoolSetting("show_bar", true));
	private final BoolSetting showPercent = add(new BoolSetting("show_percent", true));
	private final BoolSetting showTime = add(new BoolSetting("show_time", true));

	private float lastProgress;

	public BreakProgressModule() {
		super("break_progress", ModuleCategory.TECHNICAL, true);
	}

	/** Progress (0..1) drawn in the last frame, 0 when not mining (for the self-test). */
	public float getLastProgress() {
		return lastProgress;
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Minecraft client = Minecraft.getInstance();
		lastProgress = 0F;
		if (client.gameMode == null || !client.gameMode.isDestroying()) {
			return;
		}
		ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor) client.gameMode;
		float progress = Math.max(0F, Math.min(1F, accessor.qolbundle$getCurrentBreakingProgress()));
		if (progress <= 0F) {
			return;
		}
		lastProgress = progress;

		int centerX = layout.getScreenWidth() / 2;
		int y = layout.getScreenHeight() / 2 + 12;
		if (showBar.get()) {
			int left = centerX - BAR_WIDTH / 2;
			context.fill(left - 1, y - 1, left + BAR_WIDTH + 1, y + 4, 0xA0000000);
			context.fill(left, y, left + Math.round(BAR_WIDTH * progress), y + 3, 0xFF55FF55);
			y += 7;
		}

		StringBuilder text = new StringBuilder();
		if (showPercent.get()) {
			text.append(Math.round(progress * 100F)).append('%');
		}
		if (showTime.get()) {
			BlockPos pos = accessor.qolbundle$getCurrentBreakingPos();
			BlockState state = client.level.getBlockState(pos);
			float perTick = state.getDestroyProgress(client.player, client.level, pos);
			if (perTick > 0F) {
				float seconds = (1F - progress) / perTick / 20F;
				if (!text.isEmpty()) {
					text.append("  ");
				}
				text.append(String.format(Locale.ROOT, "%.1fs", seconds));
			}
		}
		if (!text.isEmpty()) {
			context.drawCenteredString(client.font, text.toString(), centerX, y, HudLayout.WHITE);
		}
	}
}

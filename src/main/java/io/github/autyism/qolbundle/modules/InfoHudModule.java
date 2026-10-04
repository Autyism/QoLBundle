package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A few always-useful lines in a screen corner: coordinates, facing, FPS, in-game time, real clock. */
public class InfoHudModule extends Module {
	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));
	private final IntSetting textSize = add(new IntSetting("text_size", 100, 50, 200, "%"));
	private final BoolSetting showCoords = add(new BoolSetting("show_coords", true));
	private final BoolSetting showFacing = add(new BoolSetting("show_facing", true));
	private final BoolSetting showFps = add(new BoolSetting("show_fps", true));
	private final BoolSetting showGameTime = add(new BoolSetting("show_game_time", true));
	private final BoolSetting showRealTime = add(new BoolSetting("show_real_time", false));

	private final List<Text> lines = new ArrayList<>();

	public InfoHudModule() {
		super("info_hud", ModuleCategory.TECHNICAL, true);
	}

	public IntSetting textSizeSetting() {
		return textSize;
	}

	/** The lines drawn in the last frame (for the self-test). */
	public List<Text> getLines() {
		return lines;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		lines.clear();
		String key = getTranslationKey() + ".line.";
		if (showCoords.get()) {
			lines.add(Text.translatable(key + "coords",
					String.format(Locale.ROOT, "%.1f", client.player.getX()),
					String.format(Locale.ROOT, "%.1f", client.player.getY()),
					String.format(Locale.ROOT, "%.1f", client.player.getZ())));
		}
		if (showFacing.get()) {
			Direction facing = client.player.getHorizontalFacing();
			String axis = (facing.getDirection() == Direction.AxisDirection.POSITIVE ? "+" : "-")
					+ facing.getAxis().asString().toUpperCase(Locale.ROOT);
			lines.add(Text.translatable(key + "facing",
					Text.translatable("qolbundle.direction." + facing.asString()), axis));
		}
		if (showFps.get()) {
			lines.add(Text.translatable(key + "fps", client.getCurrentFps()));
		}
		if (showGameTime.get()) {
			long time = client.world.getTimeOfDay();
			long day = time / 24000L;
			long ofDay = Math.floorMod(time, 24000L);
			// Tick 0 of a Minecraft day is 06:00.
			long hour = (ofDay / 1000L + 6L) % 24L;
			long minute = ofDay % 1000L * 60L / 1000L;
			lines.add(Text.translatable(key + "game_time", day, String.format(Locale.ROOT, "%02d:%02d", hour, minute)));
		}
		if (showRealTime.get()) {
			LocalTime now = LocalTime.now();
			lines.add(Text.translatable(key + "real_time",
					String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute())));
		}
		layout.drawLines(context, client.textRenderer, position.get(), lines, textSize.get() / 100F);
	}
}

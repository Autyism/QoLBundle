package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.util.Sight;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

/**
 * Approach alert: a sound and an arrow when another player comes close, from any side, as long as
 * nothing solid is between you (somebody sneaking up behind you in the open).
 *
 * <p>Not a radar and not through walls: a player behind a wall never triggers it, and the arrow
 * shows where they were at the moment of the alert, for a few seconds; it does not follow them.
 */
public class ApproachAlertModule extends Module {
	private static final int SHOW_TICKS = 80;
	private static final int RING = 58;
	private static final int COLOR = 0xFFFFE040;

	private final IntSetting radius = add(new IntSetting("radius", 12, 4, 32));
	private final IntSetting cooldownSeconds = add(new IntSetting("cooldown", 20, 5, 120, " s"));
	private final BoolSetting onlySneaking = add(new BoolSetting("only_sneaking", false));
	private final BoolSetting sound = add(new BoolSetting("sound", true));

	/** Players that are close and in sight right now. */
	private final Set<UUID> inside = new HashSet<>();
	private final Map<UUID, Long> lastAlert = new HashMap<>();
	private long tick;
	@Nullable
	private Vec3 alertPos;
	@Nullable
	private Component alertName;
	private long showUntil;
	private int alerts;

	public ApproachAlertModule() {
		super("approach_alert", ModuleCategory.PVP, true);
	}

	public int getAlerts() {
		return alerts;
	}

	/** Where the player who set off the alert was, while the arrow is shown; otherwise null. */
	@Nullable
	public Vec3 getAlertPos() {
		return tick < showUntil ? alertPos : null;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		inside.clear();
		lastAlert.clear();
		showUntil = 0;
	}

	@Override
	public void onTick(Minecraft client) {
		tick++;
		if (client.player == null || client.level == null) {
			inside.clear();
			return;
		}
		if (tick % 4 != 0) {
			return;
		}
		Set<UUID> now = new HashSet<>();
		for (Avatar other : Sight.visiblePlayers(client, radius.get())) {
			if (onlySneaking.get() && !other.isShiftKeyDown()) {
				continue;
			}
			UUID id = other.getUUID();
			now.add(id);
			Long last = lastAlert.get(id);
			if (!inside.contains(id) && (last == null || tick - last > cooldownSeconds.get() * 20L)) {
				lastAlert.put(id, tick);
				alertPos = other.position();
				alertName = other.getName();
				showUntil = tick + SHOW_TICKS;
				alerts++;
				if (sound.get()) {
					client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 0.8F));
				}
			}
		}
		inside.clear();
		inside.addAll(now);
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Vec3 where = getAlertPos();
		if (where == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		Vec3 eye = client.gameRenderer.getMainCamera().position();
		float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(eye, client.gameRenderer.getMainCamera().yRot(), where));
		context.pose().pushMatrix();
		context.pose().translate(centerX, centerY);
		context.pose().rotate(angle);
		for (int row = 0; row < 9; row++) {
			context.fill(-row, -RING + row, row + 1, -RING + row + 1, COLOR);
		}
		context.pose().popMatrix();

		Component label = Component.translatable(getTranslationKey() + ".hud", alertName, (int) Math.round(where.distanceTo(eye)));
		int width = client.font.width(label);
		int labelRadius = RING + 12;
		int x = centerX + Math.round(Mth.sin(angle) * (labelRadius + width / 2F)) - width / 2;
		int y = centerY - Math.round(Mth.cos(angle) * labelRadius) - 4;
		x = Mth.clamp(x, 2, layout.getScreenWidth() - width - 2);
		context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
		context.drawString(client.font, label, x, y, COLOR);
	}
}

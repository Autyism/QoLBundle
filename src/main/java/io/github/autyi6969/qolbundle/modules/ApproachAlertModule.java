package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import io.github.autyi6969.qolbundle.util.Sight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
	private Vec3d alertPos;
	@Nullable
	private Text alertName;
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
	public Vec3d getAlertPos() {
		return tick < showUntil ? alertPos : null;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		inside.clear();
		lastAlert.clear();
		showUntil = 0;
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		if (client.player == null || client.world == null) {
			inside.clear();
			return;
		}
		if (tick % 4 != 0) {
			return;
		}
		Set<UUID> now = new HashSet<>();
		for (PlayerLikeEntity other : Sight.visiblePlayers(client, radius.get())) {
			if (onlySneaking.get() && !other.isSneaking()) {
				continue;
			}
			UUID id = other.getUuid();
			now.add(id);
			Long last = lastAlert.get(id);
			if (!inside.contains(id) && (last == null || tick - last > cooldownSeconds.get() * 20L)) {
				lastAlert.put(id, tick);
				alertPos = other.getEntityPos();
				alertName = other.getName();
				showUntil = tick + SHOW_TICKS;
				alerts++;
				if (sound.get()) {
					client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7F, 0.8F));
				}
			}
		}
		inside.clear();
		inside.addAll(now);
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Vec3d where = getAlertPos();
		if (where == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
		float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(eye, client.gameRenderer.getCamera().getYaw(), where));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(centerX, centerY);
		context.getMatrices().rotate(angle);
		for (int row = 0; row < 9; row++) {
			context.fill(-row, -RING + row, row + 1, -RING + row + 1, COLOR);
		}
		context.getMatrices().popMatrix();

		Text label = Text.translatable(getTranslationKey() + ".hud", alertName, (int) Math.round(where.distanceTo(eye)));
		int width = client.textRenderer.getWidth(label);
		int labelRadius = RING + 12;
		int x = centerX + Math.round(MathHelper.sin(angle) * (labelRadius + width / 2F)) - width / 2;
		int y = centerY - Math.round(MathHelper.cos(angle) * labelRadius) - 4;
		x = MathHelper.clamp(x, 2, layout.getScreenWidth() - width - 2);
		context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
		context.drawTextWithShadow(client.textRenderer, label, x, y, COLOR);
	}
}

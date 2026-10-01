package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Escape trail: remembers the way you came during the last 30 seconds of moving, and points back
 * along it. The arrow leads along the path you actually took (around the corner you came round),
 * not in a straight line to where you were. Walking back along the trail uses it up, so the arrow
 * keeps pointing further back.
 *
 * <p>Only the player's own past positions are used.
 */
public class EscapeTrailModule extends Module {
	public enum Show {
		AFTER_DAMAGE,
		ALWAYS
	}

	/** A place the player passed. ticks = how many ticks of moving it took to get here from the point before. */
	private record Point(Vec3d pos, int ticks) {
	}

	/** A new point is dropped every time the player is this far from the last one. */
	private static final double STEP = 1.5;
	/** Being this close to an older point means the player is back on the trail there. */
	private static final double REJOIN = 2.0;
	/** The arrow aims at the first point at least this far away, so it leads along the path. */
	private static final double LOOK_AHEAD = 4.0;
	private static final double TELEPORT = 16.0;
	private static final int RING = 34;
	private static final int COLOR = 0xFFFFAA00;

	private final IntSetting seconds = add(new IntSetting("seconds", 30, 10, 120, " s"));
	private final EnumSetting<Show> show = add(new EnumSetting<>("show", Show.AFTER_DAMAGE));
	private final IntSetting damageSeconds = add(new IntSetting("damage_seconds", 15, 5, 60, " s"));
	private final BoolSetting showTrail = add(new BoolSetting("show_trail", true));

	private final KeyBinding showKey;
	private final List<Point> trail = new ArrayList<>();
	@Nullable
	private ClientWorld world;
	@Nullable
	private Vec3d lastPos;
	private int movingTicks;
	private float lastHealth;
	private long tick;
	private long showUntil;

	public EscapeTrailModule() {
		super("escape_trail", ModuleCategory.INFO, true);
		showKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.escape_trail_show",
				InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), QoLBundleClient.KEY_CATEGORY));
	}

	public EnumSetting<Show> showSetting() {
		return show;
	}

	public int getPointCount() {
		return trail.size();
	}

	public boolean isShowing() {
		return show.get() == Show.ALWAYS || tick < showUntil;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		reset();
	}

	private void reset() {
		trail.clear();
		lastPos = null;
		movingTicks = 0;
		showUntil = 0;
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			reset();
			return;
		}
		while (showKey.wasPressed()) {
			// On demand: show for half a minute, or put it away again.
			showUntil = tick < showUntil ? 0 : tick + 30 * 20;
		}
		Vec3d pos = player.getEntityPos();
		if (client.world != world || lastPos != null && pos.squaredDistanceTo(lastPos) > TELEPORT * TELEPORT || player.isDead()) {
			// Another world, a teleport, a respawn: the old way back leads nowhere.
			reset();
			world = client.world;
			lastHealth = player.getHealth();
		}
		if (player.getHealth() < lastHealth - 0.001F) {
			showUntil = Math.max(showUntil, tick + damageSeconds.get() * 20L);
		}
		lastHealth = player.getHealth();
		if (lastPos != null && pos.squaredDistanceTo(lastPos) > 1.0E-4) {
			movingTicks++;
		}
		lastPos = pos;

		if (trail.isEmpty()) {
			trail.add(new Point(pos, 0));
			return;
		}
		// Back on an older part of the trail: everything after it has been walked back, drop it.
		for (int i = 0; i < trail.size() - 2; i++) {
			if (trail.get(i).pos.squaredDistanceTo(pos) < REJOIN * REJOIN) {
				trail.subList(i + 1, trail.size()).clear();
				movingTicks = 0;
				break;
			}
		}
		if (trail.get(trail.size() - 1).pos.squaredDistanceTo(pos) >= STEP * STEP) {
			trail.add(new Point(pos, movingTicks));
			movingTicks = 0;
			// Keep only the last N seconds of moving. Standing still does not use the trail up.
			// (The first point's own ticks belong to a piece that is already gone.)
			int total = 0;
			for (int i = 1; i < trail.size(); i++) {
				total += trail.get(i).ticks;
			}
			while (trail.size() > 2 && total > seconds.get() * 20) {
				total -= trail.get(1).ticks;
				trail.remove(0);
			}
		}
	}

	/** Where the arrow points: the nearest spot along the way back that is a few blocks away. */
	@Nullable
	public Vec3d getTarget() {
		if (lastPos == null) {
			return null;
		}
		for (int i = trail.size() - 1; i >= 0; i--) {
			if (trail.get(i).pos.squaredDistanceTo(lastPos) >= LOOK_AHEAD * LOOK_AHEAD) {
				return trail.get(i).pos;
			}
		}
		// The whole trail is close by: aim at where it starts, unless the player stands on it.
		Vec3d first = trail.isEmpty() ? null : trail.get(0).pos;
		return first != null && first.squaredDistanceTo(lastPos) >= STEP * STEP ? first : null;
	}

	/** How many blocks of path lie between the player and the start of the trail. */
	public double getTrailLength() {
		if (trail.isEmpty() || lastPos == null) {
			return 0;
		}
		double length = trail.get(trail.size() - 1).pos.distanceTo(lastPos);
		for (int i = 1; i < trail.size(); i++) {
			length += trail.get(i).pos.distanceTo(trail.get(i - 1).pos);
		}
		return length;
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		if (!showTrail.get() || !isShowing() || trail.size() < 2) {
			return;
		}
		// The way back, drawn a little above the ground; every other piece is an arrow pointing back.
		for (int i = trail.size() - 1; i >= 1; i--) {
			Vec3d from = trail.get(i).pos.add(0, 0.15, 0);
			Vec3d to = trail.get(i - 1).pos.add(0, 0.15, 0);
			if (i % 2 == 0) {
				GizmoDrawing.arrow(from, to, COLOR, 2.5F);
			} else {
				GizmoDrawing.line(from, to, COLOR, 2.5F);
			}
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Vec3d target = getTarget();
		if (target == null || !isShowing()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(client.gameRenderer.getCamera().getCameraPos(),
				client.gameRenderer.getCamera().getYaw(), target));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(centerX, centerY);
		context.getMatrices().rotate(angle);
		for (int row = 0; row < 7; row++) {
			context.fill(-row, -RING + row, row + 1, -RING + row + 1, COLOR);
		}
		context.getMatrices().popMatrix();

		Text label = Text.translatable(getTranslationKey() + ".hud", (int) Math.round(getTrailLength()));
		int width = client.textRenderer.getWidth(label);
		int labelRadius = RING + 10;
		int x = centerX + Math.round(MathHelper.sin(angle) * (labelRadius + width / 2F)) - width / 2;
		int y = centerY - Math.round(MathHelper.cos(angle) * labelRadius) - 4;
		context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
		context.drawTextWithShadow(client.textRenderer, label, x, y, COLOR);
	}
}

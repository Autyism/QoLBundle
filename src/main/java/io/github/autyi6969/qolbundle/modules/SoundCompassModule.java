package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * A ring around the crosshair with one pointer per sound heard recently, showing which way it came
 * from. It uses exactly what the vanilla subtitles use (only sounds that have a subtitle, only
 * within hearing range), just drawn as directions instead of "&lt;" and "&gt;".
 */
public class SoundCompassModule extends Module implements SoundInstanceListener {
	/** Subtitle keys of sounds worth shouting about. A trailing dot matches everything below it. */
	private static final String[] DANGER_KEYS = {
		"subtitles.entity.creeper.primed",
		"subtitles.entity.tnt.primed",
		"subtitles.entity.warden.",
		"subtitles.entity.ghast.shoot",
		"subtitles.entity.blaze.shoot",
		"subtitles.entity.wither.shoot",
		"subtitles.entity.ender_dragon.shoot"
	};
	private static final int MAX_ENTRIES = 12;

	private final IntSetting radius = add(new IntSetting("radius", 50, 30, 100));
	private final IntSetting duration = add(new IntSetting("duration", 3, 1, 8));
	private final BoolSetting showLabels = add(new BoolSetting("show_labels", true));
	private final BoolSetting highlightDanger = add(new BoolSetting("highlight_danger", true));
	private final BoolSetting ignoreOwn = add(new BoolSetting("ignore_own", true));

	private final List<Entry> entries = new ArrayList<>();
	private boolean registered;

	public SoundCompassModule() {
		super("sound_compass", ModuleCategory.TOOLS, true);
	}

	/** One sound on the ring. The same kind of sound heard again just moves and refreshes its pointer. */
	public static final class Entry {
		private final Text text;
		private final boolean danger;
		private Vec3d pos;
		private float range;
		private long heardMs;

		private Entry(Text text, boolean danger, Vec3d pos, float range, long heardMs) {
			this.text = text;
			this.danger = danger;
			this.pos = pos;
			this.range = range;
			this.heardMs = heardMs;
		}

		public Text text() {
			return text;
		}

		public boolean danger() {
			return danger;
		}

		public Vec3d pos() {
			return pos;
		}
	}

	/** Sounds currently on the ring (for the self-test). */
	public List<Entry> getEntries() {
		return entries;
	}

	/**
	 * Direction of a point relative to where the camera looks, in degrees:
	 * 0 = straight ahead, 90 = to the right, 180 or -180 = behind, -90 = to the left.
	 */
	public static float relativeAngle(Vec3d from, float cameraYaw, Vec3d to) {
		// Minecraft yaw: 0 = south (+Z), 90 = west (-X), growing clockwise seen from above.
		float yawToTarget = (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
		return MathHelper.wrapDegrees(yawToTarget - cameraYaw);
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		entries.clear();
		if (!enabled && registered) {
			MinecraftClient.getInstance().getSoundManager().unregisterListener(this);
			registered = false;
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (!registered) {
			// Not possible earlier: the sound system does not exist yet while mods are being loaded.
			client.getSoundManager().registerListener(this);
			registered = true;
		}
		if (client.world == null) {
			entries.clear();
			return;
		}
		long now = Util.getMeasuringTimeMs();
		long lifetime = duration.get() * 1000L;
		entries.removeIf(entry -> now - entry.heardMs > lifetime);
	}

	@Override
	public void onSoundPlayed(SoundInstance sound, WeightedSoundSet soundSet, float range) {
		Text subtitle = soundSet.getSubtitle();
		// No subtitle, or a sound without a place in the world (UI clicks, music): nothing to point at.
		if (subtitle == null || !isEnabled() || sound.isRelative()
				|| sound.getAttenuationType() == SoundInstance.AttenuationType.NONE) {
			return;
		}
		Vec3d pos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
		long now = Util.getMeasuringTimeMs();
		for (Entry entry : entries) {
			if (entry.text.equals(subtitle)) {
				entry.pos = pos;
				entry.range = range;
				entry.heardMs = now;
				return;
			}
		}
		if (entries.size() >= MAX_ENTRIES) {
			entries.remove(0);
		}
		entries.add(new Entry(subtitle, isDanger(subtitle), pos, range, now));
	}

	private static boolean isDanger(Text subtitle) {
		if (!(subtitle.getContent() instanceof TranslatableTextContent content)) {
			return false;
		}
		String key = content.getKey();
		for (String danger : DANGER_KEYS) {
			if (danger.endsWith(".") ? key.startsWith(danger) : key.equals(danger)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (entries.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d ear = client.gameRenderer.getCamera().getCameraPos();
		float yaw = client.gameRenderer.getCamera().getYaw();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		int ring = radius.get();
		long now = Util.getMeasuringTimeMs();
		long lifetime = duration.get() * 1000L;

		for (Entry entry : entries) {
			double distance = entry.pos.distanceTo(ear);
			// Same rule as the subtitles: sounds out of earshot are not shown.
			if (Double.isFinite(entry.range) && distance > entry.range) {
				continue;
			}
			if (ignoreOwn.get() && distance < 2.0) {
				continue; // your own footsteps and the like
			}
			float age = (now - entry.heardMs) / (float) lifetime;
			if (age >= 1F) {
				continue;
			}
			// Full strength for the first half of its life, then fading out.
			int alpha = (int) (255 * Math.min(1F, (1F - age) * 2F));
			if (alpha < 8) {
				continue;
			}
			boolean danger = entry.danger && highlightDanger.get();
			int rgb = danger ? 0xFF5555 : 0xFFFFFF;
			int color = alpha << 24 | rgb;
			float angle = (float) Math.toRadians(relativeAngle(ear, yaw, entry.pos));

			context.getMatrices().pushMatrix();
			context.getMatrices().translate(centerX, centerY);
			context.getMatrices().rotate(angle);
			drawPointer(context, ring, danger ? 7 : 5, color);
			context.getMatrices().popMatrix();

			if (showLabels.get()) {
				Text label = entry.text;
				double height = entry.pos.y - ear.y;
				if (height > 3.0) {
					label = Text.translatable(getTranslationKey() + ".hud.above", label);
				} else if (height < -3.0) {
					label = Text.translatable(getTranslationKey() + ".hud.below", label);
				}
				int width = client.textRenderer.getWidth(label);
				int labelRadius = ring + 14;
				int x = centerX + Math.round(MathHelper.sin(angle) * (labelRadius + width / 2F)) - width / 2;
				int y = centerY - Math.round(MathHelper.cos(angle) * labelRadius) - 4;
				x = MathHelper.clamp(x, 2, layout.getScreenWidth() - width - 2);
				context.fill(x - 2, y - 1, x + width + 2, y + 9, (alpha / 2) << 24);
				context.drawTextWithShadow(client.textRenderer, label, x, y, color);
			}
		}
	}

	/** A small triangle whose tip sits on the ring, pointing away from the centre (drawn pointing up, the caller rotates it). */
	private static void drawPointer(DrawContext context, int ring, int size, int color) {
		for (int row = 0; row < size; row++) {
			context.fill(-row, -ring + row, row + 1, -ring + row + 1, color);
		}
	}
}

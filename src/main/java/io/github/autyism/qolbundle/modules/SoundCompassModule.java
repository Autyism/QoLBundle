package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A ring around the crosshair with one pointer per sound heard recently, showing which way it came
 * from. It uses exactly what the vanilla subtitles use (only sounds that have a subtitle, only
 * within hearing range), just drawn as directions instead of "&lt;" and "&gt;".
 */
public class SoundCompassModule extends Module implements SoundEventListener {
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
		private final Component text;
		private final boolean danger;
		private Vec3 pos;
		private float range;
		private long heardMs;

		private Entry(Component text, boolean danger, Vec3 pos, float range, long heardMs) {
			this.text = text;
			this.danger = danger;
			this.pos = pos;
			this.range = range;
			this.heardMs = heardMs;
		}

		public Component text() {
			return text;
		}

		public boolean danger() {
			return danger;
		}

		public Vec3 pos() {
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
	public static float relativeAngle(Vec3 from, float cameraYaw, Vec3 to) {
		// Minecraft yaw: 0 = south (+Z), 90 = west (-X), growing clockwise seen from above.
		float yawToTarget = (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
		return Mth.wrapDegrees(yawToTarget - cameraYaw);
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		entries.clear();
		if (!enabled && registered) {
			Minecraft.getInstance().getSoundManager().removeListener(this);
			registered = false;
		}
	}

	@Override
	public void onTick(Minecraft client) {
		if (!registered) {
			// Not possible earlier: the sound system does not exist yet while mods are being loaded.
			client.getSoundManager().addListener(this);
			registered = true;
		}
		if (client.level == null) {
			entries.clear();
			return;
		}
		long now = Util.getMillis();
		long lifetime = duration.get() * 1000L;
		entries.removeIf(entry -> now - entry.heardMs > lifetime);
	}

	@Override
	public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundSet, float range) {
		Component subtitle = soundSet.getSubtitle();
		// No subtitle, or a sound without a place in the world (UI clicks, music): nothing to point at.
		if (subtitle == null || !isEnabled() || sound.isRelative()
				|| sound.getAttenuation() == SoundInstance.Attenuation.NONE) {
			return;
		}
		Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
		long now = Util.getMillis();
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

	private static boolean isDanger(Component subtitle) {
		if (!(subtitle.getContents() instanceof TranslatableContents content)) {
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
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (entries.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Vec3 ear = client.gameRenderer.getMainCamera().position();
		float yaw = client.gameRenderer.getMainCamera().yRot();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		int ring = radius.get();
		long now = Util.getMillis();
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

			context.pose().pushMatrix();
			context.pose().translate(centerX, centerY);
			context.pose().rotate(angle);
			drawPointer(context, ring, danger ? 7 : 5, color);
			context.pose().popMatrix();

			if (showLabels.get()) {
				Component label = entry.text;
				double height = entry.pos.y - ear.y;
				if (height > 3.0) {
					label = Component.translatable(getTranslationKey() + ".hud.above", label);
				} else if (height < -3.0) {
					label = Component.translatable(getTranslationKey() + ".hud.below", label);
				}
				int width = client.font.width(label);
				int labelRadius = ring + 14;
				int x = centerX + Math.round(Mth.sin(angle) * (labelRadius + width / 2F)) - width / 2;
				int y = centerY - Math.round(Mth.cos(angle) * labelRadius) - 4;
				x = Mth.clamp(x, 2, layout.getScreenWidth() - width - 2);
				context.fill(x - 2, y - 1, x + width + 2, y + 9, (alpha / 2) << 24);
				context.drawString(client.font, label, x, y, color);
			}
		}
	}

	/** A small triangle whose tip sits on the ring, pointing away from the centre (drawn pointing up, the caller rotates it). */
	private static void drawPointer(GuiGraphics context, int ring, int size, int color) {
		for (int row = 0; row < size; row++) {
			context.fill(-row, -ring + row, row + 1, -ring + row + 1, color);
		}
	}
}

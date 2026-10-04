package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.util.Sight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Somebody is watching you": a player in front of you who keeps their crosshair on you for more
 * than a few seconds gets called out.
 *
 * <p>Uses the direction the other player's head points, which the game shows on their model
 * anyway. Only players in your field of view with nothing solid in between are considered.
 */
public class StareAlertModule extends Module {
	private final IntSetting seconds = add(new IntSetting("seconds", 3, 1, 10, " s"));
	private final IntSetting angle = add(new IntSetting("angle", 6, 2, 15, "°"));
	private final IntSetting range = add(new IntSetting("range", 32, 8, 64));
	private final BoolSetting sound = add(new BoolSetting("sound", true));

	/** For how many ticks each player has had their crosshair on me. */
	private final Map<UUID, Integer> staring = new HashMap<>();
	private final List<Text> starers = new ArrayList<>();
	private long tick;

	public StareAlertModule() {
		super("stare_alert", ModuleCategory.PVP, true);
	}

	public IntSetting secondsSetting() {
		return seconds;
	}

	/** Names of the players who have been looking at me for long enough. */
	public List<Text> getStarers() {
		return starers;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		staring.clear();
		starers.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		if (client.player == null || client.world == null) {
			staring.clear();
			starers.clear();
			return;
		}
		if (tick % 2 != 0) {
			return;
		}
		Map<UUID, Integer> now = new HashMap<>();
		starers.clear();
		Vec3d myEyes = client.player.getEyePos();
		Vec3d myChest = client.player.getBoundingBox().getCenter();
		double limit = Math.cos(Math.toRadians(angle.get()));
		int needed = seconds.get() * 20;
		for (PlayerLikeEntity other : Sight.visiblePlayers(client, range.get())) {
			if (!Sight.inFieldOfView(client, other)) {
				continue;
			}
			Vec3d look = other.getRotationVec(1.0F);
			Vec3d eyes = other.getEyePos();
			boolean onMe = look.dotProduct(myEyes.subtract(eyes).normalize()) >= limit
					|| look.dotProduct(myChest.subtract(eyes).normalize()) >= limit;
			if (!onMe) {
				continue;
			}
			UUID id = other.getUuid();
			int before = staring.getOrDefault(id, 0);
			int ticks = before + 2;
			now.put(id, ticks);
			if (ticks >= needed) {
				starers.add(other.getName());
				if (before < needed && sound.get()) {
					client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.8F, 0.6F));
				}
			}
		}
		staring.clear();
		staring.putAll(now);
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (starers.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int y = 34;
		for (Text name : starers) {
			Text line = Text.translatable(getTranslationKey() + ".hud", name);
			int width = client.textRenderer.getWidth(line);
			int x = (layout.getScreenWidth() - width) / 2;
			context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
			context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFF5555);
			y += 13;
		}
	}
}

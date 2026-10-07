package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.util.Sight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

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
	private final List<Component> starers = new ArrayList<>();
	private long tick;

	public StareAlertModule() {
		super("stare_alert", ModuleCategory.PVP, true);
	}

	public IntSetting secondsSetting() {
		return seconds;
	}

	/** Names of the players who have been looking at me for long enough. */
	public List<Component> getStarers() {
		return starers;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		staring.clear();
		starers.clear();
	}

	@Override
	public void onTick(Minecraft client) {
		tick++;
		if (client.player == null || client.level == null) {
			staring.clear();
			starers.clear();
			return;
		}
		if (tick % 2 != 0) {
			return;
		}
		Map<UUID, Integer> now = new HashMap<>();
		starers.clear();
		Vec3 myEyes = client.player.getEyePosition();
		Vec3 myChest = client.player.getBoundingBox().getCenter();
		double limit = Math.cos(Math.toRadians(angle.get()));
		int needed = seconds.get() * 20;
		for (Avatar other : Sight.visiblePlayers(client, range.get())) {
			if (!Sight.inFieldOfView(client, other)) {
				continue;
			}
			Vec3 look = other.getViewVector(1.0F);
			Vec3 eyes = other.getEyePosition();
			boolean onMe = look.dot(myEyes.subtract(eyes).normalize()) >= limit
					|| look.dot(myChest.subtract(eyes).normalize()) >= limit;
			if (!onMe) {
				continue;
			}
			UUID id = other.getUUID();
			int before = staring.getOrDefault(id, 0);
			int ticks = before + 2;
			now.put(id, ticks);
			if (ticks >= needed) {
				starers.add(other.getName());
				if (before < needed && sound.get()) {
					client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.6F));
				}
			}
		}
		staring.clear();
		staring.putAll(now);
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (starers.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int y = 34;
		for (Component name : starers) {
			Component line = Component.translatable(getTranslationKey() + ".hud", name);
			int width = client.font.width(line);
			int x = (layout.getScreenWidth() - width) / 2;
			context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
			context.drawString(client.font, line, x, y, 0xFFFF5555);
			y += 13;
		}
	}
}

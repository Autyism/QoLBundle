package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kill confirmation and a running tally of the fight: kills, damage dealt, damage taken, deaths.
 *
 * <p>The client is not told who killed what. So: something you hit (with your hand or with your own
 * arrow) is watched for a few seconds; health it loses right after your hit counts as damage you
 * dealt, and if it dies within five seconds of your last hit it counts as your kill. Health is the
 * number the server shows every client; some servers hide other players' health, then only the
 * kills are right.
 */
public class CombatStatsModule extends Module {
	private static final class Track {
		final LivingEntity entity;
		float health;
		long lastHit;

		Track(LivingEntity entity, float health, long lastHit) {
			this.entity = entity;
			this.health = health;
			this.lastHit = lastHit;
		}
	}

	/** Health lost this soon after my hit is my damage. */
	private static final int DAMAGE_WINDOW = 15;
	/** Dying this soon after my last hit is my kill. */
	private static final int KILL_WINDOW = 100;

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.BOTTOM_LEFT));
	private final BoolSetting alwaysShow = add(new BoolSetting("always_show", false));
	private final IntSetting combatSeconds = add(new IntSetting("combat_seconds", 15, 5, 120, " s"));
	private final BoolSetting killSound = add(new BoolSetting("kill_sound", true));

	private final KeyMapping resetKey;
	private final Map<Integer, Track> tracks = new HashMap<>();
	private final Map<Integer, Vec3> myProjectiles = new HashMap<>();
	@Nullable
	private ClientLevel world;
	private long tick;
	private long lastCombatTick = Long.MIN_VALUE / 2;
	private float lastHealth = -1;
	private boolean wasDead;
	private int kills;
	private int deaths;
	private double dealt;
	private double taken;

	public CombatStatsModule() {
		super("combat_stats", ModuleCategory.PVP, true);
		resetKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.combat_stats_reset",
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), QoLBundleClient.KEY_CATEGORY));
		AttackEntityCallback.EVENT.register((player, attackedWorld, hand, entity, hit) -> {
			if (attackedWorld.isClientSide() && isEnabled() && player == Minecraft.getInstance().player && entity instanceof LivingEntity living) {
				markHit(living);
			}
			return InteractionResult.PASS; // only watching
		});
	}

	public int getKills() {
		return kills;
	}

	public int getDeaths() {
		return deaths;
	}

	public double getDealt() {
		return dealt;
	}

	public double getTaken() {
		return taken;
	}

	public void reset() {
		kills = 0;
		deaths = 0;
		dealt = 0;
		taken = 0;
		tracks.clear();
		myProjectiles.clear();
		lastCombatTick = Long.MIN_VALUE / 2;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		reset();
		lastHealth = -1;
	}

	private void markHit(LivingEntity target) {
		Track track = tracks.get(target.getId());
		if (track == null) {
			tracks.put(target.getId(), new Track(target, target.getHealth(), tick));
		} else {
			track.lastHit = tick;
		}
		lastCombatTick = tick;
	}

	@Override
	public void onTick(Minecraft client) {
		tick++;
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			tracks.clear();
			myProjectiles.clear();
			lastHealth = -1;
			return;
		}
		while (resetKey.consumeClick()) {
			reset();
		}
		if (client.level != world) {
			world = client.level;
			tracks.clear();
			myProjectiles.clear();
			lastHealth = -1; // a different world: the health there is not "damage taken"
		}

		// Myself.
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
			lastCombatTick = tick;
		}
		wasDead = dead;
		float health = player.getHealth();
		if (lastHealth >= 0 && health < lastHealth - 0.001F) {
			taken += lastHealth - health;
			lastCombatTick = tick;
		}
		lastHealth = health;

		// My own arrows and the like: what stands where one of them stopped has been hit by me.
		Map<Integer, Vec3> flying = new HashMap<>();
		for (Projectile projectile : client.level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(96.0),
				entity -> entity.getOwner() == player)) {
			if (projectile.getDeltaMovement().lengthSqr() > 0.01) {
				flying.put(projectile.getId(), projectile.position());
			}
		}
		for (Map.Entry<Integer, Vec3> entry : myProjectiles.entrySet()) {
			if (!flying.containsKey(entry.getKey())) {
				Vec3 end = entry.getValue();
				List<LivingEntity> struck = client.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(end, 1.0, 1.0, 1.0).inflate(1.2),
						entity -> entity != player && entity.getBoundingBox().inflate(0.8).contains(end));
				if (!struck.isEmpty()) {
					markHit(struck.get(0));
				}
			}
		}
		myProjectiles.clear();
		myProjectiles.putAll(flying);

		// What I hit.
		Iterator<Track> iterator = tracks.values().iterator();
		while (iterator.hasNext()) {
			Track track = iterator.next();
			LivingEntity entity = track.entity;
			float now = entity.getHealth();
			if (now < track.health - 0.001F && tick - track.lastHit <= DAMAGE_WINDOW) {
				dealt += track.health - now;
				lastCombatTick = tick;
			}
			track.health = now;
			if (entity.isDeadOrDying()) {
				if (tick - track.lastHit <= KILL_WINDOW) {
					kills++;
					lastCombatTick = tick;
					client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".kill", entity.getName())
							.withStyle(ChatFormatting.GREEN), false);
					if (killSound.get()) {
						client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 0.6F, 0.7F));
					}
				}
				iterator.remove();
			} else if (entity.isRemoved() || tick - track.lastHit > KILL_WINDOW * 2) {
				iterator.remove(); // out of sight or the fight is long over
			}
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (!alwaysShow.get() && tick - lastCombatTick > combatSeconds.get() * 20L) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Component line = Component.translatable(getTranslationKey() + ".hud", kills, String.format(Locale.ROOT, "%.1f", dealt),
				String.format(Locale.ROOT, "%.1f", taken), deaths);
		layout.drawLines(context, client.font, position.get(), List.of(line));
	}
}

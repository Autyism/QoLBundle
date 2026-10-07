package io.github.autyism.qolbundle.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.input.ViewHooks;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.DoubleSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>Freecam: the camera leaves the body and flies around freely while the player stands still.
 * Purely a different viewpoint on what the client already has loaded: the player does not move,
 * nothing extra is requested from the server, and clicking does nothing to the world.
 *
 * <p>While flying, a right click drops a marker on the block you look at (a left click on a
 * marker removes it). Markers stay after you return to your body: arrows around the crosshair
 * and a beam in the world lead you there. They are remembered per world.
 *
 * <p>The camera itself is moved in {@link io.github.autyism.qolbundle.mixin.CameraMixin}; the
 * world renderer is told not to skip "hidden" chunks while flying in
 * {@link io.github.autyism.qolbundle.mixin.WorldRendererMixin}.
 */
public class FreecamModule extends Module {
	private static final double SPRINT_FACTOR = 3.0;
	private static final String SECTION = "freecam_markers";
	private static final double MARK_REACH = 256.0;
	private static final int RING = 62;
	/** A marker is "under the crosshair" when it is within this many degrees of the view direction. */
	private static final double PICK_DEGREES = 6.0;

	@Nullable
	private static FreecamModule instance;

	private final DoubleSetting speed = add(new DoubleSetting("speed", 0.5, 0.1, 3.0, 0.1, ""));
	private final BoolSetting exitOnDamage = add(new BoolSetting("exit_on_damage", true));
	private final BoolSetting showHud = add(new BoolSetting("show_hud", true));
	private final BoolSetting fullbright = add(new BoolSetting("fullbright", true));
	private final BoolSetting markers = add(new BoolSetting("markers", true));
	private final IntSetting maxMarkers = add(new IntSetting("max_markers", 10, 1, 30));
	private final BoolSetting removeOnArrival = add(new BoolSetting("remove_on_arrival", true));

	/** A spot marked from the free camera. */
	public record Marker(int number, String dimension, BlockPos pos) {
	}

	private final KeyMapping toggleKey;
	private final KeyMapping clearKey;
	private boolean active;
	private Vec3 pos = Vec3.ZERO;
	private Vec3 lastPos = Vec3.ZERO;
	private float yaw;
	private float pitch;
	private float lastHealth;
	@Nullable
	private ClientLevel world;

	private final List<Marker> markerList = new ArrayList<>();
	private int nextNumber = 1;
	@Nullable
	private String loadedWorldId;

	public FreecamModule() {
		super("freecam", ModuleCategory.GREY, false);
		instance = this;
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.freecam_toggle",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, QoLBundleClient.KEY_CATEGORY));
		clearKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.freecam_clear_markers",
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), QoLBundleClient.KEY_CATEGORY));
		ViewHooks.register(this::turnCamera);
	}

	/** The module while the free camera is in use, otherwise null. Used by the mixins. */
	@Nullable
	public static FreecamModule current() {
		return instance != null && instance.active ? instance : null;
	}

	/** True when dark places should be lit because the free camera is flying (used by the light map mixin). */
	public static boolean wantsFullbright() {
		return instance != null && instance.active && instance.fullbright.get();
	}

	public boolean isActive() {
		return active;
	}

	/** Camera position for this frame, smoothed between two ticks. */
	public Vec3 getCameraPos(float tickProgress) {
		return lastPos.lerp(pos, tickProgress);
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	/** Puts the free camera somewhere directly (for the self-test). */
	public void placeCamera(Vec3 where, float newYaw, float newPitch) {
		pos = where;
		lastPos = where;
		yaw = newYaw;
		pitch = newPitch;
	}

	public List<Marker> getMarkers() {
		syncWorldData();
		return markerList;
	}

	public void clearMarkers() {
		syncWorldData();
		markerList.clear();
		store();
	}

	public void setActive(Minecraft client, boolean on) {
		LocalPlayer player = client.player;
		if (on == active || (on && (player == null || client.level == null))) {
			return;
		}
		active = on;
		if (on) {
			pos = player.getEyePosition();
			lastPos = pos;
			yaw = player.getYRot();
			pitch = player.getXRot();
			lastHealth = player.getHealth();
			world = client.level;
		} else {
			world = null;
		}
		// Which chunks are drawn depends on whether the camera is free; have it worked out afresh.
		//? if >=26.2 {
		/*client.levelRenderer.sectionOcclusionGraph().invalidate();
		client.levelRenderer.cloudRenderer().markForRebuild();
		*///?} else
		client.levelRenderer.needsUpdate();
		client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + (on ? ".on" : ".off")), false);
	}

	private boolean turnCamera(double deltaX, double deltaY) {
		if (!active) {
			return false;
		}
		// Same sensitivity scaling as the game uses for the player.
		yaw += (float) deltaX * 0.15F;
		pitch = Mth.clamp(pitch + (float) deltaY * 0.15F, -90F, 90F);
		return true;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled) {
			setActive(Minecraft.getInstance(), false);
		}
	}

	@Override
	public void onStartTick(Minecraft client) {
		if (!active) {
			return;
		}
		// Out of body: clicks must not hit, place or pick anything where the body happens to look.
		// They are used for the markers instead.
		Options options = client.options;
		boolean rightClick = false;
		boolean leftClick = false;
		while (options.keyUse.consumeClick()) {
			rightClick = true;
		}
		while (options.keyAttack.consumeClick()) {
			leftClick = true;
		}
		while (options.keyPickItem.consumeClick()) {
			// discard
		}
		options.keyUse.setDown(false);
		options.keyAttack.setDown(false);
		options.keyPickItem.setDown(false);
		if (markers.get() && client.level != null && client.screen == null) {
			if (rightClick) {
				addMarkerAtCrosshair(client);
			} else if (leftClick) {
				removeMarkerAtCrosshair(client);
			}
		}
	}

	@Override
	public void onTick(Minecraft client) {
		syncWorldData();
		while (toggleKey.consumeClick()) {
			setActive(client, !active);
		}
		while (clearKey.consumeClick()) {
			clearMarkers();
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".markers_cleared"), false);
		}
		LocalPlayer player = client.player;
		if (player != null && client.level != null && removeOnArrival.get() && !markerList.isEmpty()) {
			String here = client.level.dimension().identifier().toString();
			if (markerList.removeIf(marker -> marker.dimension.equals(here)
					&& marker.pos.distSqr(player.blockPosition()) <= 3 * 3)) {
				store();
			}
		}
		if (!active) {
			return;
		}
		if (player == null || client.level != world || player.isDeadOrDying()) {
			setActive(client, false);
			return;
		}
		if (exitOnDamage.get() && player.getHealth() < lastHealth - 0.001F) {
			setActive(client, false);
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".off_damage").withStyle(ChatFormatting.RED), false);
			return;
		}
		lastHealth = player.getHealth();

		lastPos = pos;
		if (client.screen != null) {
			return; // typing in chat or a menu: keys are not movement
		}
		Options options = client.options;
		double forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
		double sideways = (options.keyRight.isDown() ? 1 : 0) - (options.keyLeft.isDown() ? 1 : 0);
		double up = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
		if (forward == 0 && sideways == 0 && up == 0) {
			return;
		}
		// Yaw 0 looks towards +Z; "right" of that is -X.
		double yawRadians = Math.toRadians(yaw);
		Vec3 move = new Vec3(-Math.sin(yawRadians) * forward - Math.cos(yawRadians) * sideways,
				up,
				Math.cos(yawRadians) * forward - Math.sin(yawRadians) * sideways);
		if (move.lengthSqr() > 1.0) {
			move = move.normalize();
		}
		double pace = speed.get() * (options.keySprint.isDown() ? SPRINT_FACTOR : 1.0);
		pos = pos.add(move.scale(pace));
	}

	// ---- markers -------------------------------------------------------------------------------

	private Vec3 lookDirection() {
		return Vec3.directionFromRotation(pitch, yaw);
	}

	/** Marks the block the free camera looks at; with nothing in sight, the camera's own position. */
	public void addMarkerAtCrosshair(Minecraft client) {
		if (client.level == null || client.player == null) {
			return;
		}
		syncWorldData();
		Vec3 end = pos.add(lookDirection().scale(MARK_REACH));
		BlockHitResult hit = client.level.clip(new ClipContext(pos, end, ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, client.player));
		BlockPos where = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.containing(pos);
		String dimension = client.level.dimension().identifier().toString();
		markerList.removeIf(marker -> marker.dimension.equals(dimension) && marker.pos.equals(where));
		while (markerList.size() >= maxMarkers.get()) {
			markerList.remove(0); // the oldest makes room
		}
		Marker marker = new Marker(nextNumber++, dimension, where.immutable());
		markerList.add(marker);
		store();
		client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".marker_added",
				marker.number, where.getX(), where.getY(), where.getZ()), false);
	}

	/** Removes the marker closest to the middle of the view, if one is close to it. */
	public void removeMarkerAtCrosshair(Minecraft client) {
		if (client.level == null) {
			return;
		}
		syncWorldData();
		String dimension = client.level.dimension().identifier().toString();
		Vec3 look = lookDirection();
		Marker best = null;
		double bestAngle = PICK_DEGREES;
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(dimension)) {
				continue;
			}
			Vec3 to = Vec3.atCenterOf(marker.pos).subtract(pos);
			if (to.lengthSqr() < 1.0E-4) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1.0, 1.0)));
			if (angle < bestAngle) {
				bestAngle = angle;
				best = marker;
			}
		}
		if (best != null) {
			markerList.remove(best);
			store();
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".marker_removed", best.number), false);
		}
	}

	private void syncWorldData() {
		String worldId = WorldData.getWorldId();
		if (Objects.equals(worldId, loadedWorldId)) {
			return;
		}
		loadedWorldId = worldId;
		markerList.clear();
		nextNumber = 1;
		JsonObject section = WorldData.section(SECTION);
		if (section == null || !section.has("markers") || !section.get("markers").isJsonArray()) {
			return;
		}
		for (JsonElement element : section.getAsJsonArray("markers")) {
			try {
				JsonObject json = element.getAsJsonObject();
				Marker marker = new Marker(json.get("n").getAsInt(), json.get("dimension").getAsString(),
						new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()));
				markerList.add(marker);
				nextNumber = Math.max(nextNumber, marker.number + 1);
			} catch (RuntimeException ignored) {
				// skip a damaged entry
			}
		}
	}

	private void store() {
		JsonObject section = WorldData.section(SECTION);
		if (section == null) {
			return;
		}
		JsonArray array = new JsonArray();
		for (Marker marker : markerList) {
			JsonObject json = new JsonObject();
			json.addProperty("n", marker.number);
			json.addProperty("dimension", marker.dimension);
			json.addProperty("x", marker.pos.getX());
			json.addProperty("y", marker.pos.getY());
			json.addProperty("z", marker.pos.getZ());
			array.add(json);
		}
		section.add("markers", array);
		WorldData.markDirty();
	}

	// ---- drawing -------------------------------------------------------------------------------

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || markerList.isEmpty() || !markers.get()) {
			return;
		}
		String here = client.level.dimension().identifier().toString();
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(here)) {
				continue;
			}
			// Your own markers: they are meant to be seen from anywhere, through walls too.
			Gizmos.cuboid(new AABB(marker.pos).inflate(0.03), GizmoStyle.stroke(0xFFFF55FF, 3.0F)).setAlwaysOnTop();
			Vec3 base = Vec3.atCenterOf(marker.pos);
			Gizmos.line(base, base.add(0, 24, 0), 0xB0FF55FF, 3.0F).setAlwaysOnTop();
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Minecraft client = Minecraft.getInstance();
		if (markers.get() && !markerList.isEmpty()) {
			drawMarkerArrows(context, client, layout);
		}
		if (!active || !showHud.get()) {
			return;
		}
		int distance = (int) Math.round(pos.distanceTo(client.player.getEyePosition()));
		Component text = Component.translatable(getTranslationKey() + ".hud", toggleKey.getTranslatedKeyMessage(), distance);
		int width = client.font.width(text);
		int x = (layout.getScreenWidth() - width) / 2;
		context.fill(x - 3, 4, x + width + 3, 16, 0x90000000);
		context.drawString(client.font, text, x, 6, 0xFFFFAA00);
		if (markers.get()) {
			Component hint = Component.translatable(getTranslationKey() + ".hud_markers");
			int hintWidth = client.font.width(hint);
			int hintX = (layout.getScreenWidth() - hintWidth) / 2;
			context.fill(hintX - 3, 16, hintX + hintWidth + 3, 27, 0x90000000);
			context.drawString(client.font, hint, hintX, 17, 0xFFFF55FF);
		}
	}

	/** One arrow per marker on a ring around the crosshair, with its number and distance. */
	private void drawMarkerArrows(GuiGraphics context, Minecraft client, HudLayout layout) {
		String here = client.level.dimension().identifier().toString();
		Vec3 eye = client.gameRenderer.getMainCamera().position();
		float cameraYaw = client.gameRenderer.getMainCamera().yRot();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(here)) {
				continue;
			}
			Vec3 spot = Vec3.atCenterOf(marker.pos);
			float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(eye, cameraYaw, spot));
			context.pose().pushMatrix();
			context.pose().translate(centerX, centerY);
			context.pose().rotate(angle);
			for (int row = 0; row < 6; row++) {
				context.fill(-row, -RING + row, row + 1, -RING + row + 1, 0xFFFF55FF);
			}
			context.pose().popMatrix();

			int distance = (int) Math.round(spot.distanceTo(eye));
			double height = spot.y - eye.y;
			String key = getTranslationKey() + (height > 3 ? ".marker_label_up" : height < -3 ? ".marker_label_down" : ".marker_label");
			Component label = Component.translatable(key, marker.number, distance);
			int width = client.font.width(label);
			int labelRadius = RING + 12;
			int x = centerX + Math.round(Mth.sin(angle) * (labelRadius + width / 2F)) - width / 2;
			int y = centerY - Math.round(Mth.cos(angle) * labelRadius) - 4;
			x = Mth.clamp(x, 2, layout.getScreenWidth() - width - 2);
			context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
			context.drawString(client.font, label, x, y, 0xFFFF55FF);
		}
	}
}

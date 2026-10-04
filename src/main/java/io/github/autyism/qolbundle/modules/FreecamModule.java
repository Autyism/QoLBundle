package io.github.autyism.qolbundle.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
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

	private final KeyBinding toggleKey;
	private final KeyBinding clearKey;
	private boolean active;
	private Vec3d pos = Vec3d.ZERO;
	private Vec3d lastPos = Vec3d.ZERO;
	private float yaw;
	private float pitch;
	private float lastHealth;
	@Nullable
	private ClientWorld world;

	private final List<Marker> markerList = new ArrayList<>();
	private int nextNumber = 1;
	@Nullable
	private String loadedWorldId;

	public FreecamModule() {
		super("freecam", ModuleCategory.GREY, false);
		instance = this;
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.freecam_toggle",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, QoLBundleClient.KEY_CATEGORY));
		clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.freecam_clear_markers",
				InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), QoLBundleClient.KEY_CATEGORY));
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
	public Vec3d getCameraPos(float tickProgress) {
		return lastPos.lerp(pos, tickProgress);
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}

	/** Puts the free camera somewhere directly (for the self-test). */
	public void placeCamera(Vec3d where, float newYaw, float newPitch) {
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

	public void setActive(MinecraftClient client, boolean on) {
		ClientPlayerEntity player = client.player;
		if (on == active || (on && (player == null || client.world == null))) {
			return;
		}
		active = on;
		if (on) {
			pos = player.getEyePos();
			lastPos = pos;
			yaw = player.getYaw();
			pitch = player.getPitch();
			lastHealth = player.getHealth();
			world = client.world;
		} else {
			world = null;
		}
		// Which chunks are drawn depends on whether the camera is free; have it worked out afresh.
		client.worldRenderer.scheduleTerrainUpdate();
		client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + (on ? ".on" : ".off")), false);
	}

	private boolean turnCamera(double deltaX, double deltaY) {
		if (!active) {
			return false;
		}
		// Same sensitivity scaling as the game uses for the player.
		yaw += (float) deltaX * 0.15F;
		pitch = MathHelper.clamp(pitch + (float) deltaY * 0.15F, -90F, 90F);
		return true;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled) {
			setActive(MinecraftClient.getInstance(), false);
		}
	}

	@Override
	public void onStartTick(MinecraftClient client) {
		if (!active) {
			return;
		}
		// Out of body: clicks must not hit, place or pick anything where the body happens to look.
		// They are used for the markers instead.
		GameOptions options = client.options;
		boolean rightClick = false;
		boolean leftClick = false;
		while (options.useKey.wasPressed()) {
			rightClick = true;
		}
		while (options.attackKey.wasPressed()) {
			leftClick = true;
		}
		while (options.pickItemKey.wasPressed()) {
			// discard
		}
		options.useKey.setPressed(false);
		options.attackKey.setPressed(false);
		options.pickItemKey.setPressed(false);
		if (markers.get() && client.world != null && client.currentScreen == null) {
			if (rightClick) {
				addMarkerAtCrosshair(client);
			} else if (leftClick) {
				removeMarkerAtCrosshair(client);
			}
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		syncWorldData();
		while (toggleKey.wasPressed()) {
			setActive(client, !active);
		}
		while (clearKey.wasPressed()) {
			clearMarkers();
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".markers_cleared"), false);
		}
		ClientPlayerEntity player = client.player;
		if (player != null && client.world != null && removeOnArrival.get() && !markerList.isEmpty()) {
			String here = client.world.getRegistryKey().getValue().toString();
			if (markerList.removeIf(marker -> marker.dimension.equals(here)
					&& marker.pos.getSquaredDistance(player.getBlockPos()) <= 3 * 3)) {
				store();
			}
		}
		if (!active) {
			return;
		}
		if (player == null || client.world != world || player.isDead()) {
			setActive(client, false);
			return;
		}
		if (exitOnDamage.get() && player.getHealth() < lastHealth - 0.001F) {
			setActive(client, false);
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".off_damage").formatted(Formatting.RED), false);
			return;
		}
		lastHealth = player.getHealth();

		lastPos = pos;
		if (client.currentScreen != null) {
			return; // typing in chat or a menu: keys are not movement
		}
		GameOptions options = client.options;
		double forward = (options.forwardKey.isPressed() ? 1 : 0) - (options.backKey.isPressed() ? 1 : 0);
		double sideways = (options.rightKey.isPressed() ? 1 : 0) - (options.leftKey.isPressed() ? 1 : 0);
		double up = (options.jumpKey.isPressed() ? 1 : 0) - (options.sneakKey.isPressed() ? 1 : 0);
		if (forward == 0 && sideways == 0 && up == 0) {
			return;
		}
		// Yaw 0 looks towards +Z; "right" of that is -X.
		double yawRadians = Math.toRadians(yaw);
		Vec3d move = new Vec3d(-Math.sin(yawRadians) * forward - Math.cos(yawRadians) * sideways,
				up,
				Math.cos(yawRadians) * forward - Math.sin(yawRadians) * sideways);
		if (move.lengthSquared() > 1.0) {
			move = move.normalize();
		}
		double pace = speed.get() * (options.sprintKey.isPressed() ? SPRINT_FACTOR : 1.0);
		pos = pos.add(move.multiply(pace));
	}

	// ---- markers -------------------------------------------------------------------------------

	private Vec3d lookDirection() {
		return Vec3d.fromPolar(pitch, yaw);
	}

	/** Marks the block the free camera looks at; with nothing in sight, the camera's own position. */
	public void addMarkerAtCrosshair(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		syncWorldData();
		Vec3d end = pos.add(lookDirection().multiply(MARK_REACH));
		BlockHitResult hit = client.world.raycast(new RaycastContext(pos, end, RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE, client.player));
		BlockPos where = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.ofFloored(pos);
		String dimension = client.world.getRegistryKey().getValue().toString();
		markerList.removeIf(marker -> marker.dimension.equals(dimension) && marker.pos.equals(where));
		while (markerList.size() >= maxMarkers.get()) {
			markerList.remove(0); // the oldest makes room
		}
		Marker marker = new Marker(nextNumber++, dimension, where.toImmutable());
		markerList.add(marker);
		store();
		client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".marker_added",
				marker.number, where.getX(), where.getY(), where.getZ()), false);
	}

	/** Removes the marker closest to the middle of the view, if one is close to it. */
	public void removeMarkerAtCrosshair(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		syncWorldData();
		String dimension = client.world.getRegistryKey().getValue().toString();
		Vec3d look = lookDirection();
		Marker best = null;
		double bestAngle = PICK_DEGREES;
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(dimension)) {
				continue;
			}
			Vec3d to = Vec3d.ofCenter(marker.pos).subtract(pos);
			if (to.lengthSquared() < 1.0E-4) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(MathHelper.clamp(to.normalize().dotProduct(look), -1.0, 1.0)));
			if (angle < bestAngle) {
				bestAngle = angle;
				best = marker;
			}
		}
		if (best != null) {
			markerList.remove(best);
			store();
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".marker_removed", best.number), false);
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || markerList.isEmpty() || !markers.get()) {
			return;
		}
		String here = client.world.getRegistryKey().getValue().toString();
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(here)) {
				continue;
			}
			// Your own markers: they are meant to be seen from anywhere, through walls too.
			GizmoDrawing.box(new Box(marker.pos).expand(0.03), DrawStyle.stroked(0xFFFF55FF, 3.0F)).ignoreOcclusion();
			Vec3d base = Vec3d.ofCenter(marker.pos);
			GizmoDrawing.line(base, base.add(0, 24, 0), 0xB0FF55FF, 3.0F).ignoreOcclusion();
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (markers.get() && !markerList.isEmpty()) {
			drawMarkerArrows(context, client, layout);
		}
		if (!active || !showHud.get()) {
			return;
		}
		int distance = (int) Math.round(pos.distanceTo(client.player.getEyePos()));
		Text text = Text.translatable(getTranslationKey() + ".hud", toggleKey.getBoundKeyLocalizedText(), distance);
		int width = client.textRenderer.getWidth(text);
		int x = (layout.getScreenWidth() - width) / 2;
		context.fill(x - 3, 4, x + width + 3, 16, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, text, x, 6, 0xFFFFAA00);
		if (markers.get()) {
			Text hint = Text.translatable(getTranslationKey() + ".hud_markers");
			int hintWidth = client.textRenderer.getWidth(hint);
			int hintX = (layout.getScreenWidth() - hintWidth) / 2;
			context.fill(hintX - 3, 16, hintX + hintWidth + 3, 27, 0x90000000);
			context.drawTextWithShadow(client.textRenderer, hint, hintX, 17, 0xFFFF55FF);
		}
	}

	/** One arrow per marker on a ring around the crosshair, with its number and distance. */
	private void drawMarkerArrows(DrawContext context, MinecraftClient client, HudLayout layout) {
		String here = client.world.getRegistryKey().getValue().toString();
		Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
		float cameraYaw = client.gameRenderer.getCamera().getYaw();
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;
		for (Marker marker : markerList) {
			if (!marker.dimension.equals(here)) {
				continue;
			}
			Vec3d spot = Vec3d.ofCenter(marker.pos);
			float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(eye, cameraYaw, spot));
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(centerX, centerY);
			context.getMatrices().rotate(angle);
			for (int row = 0; row < 6; row++) {
				context.fill(-row, -RING + row, row + 1, -RING + row + 1, 0xFFFF55FF);
			}
			context.getMatrices().popMatrix();

			int distance = (int) Math.round(spot.distanceTo(eye));
			double height = spot.y - eye.y;
			String key = getTranslationKey() + (height > 3 ? ".marker_label_up" : height < -3 ? ".marker_label_down" : ".marker_label");
			Text label = Text.translatable(key, marker.number, distance);
			int width = client.textRenderer.getWidth(label);
			int labelRadius = RING + 12;
			int x = centerX + Math.round(MathHelper.sin(angle) * (labelRadius + width / 2F)) - width / 2;
			int y = centerY - Math.round(MathHelper.cos(angle) * labelRadius) - 4;
			x = MathHelper.clamp(x, 2, layout.getScreenWidth() - width - 2);
			context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
			context.drawTextWithShadow(client.textRenderer, label, x, y, 0xFFFF55FF);
		}
	}
}

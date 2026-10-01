package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for travelling on the Nether roof (above the bedrock ceiling, Y 128 and up), where there
 * is nothing to navigate by: a heading tape at the top of the screen, the Nether and Overworld
 * coordinates side by side (times 8), and what a portal built on the spot would connect to.
 * An Overworld destination can be typed in; the tape then shows which way it lies.
 */
public class NetherRoofModule extends Module {
	private static final int ROOF_Y = 128;
	private static final int TAPE_WIDTH = 180;
	/** Degrees of heading visible across the tape. */
	private static final float TAPE_SPAN = 120F;
	private static final String[] POINTS = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};

	private final BoolSetting showTape = add(new BoolSetting("show_tape", true));
	private final BoolSetting showCoords = add(new BoolSetting("show_coords", true));
	private final BoolSetting showPortal = add(new BoolSetting("show_portal", true));
	private final StringSetting destination = add(new StringSetting("destination", "", 40));

	private boolean active;
	private int overworldX;
	private int overworldZ;
	private final List<Text> lines = new ArrayList<>();

	public NetherRoofModule() {
		super("nether_roof", ModuleCategory.TECHNICAL, true);
	}

	/** True while the player stands on the Nether roof (for the self-test). */
	public boolean isActive() {
		return active;
	}

	public int getOverworldX() {
		return overworldX;
	}

	public int getOverworldZ() {
		return overworldZ;
	}

	public StringSetting destinationSetting() {
		return destination;
	}

	/** The typed destination as Overworld x and z, or null when the box is empty or not two numbers. */
	private int @Nullable [] parseDestination() {
		String[] parts = destination.get().trim().split("[,\\s]+");
		if (parts.length != 2) {
			return null;
		}
		try {
			return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		active = client.player != null && client.world != null && client.world.getRegistryKey() == World.NETHER
				&& client.player.getY() >= ROOF_Y;
		lines.clear();
		if (!active) {
			return;
		}
		String key = getTranslationKey() + ".hud.";
		int x = MathHelper.floor(client.player.getX());
		int z = MathHelper.floor(client.player.getZ());
		overworldX = MathHelper.floor(client.player.getX() * 8.0);
		overworldZ = MathHelper.floor(client.player.getZ() * 8.0);
		if (showCoords.get()) {
			lines.add(Text.translatable(key + "coords", x, z, overworldX, overworldZ));
		}
		if (showPortal.get() && ModuleRegistry.get("portal_calculator") instanceof PortalCalculatorModule portals && portals.isEnabled()) {
			BlockPos exit = portals.predictOverworldExit(new BlockPos(overworldX, client.player.getBlockY(), overworldZ));
			lines.add(exit != null
					? Text.translatable(key + "portal_known", exit.getX(), exit.getY(), exit.getZ()).withColor(0x55FF55)
					: Text.translatable(key + "portal_new").withColor(0xFFFF55));
		}
		int[] target = parseDestination();
		if (target != null) {
			double dx = target[0] / 8.0 - client.player.getX();
			double dz = target[1] / 8.0 - client.player.getZ();
			lines.add(Text.translatable(key + "destination", target[0], target[1], (int) Math.round(Math.hypot(dx, dz))).withColor(0x55FFFF));
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!active) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int y = 6;
		if (showTape.get() && !layout.isBlocked(io.github.autyi6969.qolbundle.hud.HudAnchor.TOP_LEFT)) {
			drawTape(context, client, centerX, y);
			y += 26;
		}
		for (Text line : lines) {
			int width = client.textRenderer.getWidth(line);
			context.fill(centerX - width / 2 - 3, y - 1, centerX + width / 2 + 3, y + 9, 0x80000000);
			context.drawTextWithShadow(client.textRenderer, line, centerX - width / 2, y, HudLayout.WHITE);
			y += HudLayout.LINE_HEIGHT;
		}
	}

	/** A strip of headings that slides as you turn; the middle is where you are looking. */
	private void drawTape(DrawContext context, MinecraftClient client, int centerX, int top) {
		float yaw = MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw());
		float pixelsPerDegree = TAPE_WIDTH / TAPE_SPAN;
		int left = centerX - TAPE_WIDTH / 2;
		context.fill(left - 2, top, left + TAPE_WIDTH + 2, top + 22, 0x80000000);
		// A tick every 15 degrees, a name every 45.
		for (int heading = 0; heading < 360; heading += 15) {
			float delta = MathHelper.wrapDegrees(heading - yaw);
			if (Math.abs(delta) > TAPE_SPAN / 2) {
				continue;
			}
			int x = centerX + Math.round(delta * pixelsPerDegree);
			boolean named = heading % 45 == 0;
			context.fill(x, top + (named ? 12 : 16), x + 1, top + 20, named ? 0xFFFFFFFF : 0xFFAAAAAA);
			if (named) {
				// Yaw 0 is south, 90 west, 180 north, 270 east.
				Text name = Text.translatable("qolbundle.compass." + POINTS[heading / 45]);
				boolean cardinal = heading % 90 == 0;
				context.drawCenteredTextWithShadow(client.textRenderer, name, x, top + 2, cardinal ? 0xFFFFD75E : 0xFFCCCCCC);
			}
		}
		context.fill(centerX, top, centerX + 1, top + 22, 0xFFFF5555);

		int[] target = parseDestination();
		if (target != null) {
			double dx = target[0] / 8.0 - client.player.getX();
			double dz = target[1] / 8.0 - client.player.getZ();
			float delta = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
			// Off the tape: pin the marker to the edge it lies beyond.
			int x = centerX + Math.round(MathHelper.clamp(delta, -TAPE_SPAN / 2, TAPE_SPAN / 2) * pixelsPerDegree);
			for (int row = 0; row < 5; row++) {
				context.fill(x - row, top + 21 - row, x + row + 1, top + 22 - row, 0xFF55FFFF);
			}
		}
	}
}

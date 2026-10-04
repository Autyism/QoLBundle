package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.render.HighlightColor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Draws the border of the chunk you are standing in as four see-through coloured walls, with a
 * line at every corner. Optionally marks the corners of the eight chunks around it.
 */
public class ChunkBordersModule extends Module {
	/** Height rings are drawn this far above and below the player, every RING_STEP blocks. */
	private static final int RING_REACH = 24;
	private static final int RING_STEP = 8;

	private final EnumSetting<HighlightColor> color = add(new EnumSetting<>("color", HighlightColor.YELLOW));
	private final IntSetting opacity = add(new IntSetting("opacity", 25, 5, 80, "%"));
	private final BoolSetting rings = add(new BoolSetting("rings", true));
	private final BoolSetting neighbors = add(new BoolSetting("neighbors", true));

	private int wallsDrawn;

	public ChunkBordersModule() {
		super("chunk_borders", ModuleCategory.TECHNICAL, false);
	}

	/** Walls drawn in the last frame (for the self-test). */
	public int getWallsDrawn() {
		return wallsDrawn;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		wallsDrawn = 0;
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}
		double bottom = client.world.getBottomY();
		double top = client.world.getTopYInclusive() + 1;
		double x0 = (client.player.getBlockX() >> 4) * 16.0;
		double z0 = (client.player.getBlockZ() >> 4) * 16.0;
		double x1 = x0 + 16.0;
		double z1 = z0 + 16.0;

		DrawStyle fill = DrawStyle.filled(color.get().withOpacity(opacity.get()));
		wallsDrawn = 0;
		wall(new Vec3d(x0, bottom, z0), new Vec3d(x1, top, z0), Direction.NORTH, fill);
		wall(new Vec3d(x0, bottom, z1), new Vec3d(x1, top, z1), Direction.NORTH, fill);
		wall(new Vec3d(x0, bottom, z0), new Vec3d(x0, top, z1), Direction.WEST, fill);
		wall(new Vec3d(x1, bottom, z0), new Vec3d(x1, top, z1), Direction.WEST, fill);

		int solid = color.get().withOpacity(100);
		for (int i = 0; i <= 1; i++) {
			for (int j = 0; j <= 1; j++) {
				GizmoDrawing.line(new Vec3d(x0 + i * 16, bottom, z0 + j * 16), new Vec3d(x0 + i * 16, top, z0 + j * 16), solid, 3.0F);
			}
		}
		if (rings.get()) {
			// Horizontal lines around the chunk every 8 blocks near the player: they make the
			// see-through walls readable and double as a height ruler.
			int ringColor = color.get().withOpacity(70);
			int base = Math.floorDiv(client.player.getBlockY(), RING_STEP) * RING_STEP;
			for (int y = base - RING_REACH; y <= base + RING_REACH; y += RING_STEP) {
				if (y < bottom || y > top) {
					continue;
				}
				Vec3d a = new Vec3d(x0, y, z0);
				Vec3d b = new Vec3d(x1, y, z0);
				Vec3d c = new Vec3d(x1, y, z1);
				Vec3d d = new Vec3d(x0, y, z1);
				GizmoDrawing.line(a, b, ringColor, 1.5F);
				GizmoDrawing.line(b, c, ringColor, 1.5F);
				GizmoDrawing.line(c, d, ringColor, 1.5F);
				GizmoDrawing.line(d, a, ringColor, 1.5F);
			}
		}
		if (neighbors.get()) {
			int faint = color.get().withOpacity(45);
			for (int i = -1; i <= 2; i++) {
				for (int j = -1; j <= 2; j++) {
					boolean ownCorner = (i == 0 || i == 1) && (j == 0 || j == 1);
					if (!ownCorner) {
						GizmoDrawing.line(new Vec3d(x0 + i * 16, bottom, z0 + j * 16), new Vec3d(x0 + i * 16, top, z0 + j * 16), faint, 1.5F);
					}
				}
			}
		}
	}

	/** A flat rectangle, drawn twice so it is visible from both sides. */
	private void wall(Vec3d min, Vec3d max, Direction facing, DrawStyle style) {
		GizmoDrawing.face(min, max, facing, style);
		GizmoDrawing.face(min, max, facing.getOpposite(), style);
		wallsDrawn++;
	}
}

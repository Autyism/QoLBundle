package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.render.HighlightColor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shows how far a beacon or a conduit reaches: look at one, or hold one to preview the spot you
 * are about to place it on. The numbers are the game's: a beacon covers a square column
 * (20 to 50 blocks to each side depending on its pyramid, the same distance down, and all the way
 * up); a conduit covers a ball of 16 blocks for every 7 frame blocks.
 */
public class EffectRangeModule extends Module {
	private static final double LOOK_RANGE = 32.0;
	private static final int CIRCLE_POINTS = 48;

	private final EnumSetting<HighlightColor> color = add(new EnumSetting<>("color", HighlightColor.CYAN));
	private final BoolSetting whenLooking = add(new BoolSetting("when_looking", true));
	private final BoolSetting whenHolding = add(new BoolSetting("when_holding", true));

	public enum Source {
		BEACON,
		CONDUIT
	}

	/**
	 * What is being shown.
	 *
	 * @param level beacon: pyramid level 0-4; conduit: number of frame blocks
	 * @param range blocks; 0 when the beacon has no pyramid / the conduit has too little frame
	 */
	public record Shown(Source source, BlockPos pos, int level, int range, boolean preview) {
	}

	@Nullable
	private Shown shown;

	public EffectRangeModule() {
		super("effect_range", ModuleCategory.TECHNICAL, true);
	}

	@Nullable
	public Shown getShown() {
		return shown;
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		shown = null;
		if (player == null || client.level == null) {
			return;
		}
		ClientLevel world = client.level;
		HitResult hit = player.pick(LOOK_RANGE, 1.0F, false);
		BlockHitResult blockHit = hit.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hit : null;

		if (whenLooking.get() && blockHit != null) {
			BlockState state = world.getBlockState(blockHit.getBlockPos());
			if (state.is(Blocks.BEACON)) {
				shown = beacon(world, blockHit.getBlockPos(), false);
				return;
			}
			if (state.is(Blocks.CONDUIT)) {
				shown = conduit(world, blockHit.getBlockPos(), false);
				return;
			}
		}
		if (whenHolding.get() && blockHit != null && blockHit.getLocation().distanceTo(player.getEyePosition()) <= 6.0) {
			// Where the block would go if placed now.
			BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
			boolean beaconInHand = player.getMainHandItem().is(Items.BEACON) || player.getOffhandItem().is(Items.BEACON);
			boolean conduitInHand = player.getMainHandItem().is(Items.CONDUIT) || player.getOffhandItem().is(Items.CONDUIT);
			if (beaconInHand) {
				shown = beacon(world, target, true);
			} else if (conduitInHand) {
				shown = conduit(world, target, true);
			}
		}
	}

	/** Same check as the game: complete layers of beacon base blocks under it, up to four. */
	private static Shown beacon(ClientLevel world, BlockPos pos, boolean preview) {
		int level = 0;
		for (int layer = 1; layer <= 4; layer++) {
			int y = pos.getY() - layer;
			if (y < world.getMinY() || !layerComplete(world, pos, layer, y)) {
				break;
			}
			level = layer;
		}
		return new Shown(Source.BEACON, pos, level, level == 0 ? 0 : level * 10 + 10, preview);
	}

	private static boolean layerComplete(ClientLevel world, BlockPos pos, int layer, int y) {
		BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
		for (int x = pos.getX() - layer; x <= pos.getX() + layer; x++) {
			for (int z = pos.getZ() - layer; z <= pos.getZ() + layer; z++) {
				if (!world.getBlockState(check.set(x, y, z)).is(BlockTags.BEACON_BASE_BLOCKS)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Same pattern as the game: the three prismarine rings around the conduit, 42 blocks at most. */
	private static Shown conduit(ClientLevel world, BlockPos pos, boolean preview) {
		int frame = 0;
		BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				for (int k = -2; k <= 2; k++) {
					int l = Math.abs(i);
					int m = Math.abs(j);
					int n = Math.abs(k);
					if ((l > 1 || m > 1 || n > 1) && (i == 0 && (m == 2 || n == 2) || j == 0 && (l == 2 || n == 2) || k == 0 && (l == 2 || m == 2))) {
						BlockState state = world.getBlockState(check.set(pos.getX() + i, pos.getY() + j, pos.getZ() + k));
						if (state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS)
								|| state.is(Blocks.SEA_LANTERN) || state.is(Blocks.DARK_PRISMARINE)) {
							frame++;
						}
					}
				}
			}
		}
		return new Shown(Source.CONDUIT, pos, frame, frame >= 16 ? frame / 7 * 16 : 0, preview);
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Shown current = shown;
		Minecraft client = Minecraft.getInstance();
		if (current == null || current.range <= 0 || client.player == null || client.level == null) {
			return;
		}
		int line = color.get().withOpacity(100);
		int faint = color.get().withOpacity(55);
		double playerY = client.player.getY() + 0.05;
		if (current.source == Source.BEACON) {
			double x0 = current.pos.getX() - current.range;
			double x1 = current.pos.getX() + 1 + current.range;
			double z0 = current.pos.getZ() - current.range;
			double z1 = current.pos.getZ() + 1 + current.range;
			double bottom = Math.max(client.level.getMinY(), current.pos.getY() - current.range);
			double top = client.level.getMaxY() + 1;
			for (double x : new double[] {x0, x1}) {
				for (double z : new double[] {z0, z1}) {
					Gizmos.line(new Vec3(x, bottom, z), new Vec3(x, top, z), line, 3.0F);
				}
			}
			rectangle(x0, z0, x1, z1, bottom, faint);
			rectangle(x0, z0, x1, z1, current.pos.getY(), faint);
			// The border at your own height is the one you can walk up to.
			rectangle(x0, z0, x1, z1, Math.max(bottom, playerY), line);
		} else {
			Vec3 center = Vec3.atCenterOf(current.pos);
			double r = current.range;
			circle(center, r, 0, line);
			circle(center, r, 1, faint);
			circle(center, r, 2, faint);
			// Where the ball cuts through your own height.
			double dy = playerY - center.y;
			if (Math.abs(dy) < r) {
				circle(new Vec3(center.x, playerY, center.z), Math.sqrt(r * r - dy * dy), 0, line);
			}
		}
	}

	private static void rectangle(double x0, double z0, double x1, double z1, double y, int color) {
		Vec3 a = new Vec3(x0, y, z0);
		Vec3 b = new Vec3(x1, y, z0);
		Vec3 c = new Vec3(x1, y, z1);
		Vec3 d = new Vec3(x0, y, z1);
		Gizmos.line(a, b, color, 2.0F);
		Gizmos.line(b, c, color, 2.0F);
		Gizmos.line(c, d, color, 2.0F);
		Gizmos.line(d, a, color, 2.0F);
	}

	/** A circle around the centre. plane 0 = flat, 1 and 2 = the two upright ones. */
	private static void circle(Vec3 center, double radius, int plane, int color) {
		Vec3 previous = null;
		for (int i = 0; i <= CIRCLE_POINTS; i++) {
			double angle = i * 2.0 * Math.PI / CIRCLE_POINTS;
			double a = Math.cos(angle) * radius;
			double b = Math.sin(angle) * radius;
			Vec3 point = switch (plane) {
				case 1 -> center.add(a, b, 0);
				case 2 -> center.add(0, b, a);
				default -> center.add(a, 0, b);
			};
			if (previous != null) {
				Gizmos.line(previous, point, color, 2.0F);
			}
			previous = point;
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Shown current = shown;
		if (current == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		Component text;
		if (current.source == Source.BEACON) {
			text = current.range > 0
					? Component.translatable(key + "beacon", current.level, current.range)
					: Component.translatable(key + "beacon_none");
		} else {
			text = current.range > 0
					? Component.translatable(key + "conduit", current.range, current.level)
					: Component.translatable(key + "conduit_none", current.level);
		}
		if (current.preview) {
			text = Component.translatable(key + "preview", text);
		}
		int width = client.font.width(text);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 + 30;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawString(client.font, text, x, y, current.range > 0 ? 0xFF55FFFF : 0xFFAAAAAA);
	}
}

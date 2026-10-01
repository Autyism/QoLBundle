package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.jspecify.annotations.Nullable;

/**
 * Lava safety net. Two jobs:
 * a warning (orange screen edges, a line of text) while there is lava within a few blocks below
 * your feet with nothing solid in between, and, once you are in lava, an arrow to the nearest
 * spot you can stand on.
 *
 * <p>No seeing through walls: lava only counts when the way down to it is open, and the escape
 * spot is searched among the blocks right around you.
 */
public class LavaSafetyModule extends Module {
	private static final int SCAN_TICKS = 4;
	private static final int EDGE_DEPTH = 22;

	private final IntSetting depth = add(new IntSetting("depth", 5, 1, 12));
	private final BoolSetting edgeWarning = add(new BoolSetting("edge_warning", true));
	private final BoolSetting warningText = add(new BoolSetting("warning_text", true));
	private final BoolSetting warningSound = add(new BoolSetting("warning_sound", true));
	private final BoolSetting escapeArrow = add(new BoolSetting("escape_arrow", true));
	private final IntSetting searchRadius = add(new IntSetting("search_radius", 10, 4, 16));

	/** Blocks down to the nearest lava under the player, 0 when there is none in range. */
	private int lavaBelow;
	private boolean inLava;
	/** Where to stand: the block position of the feet. */
	@Nullable
	private BlockPos escape;
	private int ticks;

	public LavaSafetyModule() {
		super("lava_safety", ModuleCategory.INFO, true);
	}

	/** Blocks down to open lava below the player, 0 if none (for the self-test). */
	public int getLavaBelow() {
		return lavaBelow;
	}

	public boolean isInLava() {
		return inLava;
	}

	@Nullable
	public BlockPos getEscape() {
		return escape;
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			lavaBelow = 0;
			inLava = false;
			escape = null;
			return;
		}
		// Lava cannot hurt in these cases, so stay quiet.
		if (player.getAbilities().invulnerable || player.isSpectator() || player.isFireImmune()
				|| player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
			lavaBelow = 0;
			inLava = false;
			escape = null;
			return;
		}
		if (ticks++ % SCAN_TICKS != 0) {
			return;
		}
		ClientWorld world = client.world;
		boolean wasInLava = inLava;
		int before = lavaBelow;
		inLava = player.isInLava();
		if (inLava) {
			lavaBelow = 0;
			escape = findEscape(world, player);
		} else {
			escape = null;
			lavaBelow = scanBelow(world, player);
			if (lavaBelow > 0 && before == 0 && !wasInLava && warningSound.get()) {
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.2F));
			}
		}
	}

	/**
	 * Looks straight down under the player and the eight columns around, from the feet until
	 * something solid. Lava met on the way counts; lava under a floor does not.
	 */
	private int scanBelow(ClientWorld world, ClientPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		int nearest = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int down = 0; down <= depth.get(); down++) {
					pos.set(feet.getX() + dx, feet.getY() - down, feet.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					if (state.getFluidState().isIn(FluidTags.LAVA)) {
						if (nearest == 0 || down < nearest) {
							nearest = Math.max(1, down);
						}
						break;
					}
					if (!state.getCollisionShape(world, pos).isEmpty()) {
						break; // a floor (or a wall): what lies under it is neither visible nor a danger
					}
				}
			}
		}
		return nearest;
	}

	/** Nearest place with a solid block to stand on and two free blocks above it. */
	@Nullable
	private BlockPos findEscape(ClientWorld world, ClientPlayerEntity player) {
		BlockPos center = player.getBlockPos();
		int reach = searchRadius.get();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = -3; dy <= 6; dy++) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					// Climbing costs more than walking, so prefer spots that are not far above.
					double distance = dx * dx + dz * dz + dy * dy * (dy > 0 ? 3.0 : 1.0);
					if (distance < bestDistance && canStandAt(world, pos)) {
						bestDistance = distance;
						best = pos.toImmutable();
					}
				}
			}
		}
		return best;
	}

	private static boolean canStandAt(ClientWorld world, BlockPos.Mutable feet) {
		if (!isFree(world, feet)) {
			return false;
		}
		feet.move(Direction.UP);
		boolean headFree = isFree(world, feet);
		feet.move(Direction.DOWN, 2);
		BlockState floor = world.getBlockState(feet);
		boolean solidFloor = floor.isSideSolidFullSquare(world, feet, Direction.UP)
				&& !floor.isOf(Blocks.MAGMA_BLOCK) && !floor.isOf(Blocks.CAMPFIRE) && !floor.isOf(Blocks.SOUL_CAMPFIRE);
		feet.move(Direction.UP);
		return headFree && solidFloor;
	}

	/** Nothing to bump into, and neither lava nor fire. */
	private static boolean isFree(ClientWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty()
				&& !state.isOf(Blocks.FIRE) && !state.isOf(Blocks.SOUL_FIRE);
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		BlockPos target = escape;
		if (inLava && target != null && escapeArrow.get()) {
			GizmoDrawing.box(new Box(target).contract(0.1), DrawStyle.stroked(0xFF55FF55, 3.0F));
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		int centerX = layout.getScreenWidth() / 2;
		int centerY = layout.getScreenHeight() / 2;

		if (inLava && escapeArrow.get()) {
			BlockPos target = escape;
			Text text;
			if (target == null) {
				text = Text.translatable(key + "no_escape");
			} else {
				Vec3d spot = Vec3d.ofBottomCenter(target);
				Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
				float angle = (float) Math.toRadians(SoundCompassModule.relativeAngle(eye, client.gameRenderer.getCamera().getYaw(), spot));
				context.getMatrices().pushMatrix();
				context.getMatrices().translate(centerX, centerY);
				context.getMatrices().rotate(angle);
				// A fat arrow head 34 px from the crosshair, pointing at the safe spot.
				for (int row = 0; row < 12; row++) {
					context.fill(-row, -46 + row, row + 1, -45 + row, 0xFF55FF55);
				}
				context.getMatrices().popMatrix();
				int distance = (int) Math.round(Math.sqrt(target.getSquaredDistance(client.player.getBlockPos())));
				int height = target.getY() - client.player.getBlockY();
				text = Text.translatable(key + (height > 0 ? "escape_up" : "escape"), distance, Math.abs(height));
			}
			drawCentered(context, client, text, centerX, centerY + 56, 0xFF55FF55);
			return;
		}

		if (lavaBelow > 0) {
			if (edgeWarning.get()) {
				drawOrangeEdges(context, layout.getScreenWidth(), layout.getScreenHeight());
			}
			if (warningText.get()) {
				drawCentered(context, client, Text.translatable(key + "below", lavaBelow), centerX, centerY + 30, 0xFFFFAA00);
			}
		}
	}

	private static void drawCentered(DrawContext context, MinecraftClient client, Text text, int centerX, int y, int color) {
		int width = client.textRenderer.getWidth(text);
		context.fill(centerX - width / 2 - 3, y - 2, centerX + width / 2 + 3, y + 10, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, text, centerX - width / 2, y, color);
	}

	private static void drawOrangeEdges(DrawContext context, int width, int height) {
		for (int i = 0; i < EDGE_DEPTH; i++) {
			float fade = 1F - i / (float) EDGE_DEPTH;
			int alpha = MathHelper.clamp((int) (140 * fade * fade), 0, 255);
			int color = alpha << 24 | 0xFF7A00;
			context.fill(i, i, width - i, i + 1, color);
			context.fill(i, height - i - 1, width - i, height - i, color);
			context.fill(i, i + 1, i + 1, height - i - 1, color);
			context.fill(width - i - 1, i + 1, width - i, height - i - 1, color);
		}
	}
}

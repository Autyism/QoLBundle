package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.mixin.BlockItemInvoker;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
//? if <26.1
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
//? if >=26.1 {
/*import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import java.util.ArrayList;
*///?} else {
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
//? if <26.3
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Placement master, three aids for placing blocks the right way round:
 * <ol>
 * <li>a see-through preview of the block exactly as it would be placed (which way it faces);</li>
 * <li>for slabs, stairs and trapdoors, a mark on the face you aim at showing whether you are in
 * the "upper half" or "lower half" zone;</li>
 * <li>while the lock key is held, a placement that would come out differently from your last one
 * is stopped before it happens.</li>
 * </ol>
 *
 * <p>Everything here is preview and "don't do it". A click is either passed on untouched or not
 * made at all; what the server receives is never altered.
 */
public class PlacementMasterModule extends Module {
	public enum PreviewBlocks {
		ORIENTED,
		ALL
	}

	/** What makes two placements "the same way round". Shapes that depend on neighbours are left out. */
	private static final List<Property<?>> ORIENTATION = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
			BlockStateProperties.FACING_HOPPER, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.HALF, BlockStateProperties.SLAB_TYPE,
			BlockStateProperties.ATTACH_FACE, BlockStateProperties.ROTATION_16, BlockStateProperties.ORIENTATION, BlockStateProperties.DOOR_HINGE,
			BlockStateProperties.VERTICAL_DIRECTION, BlockStateProperties.BELL_ATTACHMENT);
	private static final List<Property<Direction>> FACINGS = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING_HOPPER);
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;

	/** A placement worked out in advance: this state would appear at this position. */
	public record Placement(BlockPos pos, BlockState state, BlockHitResult hit) {
	}

	private final BoolSetting preview = add(new BoolSetting("preview", true));
	private final EnumSetting<PreviewBlocks> previewBlocks = add(new EnumSetting<>("preview_blocks", PreviewBlocks.ORIENTED));
	private final IntSetting opacity = add(new IntSetting("opacity", 55, 20, 90, "%"));
	private final BoolSetting halfGuide = add(new BoolSetting("half_guide", true));
	private final BoolSetting showLabel = add(new BoolSetting("show_label", true));
	private final BoolSetting lock = add(new BoolSetting("lock", true));

	private final KeyMapping lockKey;
	@Nullable
	private Placement current;
	/** Orientation of the last block that really got placed (property name to value). */
	@Nullable
	private Map<String, String> lastOrientation;
	@Nullable
	private Placement pending;
	private int pendingTicks;
	private int stoppedCount;

	public PlacementMasterModule() {
		super("placement_master", ModuleCategory.TOOLS, true);
		lockKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.placement_lock",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, QoLBundleClient.KEY_CATEGORY));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			Minecraft client = Minecraft.getInstance();
			if (!world.isClientSide() || !isEnabled() || player != client.player) {
				return InteractionResult.PASS;
			}
			Placement placement = predict(client.player, hand, hit);
			if (placement == null) {
				return InteractionResult.PASS;
			}
			if (isLocking() && conflicts(placement.state)) {
				// Not the way round you locked: the click is simply not made.
				stoppedCount++;
				client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".stopped").withStyle(ChatFormatting.RED), false);
				return InteractionResult.FAIL;
			}
			pending = placement;
			pendingTicks = 4;
			return InteractionResult.PASS; // untouched, the game goes on as usual
		});
	}

	public KeyMapping getLockKey() {
		return lockKey;
	}

	/** The placement currently previewed, or null (nothing to place where the player looks). */
	@Nullable
	public Placement getCurrent() {
		return current;
	}

	@Nullable
	public Map<String, String> getLastOrientation() {
		return lastOrientation;
	}

	/** How many clicks the lock has stopped (for the self-test). */
	public int getStoppedCount() {
		return stoppedCount;
	}

	/** True when the lock key is held and there is something to compare with. */
	public boolean isLocking() {
		return lock.get() && lockKey.isDown() && lastOrientation != null;
	}

	/** True when this state is turned differently from the last block placed. */
	public boolean conflicts(BlockState state) {
		if (lastOrientation == null) {
			return false;
		}
		for (Map.Entry<String, String> entry : orientationOf(state).entrySet()) {
			String locked = lastOrientation.get(entry.getKey());
			if (locked != null && !locked.equals(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		current = null;
		pending = null;
		lastOrientation = null;
	}

	// ---- working out the placement -------------------------------------------------------------

	/** The same steps the game takes when a block item is used on a block, without placing anything. */
	@Nullable
	private static Placement predict(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem item) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
		if (!context.canPlace()) {
			return null;
		}
		context = item.updatePlacementContext(context);
		if (context == null) {
			return null;
		}
		BlockState state = ((BlockItemInvoker) item).qolbundle$getPlacementState(context);
		return state == null ? null : new Placement(context.getClickedPos().immutable(), state, hit);
	}

	private static Map<String, String> orientationOf(BlockState state) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Property<?> property : ORIENTATION) {
			if (state.hasProperty(property)) {
				result.put(property.getName(), valueName(state, property));
			}
		}
		return result;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel world = client.level;
		current = null;
		if (player == null || world == null) {
			pending = null;
			return;
		}
		if (pending != null) {
			// The block is there on the client right after the click when the placement went through.
			BlockState placed = world.getBlockState(pending.pos);
			if (placed.is(pending.state.getBlock())) {
				Map<String, String> orientation = orientationOf(placed);
				if (!orientation.isEmpty()) {
					lastOrientation = orientation;
				}
				pending = null;
			} else if (--pendingTicks <= 0) {
				pending = null;
			}
		}
		if (client.screen != null || !(client.hitResult instanceof BlockHitResult hit)) {
			return;
		}
		InteractionHand hand = player.getMainHandItem().getItem() instanceof BlockItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		current = predict(player, hand, hit);
	}

	/** True when the see-through block is being drawn right now. */
	public boolean isPreviewShown() {
		return current != null && preview.get() && isShown(current);
	}

	private boolean isShown(Placement placement) {
		return previewBlocks.get() == PreviewBlocks.ALL || !orientationOf(placement.state).isEmpty();
	}

	/** For slabs, stairs, trapdoors aimed at the side of a block: +1 upper half, -1 lower half, 0 no such choice. */
	public int halfZone() {
		Placement placement = current;
		if (placement == null || !placement.hit.getDirection().getAxis().isHorizontal()) {
			return 0;
		}
		BlockState state = placement.state;
		if (state.hasProperty(BlockStateProperties.HALF)) {
			return state.getValue(BlockStateProperties.HALF) == Half.TOP ? 1 : -1;
		}
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
			return state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP ? 1 : -1;
		}
		return 0;
	}

	// ---- drawing -------------------------------------------------------------------------------

	private int accent(Placement placement) {
		return isLocking() ? (conflicts(placement.state) ? RED : GREEN) : WHITE;
	}

	/** The see-through block itself, handed to the game together with the entities of this frame. */
	@Override
	public void onSubmitWorld(WorldRenderContext context) {
		Placement placement = current;
		Minecraft client = Minecraft.getInstance();
		if (placement == null || !preview.get() || !isShown(placement) || client.level == null) {
			return;
		}
		BlockState state = placement.state;
		BlockPos pos = placement.pos;
		//? if >=26.1 {
		/*BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
		*///?} else
		BlockStateModel model = client.getBlockRenderer().getBlockModel(state);
		ClientLevel world = client.level;
		boolean wrong = isLocking() && conflicts(state);
		float alpha = opacity.get() / 100F;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		//? if >=26.1 {
		/*PoseStack matrices = context.poseStack();
		*///?} else
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		// A hair smaller than a real block, so its faces do not flicker against the neighbours.
		matrices.translate(0.5, 0.5, 0.5);
		matrices.scale(0.998F, 0.998F, 0.998F);
		matrices.translate(-0.5, -0.5, -0.5);
		//? if >=26.2 {
		/*// Queued with the game's own see-through blocks, which are drawn after the debug lines
		// (custom geometry would be drawn before them and hide the lines behind the block).
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);
		List<BlockTintSource> sources = client.getBlockColors().getTintSources(state);
		int[] tints = new int[sources.size()];
		for (int i = 0; i < tints.length; i++) {
			tints[i] = sources.get(i).colorInWorld(state, world, pos);
		}
		int color = ARGB.colorFromFloat(alpha, 1F, wrong ? 0.35F : 1F, wrong ? 0.35F : 1F);
		if (context.submitNodeCollector().order(0) instanceof net.minecraft.client.renderer.SubmitNodeCollection queue) {
			queue.translucentBlocksAndItems.submit(new net.minecraft.client.renderer.feature.BlockModelFeatureRenderer.Submit(matrices.last().copy(),
					Sheets.translucentBlockItemSheet(), parts, tints, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null));
		} else {
			// Another renderer took over the queue: draw it as plain geometry.
			context.submitNodeCollector().submitCustomGeometry(matrices, Sheets.translucentBlockItemSheet(), (entry, consumer) -> {
				QuadInstance instance = new QuadInstance();
				instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
				instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
				for (BlockStateModelPart part : parts) {
					for (int side = -1; side < 6; side++) {
						for (BakedQuad quad : part.getQuads(side < 0 ? null : Direction.from3DDataValue(side))) {
							int tintIndex = quad.materialInfo().tintIndex();
							instance.setColor(tintIndex >= 0 && tintIndex < tints.length ? ARGB.multiply(color, tints[tintIndex]) : color);
							consumer.putBakedQuad(entry, quad, instance);
						}
					}
				}
			});
		}
		*///?} elif >=26.1 {
		/*net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = context.bufferSource();
		VertexConsumer consumer = buffers.getBuffer(Sheets.translucentBlockItemSheet());
		PoseStack.Pose entry = matrices.last();
		{
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			for (BlockStateModelPart part : parts) {
				for (int side = -1; side < 6; side++) {
					for (BakedQuad quad : part.getQuads(side < 0 ? null : Direction.from3DDataValue(side))) {
						float red = 1F;
						float green = 1F;
						float blue = 1F;
						if (quad.materialInfo().isTinted()) {
							BlockTintSource source = client.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
							int tint = source == null ? -1 : source.colorInWorld(state, world, pos);
							red = (tint >> 16 & 0xFF) / 255F;
							green = (tint >> 8 & 0xFF) / 255F;
							blue = (tint & 0xFF) / 255F;
						}
						if (wrong) {
							green *= 0.35F;
							blue *= 0.35F;
						}
						QuadInstance instance = new QuadInstance();
						instance.setColor(ARGB.colorFromFloat(alpha, red, green, blue));
						instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
						instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
						consumer.putBakedQuad(entry, quad, instance);
					}
				}
			}
		}
		buffers.endBatch(Sheets.translucentBlockItemSheet());
		*///?} else {
		context.commandQueue().submitCustomGeometry(matrices, Sheets.translucentBlockItemSheet(), (entry, consumer) -> {
			for (BlockModelPart part : model.collectParts(RandomSource.create(42L))) {
				for (int side = -1; side < 6; side++) {
					for (BakedQuad quad : part.getQuads(side < 0 ? null : Direction.from3DDataValue(side))) {
						float red = 1F;
						float green = 1F;
						float blue = 1F;
						if (quad.isTinted()) {
							int tint = client.getBlockColors().getColor(state, world, pos, quad.tintIndex());
							red = (tint >> 16 & 0xFF) / 255F;
							green = (tint >> 8 & 0xFF) / 255F;
							blue = (tint & 0xFF) / 255F;
						}
						if (wrong) {
							green *= 0.35F;
							blue *= 0.35F;
						}
						consumer.putBulkData(entry, quad, red, green, blue, alpha, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
					}
				}
			}
		});
		//?}
		matrices.popPose();
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Placement placement = current;
		Minecraft client = Minecraft.getInstance();
		if (placement == null || client.level == null) {
			return;
		}
		int color = accent(placement);
		if (preview.get() && isShown(placement)) {
			BlockPos pos = placement.pos;
			for (AABB box : placement.state.getShape(client.level, pos).toAabbs()) {
				Gizmos.cuboid(box.move(pos), GizmoStyle.stroke(color, 2.0F));
			}
			// Which way it faces: an arrow out of the middle of the block.
			for (Property<Direction> property : FACINGS) {
				if (placement.state.hasProperty(property)) {
					Vec3 middle = Vec3.atCenterOf(pos);
					Direction facing = placement.state.getValue(property);
					Gizmos.arrow(middle, middle.add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.95)), 0xFFFFFF55, 3.0F).setAlwaysOnTop();
					break;
				}
			}
		}
		int zone = halfZone();
		if (halfGuide.get() && zone != 0) {
			// The face being aimed at, split in two: the half the crosshair is in is the half you get.
			BlockPos on = placement.hit.getBlockPos();
			Direction side = placement.hit.getDirection();
			Vec3 out = Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.004);
			double low = on.getY() + (zone > 0 ? 0.5 : 0.0);
			Vec3 min = new Vec3(on.getX(), low, on.getZ()).add(out);
			Vec3 max = new Vec3(on.getX() + 1, low + 0.5, on.getZ() + 1).add(out);
			Gizmos.rect(min, max, side, GizmoStyle.strokeAndFill(0xFF55FFFF, 2.0F, 0x5055FFFF));
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Placement placement = current;
		if (placement == null || !showLabel.get()) {
			return;
		}
		Map<String, String> orientation = orientationOf(placement.state);
		if (orientation.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int y = layout.getScreenHeight() / 2 + 14;
		drawCentered(context, client, describe(orientation), centerX, y, WHITE);
		if (isLocking()) {
			boolean wrong = conflicts(placement.state);
			Component line = Component.translatable(getTranslationKey() + (wrong ? ".hud.lock_wrong" : ".hud.lock_ok"), describe(lastOrientation));
			drawCentered(context, client, line, centerX, y + 11, wrong ? RED : GREEN);
		}
	}

	private static void drawCentered(GuiGraphics context, Minecraft client, Component text, int centerX, int y, int color) {
		int width = client.font.width(text);
		context.fill(centerX - width / 2 - 3, y - 1, centerX + width / 2 + 3, y + 9, 0x80000000);
		context.drawString(client.font, text, centerX - width / 2, y, color);
	}

	/** "facing north · upper half" from the orientation properties, in the player's language. */
	private Component describe(Map<String, String> orientation) {
		String key = getTranslationKey() + ".";
		MutableComponent text = Component.empty();
		boolean first = true;
		for (Map.Entry<String, String> entry : orientation.entrySet()) {
			if (!first) {
				text.append(Component.literal(" · "));
			}
			first = false;
			String valueKey = key + "value." + entry.getValue();
			Component value = I18n.exists(valueKey) ? Component.translatable(valueKey) : Component.literal(entry.getValue());
			String propertyKey = key + "property." + entry.getKey();
			text.append(I18n.exists(propertyKey) ? Component.translatable(propertyKey, value) : Component.literal(entry.getKey() + " " + entry.getValue()));
		}
		return text;
	}

	/** Used by the self-test: the block of the previewed placement. */
	@Nullable
	public Block getCurrentBlock() {
		return current == null ? null : current.state.getBlock();
	}
}

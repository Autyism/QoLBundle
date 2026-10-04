package io.github.autyism.qolbundle.modules;

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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.jspecify.annotations.Nullable;
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
	private static final List<Property<?>> ORIENTATION = List.of(Properties.FACING, Properties.HORIZONTAL_FACING,
			Properties.HOPPER_FACING, Properties.AXIS, Properties.HORIZONTAL_AXIS, Properties.BLOCK_HALF, Properties.SLAB_TYPE,
			Properties.BLOCK_FACE, Properties.ROTATION, Properties.ORIENTATION, Properties.DOOR_HINGE,
			Properties.VERTICAL_DIRECTION, Properties.ATTACHMENT);
	private static final List<Property<Direction>> FACINGS = List.of(Properties.FACING, Properties.HORIZONTAL_FACING, Properties.HOPPER_FACING);
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

	private final KeyBinding lockKey;
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
		lockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.placement_lock",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, QoLBundleClient.KEY_CATEGORY));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (!world.isClient() || !isEnabled() || player != client.player) {
				return ActionResult.PASS;
			}
			Placement placement = predict(client.player, hand, hit);
			if (placement == null) {
				return ActionResult.PASS;
			}
			if (isLocking() && conflicts(placement.state)) {
				// Not the way round you locked: the click is simply not made.
				stoppedCount++;
				client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".stopped").formatted(Formatting.RED), false);
				return ActionResult.FAIL;
			}
			pending = placement;
			pendingTicks = 4;
			return ActionResult.PASS; // untouched, the game goes on as usual
		});
	}

	public KeyBinding getLockKey() {
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
		return lock.get() && lockKey.isPressed() && lastOrientation != null;
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
	private static Placement predict(ClientPlayerEntity player, Hand hand, BlockHitResult hit) {
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof BlockItem item) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		ItemPlacementContext context = new ItemPlacementContext(player, hand, stack, hit);
		if (!context.canPlace()) {
			return null;
		}
		context = item.getPlacementContext(context);
		if (context == null) {
			return null;
		}
		BlockState state = ((BlockItemInvoker) item).qolbundle$getPlacementState(context);
		return state == null ? null : new Placement(context.getBlockPos().toImmutable(), state, hit);
	}

	private static Map<String, String> orientationOf(BlockState state) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Property<?> property : ORIENTATION) {
			if (state.contains(property)) {
				result.put(property.getName(), valueName(state, property));
			}
		}
		return result;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.name(state.get(property));
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		current = null;
		if (player == null || world == null) {
			pending = null;
			return;
		}
		if (pending != null) {
			// The block is there on the client right after the click when the placement went through.
			BlockState placed = world.getBlockState(pending.pos);
			if (placed.isOf(pending.state.getBlock())) {
				Map<String, String> orientation = orientationOf(placed);
				if (!orientation.isEmpty()) {
					lastOrientation = orientation;
				}
				pending = null;
			} else if (--pendingTicks <= 0) {
				pending = null;
			}
		}
		if (client.currentScreen != null || !(client.crosshairTarget instanceof BlockHitResult hit)) {
			return;
		}
		Hand hand = player.getMainHandStack().getItem() instanceof BlockItem ? Hand.MAIN_HAND : Hand.OFF_HAND;
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
		if (placement == null || !placement.hit.getSide().getAxis().isHorizontal()) {
			return 0;
		}
		BlockState state = placement.state;
		if (state.contains(Properties.BLOCK_HALF)) {
			return state.get(Properties.BLOCK_HALF) == BlockHalf.TOP ? 1 : -1;
		}
		if (state.contains(Properties.SLAB_TYPE) && state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE) {
			return state.get(Properties.SLAB_TYPE) == SlabType.TOP ? 1 : -1;
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (placement == null || !preview.get() || !isShown(placement) || client.world == null) {
			return;
		}
		BlockState state = placement.state;
		BlockPos pos = placement.pos;
		BlockStateModel model = client.getBlockRenderManager().getModel(state);
		ClientWorld world = client.world;
		boolean wrong = isLocking() && conflicts(state);
		float alpha = opacity.get() / 100F;
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		MatrixStack matrices = context.matrices();
		matrices.push();
		matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		// A hair smaller than a real block, so its faces do not flicker against the neighbours.
		matrices.translate(0.5, 0.5, 0.5);
		matrices.scale(0.998F, 0.998F, 0.998F);
		matrices.translate(-0.5, -0.5, -0.5);
		context.commandQueue().submitCustom(matrices, TexturedRenderLayers.getBlockTranslucentCull(), (entry, consumer) -> {
			for (BlockModelPart part : model.getParts(Random.create(42L))) {
				for (int side = -1; side < 6; side++) {
					for (BakedQuad quad : part.getQuads(side < 0 ? null : Direction.byIndex(side))) {
						float red = 1F;
						float green = 1F;
						float blue = 1F;
						if (quad.hasTint()) {
							int tint = client.getBlockColors().getColor(state, world, pos, quad.tintIndex());
							red = (tint >> 16 & 0xFF) / 255F;
							green = (tint >> 8 & 0xFF) / 255F;
							blue = (tint & 0xFF) / 255F;
						}
						if (wrong) {
							green *= 0.35F;
							blue *= 0.35F;
						}
						consumer.quad(entry, quad, red, green, blue, alpha, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
					}
				}
			}
		});
		matrices.pop();
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		Placement placement = current;
		MinecraftClient client = MinecraftClient.getInstance();
		if (placement == null || client.world == null) {
			return;
		}
		int color = accent(placement);
		if (preview.get() && isShown(placement)) {
			BlockPos pos = placement.pos;
			for (Box box : placement.state.getOutlineShape(client.world, pos).getBoundingBoxes()) {
				GizmoDrawing.box(box.offset(pos), DrawStyle.stroked(color, 2.0F));
			}
			// Which way it faces: an arrow out of the middle of the block.
			for (Property<Direction> property : FACINGS) {
				if (placement.state.contains(property)) {
					Vec3d middle = Vec3d.ofCenter(pos);
					Direction facing = placement.state.get(property);
					GizmoDrawing.arrow(middle, middle.add(Vec3d.of(facing.getVector()).multiply(0.95)), 0xFFFFFF55, 3.0F).ignoreOcclusion();
					break;
				}
			}
		}
		int zone = halfZone();
		if (halfGuide.get() && zone != 0) {
			// The face being aimed at, split in two: the half the crosshair is in is the half you get.
			BlockPos on = placement.hit.getBlockPos();
			Direction side = placement.hit.getSide();
			Vec3d out = Vec3d.of(side.getVector()).multiply(0.004);
			double low = on.getY() + (zone > 0 ? 0.5 : 0.0);
			Vec3d min = new Vec3d(on.getX(), low, on.getZ()).add(out);
			Vec3d max = new Vec3d(on.getX() + 1, low + 0.5, on.getZ() + 1).add(out);
			GizmoDrawing.face(min, max, side, DrawStyle.filledAndStroked(0xFF55FFFF, 2.0F, 0x5055FFFF));
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		Placement placement = current;
		if (placement == null || !showLabel.get()) {
			return;
		}
		Map<String, String> orientation = orientationOf(placement.state);
		if (orientation.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int centerX = layout.getScreenWidth() / 2;
		int y = layout.getScreenHeight() / 2 + 14;
		drawCentered(context, client, describe(orientation), centerX, y, WHITE);
		if (isLocking()) {
			boolean wrong = conflicts(placement.state);
			Text line = Text.translatable(getTranslationKey() + (wrong ? ".hud.lock_wrong" : ".hud.lock_ok"), describe(lastOrientation));
			drawCentered(context, client, line, centerX, y + 11, wrong ? RED : GREEN);
		}
	}

	private static void drawCentered(DrawContext context, MinecraftClient client, Text text, int centerX, int y, int color) {
		int width = client.textRenderer.getWidth(text);
		context.fill(centerX - width / 2 - 3, y - 1, centerX + width / 2 + 3, y + 9, 0x80000000);
		context.drawTextWithShadow(client.textRenderer, text, centerX - width / 2, y, color);
	}

	/** "facing north · upper half" from the orientation properties, in the player's language. */
	private Text describe(Map<String, String> orientation) {
		String key = getTranslationKey() + ".";
		MutableText text = Text.empty();
		boolean first = true;
		for (Map.Entry<String, String> entry : orientation.entrySet()) {
			if (!first) {
				text.append(Text.literal(" · "));
			}
			first = false;
			String valueKey = key + "value." + entry.getValue();
			Text value = I18n.hasTranslation(valueKey) ? Text.translatable(valueKey) : Text.literal(entry.getValue());
			String propertyKey = key + "property." + entry.getKey();
			text.append(I18n.hasTranslation(propertyKey) ? Text.translatable(propertyKey, value) : Text.literal(entry.getKey() + " " + entry.getValue()));
		}
		return text;
	}

	/** Used by the self-test: the block of the previewed placement. */
	@Nullable
	public Block getCurrentBlock() {
		return current == null ? null : current.state.getBlock();
	}
}

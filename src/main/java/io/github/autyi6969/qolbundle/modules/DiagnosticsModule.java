package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.hud.HudAnchor;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BulbBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DetectorRailBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TargetBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.enums.ComparatorMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redstone machine diagnostics. Look at a redstone component and press the scan key: everything
 * connected to it is taken as "the machine", and from then on five views of it are kept up to date:
 * <ol>
 * <li>signal flow: arrows along the redstone lines, and marks where a signal dies;</li>
 * <li>bottlenecks: locked hoppers, repeater delays, the slowest repeating component, more drops
 * than one hopper line can carry;</li>
 * <li>overview: how many of each component there are and how many are on right now;</li>
 * <li>output rate: dropped items appearing at the machine (per minute / hour), and what was added
 * to its chests between two times you opened them;</li>
 * <li>slice: only one layer of the machine, shown through the blocks around it.</li>
 * </ol>
 *
 * <p>Everything is read from block states and entities the client already has. What is inside a
 * hopper or chest is not known to the client unless the player opens it.
 */
public class DiagnosticsModule extends Module {
	public enum Kind {
		WIRE, REPEATER, COMPARATOR, TORCH, OBSERVER, PISTON, HOPPER, DROPPER, CRAFTER, LAMP, INPUT, SOURCE, RAIL,
		NOTE_BLOCK, DOOR, TNT, CONTAINER
	}

	public enum SliceAxis {
		Y, X, Z
	}

	/** One component of the scanned machine. */
	public static final class Part {
		public final BlockPos pos;
		public final Kind kind;
		BlockState state;
		boolean active;
		long lastRise = -1;
		/** Game ticks between the last two times it switched on; 0 while unknown. */
		int period;
		boolean gone;

		Part(BlockPos pos, Kind kind, BlockState state) {
			this.pos = pos;
			this.kind = kind;
			this.state = state;
		}

		public int getPeriod() {
			return period;
		}

		public boolean isActive() {
			return active;
		}
	}

	private static final class ChestWatch {
		long baseMs;
		Map<String, Integer> base = Map.of();
		long latestMs;
		Map<String, Integer> latest = Map.of();
	}

	private static final int MAX_PARTS = 1500;
	private static final double DRAW_RANGE = 64.0;
	/** One hopper line moves an item every 8 game ticks. */
	private static final double HOPPER_ITEMS_PER_SECOND = 2.5;
	private static final long MIN_CHEST_INTERVAL_MS = 20_000;
	private static final int ORANGE = 0xFFFFAA00;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GRAY = 0xFF808080;

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));
	private final BoolSetting signalFlow = add(new BoolSetting("signal_flow", true));
	private final BoolSetting bottlenecks = add(new BoolSetting("bottlenecks", true));
	private final BoolSetting overview = add(new BoolSetting("overview", true));
	private final BoolSetting rates = add(new BoolSetting("rates", true));
	private final EnumSetting<SliceAxis> sliceAxis = add(new EnumSetting<>("slice_axis", SliceAxis.Y));
	private final IntSetting scanRadius = add(new IntSetting("scan_radius", 24, 8, 48));

	private final KeyBinding scanKey;
	private final KeyBinding sliceUpKey;
	private final KeyBinding sliceDownKey;

	private final List<Part> parts = new ArrayList<>();
	private final Map<BlockPos, Part> byPos = new HashMap<>();
	@Nullable
	private ClientWorld scannedWorld;
	private BlockPos min = BlockPos.ORIGIN;
	private BlockPos max = BlockPos.ORIGIN;
	private long tick;
	private long scanTick;
	private long scanWallMs;
	private boolean changed;
	/** Which layer is shown alone; -1 = the whole machine. */
	private int slice = -1;

	// Worked out every tick from the parts (for the slice when one is chosen).
	private final Map<Kind, int[]> counts = new EnumMap<>(Kind.class);
	private final List<Part> lockedHoppers = new ArrayList<>();
	private final List<Part> breakPoints = new ArrayList<>();
	@Nullable
	private Part slowest;
	private int repeaterDelayTotal;
	private int repeaterDelayMax;
	private int shownParts;

	private final Set<Integer> seenItems = new HashSet<>();
	private final Map<String, Integer> itemTotals = new LinkedHashMap<>();
	private final Map<ChestMemoryModule.Chest, ChestWatch> chestWatches = new IdentityHashMap<>();

	public DiagnosticsModule() {
		super("diagnostics", ModuleCategory.TECHNICAL, true);
		scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.diagnostics_scan",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, QoLBundleClient.KEY_CATEGORY));
		sliceUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.diagnostics_slice_next",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, QoLBundleClient.KEY_CATEGORY));
		sliceDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.diagnostics_slice_previous",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, QoLBundleClient.KEY_CATEGORY));
	}

	// ---- what the self-test (and nothing else) asks ----------------------------------------------

	public List<Part> getParts() {
		return parts;
	}

	public int count(Kind kind) {
		int[] pair = counts.get(kind);
		return pair == null ? 0 : pair[0];
	}

	public int activeCount(Kind kind) {
		int[] pair = counts.get(kind);
		return pair == null ? 0 : pair[1];
	}

	public List<Part> getBreakPoints() {
		return breakPoints;
	}

	public List<Part> getLockedHoppers() {
		return lockedHoppers;
	}

	@Nullable
	public Part getSlowest() {
		return slowest;
	}

	public int getItemTotal(String itemId) {
		return itemTotals.getOrDefault(itemId, 0);
	}

	/** How many of an item were added to the machine's chests between the first and the latest look. */
	public int getChestGain(String itemId) {
		int gain = 0;
		for (ChestWatch watch : chestWatches.values()) {
			gain += watch.latest.getOrDefault(itemId, 0) - watch.base.getOrDefault(itemId, 0);
		}
		return gain;
	}

	public boolean isChanged() {
		return changed;
	}

	public int getSlice() {
		return slice;
	}

	public int getShownParts() {
		return shownParts;
	}

	// ---- scanning ------------------------------------------------------------------------------

	@Nullable
	private static Kind kindOf(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof RedstoneWireBlock) {
			return Kind.WIRE;
		} else if (block instanceof RepeaterBlock) {
			return Kind.REPEATER;
		} else if (block instanceof ComparatorBlock) {
			return Kind.COMPARATOR;
		} else if (block instanceof RedstoneTorchBlock) {
			return Kind.TORCH;
		} else if (block instanceof ObserverBlock) {
			return Kind.OBSERVER;
		} else if (block instanceof PistonBlock) {
			return Kind.PISTON;
		} else if (block instanceof HopperBlock) {
			return Kind.HOPPER;
		} else if (block instanceof CrafterBlock) {
			return Kind.CRAFTER;
		} else if (block instanceof DispenserBlock) {
			return Kind.DROPPER;
		} else if (block instanceof RedstoneLampBlock || block instanceof BulbBlock) {
			return Kind.LAMP;
		} else if (block instanceof DetectorRailBlock || block instanceof LeverBlock || block instanceof ButtonBlock
				|| block instanceof AbstractPressurePlateBlock || block instanceof TripwireHookBlock || block instanceof TargetBlock
				|| block instanceof DaylightDetectorBlock || block instanceof SculkSensorBlock || block instanceof LecternBlock) {
			return Kind.INPUT;
		} else if (state.isOf(Blocks.REDSTONE_BLOCK)) {
			return Kind.SOURCE;
		} else if (block instanceof AbstractRailBlock) {
			return state.contains(Properties.POWERED) ? Kind.RAIL : null; // plain rails are not redstone
		} else if (block instanceof NoteBlock) {
			return Kind.NOTE_BLOCK;
		} else if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock) {
			return Kind.DOOR;
		} else if (block instanceof TntBlock) {
			return Kind.TNT;
		} else if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock
				|| block instanceof AbstractFurnaceBlock) {
			return Kind.CONTAINER;
		}
		return null;
	}

	private static boolean flag(BlockState state, BooleanProperty property) {
		return state.contains(property) && state.get(property);
	}

	/** "On" in whatever sense fits the component; for a hopper it means locked. */
	private static boolean isActive(Kind kind, BlockState state) {
		return switch (kind) {
			case WIRE -> state.get(Properties.POWER) > 0;
			case HOPPER -> state.contains(Properties.ENABLED) && !state.get(Properties.ENABLED);
			case PISTON -> flag(state, Properties.EXTENDED);
			case DROPPER, CRAFTER -> flag(state, Properties.TRIGGERED);
			case TORCH, LAMP -> flag(state, Properties.LIT);
			case DOOR -> flag(state, Properties.OPEN);
			case SOURCE -> true;
			case TNT, CONTAINER -> false;
			default -> flag(state, Properties.POWERED) || state.contains(Properties.POWER) && state.get(Properties.POWER) > 0;
		};
	}

	/**
	 * Takes everything connected to the block at start as the machine. Connected means: touching,
	 * or one solid block in between (components act through the block they sit on or point at).
	 *
	 * @return false when there is no redstone component at or right next to start
	 */
	public boolean scan(ClientWorld world, BlockPos start) {
		BlockPos origin = null;
		if (kindOf(world.getBlockState(start)) != null) {
			origin = start;
		} else {
			for (Direction direction : Direction.values()) {
				if (kindOf(world.getBlockState(start.offset(direction))) != null) {
					origin = start.offset(direction);
					break;
				}
			}
		}
		if (origin == null) {
			return false;
		}
		clear();
		scannedWorld = world;
		int radius = scanRadius.get();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		visit(world, origin, origin, radius, queue, visited);
		while (!queue.isEmpty() && parts.size() < MAX_PARTS) {
			BlockPos pos = queue.poll();
			Part part = byPos.get(pos);
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.offset(direction);
				if (!visit(world, next, origin, radius, queue, visited) && world.getBlockState(next).isSolidBlock(world, next)) {
					for (Direction beyond : Direction.values()) {
						visit(world, next.offset(beyond), origin, radius, queue, visited);
					}
				}
			}
			if (part.kind == Kind.WIRE) {
				// Redstone dust also runs up and down one block along a slope.
				for (Direction direction : Direction.Type.HORIZONTAL) {
					visit(world, pos.offset(direction).up(), origin, radius, queue, visited);
					visit(world, pos.offset(direction).down(), origin, radius, queue, visited);
				}
			}
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Part part : parts) {
			minX = Math.min(minX, part.pos.getX());
			minY = Math.min(minY, part.pos.getY());
			minZ = Math.min(minZ, part.pos.getZ());
			maxX = Math.max(maxX, part.pos.getX());
			maxY = Math.max(maxY, part.pos.getY());
			maxZ = Math.max(maxZ, part.pos.getZ());
		}
		min = new BlockPos(minX, minY, minZ);
		max = new BlockPos(maxX, maxY, maxZ);
		scanTick = tick;
		scanWallMs = System.currentTimeMillis();
		summarize();
		return true;
	}

	/** Adds the block as a part if it is a component not seen yet. Returns whether it is a component at all. */
	private boolean visit(ClientWorld world, BlockPos pos, BlockPos origin, int radius, ArrayDeque<BlockPos> queue, Set<BlockPos> visited) {
		if (Math.abs(pos.getX() - origin.getX()) > radius || Math.abs(pos.getY() - origin.getY()) > radius
				|| Math.abs(pos.getZ() - origin.getZ()) > radius) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		Kind kind = kindOf(state);
		if (kind == null) {
			return false;
		}
		BlockPos fixed = pos.toImmutable();
		if (visited.add(fixed) && parts.size() < MAX_PARTS) {
			Part part = new Part(fixed, kind, state);
			part.active = isActive(kind, state);
			parts.add(part);
			byPos.put(fixed, part);
			queue.add(fixed);
		}
		return true;
	}

	public void clear() {
		parts.clear();
		byPos.clear();
		scannedWorld = null;
		slice = -1;
		changed = false;
		seenItems.clear();
		itemTotals.clear();
		chestWatches.clear();
		counts.clear();
		lockedHoppers.clear();
		breakPoints.clear();
		slowest = null;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		clear();
	}

	// ---- slice ---------------------------------------------------------------------------------

	private int coordinate(BlockPos pos) {
		return switch (sliceAxis.get()) {
			case X -> pos.getX();
			case Y -> pos.getY();
			case Z -> pos.getZ();
		};
	}

	private int layers() {
		return coordinate(max) - coordinate(min) + 1;
	}

	private boolean inSlice(BlockPos pos) {
		return slice < 0 || coordinate(pos) == coordinate(min) + slice;
	}

	/** Steps through the layers: whole machine, layer 1, layer 2, ..., last layer, whole machine again. */
	public void stepSlice(int direction) {
		if (parts.isEmpty()) {
			return;
		}
		int count = layers();
		slice += direction;
		if (slice >= count) {
			slice = -1;
		} else if (slice < -1) {
			slice = count - 1;
		}
		summarize();
	}

	// ---- keeping it up to date -----------------------------------------------------------------

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		while (scanKey.wasPressed()) {
			pressScan(client);
		}
		while (sliceUpKey.wasPressed()) {
			stepSlice(1);
		}
		while (sliceDownKey.wasPressed()) {
			stepSlice(-1);
		}
		if (parts.isEmpty()) {
			return;
		}
		ClientWorld world = client.world;
		if (world == null || world != scannedWorld) {
			clear();
			return;
		}
		for (Part part : parts) {
			BlockState state = world.getBlockState(part.pos);
			if (kindOf(state) != part.kind) {
				if (!part.gone && world.getChunkManager().isChunkLoaded(part.pos.getX() >> 4, part.pos.getZ() >> 4)) {
					part.gone = true;
					changed = true; // somebody broke or replaced a component: the scan is out of date
				}
				continue;
			}
			part.gone = false;
			part.state = state;
			boolean active = isActive(part.kind, state);
			if (active && !part.active) {
				if (part.lastRise >= 0) {
					part.period = (int) (tick - part.lastRise);
				}
				part.lastRise = tick;
			}
			part.active = active;
			if (part.period > 0 && tick - part.lastRise > Math.max(200, part.period * 3L)) {
				part.period = 0; // it stopped repeating
			}
		}
		summarize();
		if (rates.get()) {
			countDrops(world); // every tick: a drop must be seen on its own before it merges into a pile
		}
		if (rates.get() && tick % 10 == 0) {
			watchChests(world);
		}
	}

	private void pressScan(MinecraftClient client) {
		String key = getTranslationKey() + ".";
		boolean lookingAtBlock = client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
		if (client.world == null || client.player == null) {
			return;
		}
		if (!lookingAtBlock || client.player.isSneaking()) {
			if (!parts.isEmpty()) {
				clear();
				client.inGameHud.setOverlayMessage(Text.translatable(key + "cleared"), false);
			}
			return;
		}
		BlockPos target = ((BlockHitResult) client.crosshairTarget).getBlockPos();
		if (scan(client.world, target)) {
			client.inGameHud.setOverlayMessage(Text.translatable(key + "scanned", parts.size()), false);
		} else {
			client.inGameHud.setOverlayMessage(Text.translatable(key + "nothing").formatted(Formatting.RED), false);
		}
	}

	private void summarize() {
		counts.clear();
		lockedHoppers.clear();
		breakPoints.clear();
		slowest = null;
		repeaterDelayTotal = 0;
		repeaterDelayMax = 0;
		shownParts = 0;
		for (Part part : parts) {
			if (part.gone || !inSlice(part.pos)) {
				continue;
			}
			shownParts++;
			int[] pair = counts.computeIfAbsent(part.kind, kind -> new int[2]);
			pair[0]++;
			if (part.active) {
				pair[1]++;
			}
			switch (part.kind) {
				case HOPPER -> {
					if (part.active) {
						lockedHoppers.add(part);
					}
				}
				case REPEATER -> {
					int delay = part.state.get(Properties.DELAY);
					repeaterDelayTotal += delay * 2;
					repeaterDelayMax = Math.max(repeaterDelayMax, delay);
					if (flag(part.state, Properties.LOCKED)) {
						breakPoints.add(part);
					}
				}
				case WIRE -> {
					if (!part.active && hasPoweredWireNeighbor(part.pos)) {
						breakPoints.add(part); // the signal ran out of strength right before this dust
					}
				}
				default -> {
				}
			}
			if (part.kind != Kind.WIRE && part.period > 0 && (slowest == null || part.period > slowest.period)) {
				slowest = part;
			}
		}
	}

	/** Dust positions a piece of dust at pos can be joined to. */
	private List<Part> wireNeighbors(BlockPos pos) {
		List<Part> result = new ArrayList<>(4);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos side = pos.offset(direction);
			for (int dy = -1; dy <= 1; dy++) {
				Part other = byPos.get(side.up(dy));
				if (other != null && other.kind == Kind.WIRE && !other.gone) {
					result.add(other);
				}
			}
		}
		return result;
	}

	private boolean hasPoweredWireNeighbor(BlockPos pos) {
		for (Part other : wireNeighbors(pos)) {
			if (other.active) {
				return true;
			}
		}
		return false;
	}

	private Box area() {
		return new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
	}

	/** Dropped items that show up at the machine count once, with the size of the stack when first seen. */
	private void countDrops(ClientWorld world) {
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, area().expand(3.0), entity -> true)) {
			if (seenItems.add(item.getId())) {
				itemTotals.merge(Registries.ITEM.getId(item.getStack().getItem()).toString(), item.getStack().getCount(), Integer::sum);
			}
		}
	}

	/** Chests of the machine that the player opens: the first look is the baseline, later looks show what was added. */
	private void watchChests(ClientWorld world) {
		if (!(ModuleRegistry.get("chest_memory") instanceof ChestMemoryModule memory) || !memory.isEnabled()) {
			return;
		}
		String dimension = world.getRegistryKey().getValue().toString();
		Box area = area().expand(1.0);
		for (ChestMemoryModule.Chest chest : memory.getChests()) {
			if (chest.isEnderChest() || !chest.dimension.equals(dimension) || !area.contains(Vec3d.ofCenter(chest.pos))
					|| chest.seenMs < scanWallMs) {
				continue;
			}
			ChestWatch watch = chestWatches.get(chest);
			if (watch == null) {
				watch = new ChestWatch();
				watch.baseMs = chest.seenMs;
				watch.base = totalsOf(chest);
				watch.latestMs = chest.seenMs;
				watch.latest = watch.base;
				chestWatches.put(chest, watch);
			} else if (chest.seenMs > watch.latestMs) {
				watch.latestMs = chest.seenMs;
				watch.latest = totalsOf(chest);
			}
		}
	}

	private static Map<String, Integer> totalsOf(ChestMemoryModule.Chest chest) {
		Map<String, Integer> totals = new HashMap<>();
		for (ChestMemoryModule.StoredItem item : chest.items) {
			totals.merge(item.id(), item.count(), Integer::sum);
		}
		return totals;
	}

	// ---- drawing in the world ------------------------------------------------------------------

	private static Vec3d dustPoint(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.12, pos.getZ() + 0.5);
	}

	private static int powerColor(int power) {
		return 0xFF000000 | (0x69 + power * 10) << 16 | 0x1010;
	}

	private static void through(VisibilityConfigurable gizmo, boolean seeThrough) {
		if (seeThrough) {
			gizmo.ignoreOcclusion();
		}
	}

	@Override
	public void onRenderWorld(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (parts.isEmpty() || client.world != scannedWorld) {
			return;
		}
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		boolean sliced = slice >= 0;
		String key = getTranslationKey() + ".world.";
		if (sliced) {
			// The layer being looked at, as a frame around the machine at that height.
			Box layer = area();
			int at = coordinate(min) + slice;
			layer = switch (sliceAxis.get()) {
				case X -> new Box(at, layer.minY, layer.minZ, at + 1, layer.maxY, layer.maxZ);
				case Y -> new Box(layer.minX, at, layer.minZ, layer.maxX, at + 1, layer.maxZ);
				case Z -> new Box(layer.minX, layer.minY, at, layer.maxX, layer.maxY, at + 1);
			};
			GizmoDrawing.box(layer.expand(0.05), DrawStyle.stroked(0xFF55FFFF, 2.0F)).ignoreOcclusion();
		}
		for (Part part : parts) {
			if (part.gone || !inSlice(part.pos) || part.pos.getSquaredDistance(camera.x, camera.y, camera.z) > DRAW_RANGE * DRAW_RANGE) {
				continue;
			}
			BlockPos pos = part.pos;
			Vec3d middle = Vec3d.ofCenter(pos);
			if (sliced) {
				// Your own machine, one layer of it: every component of the layer shows through the rest.
				GizmoDrawing.box(new Box(pos).contract(0.1), DrawStyle.stroked(part.active ? 0xFFFF5555 : 0xFF9090FF, 1.5F)).ignoreOcclusion();
			}
			if (signalFlow.get()) {
				switch (part.kind) {
					case WIRE -> drawDust(part, sliced);
					case REPEATER, COMPARATOR, OBSERVER -> {
						// The signal leaves on the side opposite to the one the component "faces".
						Direction out = part.state.get(part.kind == Kind.OBSERVER ? Properties.FACING : Properties.HORIZONTAL_FACING).getOpposite();
						Vec3d step = Vec3d.of(out.getVector());
						through(GizmoDrawing.arrow(middle.subtract(step.multiply(0.45)), middle.add(step.multiply(0.75)),
								part.active ? powerColor(15) : GRAY, 3.0F), sliced);
					}
					default -> {
					}
				}
			}
			if (signalFlow.get() && breakPoints.contains(part)) {
				boolean lockedRepeater = part.kind == Kind.REPEATER;
				through(GizmoDrawing.box(new Box(pos).contract(0.05), DrawStyle.stroked(lockedRepeater ? ORANGE : RED, 3.0F)), sliced);
				through(GizmoDrawing.text(I18n.translate(key + (lockedRepeater ? "locked" : "signal_ends")), middle.add(0, 0.7, 0),
						TextGizmo.Style.centered(lockedRepeater ? ORANGE : RED).scaled(0.9F)), true);
			}
			if (bottlenecks.get()) {
				if (part.kind == Kind.HOPPER && part.active) {
					through(GizmoDrawing.box(new Box(pos).expand(0.02), DrawStyle.stroked(ORANGE, 3.0F)), sliced);
					through(GizmoDrawing.text(I18n.translate(key + "locked"), middle.add(0, 0.8, 0), TextGizmo.Style.centered(ORANGE).scaled(0.9F)), true);
				}
				if (part == slowest) {
					through(GizmoDrawing.box(new Box(pos).expand(0.04), DrawStyle.stroked(YELLOW, 3.0F)), sliced);
					through(GizmoDrawing.text(I18n.translate(key + "slowest", part.period), middle.add(0, 1.1, 0),
							TextGizmo.Style.centered(YELLOW).scaled(0.9F)), true);
				}
			}
		}
	}

	/** Arrows from this dust to every neighbouring dust with a weaker signal: that is the way the signal runs. */
	private void drawDust(Part part, boolean seeThrough) {
		int power = part.state.get(Properties.POWER);
		for (Part other : wireNeighbors(part.pos)) {
			if (!inSlice(other.pos)) {
				continue;
			}
			int otherPower = other.state.get(Properties.POWER);
			if (power > otherPower) {
				through(GizmoDrawing.arrow(dustPoint(part.pos), dustPoint(other.pos), powerColor(power), 2.5F), seeThrough);
			} else if (power == 0 && otherPower == 0 && part.pos.compareTo(other.pos) < 0) {
				through(GizmoDrawing.line(dustPoint(part.pos), dustPoint(other.pos), GRAY, 1.5F), seeThrough);
			}
		}
	}

	// ---- the panel -----------------------------------------------------------------------------

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (parts.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		layout.drawLines(context, client.textRenderer, position.get(), buildLines());
	}

	/** The lines of the panel (also read by the self-test). */
	public List<Text> buildLines() {
		String key = getTranslationKey() + ".hud.";
		List<Text> lines = new ArrayList<>();
		MutableText title = Text.translatable(key + "title", shownParts).formatted(Formatting.GOLD);
		if (slice >= 0) {
			title.append(Text.literal("  ")).append(Text.translatable(key + "slice", sliceAxis.get().name(),
					coordinate(min) + slice, slice + 1, layers()).formatted(Formatting.AQUA));
		}
		lines.add(title);
		if (changed) {
			lines.add(Text.translatable(key + "changed", scanKey.getBoundKeyLocalizedText()).formatted(Formatting.RED));
		}
		if (overview.get()) {
			for (Map.Entry<Kind, int[]> entry : counts.entrySet()) {
				Kind kind = entry.getKey();
				String name = kind.name().toLowerCase(Locale.ROOT);
				Text kindName = Text.translatable(getTranslationKey() + ".kind." + name);
				int[] pair = entry.getValue();
				if (kind == Kind.SOURCE || kind == Kind.TNT || kind == Kind.CONTAINER) {
					lines.add(Text.translatable(key + "count", kindName, pair[0]));
				} else {
					lines.add(Text.translatable(key + "count_active", kindName, pair[0],
							Text.translatable(getTranslationKey() + ".active." + name), pair[1])
							.formatted(kind == Kind.HOPPER && pair[1] > 0 ? Formatting.GOLD : Formatting.WHITE));
				}
			}
			int subtract = 0;
			for (Part part : parts) {
				if (part.kind == Kind.COMPARATOR && !part.gone && inSlice(part.pos)
						&& part.state.get(Properties.COMPARATOR_MODE) == ComparatorMode.SUBTRACT) {
					subtract++;
				}
			}
			if (subtract > 0) {
				lines.add(Text.translatable(key + "subtract", subtract).formatted(Formatting.GRAY));
			}
		}
		if (bottlenecks.get()) {
			if (!lockedHoppers.isEmpty()) {
				lines.add(Text.translatable(key + "locked_hoppers", lockedHoppers.size()).formatted(Formatting.GOLD));
			}
			if (!breakPoints.isEmpty()) {
				lines.add(Text.translatable(key + "break_points", breakPoints.size()).formatted(Formatting.RED));
			}
			if (repeaterDelayTotal > 0) {
				lines.add(Text.translatable(key + "repeater_delay", repeaterDelayTotal, repeaterDelayMax));
			}
			if (slowest != null) {
				lines.add(Text.translatable(key + "slowest", Text.translatable(getTranslationKey() + ".kind."
						+ slowest.kind.name().toLowerCase(Locale.ROOT)), slowest.period, String.format(Locale.ROOT, "%.1f", slowest.period / 20.0))
						.formatted(Formatting.YELLOW));
			}
		}
		if (rates.get()) {
			addRateLines(lines, key);
		}
		return lines;
	}

	private void addRateLines(List<Text> lines, String key) {
		double seconds = (tick - scanTick) / 20.0;
		int total = 0;
		for (int count : itemTotals.values()) {
			total += count;
		}
		if (total > 0 && seconds >= 5) {
			lines.add(Text.translatable(key + "drops_title", duration(seconds)).formatted(Formatting.GREEN));
			List<Map.Entry<String, Integer>> sorted = new ArrayList<>(itemTotals.entrySet());
			sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed());
			for (int i = 0; i < sorted.size() && i < 3; i++) {
				int count = sorted.get(i).getValue();
				lines.add(Text.translatable(key + "rate", ChestMemoryModule.itemOf(sorted.get(i).getKey()).getName(), count,
						perMinute(count, seconds), perHour(count, seconds)));
			}
			if (bottlenecks.get() && total / seconds > HOPPER_ITEMS_PER_SECOND) {
				lines.add(Text.translatable(key + "hopper_limit", String.format(Locale.ROOT, "%.1f", total / seconds)).formatted(Formatting.GOLD));
			}
		}
		for (ChestWatch watch : chestWatches.values()) {
			long elapsedMs = watch.latestMs - watch.baseMs;
			if (elapsedMs < MIN_CHEST_INTERVAL_MS) {
				lines.add(Text.translatable(key + "chest_wait").formatted(Formatting.GRAY));
				continue;
			}
			double chestSeconds = elapsedMs / 1000.0;
			List<Map.Entry<String, Integer>> gains = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : watch.latest.entrySet()) {
				int gain = entry.getValue() - watch.base.getOrDefault(entry.getKey(), 0);
				if (gain > 0) {
					gains.add(Map.entry(entry.getKey(), gain));
				}
			}
			gains.sort(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed());
			lines.add(Text.translatable(key + "chest_title", duration(chestSeconds)).formatted(Formatting.GREEN));
			if (gains.isEmpty()) {
				lines.add(Text.translatable(key + "chest_nothing").formatted(Formatting.GRAY));
			}
			for (int i = 0; i < gains.size() && i < 3; i++) {
				int count = gains.get(i).getValue();
				lines.add(Text.translatable(key + "rate", ChestMemoryModule.itemOf(gains.get(i).getKey()).getName(), count,
						perMinute(count, chestSeconds), perHour(count, chestSeconds)));
			}
		}
	}

	private static String perMinute(int count, double seconds) {
		return String.format(Locale.ROOT, "%.1f", count / seconds * 60.0);
	}

	private static String perHour(int count, double seconds) {
		return String.format(Locale.ROOT, "%.0f", count / seconds * 3600.0);
	}

	private Text duration(double seconds) {
		String key = getTranslationKey() + ".hud.";
		return seconds < 90 ? Text.translatable(key + "seconds", (int) seconds) : Text.translatable(key + "minutes", (int) Math.round(seconds / 60.0));
	}
}

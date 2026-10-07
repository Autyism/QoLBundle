package io.github.autyism.qolbundle.modules;

import com.google.gson.JsonObject;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Keeps track of where you will respawn and warns when that place stops working.
 *
 * <p>The server never tells the client its respawn point. What the client does see: you
 * right-clicking a bed or respawn anchor, the "Respawn point set" message that follows, the
 * {@code /spawnpoint} feedback, and the "you have no home bed" message after a failed respawn.
 * From those this module works out the respawn point and remembers it per world.
 *
 * <p>One subtle rule: the game only says "Respawn point set" when the point <em>changes</em>.
 * So clicking a bed and hearing nothing back (no "set", no "too far away" / "obstructed" /
 * "occupied") means that bed already was your respawn point.
 */
public class RespawnPointModule extends Module {
	private static final String SECTION = "respawn";
	/** A "Respawn point set" message this soon after a click belongs to that click. */
	private static final long USE_WINDOW_MS = 4000;
	/** No message at all this long after a click means: that bed already was the respawn point. */
	private static final long CONFIRM_MS = 2500;
	private static final long WARNING_MS = 10_000;
	private static final int CHECK_TICKS = 10;

	public enum Kind {
		BED,
		ANCHOR,
		COMMAND
	}

	public enum Status {
		/** Nothing recorded yet for this world. */
		UNKNOWN,
		/** A respawn point is recorded and, as far as can be seen, works. */
		SET,
		/** The recorded bed / anchor is gone or the anchor is empty. */
		BROKEN,
		/** The server said there is no valid respawn point; you respawn at world spawn. */
		NONE
	}

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_LEFT));
	private final BoolSetting showDistance = add(new BoolSetting("show_distance", true));
	private final BoolSetting showWhenUnknown = add(new BoolSetting("show_when_unknown", true));
	private final BoolSetting bedHint = add(new BoolSetting("bed_hint", true));
	private final BoolSetting warnSound = add(new BoolSetting("warn_sound", true));

	private Status status = Status.UNKNOWN;
	@Nullable
	private BlockPos pos;
	private String dimension = "";
	private Kind kind = Kind.BED;

	/** The bed / anchor the player last right-clicked, waiting for the server's "Respawn point set". */
	@Nullable
	private BlockPos lastUsedPos;
	private String lastUsedDimension = "";
	private Kind lastUsedKind = Kind.BED;
	private long lastUsedMs;
	/** False when silence after this click proves nothing (e.g. charging an anchor with glowstone). */
	private boolean lastUsedConfirmable;

	@Nullable
	private String loadedWorldId;
	private long warningUntilMs;
	private int ticks;

	public RespawnPointModule() {
		super("respawn_point", ModuleCategory.INFO, true);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() && isEnabled()) {
				noteUse(player, world, hit.getBlockPos());
			}
			return InteractionResult.PASS; // only watching, never changes what the click does
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (isEnabled()) {
				onGameMessage(message);
			}
		});
	}

	public Status getStatus() {
		return status;
	}

	@Nullable
	public BlockPos getRespawnPos() {
		return pos;
	}

	/** Forgets the recorded respawn point of the current world (used by the self-test). */
	public void forget() {
		status = Status.UNKNOWN;
		pos = null;
		warningUntilMs = 0;
		store();
	}

	// ---- learning the respawn point ------------------------------------------------------------

	private void noteUse(Player player, Level world, BlockPos clicked) {
		boolean holdingSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
		if (player.isShiftKeyDown() && holdingSomething) {
			return; // sneak-click uses the held item instead of the block
		}
		BlockState state = world.getBlockState(clicked);
		if (state.is(BlockTags.BEDS)) {
			// The game stores the head half of the bed.
			BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? clicked : clicked.relative(state.getValue(BedBlock.FACING));
			lastUsedPos = head.immutable();
			lastUsedKind = Kind.BED;
			lastUsedConfirmable = true;
		} else if (state.is(Blocks.RESPAWN_ANCHOR)) {
			lastUsedPos = clicked.immutable();
			lastUsedKind = Kind.ANCHOR;
			// Clicking with glowstone only charges the anchor; clicking an empty one does nothing.
			boolean charging = player.getMainHandItem().is(Items.GLOWSTONE) || player.getOffhandItem().is(Items.GLOWSTONE);
			lastUsedConfirmable = !charging && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
		} else {
			return;
		}
		lastUsedDimension = world.dimension().identifier().toString();
		lastUsedMs = Util.getMillis();
	}

	private void onGameMessage(Component message) {
		if (!(message.getContents() instanceof TranslatableContents content)) {
			return;
		}
		syncWorldData();
		switch (content.getKey()) {
			case "block.minecraft.set_spawn" -> {
				if (lastUsedPos != null && Util.getMillis() - lastUsedMs <= USE_WINDOW_MS) {
					setRespawn(lastUsedPos, lastUsedDimension, lastUsedKind);
					lastUsedPos = null;
				}
			}
			// The click was refused before the server got to the respawn point: it tells us nothing.
			case "block.minecraft.bed.too_far_away", "block.minecraft.bed.obstructed", "block.minecraft.bed.occupied" ->
					lastUsedPos = null;
			case "block.minecraft.spawn.not_valid" -> {
				status = Status.NONE;
				pos = null;
				store();
			}
			case "commands.spawnpoint.success.single" -> onSpawnPointCommand(content.getArgs());
			default -> {
			}
		}
	}

	/** Feedback of /spawnpoint: x, y, z, yaw, pitch, dimension, player name. */
	private void onSpawnPointCommand(Object[] args) {
		Minecraft client = Minecraft.getInstance();
		if (args.length < 7 || client.player == null) {
			return;
		}
		String target = args[6] instanceof Component text ? text.getString() : String.valueOf(args[6]);
		if (!target.equals(client.player.getName().getString())) {
			return; // somebody else's spawn point was set
		}
		try {
			BlockPos commandPos = new BlockPos(Integer.parseInt(String.valueOf(args[0])),
					Integer.parseInt(String.valueOf(args[1])), Integer.parseInt(String.valueOf(args[2])));
			setRespawn(commandPos, String.valueOf(args[5]), Kind.COMMAND);
		} catch (NumberFormatException ignored) {
			// unexpected format (modified server); leave things as they are
		}
	}

	private void setRespawn(BlockPos newPos, String newDimension, Kind newKind) {
		status = Status.SET;
		pos = newPos;
		dimension = newDimension;
		kind = newKind;
		warningUntilMs = 0;
		store();
	}

	// ---- watching it ---------------------------------------------------------------------------

	@Override
	public void onTick(Minecraft client) {
		syncWorldData();
		if (client.player == null || client.level == null) {
			return;
		}
		ClientLevel world = client.level;
		confirmSilentClick(world);
		if (pos == null || ++ticks % CHECK_TICKS != 0) {
			return;
		}
		if (status != Status.SET && status != Status.BROKEN) {
			return;
		}
		if (!dimension.equals(world.dimension().identifier().toString())
				|| !world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			return; // cannot see it from here; keep the last known state
		}
		boolean works = stillWorks(world.getBlockState(pos));
		if (!works && status == Status.SET) {
			status = Status.BROKEN;
			warningUntilMs = Util.getMillis() + WARNING_MS;
			if (warnSound.get()) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.5F));
			}
			store();
		} else if (works && status == Status.BROKEN) {
			// A new bed on the same spot (or a recharged anchor) makes the old respawn point work again.
			status = Status.SET;
			warningUntilMs = 0;
			store();
		}
	}

	/** See the class comment: a click followed by silence means "this already was your respawn point". */
	private void confirmSilentClick(ClientLevel world) {
		if (lastUsedPos == null || Util.getMillis() - lastUsedMs < CONFIRM_MS) {
			return;
		}
		BlockPos clicked = lastUsedPos;
		lastUsedPos = null;
		if (!lastUsedConfirmable || !lastUsedDimension.equals(world.dimension().identifier().toString())) {
			return;
		}
		// In dimensions where beds / anchors blow up, the block is gone by now.
		BlockState state = world.getBlockState(clicked);
		boolean stillThere = lastUsedKind == Kind.BED ? state.is(BlockTags.BEDS) : state.is(Blocks.RESPAWN_ANCHOR);
		if (stillThere) {
			setRespawn(clicked, lastUsedDimension, lastUsedKind);
		}
	}

	private boolean stillWorks(BlockState state) {
		return switch (kind) {
			case BED -> state.is(BlockTags.BEDS);
			case ANCHOR -> state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
			case COMMAND -> true; // a forced spawn point needs no block
		};
	}

	// ---- saving / loading ----------------------------------------------------------------------

	private void syncWorldData() {
		String worldId = WorldData.getWorldId();
		if (Objects.equals(worldId, loadedWorldId)) {
			return;
		}
		loadedWorldId = worldId;
		status = Status.UNKNOWN;
		pos = null;
		warningUntilMs = 0;
		lastUsedPos = null;
		JsonObject section = WorldData.section(SECTION);
		if (section == null || !section.has("status")) {
			return;
		}
		try {
			status = Status.valueOf(section.get("status").getAsString());
			if (section.has("x")) {
				pos = new BlockPos(section.get("x").getAsInt(), section.get("y").getAsInt(), section.get("z").getAsInt());
				dimension = section.get("dimension").getAsString();
				kind = Kind.valueOf(section.get("kind").getAsString());
			}
		} catch (RuntimeException e) {
			status = Status.UNKNOWN;
			pos = null;
		}
		if ((status == Status.SET || status == Status.BROKEN) && pos == null) {
			status = Status.UNKNOWN;
		}
	}

	private void store() {
		JsonObject section = WorldData.section(SECTION);
		if (section == null) {
			return;
		}
		for (String key : List.copyOf(section.keySet())) {
			section.remove(key);
		}
		section.addProperty("status", status.name());
		if (pos != null) {
			section.addProperty("x", pos.getX());
			section.addProperty("y", pos.getY());
			section.addProperty("z", pos.getZ());
			section.addProperty("dimension", dimension);
			section.addProperty("kind", kind.name());
		}
		WorldData.markDirty();
	}

	// ---- HUD -----------------------------------------------------------------------------------

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		String here = client.level.dimension().identifier().toString();

		Component line = null;
		switch (status) {
			case UNKNOWN -> {
				if (showWhenUnknown.get()) {
					line = Component.translatable(key + "unknown").withColor(0xAAAAAA);
				}
			}
			case NONE -> line = Component.translatable(key + "none").withColor(0xFFAA00);
			case SET -> {
				String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
				if (!dimension.equals(here)) {
					line = Component.translatable(key + "set_other_dimension", dimensionName(dimension), where);
				} else if (showDistance.get()) {
					int distance = (int) Math.round(Math.sqrt(client.player.blockPosition().distSqr(pos)));
					line = Component.translatable(key + "set_distance", where, distance);
				} else {
					line = Component.translatable(key + "set", where);
				}
			}
			case BROKEN -> line = Component.translatable(key + "broken", pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
					.withColor(0xFF5555);
		}
		if (line != null) {
			layout.drawLines(context, client.font, position.get(), List.of(line));
		}

		if (status == Status.BROKEN && Util.getMillis() < warningUntilMs) {
			drawBigWarning(context, client, layout, Component.translatable(key + "warning_title"),
					Component.translatable(key + (kind == Kind.ANCHOR ? "warning_anchor" : "warning_bed")));
		}

		if (bedHint.get() && client.hitResult instanceof BlockHitResult hit
				&& client.level.getBlockState(hit.getBlockPos()).is(BlockTags.BEDS)) {
			drawBedHint(context, client, layout, hit.getBlockPos(), here, key);
		}
	}

	/** Two centred lines in the upper third of the screen, the first one double size. */
	private static void drawBigWarning(GuiGraphics context, Minecraft client, HudLayout layout, Component title, Component detail) {
		int centerX = layout.getScreenWidth() / 2;
		int y = layout.getScreenHeight() / 4;
		int titleWidth = client.font.width(title) * 2;
		int detailWidth = client.font.width(detail);
		int half = Math.max(titleWidth, detailWidth) / 2 + 6;
		context.fill(centerX - half, y - 5, centerX + half, y + 34, 0xA0000000);
		// Blink the title so it catches the eye.
		boolean bright = Util.getMillis() / 400 % 2 == 0;
		context.pose().pushMatrix();
		context.pose().translate(centerX, y);
		context.pose().scale(2F, 2F);
		context.drawCenteredString(client.font, title, 0, 0, bright ? 0xFFFF5555 : 0xFFFFAAAA);
		context.pose().popMatrix();
		context.drawCenteredString(client.font, detail, centerX, y + 22, HudLayout.WHITE);
	}

	private void drawBedHint(GuiGraphics context, Minecraft client, HudLayout layout, BlockPos looked, String here, String key) {
		BlockState state = client.level.getBlockState(looked);
		BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? looked : looked.relative(state.getValue(BedBlock.FACING));
		Component hint;
		int color;
		if (status == Status.SET && kind == Kind.BED && head.equals(pos) && dimension.equals(here)) {
			hint = Component.translatable(key + "bed_yours");
			color = 0xFF55FF55;
		} else if (status == Status.UNKNOWN) {
			hint = Component.translatable(key + "bed_unsure");
			color = 0xFFAAAAAA;
		} else {
			hint = Component.translatable(key + "bed_not_yours");
			color = 0xFFFFAA00;
		}
		int width = client.font.width(hint);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 + 14;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawString(client.font, hint, x, y, color);
	}

	/** "minecraft:the_nether" -> "The Nether"; unknown (modded) dimensions are shown as their id. */
	private static Component dimensionName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String key = "qolbundle.dimension." + path.toLowerCase(Locale.ROOT);
		return I18n.exists(key) ? Component.translatable(key) : Component.literal(id);
	}
}

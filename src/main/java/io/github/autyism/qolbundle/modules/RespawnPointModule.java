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
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
			if (world.isClient() && isEnabled()) {
				noteUse(player, world, hit.getBlockPos());
			}
			return ActionResult.PASS; // only watching, never changes what the click does
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

	private void noteUse(PlayerEntity player, World world, BlockPos clicked) {
		boolean holdingSomething = !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
		if (player.isSneaking() && holdingSomething) {
			return; // sneak-click uses the held item instead of the block
		}
		BlockState state = world.getBlockState(clicked);
		if (state.isIn(BlockTags.BEDS)) {
			// The game stores the head half of the bed.
			BlockPos head = state.get(BedBlock.PART) == BedPart.HEAD ? clicked : clicked.offset(state.get(BedBlock.FACING));
			lastUsedPos = head.toImmutable();
			lastUsedKind = Kind.BED;
			lastUsedConfirmable = true;
		} else if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
			lastUsedPos = clicked.toImmutable();
			lastUsedKind = Kind.ANCHOR;
			// Clicking with glowstone only charges the anchor; clicking an empty one does nothing.
			boolean charging = player.getMainHandStack().isOf(Items.GLOWSTONE) || player.getOffHandStack().isOf(Items.GLOWSTONE);
			lastUsedConfirmable = !charging && state.get(RespawnAnchorBlock.CHARGES) > 0;
		} else {
			return;
		}
		lastUsedDimension = world.getRegistryKey().getValue().toString();
		lastUsedMs = Util.getMeasuringTimeMs();
	}

	private void onGameMessage(Text message) {
		if (!(message.getContent() instanceof TranslatableTextContent content)) {
			return;
		}
		syncWorldData();
		switch (content.getKey()) {
			case "block.minecraft.set_spawn" -> {
				if (lastUsedPos != null && Util.getMeasuringTimeMs() - lastUsedMs <= USE_WINDOW_MS) {
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (args.length < 7 || client.player == null) {
			return;
		}
		String target = args[6] instanceof Text text ? text.getString() : String.valueOf(args[6]);
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
	public void onTick(MinecraftClient client) {
		syncWorldData();
		if (client.player == null || client.world == null) {
			return;
		}
		ClientWorld world = client.world;
		confirmSilentClick(world);
		if (pos == null || ++ticks % CHECK_TICKS != 0) {
			return;
		}
		if (status != Status.SET && status != Status.BROKEN) {
			return;
		}
		if (!dimension.equals(world.getRegistryKey().getValue().toString())
				|| !world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			return; // cannot see it from here; keep the last known state
		}
		boolean works = stillWorks(world.getBlockState(pos));
		if (!works && status == Status.SET) {
			status = Status.BROKEN;
			warningUntilMs = Util.getMeasuringTimeMs() + WARNING_MS;
			if (warnSound.get()) {
				client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5F));
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
	private void confirmSilentClick(ClientWorld world) {
		if (lastUsedPos == null || Util.getMeasuringTimeMs() - lastUsedMs < CONFIRM_MS) {
			return;
		}
		BlockPos clicked = lastUsedPos;
		lastUsedPos = null;
		if (!lastUsedConfirmable || !lastUsedDimension.equals(world.getRegistryKey().getValue().toString())) {
			return;
		}
		// In dimensions where beds / anchors blow up, the block is gone by now.
		BlockState state = world.getBlockState(clicked);
		boolean stillThere = lastUsedKind == Kind.BED ? state.isIn(BlockTags.BEDS) : state.isOf(Blocks.RESPAWN_ANCHOR);
		if (stillThere) {
			setRespawn(clicked, lastUsedDimension, lastUsedKind);
		}
	}

	private boolean stillWorks(BlockState state) {
		return switch (kind) {
			case BED -> state.isIn(BlockTags.BEDS);
			case ANCHOR -> state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0;
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
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		String here = client.world.getRegistryKey().getValue().toString();

		Text line = null;
		switch (status) {
			case UNKNOWN -> {
				if (showWhenUnknown.get()) {
					line = Text.translatable(key + "unknown").withColor(0xAAAAAA);
				}
			}
			case NONE -> line = Text.translatable(key + "none").withColor(0xFFAA00);
			case SET -> {
				String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
				if (!dimension.equals(here)) {
					line = Text.translatable(key + "set_other_dimension", dimensionName(dimension), where);
				} else if (showDistance.get()) {
					int distance = (int) Math.round(Math.sqrt(client.player.getBlockPos().getSquaredDistance(pos)));
					line = Text.translatable(key + "set_distance", where, distance);
				} else {
					line = Text.translatable(key + "set", where);
				}
			}
			case BROKEN -> line = Text.translatable(key + "broken", pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
					.withColor(0xFF5555);
		}
		if (line != null) {
			layout.drawLines(context, client.textRenderer, position.get(), List.of(line));
		}

		if (status == Status.BROKEN && Util.getMeasuringTimeMs() < warningUntilMs) {
			drawBigWarning(context, client, layout, Text.translatable(key + "warning_title"),
					Text.translatable(key + (kind == Kind.ANCHOR ? "warning_anchor" : "warning_bed")));
		}

		if (bedHint.get() && client.crosshairTarget instanceof BlockHitResult hit
				&& client.world.getBlockState(hit.getBlockPos()).isIn(BlockTags.BEDS)) {
			drawBedHint(context, client, layout, hit.getBlockPos(), here, key);
		}
	}

	/** Two centred lines in the upper third of the screen, the first one double size. */
	private static void drawBigWarning(DrawContext context, MinecraftClient client, HudLayout layout, Text title, Text detail) {
		int centerX = layout.getScreenWidth() / 2;
		int y = layout.getScreenHeight() / 4;
		int titleWidth = client.textRenderer.getWidth(title) * 2;
		int detailWidth = client.textRenderer.getWidth(detail);
		int half = Math.max(titleWidth, detailWidth) / 2 + 6;
		context.fill(centerX - half, y - 5, centerX + half, y + 34, 0xA0000000);
		// Blink the title so it catches the eye.
		boolean bright = Util.getMeasuringTimeMs() / 400 % 2 == 0;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(centerX, y);
		context.getMatrices().scale(2F, 2F);
		context.drawCenteredTextWithShadow(client.textRenderer, title, 0, 0, bright ? 0xFFFF5555 : 0xFFFFAAAA);
		context.getMatrices().popMatrix();
		context.drawCenteredTextWithShadow(client.textRenderer, detail, centerX, y + 22, HudLayout.WHITE);
	}

	private void drawBedHint(DrawContext context, MinecraftClient client, HudLayout layout, BlockPos looked, String here, String key) {
		BlockState state = client.world.getBlockState(looked);
		BlockPos head = state.get(BedBlock.PART) == BedPart.HEAD ? looked : looked.offset(state.get(BedBlock.FACING));
		Text hint;
		int color;
		if (status == Status.SET && kind == Kind.BED && head.equals(pos) && dimension.equals(here)) {
			hint = Text.translatable(key + "bed_yours");
			color = 0xFF55FF55;
		} else if (status == Status.UNKNOWN) {
			hint = Text.translatable(key + "bed_unsure");
			color = 0xFFAAAAAA;
		} else {
			hint = Text.translatable(key + "bed_not_yours");
			color = 0xFFFFAA00;
		}
		int width = client.textRenderer.getWidth(hint);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 + 14;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, hint, x, y, color);
	}

	/** "minecraft:the_nether" -> "The Nether"; unknown (modded) dimensions are shown as their id. */
	private static Text dimensionName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String key = "qolbundle.dimension." + path.toLowerCase(Locale.ROOT);
		return I18n.hasTranslation(key) ? Text.translatable(key) : Text.literal(id);
	}
}

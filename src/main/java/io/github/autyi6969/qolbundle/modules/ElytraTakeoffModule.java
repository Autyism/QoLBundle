package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>One key does the usual elytra take-off for you: jump, open the elytra, fire one rocket.
 * It presses the jump key and uses the rocket exactly as the player would, a few ticks apart;
 * it does not turn the view, so look up first.
 */
public class ElytraTakeoffModule extends Module {
	private enum Step {
		IDLE,
		/** Jump key is held for this tick so the player leaves the ground. */
		JUMPING,
		/** Jump key released; waiting until the player is off the ground. */
		WAIT_AIRBORNE,
		/** Jump key pressed a second time: that opens the elytra. */
		OPENING,
		/** Elytra is open; fire the rocket. */
		FIRE,
		/** Rocket used from the hotbar; switch back to the slot the player had selected. */
		RESTORE_SLOT
	}

	private static final int TIMEOUT_TICKS = 30;
	private static final int NO_SLOT = -1;

	private final KeyBinding takeoffKey;
	private Step step = Step.IDLE;
	private int ticksInSequence;
	private int slotToRestore = NO_SLOT;
	private boolean fireRocket;
	private int takeoffs;

	public ElytraTakeoffModule() {
		super("elytra_takeoff", ModuleCategory.GREY, false);
		takeoffKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.qolbundle.elytra_takeoff",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, QoLBundleClient.KEY_CATEGORY));
	}

	public boolean isBusy() {
		return step != Step.IDLE;
	}

	/** Completed take-offs since the game started (for the self-test). */
	public int getTakeoffs() {
		return takeoffs;
	}

	/** Starts the sequence if everything needed is there; otherwise tells the player what is missing. */
	public void trigger(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || step != Step.IDLE) {
			return;
		}
		String problem = null;
		ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
		if (!chest.isOf(Items.ELYTRA) || chest.getMaxDamage() - chest.getDamage() <= 1) {
			problem = "no_elytra";
		} else if (player.isGliding()) {
			problem = "already_flying";
		} else if (player.getAbilities().flying || player.isTouchingWater() || player.hasVehicle()) {
			problem = "cannot_now";
		}
		if (problem != null) {
			client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + "." + problem).formatted(Formatting.RED), false);
			return;
		}
		ticksInSequence = 0;
		// Without rockets the first two steps still happen: jump and open the elytra.
		fireRocket = findRocketSlot(player) != NO_SLOT || player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
		if (player.isOnGround()) {
			client.options.jumpKey.setPressed(true);
			step = Step.JUMPING;
		} else {
			step = Step.WAIT_AIRBORNE; // already falling: skip the jump
		}
	}

	/** Hotbar slot (0-8) holding rockets, or NO_SLOT. */
	private static int findRocketSlot(ClientPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < PlayerInventory.HOTBAR_SIZE; slot++) {
			if (inventory.getStack(slot).isOf(Items.FIREWORK_ROCKET)) {
				return slot;
			}
		}
		return NO_SLOT;
	}

	private void abort(MinecraftClient client) {
		client.options.jumpKey.setPressed(false);
		if (slotToRestore != NO_SLOT && client.player != null) {
			client.player.getInventory().setSelectedSlot(slotToRestore);
		}
		slotToRestore = NO_SLOT;
		step = Step.IDLE;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled && step != Step.IDLE) {
			abort(MinecraftClient.getInstance());
		}
	}

	@Override
	public void onTick(MinecraftClient client) {
		while (takeoffKey.wasPressed()) {
			trigger(client);
		}
		if (step == Step.IDLE) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.interactionManager == null || ++ticksInSequence > TIMEOUT_TICKS) {
			abort(client);
			return;
		}
		switch (step) {
			case JUMPING -> {
				client.options.jumpKey.setPressed(false);
				step = Step.WAIT_AIRBORNE;
			}
			case WAIT_AIRBORNE -> {
				if (!player.isOnGround()) {
					client.options.jumpKey.setPressed(true);
					step = Step.OPENING;
				}
			}
			case OPENING -> {
				client.options.jumpKey.setPressed(false);
				// If the elytra did not open on that press, go back one step and press again.
				if (!player.isGliding()) {
					step = Step.WAIT_AIRBORNE;
				} else if (fireRocket) {
					step = Step.FIRE;
				} else {
					client.inGameHud.setOverlayMessage(Text.translatable(getTranslationKey() + ".no_rockets"), false);
					finish();
				}
			}
			case FIRE -> {
				if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
					client.interactionManager.interactItem(player, Hand.OFF_HAND);
					player.swingHand(Hand.OFF_HAND);
					finish();
				} else {
					int rocketSlot = findRocketSlot(player);
					if (rocketSlot == NO_SLOT) {
						abort(client);
						return;
					}
					slotToRestore = player.getInventory().getSelectedSlot();
					player.getInventory().setSelectedSlot(rocketSlot);
					client.interactionManager.interactItem(player, Hand.MAIN_HAND);
					player.swingHand(Hand.MAIN_HAND);
					step = Step.RESTORE_SLOT;
				}
			}
			case RESTORE_SLOT -> {
				player.getInventory().setSelectedSlot(slotToRestore);
				slotToRestore = NO_SLOT;
				finish();
			}
			default -> {
			}
		}
	}

	private void finish() {
		takeoffs++;
		step = Step.IDLE;
	}
}

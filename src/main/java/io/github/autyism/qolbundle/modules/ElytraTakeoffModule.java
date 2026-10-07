package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	private final KeyMapping takeoffKey;
	private Step step = Step.IDLE;
	private int ticksInSequence;
	private int slotToRestore = NO_SLOT;
	private boolean fireRocket;
	private int takeoffs;

	public ElytraTakeoffModule() {
		super("elytra_takeoff", ModuleCategory.GREY, false);
		takeoffKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.elytra_takeoff",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, QoLBundleClient.KEY_CATEGORY));
	}

	public boolean isBusy() {
		return step != Step.IDLE;
	}

	/** Completed take-offs since the game started (for the self-test). */
	public int getTakeoffs() {
		return takeoffs;
	}

	/** Starts the sequence if everything needed is there; otherwise tells the player what is missing. */
	public void trigger(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || step != Step.IDLE) {
			return;
		}
		String problem = null;
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA) || chest.getMaxDamage() - chest.getDamageValue() <= 1) {
			problem = "no_elytra";
		} else if (player.isFallFlying()) {
			problem = "already_flying";
		} else if (player.getAbilities().flying || player.isInWater() || player.isPassenger()) {
			problem = "cannot_now";
		}
		if (problem != null) {
			client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + "." + problem).withStyle(ChatFormatting.RED), false);
			return;
		}
		ticksInSequence = 0;
		// Without rockets the first two steps still happen: jump and open the elytra.
		fireRocket = findRocketSlot(player) != NO_SLOT || player.getOffhandItem().is(Items.FIREWORK_ROCKET);
		if (player.onGround()) {
			client.options.keyJump.setDown(true);
			step = Step.JUMPING;
		} else {
			step = Step.WAIT_AIRBORNE; // already falling: skip the jump
		}
	}

	/** Hotbar slot (0-8) holding rockets, or NO_SLOT. */
	private static int findRocketSlot(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
			if (inventory.getItem(slot).is(Items.FIREWORK_ROCKET)) {
				return slot;
			}
		}
		return NO_SLOT;
	}

	private void abort(Minecraft client) {
		client.options.keyJump.setDown(false);
		if (slotToRestore != NO_SLOT && client.player != null) {
			client.player.getInventory().setSelectedSlot(slotToRestore);
		}
		slotToRestore = NO_SLOT;
		step = Step.IDLE;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		if (!enabled && step != Step.IDLE) {
			abort(Minecraft.getInstance());
		}
	}

	@Override
	public void onTick(Minecraft client) {
		while (takeoffKey.consumeClick()) {
			trigger(client);
		}
		if (step == Step.IDLE) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || ++ticksInSequence > TIMEOUT_TICKS) {
			abort(client);
			return;
		}
		switch (step) {
			case JUMPING -> {
				client.options.keyJump.setDown(false);
				step = Step.WAIT_AIRBORNE;
			}
			case WAIT_AIRBORNE -> {
				if (!player.onGround()) {
					client.options.keyJump.setDown(true);
					step = Step.OPENING;
				}
			}
			case OPENING -> {
				client.options.keyJump.setDown(false);
				// If the elytra did not open on that press, go back one step and press again.
				if (!player.isFallFlying()) {
					step = Step.WAIT_AIRBORNE;
				} else if (fireRocket) {
					step = Step.FIRE;
				} else {
					client.gui.setOverlayMessage(Component.translatable(getTranslationKey() + ".no_rockets"), false);
					finish();
				}
			}
			case FIRE -> {
				if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
					client.gameMode.useItem(player, InteractionHand.OFF_HAND);
					player.swing(InteractionHand.OFF_HAND);
					finish();
				} else {
					int rocketSlot = findRocketSlot(player);
					if (rocketSlot == NO_SLOT) {
						abort(client);
						return;
					}
					slotToRestore = player.getInventory().getSelectedSlot();
					player.getInventory().setSelectedSlot(rocketSlot);
					client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
					player.swing(InteractionHand.MAIN_HAND);
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

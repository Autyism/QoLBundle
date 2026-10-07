package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * While falling: shows how many hearts the landing will cost if you hit the ground straight below,
 * and whether that kills you. Uses the same formula as the game, including Feather Falling,
 * Protection, Resistance, Slow Falling, Jump Boost and soft landing blocks.
 */
public class FallDamageModule extends Module {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final BoolSetting showDistance = add(new BoolSetting("show_distance", true));
	private final BoolSetting showWhenSafe = add(new BoolSetting("show_when_safe", false));

	/** Result of the last tick. */
	private boolean falling;
	private float damage;
	private double fallHeight;
	private boolean lethal;
	private boolean totem;
	private boolean groundKnown;

	public FallDamageModule() {
		super("fall_damage", ModuleCategory.INFO, true);
	}

	/** Predicted damage in half-hearts, 0 when not falling (for the self-test). */
	public float getPredictedDamage() {
		return falling ? damage : 0F;
	}

	public boolean isLethal() {
		return falling && lethal;
	}

	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		falling = false;
		if (player == null || client.level == null) {
			return;
		}
		// Situations in which the game deals no fall damage at all, or the player is not falling.
		if (player.onGround() || player.getAbilities().invulnerable || player.getAbilities().flying
				|| player.isFallFlying() || player.onClimbable() || player.isPassenger()
				|| player.isInWater() || player.isInLava() || player.isSpectator()) {
			return;
		}
		if (player.getDeltaMovement().y >= 0 && player.fallDistance <= 0) {
			return; // still going up after a jump
		}

		Landing landing = findLanding(client.level, player);
		falling = true;
		groundKnown = landing != null;
		if (landing == null) {
			// Nothing below within loaded chunks (void, or chunks not loaded yet).
			damage = 0F;
			fallHeight = 0;
			lethal = false;
			return;
		}
		fallHeight = player.fallDistance + Math.max(0, player.getY() - landing.y);
		damage = computeDamage(client.level, player, fallHeight, landing);
		float life = player.getHealth() + player.getAbsorptionAmount();
		lethal = damage >= life;
		totem = lethal && (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
				|| player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
	}

	/** Where the player's feet will come to rest when dropping straight down. */
	private record Landing(double y, float damagePerBlock, double extraDistance) {
	}

	private static Landing findLanding(ClientLevel world, LocalPlayer player) {
		AABB box = player.getBoundingBox();
		int minX = Mth.floor(box.minX + 1.0E-4);
		int maxX = Mth.floor(box.maxX - 1.0E-4);
		int minZ = Mth.floor(box.minZ + 1.0E-4);
		int maxZ = Mth.floor(box.maxZ - 1.0E-4);
		double feet = box.minY;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int y = Mth.floor(feet); y >= world.getMinY(); y--) {
			double bestTop = Double.NEGATIVE_INFINITY;
			Landing best = null;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					if (!world.hasChunk(x >> 4, z >> 4)) {
						return null;
					}
					BlockState state = world.getBlockState(pos);
					// Water, cobwebs, vines, ladders, berry bushes and powder snow all cancel the fall.
					if (state.getFluidState().is(FluidTags.WATER) || state.is(BlockTags.FALL_DAMAGE_RESETTING)
							|| state.is(Blocks.POWDER_SNOW)) {
						if (y + 1.0 > bestTop) {
							bestTop = y + 1.0;
							best = new Landing(Math.min(feet, y + 1.0), 0F, 0);
						}
						continue;
					}
					VoxelShape shape = state.getCollisionShape(world, pos);
					if (shape.isEmpty()) {
						continue;
					}
					double top = y + shape.max(Direction.Axis.Y);
					if (top <= feet + 1.0E-3 && top > bestTop) {
						bestTop = top;
						best = landingOn(state, top);
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	private static Landing landingOn(BlockState state, double top) {
		if (state.is(Blocks.SLIME_BLOCK)) {
			return new Landing(top, 0F, 0);
		}
		if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.HONEY_BLOCK)) {
			return new Landing(top, 0.2F, 0);
		}
		if (state.is(BlockTags.BEDS)) {
			return new Landing(top, 0.5F, 0);
		}
		if (state.is(Blocks.POINTED_DRIPSTONE)
				&& state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP
				&& state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP) {
			return new Landing(top, 2F, 2.5);
		}
		return new Landing(top, 1F, 0);
	}

	private static float computeDamage(ClientLevel world, LocalPlayer player, double fallHeight, Landing landing) {
		if (landing.damagePerBlock <= 0F || player.hasEffect(MobEffects.SLOW_FALLING)) {
			return 0F;
		}
		// Same as LivingEntity.computeFallDamage. Jump Boost is already part of the safe-fall attribute.
		double unsafe = fallHeight + landing.extraDistance + 1.0E-6 - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		float raw = Mth.floor(unsafe * landing.damagePerBlock * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		if (raw <= 0F) {
			return 0F;
		}
		// Armor does not reduce fall damage; Resistance and protection enchantments do.
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			raw *= Math.max(0F, 1F - 0.2F * (resistance.getAmplifier() + 1));
		}
		int protectionPoints = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = player.getItemBySlot(slot);
			protectionPoints += level(world, Enchantments.PROTECTION, stack);
			protectionPoints += 3 * level(world, Enchantments.FEATHER_FALLING, stack);
		}
		raw *= 1F - Math.min(20, protectionPoints) / 25F;
		return raw;
	}

	private static int level(ClientLevel world, ResourceKey<Enchantment> key, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Optional<Holder.Reference<Enchantment>> entry =
				world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		return entry.map(reference -> EnchantmentHelper.getItemEnchantmentLevel(reference, stack)).orElse(0);
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		if (!falling) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		String key = getTranslationKey() + ".hud.";
		Component text;
		int color;
		if (!groundKnown) {
			text = Component.translatable(key + "no_ground");
			color = 0xFFAAAAAA;
		} else if (damage <= 0F) {
			if (!showWhenSafe.get()) {
				return;
			}
			text = Component.translatable(key + "safe");
			color = 0xFF55FF55;
		} else {
			String hearts = formatHearts(damage);
			if (lethal) {
				text = Component.translatable(key + (totem ? "lethal_totem" : "lethal"), hearts);
				color = 0xFFFF5555;
			} else {
				text = Component.translatable(key + "damage", hearts);
				color = 0xFFFFFF55;
			}
		}
		if (showDistance.get() && groundKnown) {
			text = text.copy().append(Component.translatable(key + "distance", String.format(Locale.ROOT, "%.1f", fallHeight)));
		}
		int width = client.font.width(text);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 - 24;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawString(client.font, text, x, y, color);
	}

	/** Damage points to hearts: 7 -> "3.5", 8 -> "4". */
	private static String formatHearts(float damagePoints) {
		float hearts = damagePoints / 2F;
		if (Math.abs(hearts - Math.round(hearts)) < 0.05F) {
			return Integer.toString(Math.round(hearts));
		}
		return String.format(Locale.ROOT, "%.1f", hearts);
	}
}

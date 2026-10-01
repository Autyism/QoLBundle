package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

import java.util.Locale;
import java.util.Optional;

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
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		falling = false;
		if (player == null || client.world == null) {
			return;
		}
		// Situations in which the game deals no fall damage at all, or the player is not falling.
		if (player.isOnGround() || player.getAbilities().invulnerable || player.getAbilities().flying
				|| player.isGliding() || player.isClimbing() || player.hasVehicle()
				|| player.isTouchingWater() || player.isInLava() || player.isSpectator()) {
			return;
		}
		if (player.getVelocity().y >= 0 && player.fallDistance <= 0) {
			return; // still going up after a jump
		}

		Landing landing = findLanding(client.world, player);
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
		damage = computeDamage(client.world, player, fallHeight, landing);
		float life = player.getHealth() + player.getAbsorptionAmount();
		lethal = damage >= life;
		totem = lethal && (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
				|| player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
	}

	/** Where the player's feet will come to rest when dropping straight down. */
	private record Landing(double y, float damagePerBlock, double extraDistance) {
	}

	private static Landing findLanding(ClientWorld world, ClientPlayerEntity player) {
		Box box = player.getBoundingBox();
		int minX = MathHelper.floor(box.minX + 1.0E-4);
		int maxX = MathHelper.floor(box.maxX - 1.0E-4);
		int minZ = MathHelper.floor(box.minZ + 1.0E-4);
		int maxZ = MathHelper.floor(box.maxZ - 1.0E-4);
		double feet = box.minY;
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int y = MathHelper.floor(feet); y >= world.getBottomY(); y--) {
			double bestTop = Double.NEGATIVE_INFINITY;
			Landing best = null;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					if (!world.isChunkLoaded(x >> 4, z >> 4)) {
						return null;
					}
					BlockState state = world.getBlockState(pos);
					// Water, cobwebs, vines, ladders, berry bushes and powder snow all cancel the fall.
					if (state.getFluidState().isIn(FluidTags.WATER) || state.isIn(BlockTags.FALL_DAMAGE_RESETTING)
							|| state.isOf(Blocks.POWDER_SNOW)) {
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
					double top = y + shape.getMax(Direction.Axis.Y);
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
		if (state.isOf(Blocks.SLIME_BLOCK)) {
			return new Landing(top, 0F, 0);
		}
		if (state.isOf(Blocks.HAY_BLOCK) || state.isOf(Blocks.HONEY_BLOCK)) {
			return new Landing(top, 0.2F, 0);
		}
		if (state.isIn(BlockTags.BEDS)) {
			return new Landing(top, 0.5F, 0);
		}
		if (state.isOf(Blocks.POINTED_DRIPSTONE)
				&& state.get(PointedDripstoneBlock.VERTICAL_DIRECTION) == Direction.UP
				&& state.get(PointedDripstoneBlock.THICKNESS) == Thickness.TIP) {
			return new Landing(top, 2F, 2.5);
		}
		return new Landing(top, 1F, 0);
	}

	private static float computeDamage(ClientWorld world, ClientPlayerEntity player, double fallHeight, Landing landing) {
		if (landing.damagePerBlock <= 0F || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
			return 0F;
		}
		// Same as LivingEntity.computeFallDamage. Jump Boost is already part of the safe-fall attribute.
		double unsafe = fallHeight + landing.extraDistance + 1.0E-6 - player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE);
		float raw = MathHelper.floor(unsafe * landing.damagePerBlock * player.getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER));
		if (raw <= 0F) {
			return 0F;
		}
		// Armor does not reduce fall damage; Resistance and protection enchantments do.
		StatusEffectInstance resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
		if (resistance != null) {
			raw *= Math.max(0F, 1F - 0.2F * (resistance.getAmplifier() + 1));
		}
		int protectionPoints = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = player.getEquippedStack(slot);
			protectionPoints += level(world, Enchantments.PROTECTION, stack);
			protectionPoints += 3 * level(world, Enchantments.FEATHER_FALLING, stack);
		}
		raw *= 1F - Math.min(20, protectionPoints) / 25F;
		return raw;
	}

	private static int level(ClientWorld world, RegistryKey<Enchantment> key, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Optional<RegistryEntry.Reference<Enchantment>> entry =
				world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key);
		return entry.map(reference -> EnchantmentHelper.getLevel(reference, stack)).orElse(0);
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		if (!falling) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		String key = getTranslationKey() + ".hud.";
		Text text;
		int color;
		if (!groundKnown) {
			text = Text.translatable(key + "no_ground");
			color = 0xFFAAAAAA;
		} else if (damage <= 0F) {
			if (!showWhenSafe.get()) {
				return;
			}
			text = Text.translatable(key + "safe");
			color = 0xFF55FF55;
		} else {
			String hearts = formatHearts(damage);
			if (lethal) {
				text = Text.translatable(key + (totem ? "lethal_totem" : "lethal"), hearts);
				color = 0xFFFF5555;
			} else {
				text = Text.translatable(key + "damage", hearts);
				color = 0xFFFFFF55;
			}
		}
		if (showDistance.get() && groundKnown) {
			text = text.copy().append(Text.translatable(key + "distance", String.format(Locale.ROOT, "%.1f", fallHeight)));
		}
		int width = client.textRenderer.getWidth(text);
		int x = (layout.getScreenWidth() - width) / 2;
		int y = layout.getScreenHeight() / 2 - 24;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, text, x, y, color);
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

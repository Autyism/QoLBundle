package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.util.Sight;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Opponent gear panel: for the player you are looking at, what they hold, what armor they wear and
 * with which enchantments, and whether they are drinking, eating, drawing a bow or blocking right
 * now.
 *
 * <p>All of this is what the game already shows on the other player's model; the panel only reads
 * it out. It works on players in front of you with nothing solid in between, never through walls.
 */
public class EnemyGearModule extends Module {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** How far off the crosshair a player may be and still count as "the one I am looking at". */
	private static final double PICK_DEGREES = 12.0;
	private static final int LINGER_TICKS = 40;
	private static final int ROW = 20;

	/** One row of the panel: an item with the text next to it. */
	public record Row(ItemStack stack, Component name, @Nullable Component enchantments) {
	}

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_RIGHT));
	private final IntSetting range = add(new IntSetting("range", 32, 8, 64));
	private final BoolSetting showEnchantments = add(new BoolSetting("show_enchantments", true));
	private final BoolSetting showAction = add(new BoolSetting("show_action", true));

	@Nullable
	private Avatar target;
	private long lastSeen;
	private long tick;

	public EnemyGearModule() {
		super("enemy_gear", ModuleCategory.PVP, true);
	}

	@Nullable
	public Avatar getTarget() {
		return target;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		target = null;
	}

	@Override
	public void onTick(Minecraft client) {
		tick++;
		if (client.player == null || client.level == null) {
			target = null;
			return;
		}
		if (tick % 2 != 0) {
			return;
		}
		Avatar best = null;
		double bestAngle = PICK_DEGREES;
		for (Avatar other : Sight.visiblePlayers(client, range.get())) {
			double angle = Sight.angleFromCrosshair(client, other);
			if (angle < bestAngle) {
				bestAngle = angle;
				best = other;
			}
		}
		if (best != null) {
			target = best;
			lastSeen = tick;
		} else if (target != null && (tick - lastSeen > LINGER_TICKS || !target.isAlive() || target.isRemoved())) {
			target = null; // looked away for a while
		}
	}

	/** What the target is doing with the item in use, or null. */
	@Nullable
	public Component actionOf(Avatar player) {
		if (!player.isUsingItem()) {
			return null;
		}
		ItemStack inUse = player.getUseItem();
		String key = getTranslationKey() + ".action.";
		return switch (inUse.getUseAnimation()) {
			case DRINK -> Component.translatable(key + "drink", inUse.getHoverName());
			case EAT -> Component.translatable(key + "eat", inUse.getHoverName());
			case BOW -> Component.translatable(key + "bow");
			case CROSSBOW -> Component.translatable(key + "crossbow");
			case BLOCK -> Component.translatable(key + "block");
			//~ if <1.21.11 'TRIDENT, SPEAR' -> 'SPEAR'
			case TRIDENT, SPEAR -> Component.translatable(key + "throw", inUse.getHoverName());
			default -> Component.translatable(key + "other", inUse.getHoverName());
		};
	}

	public List<Row> rowsOf(Avatar player) {
		List<Row> rows = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			MutableComponent name = stack.getHoverName().copy();
			if (stack.getCount() > 1) {
				name.append(Component.literal(" ×" + stack.getCount()));
			}
			MutableComponent enchantments = null;
			if (showEnchantments.get()) {
				for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
					if (enchantments == null) {
						enchantments = Component.empty();
					} else {
						enchantments.append(Component.literal(", "));
					}
					enchantments.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
				}
			}
			rows.add(new Row(stack, name, enchantments));
		}
		return rows;
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Avatar shown = target;
		Minecraft client = Minecraft.getInstance();
		HudAnchor anchor = position.get();
		if (shown == null || client.player == null || layout.isBlocked(anchor)) {
			return;
		}
		Font textRenderer = client.font;
		Component title = Component.translatable(getTranslationKey() + ".hud.title", shown.getName(), (int) Math.round(shown.distanceTo(client.player)));
		Component action = showAction.get() ? actionOf(shown) : null;
		List<Row> rows = rowsOf(shown);

		int width = textRenderer.width(title);
		if (action != null) {
			width = Math.max(width, textRenderer.width(action));
		}
		for (Row row : rows) {
			width = Math.max(width, 20 + textRenderer.width(row.name));
			if (row.enchantments != null) {
				width = Math.max(width, 20 + textRenderer.width(row.enchantments));
			}
		}
		int height = 11 + (action != null ? 11 : 0) + rows.size() * ROW + (rows.isEmpty() ? 11 : 0);
		int top = layout.reserve(anchor, height + 4);
		int left = layout.xFor(anchor, width + 6);
		context.fill(left, top, left + width + 6, top + height + 2, 0x90000000);
		int y = top + 2;
		context.drawString(textRenderer, title, left + 3, y, 0xFFFFFF55);
		y += 11;
		if (action != null) {
			context.drawString(textRenderer, action, left + 3, y, 0xFFFF5555);
			y += 11;
		}
		if (rows.isEmpty()) {
			context.drawString(textRenderer, Component.translatable(getTranslationKey() + ".hud.nothing").withStyle(ChatFormatting.GRAY), left + 3, y, 0xFFFFFFFF);
		}
		for (Row row : rows) {
			context.renderItem(row.stack, left + 3, y + 1);
			boolean enchanted = row.enchantments != null;
			context.drawString(textRenderer, row.name, left + 23, enchanted ? y : y + 5, 0xFFFFFFFF);
			if (enchanted) {
				context.drawString(textRenderer, row.enchantments, left + 23, y + 10, 0xFFAA88FF);
			}
			y += ROW;
		}
	}
}

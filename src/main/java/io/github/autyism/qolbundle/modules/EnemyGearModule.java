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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
	public record Row(ItemStack stack, Text name, @Nullable Text enchantments) {
	}

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_RIGHT));
	private final IntSetting range = add(new IntSetting("range", 32, 8, 64));
	private final BoolSetting showEnchantments = add(new BoolSetting("show_enchantments", true));
	private final BoolSetting showAction = add(new BoolSetting("show_action", true));

	@Nullable
	private PlayerLikeEntity target;
	private long lastSeen;
	private long tick;

	public EnemyGearModule() {
		super("enemy_gear", ModuleCategory.PVP, true);
	}

	@Nullable
	public PlayerLikeEntity getTarget() {
		return target;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		target = null;
	}

	@Override
	public void onTick(MinecraftClient client) {
		tick++;
		if (client.player == null || client.world == null) {
			target = null;
			return;
		}
		if (tick % 2 != 0) {
			return;
		}
		PlayerLikeEntity best = null;
		double bestAngle = PICK_DEGREES;
		for (PlayerLikeEntity other : Sight.visiblePlayers(client, range.get())) {
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
	public Text actionOf(PlayerLikeEntity player) {
		if (!player.isUsingItem()) {
			return null;
		}
		ItemStack inUse = player.getActiveItem();
		String key = getTranslationKey() + ".action.";
		return switch (inUse.getUseAction()) {
			case DRINK -> Text.translatable(key + "drink", inUse.getName());
			case EAT -> Text.translatable(key + "eat", inUse.getName());
			case BOW -> Text.translatable(key + "bow");
			case CROSSBOW -> Text.translatable(key + "crossbow");
			case BLOCK -> Text.translatable(key + "block");
			case TRIDENT, SPEAR -> Text.translatable(key + "throw", inUse.getName());
			default -> Text.translatable(key + "other", inUse.getName());
		};
	}

	public List<Row> rowsOf(PlayerLikeEntity player) {
		List<Row> rows = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getEquippedStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			MutableText name = stack.getName().copy();
			if (stack.getCount() > 1) {
				name.append(Text.literal(" ×" + stack.getCount()));
			}
			MutableText enchantments = null;
			if (showEnchantments.get()) {
				for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
					if (enchantments == null) {
						enchantments = Text.empty();
					} else {
						enchantments.append(Text.literal(", "));
					}
					enchantments.append(Enchantment.getName(entry.getKey(), entry.getIntValue()).getString());
				}
			}
			rows.add(new Row(stack, name, enchantments));
		}
		return rows;
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		PlayerLikeEntity shown = target;
		MinecraftClient client = MinecraftClient.getInstance();
		HudAnchor anchor = position.get();
		if (shown == null || client.player == null || layout.isBlocked(anchor)) {
			return;
		}
		TextRenderer textRenderer = client.textRenderer;
		Text title = Text.translatable(getTranslationKey() + ".hud.title", shown.getName(), (int) Math.round(shown.distanceTo(client.player)));
		Text action = showAction.get() ? actionOf(shown) : null;
		List<Row> rows = rowsOf(shown);

		int width = textRenderer.getWidth(title);
		if (action != null) {
			width = Math.max(width, textRenderer.getWidth(action));
		}
		for (Row row : rows) {
			width = Math.max(width, 20 + textRenderer.getWidth(row.name));
			if (row.enchantments != null) {
				width = Math.max(width, 20 + textRenderer.getWidth(row.enchantments));
			}
		}
		int height = 11 + (action != null ? 11 : 0) + rows.size() * ROW + (rows.isEmpty() ? 11 : 0);
		int top = layout.reserve(anchor, height + 4);
		int left = layout.xFor(anchor, width + 6);
		context.fill(left, top, left + width + 6, top + height + 2, 0x90000000);
		int y = top + 2;
		context.drawTextWithShadow(textRenderer, title, left + 3, y, 0xFFFFFF55);
		y += 11;
		if (action != null) {
			context.drawTextWithShadow(textRenderer, action, left + 3, y, 0xFFFF5555);
			y += 11;
		}
		if (rows.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable(getTranslationKey() + ".hud.nothing").formatted(Formatting.GRAY), left + 3, y, 0xFFFFFFFF);
		}
		for (Row row : rows) {
			context.drawItem(row.stack, left + 3, y + 1);
			boolean enchanted = row.enchantments != null;
			context.drawTextWithShadow(textRenderer, row.name, left + 23, enchanted ? y : y + 5, 0xFFFFFFFF);
			if (enchanted) {
				context.drawTextWithShadow(textRenderer, row.enchantments, left + 23, y + 10, 0xFFAA88FF);
			}
			y += ROW;
		}
	}
}

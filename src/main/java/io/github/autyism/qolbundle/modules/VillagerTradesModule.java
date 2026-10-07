package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Look at a villager (no click needed) and see all of its trades with prices.
 *
 * <p>The server only sends a villager's trades when you open its trading screen, so the module
 * remembers the trades of every villager you have opened once this session and shows them from
 * then on. A villager you never traded with shows a hint instead.
 */
public class VillagerTradesModule extends Module {
	/** A right-click this recent decides which villager the trading screen that opens belongs to. */
	private static final long CLICK_WINDOW_MS = 5000;
	private static final int ROW_HEIGHT = 18;

	private final IntSetting maxTrades = add(new IntSetting("max_trades", 10, 3, 16));
	private final BoolSetting showStock = add(new BoolSetting("show_stock", true));

	private final Map<UUID, List<MerchantOffer>> known = new HashMap<>();
	@Nullable
	private UUID clickedVillager;
	private long clickedMs;
	@Nullable
	private String worldId;

	public VillagerTradesModule() {
		super("villager_trades", ModuleCategory.INFO, true);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() && isEnabled() && entity instanceof AbstractVillager) {
				clickedVillager = entity.getUUID();
				clickedMs = Util.getMillis();
			}
			return InteractionResult.PASS; // only watching
		});
	}

	/** Trades remembered for a villager, or null (for the self-test). */
	@Nullable
	public List<MerchantOffer> getKnownTrades(UUID villager) {
		return known.get(villager);
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		known.clear();
	}

	@Override
	public void onTick(Minecraft client) {
		if (!Objects.equals(WorldData.getWorldId(), worldId)) {
			worldId = WorldData.getWorldId();
			known.clear();
		}
		if (client.player == null || clickedVillager == null) {
			return;
		}
		// While a trading screen is open, keep the remembered copy up to date (stock changes as you trade).
		if (client.player.containerMenu instanceof MerchantMenu handler) {
			if (!handler.getOffers().isEmpty()) {
				List<MerchantOffer> copy = new ArrayList<>();
				for (MerchantOffer offer : handler.getOffers()) {
					copy.add(offer.copy());
				}
				known.put(clickedVillager, copy);
			}
		} else if (Util.getMillis() - clickedMs > CLICK_WINDOW_MS) {
			clickedVillager = null; // the click did not lead to a trading screen
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen != null || !(client.hitResult instanceof EntityHitResult hit)
				|| !(hit.getEntity() instanceof AbstractVillager merchant)) {
			return;
		}
		String key = getTranslationKey() + ".hud.";
		Component title = merchant.getDisplayName();
		if (merchant instanceof Villager villager) {
			title = Component.translatable(key + "title", title, Component.translatable("merchant.level." + villager.getVillagerData().level()));
		}
		List<MerchantOffer> offers = known.get(merchant.getUUID());

		int x = layout.getScreenWidth() / 2 + 14;
		int top;
		if (offers == null) {
			Component hint = Component.translatable(key + "unknown");
			int width = Math.max(client.font.width(title), client.font.width(hint));
			top = layout.getScreenHeight() / 2 - 12;
			context.fill(x - 3, top - 3, x + width + 3, top + 22, 0xA0000000);
			context.drawString(client.font, title, x, top, 0xFFFFD75E);
			context.drawString(client.font, hint, x, top + 11, 0xFFAAAAAA);
			return;
		}

		int shown = Math.min(offers.size(), maxTrades.get());
		List<Component> labels = new ArrayList<>();
		int width = client.font.width(title);
		for (int i = 0; i < shown; i++) {
			Component label = describe(offers.get(i));
			labels.add(label);
			width = Math.max(width, 78 + client.font.width(label));
		}
		int height = 12 + shown * ROW_HEIGHT;
		top = Math.max(4, layout.getScreenHeight() / 2 - height / 2);
		x = Math.min(x, layout.getScreenWidth() - width - 6);
		context.fill(x - 3, top - 3, x + width + 3, top + height + 1, 0xA0000000);
		context.drawString(client.font, title, x, top, 0xFFFFD75E);
		for (int i = 0; i < shown; i++) {
			MerchantOffer offer = offers.get(i);
			int y = top + 12 + i * ROW_HEIGHT;
			drawStack(context, client, offer.getCostA(), x, y);
			drawStack(context, client, offer.getCostB(), x + 18, y);
			context.drawString(client.font, "→", x + 40, y + 4, HudLayout.WHITE);
			drawStack(context, client, offer.getResult(), x + 54, y);
			context.drawString(client.font, labels.get(i), x + 76, y + 4,
					offer.isOutOfStock() ? 0xFFFF5555 : HudLayout.WHITE);
		}
	}

	private static void drawStack(GuiGraphics context, Minecraft client, ItemStack stack, int x, int y) {
		if (!stack.isEmpty()) {
			context.renderItem(stack, x, y);
			context.renderItemDecorations(client.font, stack, x, y);
		}
	}

	/** What you get, in words: the item name, for enchanted books the enchantment, and the stock. */
	private Component describe(MerchantOffer offer) {
		ItemStack sell = offer.getResult();
		MutableComponent text = Component.empty().append(sell.getHoverName());
		ItemEnchantments stored = sell.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored != null && !stored.isEmpty()) {
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
				text.append(" ").append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
			}
		}
		if (offer.isOutOfStock()) {
			text.append(Component.translatable(getTranslationKey() + ".hud.sold_out"));
		} else if (showStock.get()) {
			text.append(Component.translatable(getTranslationKey() + ".hud.stock", offer.getMaxUses() - offer.getUses()).withColor(0xAAAAAA));
		}
		return text;
	}
}

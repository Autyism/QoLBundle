package io.github.autyi6969.qolbundle.modules;

import io.github.autyi6969.qolbundle.data.WorldData;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.village.TradeOffer;
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

	private final Map<UUID, List<TradeOffer>> known = new HashMap<>();
	@Nullable
	private UUID clickedVillager;
	private long clickedMs;
	@Nullable
	private String worldId;

	public VillagerTradesModule() {
		super("villager_trades", ModuleCategory.INFO, true);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() && isEnabled() && entity instanceof MerchantEntity) {
				clickedVillager = entity.getUuid();
				clickedMs = Util.getMeasuringTimeMs();
			}
			return ActionResult.PASS; // only watching
		});
	}

	/** Trades remembered for a villager, or null (for the self-test). */
	@Nullable
	public List<TradeOffer> getKnownTrades(UUID villager) {
		return known.get(villager);
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		known.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (!Objects.equals(WorldData.getWorldId(), worldId)) {
			worldId = WorldData.getWorldId();
			known.clear();
		}
		if (client.player == null || clickedVillager == null) {
			return;
		}
		// While a trading screen is open, keep the remembered copy up to date (stock changes as you trade).
		if (client.player.currentScreenHandler instanceof MerchantScreenHandler handler) {
			if (!handler.getRecipes().isEmpty()) {
				List<TradeOffer> copy = new ArrayList<>();
				for (TradeOffer offer : handler.getRecipes()) {
					copy.add(offer.copy());
				}
				known.put(clickedVillager, copy);
			}
		} else if (Util.getMeasuringTimeMs() - clickedMs > CLICK_WINDOW_MS) {
			clickedVillager = null; // the click did not lead to a trading screen
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != null || !(client.crosshairTarget instanceof EntityHitResult hit)
				|| !(hit.getEntity() instanceof MerchantEntity merchant)) {
			return;
		}
		String key = getTranslationKey() + ".hud.";
		Text title = merchant.getDisplayName();
		if (merchant instanceof VillagerEntity villager) {
			title = Text.translatable(key + "title", title, Text.translatable("merchant.level." + villager.getVillagerData().level()));
		}
		List<TradeOffer> offers = known.get(merchant.getUuid());

		int x = layout.getScreenWidth() / 2 + 14;
		int top;
		if (offers == null) {
			Text hint = Text.translatable(key + "unknown");
			int width = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(hint));
			top = layout.getScreenHeight() / 2 - 12;
			context.fill(x - 3, top - 3, x + width + 3, top + 22, 0xA0000000);
			context.drawTextWithShadow(client.textRenderer, title, x, top, 0xFFFFD75E);
			context.drawTextWithShadow(client.textRenderer, hint, x, top + 11, 0xFFAAAAAA);
			return;
		}

		int shown = Math.min(offers.size(), maxTrades.get());
		List<Text> labels = new ArrayList<>();
		int width = client.textRenderer.getWidth(title);
		for (int i = 0; i < shown; i++) {
			Text label = describe(offers.get(i));
			labels.add(label);
			width = Math.max(width, 78 + client.textRenderer.getWidth(label));
		}
		int height = 12 + shown * ROW_HEIGHT;
		top = Math.max(4, layout.getScreenHeight() / 2 - height / 2);
		x = Math.min(x, layout.getScreenWidth() - width - 6);
		context.fill(x - 3, top - 3, x + width + 3, top + height + 1, 0xA0000000);
		context.drawTextWithShadow(client.textRenderer, title, x, top, 0xFFFFD75E);
		for (int i = 0; i < shown; i++) {
			TradeOffer offer = offers.get(i);
			int y = top + 12 + i * ROW_HEIGHT;
			drawStack(context, client, offer.getDisplayedFirstBuyItem(), x, y);
			drawStack(context, client, offer.getDisplayedSecondBuyItem(), x + 18, y);
			context.drawTextWithShadow(client.textRenderer, "→", x + 40, y + 4, HudLayout.WHITE);
			drawStack(context, client, offer.getSellItem(), x + 54, y);
			context.drawTextWithShadow(client.textRenderer, labels.get(i), x + 76, y + 4,
					offer.isDisabled() ? 0xFFFF5555 : HudLayout.WHITE);
		}
	}

	private static void drawStack(DrawContext context, MinecraftClient client, ItemStack stack, int x, int y) {
		if (!stack.isEmpty()) {
			context.drawItem(stack, x, y);
			context.drawStackOverlay(client.textRenderer, stack, x, y);
		}
	}

	/** What you get, in words: the item name, for enchanted books the enchantment, and the stock. */
	private Text describe(TradeOffer offer) {
		ItemStack sell = offer.getSellItem();
		MutableText text = Text.empty().append(sell.getName());
		ItemEnchantmentsComponent stored = sell.get(DataComponentTypes.STORED_ENCHANTMENTS);
		if (stored != null && !stored.isEmpty()) {
			for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stored.getEnchantmentEntries()) {
				text.append(" ").append(Enchantment.getName(entry.getKey(), entry.getIntValue()));
			}
		}
		if (offer.isDisabled()) {
			text.append(Text.translatable(getTranslationKey() + ".hud.sold_out"));
		} else if (showStock.get()) {
			text.append(Text.translatable(getTranslationKey() + ".hud.stock", offer.getMaxUses() - offer.getUses()).withColor(0xAAAAAA));
		}
		return text;
	}
}

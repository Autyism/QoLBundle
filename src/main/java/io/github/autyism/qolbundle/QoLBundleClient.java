package io.github.autyism.qolbundle;

import io.github.autyism.qolbundle.modules.StareAlertModule;
import io.github.autyism.qolbundle.modules.ApproachAlertModule;
import io.github.autyism.qolbundle.modules.EnemyGearModule;
import io.github.autyism.qolbundle.modules.CombatStatsModule;
import io.github.autyism.qolbundle.modules.ProjectileDirectionModule;
import io.github.autyism.qolbundle.modules.LootTimerModule;
import io.github.autyism.qolbundle.modules.AttackCooldownModule;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.api.QoLBundleAddon;
import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.gui.ModuleListScreen;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.modules.AfkClickerModule;
import io.github.autyism.qolbundle.modules.ArmorHudModule;
import io.github.autyism.qolbundle.modules.BreakProgressModule;
import io.github.autyism.qolbundle.modules.ChatEnhancementsModule;
import io.github.autyism.qolbundle.modules.ChestMemoryModule;
import io.github.autyism.qolbundle.modules.ChunkBordersModule;
import io.github.autyism.qolbundle.modules.DiagnosticsModule;
import io.github.autyism.qolbundle.modules.DurabilityAlertModule;
import io.github.autyism.qolbundle.modules.EffectRangeModule;
import io.github.autyism.qolbundle.modules.ElytraDashboardModule;
import io.github.autyism.qolbundle.modules.ElytraTakeoffModule;
import io.github.autyism.qolbundle.modules.EntityCounterModule;
import io.github.autyism.qolbundle.modules.EscapeTrailModule;
import io.github.autyism.qolbundle.modules.FallDamageModule;
import io.github.autyism.qolbundle.modules.FluidVisionModule;
import io.github.autyism.qolbundle.modules.FreecamModule;
import io.github.autyism.qolbundle.modules.FullbrightModule;
import io.github.autyism.qolbundle.modules.HotbarLayoutsModule;
import io.github.autyism.qolbundle.modules.InfoHudModule;
import io.github.autyism.qolbundle.modules.ItemSearchModule;
import io.github.autyism.qolbundle.modules.LavaSafetyModule;
import io.github.autyism.qolbundle.modules.NetherRoofModule;
import io.github.autyism.qolbundle.modules.PlacementMasterModule;
import io.github.autyism.qolbundle.modules.PortalCalculatorModule;
import io.github.autyism.qolbundle.modules.ProjectileLandingModule;
import io.github.autyism.qolbundle.modules.RecipeHelperModule;
import io.github.autyism.qolbundle.modules.RearMirrorModule;
import io.github.autyism.qolbundle.modules.RespawnPointModule;
import io.github.autyism.qolbundle.modules.ShulkerManagerModule;
import io.github.autyism.qolbundle.modules.SlimeChunksModule;
import io.github.autyism.qolbundle.modules.SoundCompassModule;
import io.github.autyism.qolbundle.modules.VillagerTradesModule;
import io.github.autyism.qolbundle.selftest.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QoLBundleClient implements ClientModInitializer {
	public static final String MOD_ID = "qolbundle";
	public static final Logger LOGGER = LoggerFactory.getLogger("QoLBundle");

	/** The "QoL Bundle" section in Options > Controls > Key Binds. */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(id("main"));
	private static KeyMapping openSettingsKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		WorldData.init();
		registerModules();
		ConfigManager.load();

		openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.qolbundle.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			for (Module module : ModuleRegistry.all()) {
				if (module.isEnabled()) {
					module.onStartTick(client);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud"), this::onRenderHud);
		// The moment the game collects its own debug shapes; ours are drawn the same way.
		//? if >=26.2 {
		/*LevelRenderEvents.BEFORE_GIZMOS.register(this::onRenderWorld);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::onSubmitWorld);
		*///?} elif >=26.1 {
		/*LevelRenderEvents.BEFORE_GIZMOS.register(this::onRenderWorld);
		// Right after the game's own debug shapes, so lines behind a see-through block stay visible.
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(this::onSubmitWorld);
		*///?} else {
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::onRenderWorld);
		WorldRenderEvents.BEFORE_ENTITIES.register(this::onSubmitWorld);
		//?}
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());

		if (SelfTest.isRequested()) {
			SelfTest.install();
		}

		LOGGER.info("QoL Bundle initialized with {} module(s)", ModuleRegistry.all().size());
	}

	/** The one place where modules are added. Order here = order in the settings screen and on the HUD. */
	private static void registerModules() {
		ModuleRegistry.register(new FullbrightModule());
		ModuleRegistry.register(new InfoHudModule());
		ModuleRegistry.register(new ArmorHudModule());
		ModuleRegistry.register(new BreakProgressModule());
		ModuleRegistry.register(new PortalCalculatorModule());
		ModuleRegistry.register(new NetherRoofModule());
		ModuleRegistry.register(new EffectRangeModule());
		ModuleRegistry.register(new EntityCounterModule());
		ModuleRegistry.register(new ChunkBordersModule());
		ModuleRegistry.register(new SlimeChunksModule());
		ModuleRegistry.register(new FallDamageModule());
		ModuleRegistry.register(new RespawnPointModule());
		ModuleRegistry.register(new VillagerTradesModule());
		ModuleRegistry.register(new ProjectileLandingModule());
		ModuleRegistry.register(new LavaSafetyModule());
		ModuleRegistry.register(new DurabilityAlertModule());
		ModuleRegistry.register(new ElytraDashboardModule());
		ModuleRegistry.register(new SoundCompassModule());
		ModuleRegistry.register(new ChatEnhancementsModule());
		ModuleRegistry.register(new ItemSearchModule());
		ModuleRegistry.register(new HotbarLayoutsModule());
		ModuleRegistry.register(new ChestMemoryModule());
		ModuleRegistry.register(new ShulkerManagerModule());
		ModuleRegistry.register(new RecipeHelperModule());
		ModuleRegistry.register(new PlacementMasterModule());
		ModuleRegistry.register(new DiagnosticsModule());
		ModuleRegistry.register(new EscapeTrailModule());
		ModuleRegistry.register(new AttackCooldownModule());
		ModuleRegistry.register(new LootTimerModule());
		ModuleRegistry.register(new ProjectileDirectionModule());
		ModuleRegistry.register(new CombatStatsModule());
		ModuleRegistry.register(new EnemyGearModule());
		ModuleRegistry.register(new ApproachAlertModule());
		ModuleRegistry.register(new StareAlertModule());
		// Grey zone (single-player / own server), all off by default.
		ModuleRegistry.register(new AfkClickerModule());
		ModuleRegistry.register(new FreecamModule());
		ModuleRegistry.register(new ElytraTakeoffModule());
		ModuleRegistry.register(new FluidVisionModule());
		ModuleRegistry.register(new RearMirrorModule());
		// Modules from add-on mods (the X-ray add-on lives in its own jar).
		for (QoLBundleAddon addon : addons()) {
			addon.registerModules();
		}
	}

	/** Add-on mods that declared the "qolbundle" entrypoint. */
	public static List<QoLBundleAddon> addons() {
		return FabricLoader.getInstance().getEntrypoints(QoLBundleAddon.ENTRYPOINT_KEY, QoLBundleAddon.class);
	}

	private void onClientTick(Minecraft client) {
		while (openSettingsKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ModuleListScreen(null));
			}
		}
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onTick(client);
			}
		}
		WorldData.tick();
	}

	private void onSubmitWorld(WorldRenderContext context) {
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onSubmitWorld(context);
			}
		}
	}

	private void onRenderWorld(WorldRenderContext context) {
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onRenderWorld(context);
			}
		}
	}

	private void onRenderHud(GuiGraphics context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		HudLayout layout = new HudLayout(context.guiWidth(), context.guiHeight(),
				client.getDebugOverlay().showDebugScreen());
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onRenderHud(context, tickCounter, layout);
			}
		}
	}
}

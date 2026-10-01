package io.github.autyi6969.qolbundle;

import io.github.autyi6969.qolbundle.api.QoLBundleAddon;
import io.github.autyi6969.qolbundle.config.ConfigManager;
import io.github.autyi6969.qolbundle.data.WorldData;
import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.AfkClickerModule;
import io.github.autyi6969.qolbundle.modules.ArmorHudModule;
import io.github.autyi6969.qolbundle.modules.BreakProgressModule;
import io.github.autyi6969.qolbundle.modules.ChatEnhancementsModule;
import io.github.autyi6969.qolbundle.modules.ChestMemoryModule;
import io.github.autyi6969.qolbundle.modules.ChunkBordersModule;
import io.github.autyi6969.qolbundle.modules.DurabilityAlertModule;
import io.github.autyi6969.qolbundle.modules.EffectRangeModule;
import io.github.autyi6969.qolbundle.modules.ElytraDashboardModule;
import io.github.autyi6969.qolbundle.modules.ElytraTakeoffModule;
import io.github.autyi6969.qolbundle.modules.EntityCounterModule;
import io.github.autyi6969.qolbundle.modules.FallDamageModule;
import io.github.autyi6969.qolbundle.modules.FluidVisionModule;
import io.github.autyi6969.qolbundle.modules.FreecamModule;
import io.github.autyi6969.qolbundle.modules.FullbrightModule;
import io.github.autyi6969.qolbundle.modules.HotbarLayoutsModule;
import io.github.autyi6969.qolbundle.modules.InfoHudModule;
import io.github.autyi6969.qolbundle.modules.ItemSearchModule;
import io.github.autyi6969.qolbundle.modules.LavaSafetyModule;
import io.github.autyi6969.qolbundle.modules.NetherRoofModule;
import io.github.autyi6969.qolbundle.modules.PortalCalculatorModule;
import io.github.autyi6969.qolbundle.modules.ProjectileLandingModule;
import io.github.autyi6969.qolbundle.modules.RecipeHelperModule;
import io.github.autyi6969.qolbundle.modules.RespawnPointModule;
import io.github.autyi6969.qolbundle.modules.ShulkerManagerModule;
import io.github.autyi6969.qolbundle.modules.SlimeChunksModule;
import io.github.autyi6969.qolbundle.modules.SoundCompassModule;
import io.github.autyi6969.qolbundle.modules.VillagerTradesModule;
import io.github.autyi6969.qolbundle.selftest.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QoLBundleClient implements ClientModInitializer {
	public static final String MOD_ID = "qolbundle";
	public static final Logger LOGGER = LoggerFactory.getLogger("QoLBundle");

	/** The "QoL Bundle" section in Options > Controls > Key Binds. */
	public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(id("main"));
	private static KeyBinding openSettingsKey;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		WorldData.init();
		registerModules();
		ConfigManager.load();

		openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.qolbundle.open_settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));

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
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::onRenderWorld);
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
		// Grey zone (single-player / own server), all off by default.
		ModuleRegistry.register(new AfkClickerModule());
		ModuleRegistry.register(new FreecamModule());
		ModuleRegistry.register(new ElytraTakeoffModule());
		ModuleRegistry.register(new FluidVisionModule());
		// Modules from add-on mods (the X-ray add-on lives in its own jar).
		for (QoLBundleAddon addon : addons()) {
			addon.registerModules();
		}
	}

	/** Add-on mods that declared the "qolbundle" entrypoint. */
	public static List<QoLBundleAddon> addons() {
		return FabricLoader.getInstance().getEntrypoints(QoLBundleAddon.ENTRYPOINT_KEY, QoLBundleAddon.class);
	}

	private void onClientTick(MinecraftClient client) {
		while (openSettingsKey.wasPressed()) {
			if (client.currentScreen == null) {
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

	private void onRenderWorld(WorldRenderContext context) {
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onRenderWorld(context);
			}
		}
	}

	private void onRenderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}
		HudLayout layout = new HudLayout(context.getScaledWindowWidth(), context.getScaledWindowHeight(),
				client.getDebugHud().shouldShowDebugHud());
		for (Module module : ModuleRegistry.all()) {
			if (module.isEnabled()) {
				module.onRenderHud(context, tickCounter, layout);
			}
		}
	}
}

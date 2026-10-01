package io.github.autyi6969.qolbundle;

import io.github.autyi6969.qolbundle.config.ConfigManager;
import io.github.autyi6969.qolbundle.data.WorldData;
import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.ArmorHudModule;
import io.github.autyi6969.qolbundle.modules.BreakProgressModule;
import io.github.autyi6969.qolbundle.modules.ChunkBordersModule;
import io.github.autyi6969.qolbundle.modules.DurabilityAlertModule;
import io.github.autyi6969.qolbundle.modules.ElytraDashboardModule;
import io.github.autyi6969.qolbundle.modules.EntityCounterModule;
import io.github.autyi6969.qolbundle.modules.FallDamageModule;
import io.github.autyi6969.qolbundle.modules.FullbrightModule;
import io.github.autyi6969.qolbundle.modules.InfoHudModule;
import io.github.autyi6969.qolbundle.modules.PortalCalculatorModule;
import io.github.autyi6969.qolbundle.modules.RespawnPointModule;
import io.github.autyi6969.qolbundle.modules.SlimeChunksModule;
import io.github.autyi6969.qolbundle.selftest.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QoLBundleClient implements ClientModInitializer {
	public static final String MOD_ID = "qolbundle";
	public static final Logger LOGGER = LoggerFactory.getLogger("QoLBundle");

	private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(id("main"));
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
		ModuleRegistry.register(new EntityCounterModule());
		ModuleRegistry.register(new ChunkBordersModule());
		ModuleRegistry.register(new SlimeChunksModule());
		ModuleRegistry.register(new FallDamageModule());
		ModuleRegistry.register(new RespawnPointModule());
		ModuleRegistry.register(new DurabilityAlertModule());
		ModuleRegistry.register(new ElytraDashboardModule());
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

package io.github.autyi6969.qolbundle.selftest;

import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.gui.ModuleSettingsScreen;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.DurabilityAlertModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

/**
 * What the self-test actually does. Order: reach the title screen, settings screens, enter the
 * creative superflat world "selftest", then one scenario per module.
 *
 * <p>The player stands on the superflat surface at y = -60.
 */
final class Scenarios {
	private static final String WORLD_NAME = "selftest";
	static final int GROUND_Y = -60;

	private Scenarios() {
	}

	static void build(SelfTest test) {
		test.scenario("boot", true, Scenarios::boot);
		test.scenario("settings_screen", false, Scenarios::settingsScreen);
		test.scenario("enter_world", true, Scenarios::enterWorld);
		test.scenario("durability_alert", false, Scenarios::durabilityAlert);
	}

	private static void boot(SelfTest.Script s) {
		s.waitUntil("title screen", client -> {
			if (client.getOverlay() != null) {
				return false;
			}
			if (client.currentScreen instanceof AccessibilityOnboardingScreen) {
				// First start of a fresh run/ folder shows this instead of the title screen.
				client.options.onboardAccessibility = false;
				client.setScreen(new TitleScreen());
				return false;
			}
			return client.currentScreen instanceof TitleScreen;
		}, 20 * 120);
		s.run("test-friendly options", client -> {
			// The window is usually in the background while the test runs; do not pause because of that.
			client.options.pauseOnLostFocus = false;
			client.options.onboardAccessibility = false;
			// No tutorial pop-ups in the screenshots.
			client.options.tutorialStep = TutorialStep.NONE;
			client.getTutorialManager().setStep(TutorialStep.NONE);
		});
	}

	private static void settingsScreen(SelfTest.Script s) {
		s.run("open module list", client -> client.setScreen(new ModuleListScreen(client.currentScreen)));
		s.waitTicks(10);
		s.check("module list screen is open", client -> client.currentScreen instanceof ModuleListScreen);
		s.screenshot("00_settings_list");
		if (!ModuleRegistry.all().isEmpty()) {
			// The module with the most settings makes the most telling screenshot.
			Module richest = ModuleRegistry.all().get(0);
			for (Module module : ModuleRegistry.all()) {
				if (module.getSettings().size() > richest.getSettings().size()) {
					richest = module;
				}
			}
			Module shown = richest;
			s.run("open settings of " + shown.getId(),
					client -> client.setScreen(new ModuleSettingsScreen(client.currentScreen, shown)));
			s.waitTicks(10);
			s.check("module settings screen is open", client -> client.currentScreen instanceof ModuleSettingsScreen);
			s.screenshot("00_settings_module");
		}
		s.run("back to title", client -> client.setScreen(new TitleScreen()));
		s.waitTicks(5);
	}

	private static void enterWorld(SelfTest.Script s) {
		s.run("create or load world '" + WORLD_NAME + "'", client -> client.send(() -> {
			if (client.getLevelStorage().levelExists(WORLD_NAME)) {
				client.createIntegratedServerLoader().start(WORLD_NAME, () -> client.setScreen(new TitleScreen()));
			} else {
				LevelInfo info = new LevelInfo(WORLD_NAME, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
						new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE);
				// Superflat, no structures, fixed seed: the same world every time.
				client.createIntegratedServerLoader().createAndStart(WORLD_NAME, info,
						new GeneratorOptions(0L, false, false), WorldPresets::createTestOptions, client.currentScreen);
			}
		}));
		s.waitUntil("player is in the world", Scenarios::inWorld, 20 * 150);
		s.command("gamemode creative @a");
		s.command("time set noon");
		s.command("weather clear");
		resetPlayer(s);
		s.waitTicks(60);
		s.check("player is in creative mode", client -> client.player != null && client.player.isCreative());
		s.screenshot("01_world");
	}

	private static void durabilityAlert(SelfTest.Script s) {
		DurabilityAlertModule module = module("durability_alert");
		isolate(s, module);
		s.command("give @a diamond_pickaxe");
		s.waitTicks(20);
		s.check("no alert with a brand-new pickaxe", client -> !module.isAlertActive());
		s.command("clear @a");
		// Diamond pickaxe: 1561 uses. damage=1530 leaves 31 = 2 %.
		s.command("give @a diamond_pickaxe[damage=1530]");
		s.waitTicks(15);
		s.check("alert active with a pickaxe at 2 %", client -> module.isAlertActive());
		s.screenshot("02_durability_alert");
	}

	@SuppressWarnings("unchecked")
	private static <M extends Module> M module(String id) {
		Module module = ModuleRegistry.get(id);
		if (module == null) {
			throw new IllegalStateException("module not registered: " + id);
		}
		return (M) module;
	}

	private static boolean inWorld(MinecraftClient client) {
		return client.player != null && client.world != null && client.currentScreen == null && client.getOverlay() == null;
	}

	/** Puts the player back to a known state: empty inventory, creative, standing at the origin looking north. */
	static void resetPlayer(SelfTest.Script s) {
		s.command("gamemode creative @a");
		s.command("clear @a");
		s.command("effect clear @a");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 0");
		s.run("select hotbar slot 1 and clear chat", client -> {
			if (client.player != null) {
				client.player.getInventory().setSelectedSlot(0);
			}
			client.inGameHud.getChatHud().clear(false);
		});
		s.waitTicks(10);
	}

	/** Start of a module scenario: known player state, and only this one module switched on. */
	static void isolate(SelfTest.Script s, Module only) {
		s.run("enable only " + only.getId(), client -> {
			for (Module module : ModuleRegistry.all()) {
				module.setEnabled(module == only);
			}
		});
		resetPlayer(s);
	}
}

package io.github.autyi6969.qolbundle.selftest;

import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.gui.ModuleSettingsScreen;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.ArmorHudModule;
import io.github.autyi6969.qolbundle.modules.BreakProgressModule;
import io.github.autyi6969.qolbundle.modules.DurabilityAlertModule;
import io.github.autyi6969.qolbundle.modules.FallDamageModule;
import io.github.autyi6969.qolbundle.modules.FullbrightModule;
import io.github.autyi6969.qolbundle.modules.InfoHudModule;
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
		test.scenario("fullbright", false, Scenarios::fullbright);
		test.scenario("info_hud", false, Scenarios::infoHud);
		test.scenario("armor_hud", false, Scenarios::armorHud);
		test.scenario("break_progress", false, Scenarios::breakProgress);
		test.scenario("fall_damage", false, Scenarios::fallDamage);
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

	private static void fullbright(SelfTest.Script s) {
		FullbrightModule module = module("fullbright");
		isolate(s, module);
		// A closed stone room around the player: pitch dark inside.
		s.command("fill -3 -60 -3 3 -55 3 stone hollow");
		s.command("tp @a 0.5 -59 0.5 180 0");
		s.run("fullbright off", client -> module.setEnabled(false));
		s.waitTicks(30);
		s.screenshot("03_fullbright_off");
		s.run("fullbright on", client -> module.setEnabled(true));
		s.waitTicks(20);
		s.screenshot("03_fullbright_on");
	}

	private static void infoHud(SelfTest.Script s) {
		InfoHudModule module = module("info_hud");
		isolate(s, module);
		s.waitTicks(10);
		s.check("a line shows the player's x coordinate 0.5",
				client -> module.getLines().stream().anyMatch(line -> line.getString().contains("0.5")));
		s.check("at least 4 lines are shown", client -> module.getLines().size() >= 4);
		s.screenshot("04_info_hud");
	}

	private static void armorHud(SelfTest.Script s) {
		ArmorHudModule module = module("armor_hud");
		isolate(s, module);
		s.command("item replace entity @a armor.head with diamond_helmet");
		s.command("item replace entity @a armor.chest with iron_chestplate[damage=120]");
		s.command("item replace entity @a armor.legs with golden_leggings[damage=95]");
		s.command("item replace entity @a armor.feet with netherite_boots[damage=200]");
		s.command("give @a diamond_pickaxe[damage=700]");
		s.waitTicks(15);
		s.clearChat();
		s.check("4 armor pieces + 1 tool are shown", client -> module.getShownCount() == 5);
		s.screenshot("05_armor_hud");
	}

	private static void breakProgress(SelfTest.Script s) {
		BreakProgressModule module = module("break_progress");
		isolate(s, module);
		// A stone pillar right in front of the player, mined by hand in survival (takes 7.5 s).
		s.command("fill 0 -60 -2 0 -58 -2 stone");
		s.command("gamemode survival @a");
		s.waitTicks(10);
		s.run("grab the mouse as a focused window would", client -> {
			// The game only mines while the mouse is grabbed, and only grabs it when the window has
			// focus. The test window is usually in the background, so tell the game it is focused.
			client.onWindowFocusChanged(true);
			client.mouse.lockCursor();
		});
		s.waitTicks(3);
		s.run("hold the attack key", client -> client.options.attackKey.setPressed(true));
		s.waitTicks(40);
		s.clearChat();
		s.info("break progress", client -> module.getLastProgress());
		s.check("break progress is above 5 %", client -> module.getLastProgress() > 0.05F);
		s.screenshot("06_break_progress");
		s.run("release the attack key", client -> client.options.attackKey.setPressed(false));
		s.command("gamemode creative @a");
	}

	private static void fallDamage(SelfTest.Script s) {
		FallDamageModule module = module("fall_damage");
		isolate(s, module);
		// 20 blocks above the ground: 20 - 3 safe blocks = 17 damage = 8.5 hearts, survivable with 10 hearts.
		s.command("gamemode survival @a");
		s.command("tp @a 0.5 " + (GROUND_Y + 20) + " 0.5 180 20");
		s.waitTicks(5);
		s.clearChat();
		s.info("predicted damage from 20 blocks", client -> module.getPredictedDamage());
		s.check("20 block fall predicts 17 damage", client -> module.getPredictedDamage() == 17F);
		s.check("20 block fall is not lethal", client -> !module.isLethal());
		s.screenshot("07_fall_damage_survivable");
		// Creative before touching down, so the test player never actually gets hurt.
		s.command("gamemode creative @a");
		s.waitTicks(30);

		s.command("gamemode survival @a");
		s.command("tp @a 0.5 " + (GROUND_Y + 100) + " 0.5 180 20");
		s.waitTicks(8);
		s.clearChat();
		s.info("predicted damage from 100 blocks", client -> module.getPredictedDamage());
		s.check("100 block fall predicts 97 damage", client -> module.getPredictedDamage() == 97F);
		s.check("100 block fall is lethal", client -> module.isLethal());
		s.screenshot("07_fall_damage_lethal");
		s.command("gamemode creative @a");
		s.waitTicks(70);
		s.check("player is back on the ground and alive",
				client -> client.player != null && client.player.isOnGround() && client.player.isAlive());
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
		// Remove anything an earlier scenario built around the origin.
		s.command("fill -8 -60 -8 8 -50 8 air");
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

package io.github.autyi6969.qolbundle.selftest;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import io.github.autyi6969.qolbundle.modules.PlacementMasterModule;
import net.minecraft.util.math.Direction;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.api.QoLBundleAddon;
import io.github.autyi6969.qolbundle.config.ConfigManager;
import io.github.autyi6969.qolbundle.gui.ChatSearchScreen;
import io.github.autyi6969.qolbundle.gui.ChestMemoryScreen;
import io.github.autyi6969.qolbundle.gui.HotbarLayoutScreen;
import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.gui.ModuleSettingsScreen;
import io.github.autyi6969.qolbundle.mixin.ChatHudAccessor;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.AfkClickerModule;
import io.github.autyi6969.qolbundle.modules.ArmorHudModule;
import io.github.autyi6969.qolbundle.modules.BreakProgressModule;
import io.github.autyi6969.qolbundle.modules.ChatEnhancementsModule;
import io.github.autyi6969.qolbundle.modules.ChestMemoryModule;
import io.github.autyi6969.qolbundle.modules.ChunkBordersModule;
import io.github.autyi6969.qolbundle.modules.DiagnosticsModule;
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
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * What the self-test actually does. Order: reach the title screen, settings screens, enter the
 * creative superflat world "selftest", then one scenario per module.
 *
 * <p>The player stands on the superflat surface at y = -60.
 */
public final class Scenarios {
	private static final String WORLD_NAME = "selftest";
	/** A second world with ordinary terrain (hills, caves), for what the flat one cannot show. */
	private static final String NORMAL_WORLD_NAME = "selftest_normal";
	public static final int GROUND_Y = -60;

	private Scenarios() {
	}

	static void build(SelfTest test) {
		test.scenario("boot", true, s -> boot(test, s));
		test.scenario("settings_screen", false, Scenarios::settingsScreen);
		test.scenario("share_code", false, Scenarios::shareCode);
		test.scenario("enter_world", true, Scenarios::enterWorld);
		test.scenario("durability_alert", false, Scenarios::durabilityAlert);
		test.scenario("fullbright", false, Scenarios::fullbright);
		test.scenario("info_hud", false, Scenarios::infoHud);
		test.scenario("armor_hud", false, Scenarios::armorHud);
		test.scenario("break_progress", false, Scenarios::breakProgress);
		test.scenario("fall_damage", false, Scenarios::fallDamage);
		test.scenario("portal_calculator", false, Scenarios::portalCalculator);
		test.scenario("respawn_point", false, Scenarios::respawnPoint);
		test.scenario("entity_counter", false, Scenarios::entityCounter);
		test.scenario("chunk_borders", false, Scenarios::chunkBorders);
		test.scenario("slime_chunks", false, Scenarios::slimeChunks);
		test.scenario("elytra_dashboard", false, Scenarios::elytraDashboard);
		test.scenario("sound_compass", false, Scenarios::soundCompass);
		test.scenario("chat_enhancements", false, Scenarios::chatEnhancements);
		test.scenario("villager_trades", false, Scenarios::villagerTrades);
		test.scenario("projectile_landing", false, Scenarios::projectileLanding);
		test.scenario("lava_safety", false, Scenarios::lavaSafety);
		test.scenario("effect_range", false, Scenarios::effectRange);
		test.scenario("item_search", false, Scenarios::itemSearch);
		test.scenario("hotbar_layouts", false, Scenarios::hotbarLayouts);
		test.scenario("nether_roof", false, Scenarios::netherRoof);
		test.scenario("chest_memory", false, Scenarios::chestMemory);
		test.scenario("shulker_manager", false, Scenarios::shulkerManager);
		test.scenario("recipe_helper", false, Scenarios::recipeHelper);
		test.scenario("placement_master", false, Scenarios::placementMaster);
		test.scenario("diagnostics", false, Scenarios::diagnostics);
		test.scenario("afk_clicker", false, Scenarios::afkClicker);
		test.scenario("fluid_vision", false, Scenarios::fluidVision);
		test.scenario("freecam", false, Scenarios::freecam);
		test.scenario("elytra_takeoff", false, Scenarios::elytraTakeoff);
		for (QoLBundleAddon addon : QoLBundleClient.addons()) {
			addon.registerSelfTests(test);
		}
		test.scenario("freecam_underground", false, Scenarios::freecamUnderground);
		test.scenario("chinese_ui", false, Scenarios::chineseUi);
	}

	private static void boot(SelfTest test, SelfTest.Script s) {
		s.waitUntil("title screen", client -> {
			if (client.getOverlay() != null) {
				return false;
			}
			if (client.currentScreen instanceof AccessibilityOnboardingScreen) {
				// First start of a fresh run/ folder shows this instead of the title screen. Skip it for
				// this run only; the option itself is not touched, so the player still gets to see it.
				client.setScreen(new TitleScreen());
				return false;
			}
			return client.currentScreen instanceof TitleScreen;
		}, 20 * 120);
		s.run("test-friendly options", client -> {
			boolean pauseOnLostFocus = client.options.pauseOnLostFocus;
			TutorialStep tutorialStep = client.options.tutorialStep;
			// The window is usually in the background while the test runs; do not pause because of that.
			client.options.pauseOnLostFocus = false;
			// No tutorial pop-ups in the screenshots.
			client.options.tutorialStep = TutorialStep.NONE;
			client.getTutorialManager().setStep(TutorialStep.NONE);
			// The game saves its options on exit: hand back what the player had, so a test run
			// leaves options.txt as it found it.
			test.onFinish(c -> {
				c.options.pauseOnLostFocus = pauseOnLostFocus;
				c.options.tutorialStep = tutorialStep;
				c.options.write();
			});
		});
	}

	private static void settingsScreen(SelfTest.Script s) {
		s.check("Mod Menu's configure button leads to our settings screen", Scenarios::modMenuOpensSettings);
		s.run("open module list", client -> client.setScreen(new ModuleListScreen(client.currentScreen)));
		s.waitTicks(10);
		s.check("module list screen is open", client -> client.currentScreen instanceof ModuleListScreen);
		s.screenshot("00_settings_list");
		s.run("scroll the module list to the bottom", client -> {
			if (client.currentScreen != null) {
				client.currentScreen.mouseScrolled(client.currentScreen.width / 2.0, client.currentScreen.height / 2.0, 0, -100);
			}
		});
		s.screenshot("00_settings_list_bottom");
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

	/**
	 * Asks Mod Menu which screen its "configure" button would open for this mod. Done by name
	 * (reflection) so the self-test also works in a client without Mod Menu.
	 */
	private static boolean modMenuOpensSettings(MinecraftClient client) {
		try {
			Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
			Object screen = modMenu.getMethod("getConfigScreen", String.class, Screen.class)
					.invoke(null, "qolbundle", client.currentScreen);
			return screen instanceof ModuleListScreen;
		} catch (ClassNotFoundException e) {
			return true; // Mod Menu is not installed in this client: nothing to check
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Export the settings as a share code, change them, import the code: everything must be back. */
	private static void shareCode(SelfTest.Script s) {
		InfoHudModule infoHud = module("info_hud");
		FullbrightModule fullbright = module("fullbright");
		HotbarLayoutsModule layouts = module("hotbar_layouts");
		String[] code = new String[1];
		boolean[] wasOn = new boolean[1];
		s.run("set some unusual values and export", client -> {
			wasOn[0] = fullbright.isEnabled();
			infoHud.textSizeSetting().set(150);
			fullbright.setEnabled(!wasOn[0]);
			layouts.setLayoutName(4, "Shared");
			code[0] = ConfigManager.exportCode();
		});
		s.info("share code length", client -> code[0].length());
		s.run("change them back", client -> {
			infoHud.textSizeSetting().reset();
			fullbright.setEnabled(wasOn[0]);
			layouts.setLayoutName(4, "");
		});
		s.check("text that is not a share code is refused and changes nothing", client ->
				!ConfigManager.importCode("hello") && !ConfigManager.importCode("QOL1:not-base64!") && infoHud.textSizeSetting().get() == 100);
		s.check("importing the code brings all three values back", client -> ConfigManager.importCode(code[0])
				&& infoHud.textSizeSetting().get() == 150 && fullbright.isEnabled() != wasOn[0] && layouts.getLayoutName(4).equals("Shared"));
		s.run("restore the real values", client -> {
			infoHud.textSizeSetting().reset();
			fullbright.setEnabled(wasOn[0]);
			layouts.setLayoutName(4, "");
		});
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

		// One reminder per item until it is repaired.
		int[] alerts = new int[1];
		s.run("remember how often the alert has fired", client -> alerts[0] = module.getAlertCount());
		s.command("item replace entity @a weapon.mainhand with diamond_pickaxe[damage=1545]");
		s.waitTicks(15);
		s.check("the same pickaxe losing more durability does not alert again", client -> module.getAlertCount() == alerts[0]);
		s.command("item replace entity @a weapon.mainhand with diamond_pickaxe[damage=0]");
		s.waitTicks(15);
		s.command("item replace entity @a weapon.mainhand with diamond_pickaxe[damage=1530]");
		s.waitTicks(15);
		s.check("after a repair it alerts again when it runs low", client -> module.getAlertCount() == alerts[0] + 1);
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
		s.run("text size 150 %", client -> module.textSizeSetting().set(150));
		s.screenshot("04_info_hud_large");
		s.run("text size back to normal", client -> module.textSizeSetting().reset());
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
		s.check("35 of 36 inventory slots are counted as free (only the pickaxe takes one)", client -> module.getFreeSlots() == 35);
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
		s.run("release the attack key and the mouse", client -> {
			client.options.attackKey.setPressed(false);
			// Give the mouse back, and tell the game the truth about window focus again.
			client.mouse.unlockCursor();
			client.onWindowFocusChanged(GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
		});
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

	private static void portalCalculator(SelfTest.Script s) {
		PortalCalculatorModule module = module("portal_calculator");
		isolate(s, module);
		s.run("forget portals remembered by earlier runs", client -> module.forgetAll());
		// A 2x3 portal in an obsidian frame, 3 blocks in front of where the player will stand.
		s.command("fill 2 -60 -5 5 -56 -5 obsidian");
		s.command("fill 3 -59 -5 4 -57 -5 nether_portal[axis=x]");
		s.command("tp @a 3.5 -60 -1.5 180 0");
		s.waitTicks(20);
		s.check("portal is recognised when looked at", client -> module.getInfo() != null);
		// Portal centre x=4.0, z=-4.5 -> divided by 8 and rounded down: 0, -1.
		s.check("nether side is x=0 z=-1", client -> module.getInfo() != null
				&& module.getInfo().target().getX() == 0 && module.getInfo().target().getZ() == -1);
		s.check("no link known yet", client -> module.getInfo() != null && module.getInfo().link() == null);
		s.screenshot("08_portal_new");

		// Walk through it for real: the game creates the nether-side portal, the module should spot it.
		s.command("tp @a 3.5 -59 -4.5 180 0");
		s.waitUntil("arrived in the nether", client -> client.world != null
				&& client.world.getRegistryKey() == World.NETHER && client.player != null && client.currentScreen == null, 20 * 30);
		s.waitTicks(60);
		s.info("nether portals known", client -> module.getKnownCount("minecraft:the_nether"));
		s.check("nether-side portal was remembered", client -> module.getKnownCount("minecraft:the_nether") >= 1);
		s.command("execute in minecraft:overworld run tp @a 3.5 -60 -1.5 180 0");
		s.waitUntil("back in the overworld", client -> client.world != null
				&& client.world.getRegistryKey() == World.OVERWORLD && client.player != null && client.currentScreen == null, 20 * 30);
		s.waitTicks(40);
		s.clearChat();
		s.check("link to the nether portal is now known", client -> module.getInfo() != null && module.getInfo().link() != null);
		s.check("way back returns to this portal", client -> module.getInfo() != null && module.getInfo().returnsHere());
		s.screenshot("08_portal_linked");
	}

	private static void respawnPoint(SelfTest.Script s) {
		RespawnPointModule module = module("respawn_point");
		BlockPos bedHead = new BlockPos(0, GROUND_Y, -4);
		isolate(s, module);
		// The world remembers the respawn point of earlier runs; move it away so that using the bed
		// really changes it (the game only announces a respawn point when it changes).
		s.command("spawnpoint @a 100 " + GROUND_Y + " 100");
		s.run("forget the recorded respawn point", client -> module.forget());
		s.command("setblock 0 " + GROUND_Y + " -4 red_bed[facing=north,part=head]");
		s.command("setblock 0 " + GROUND_Y + " -3 red_bed[facing=north,part=foot]");
		s.command("tp @a 0.5 " + GROUND_Y + " -1.0 180 40");
		s.waitTicks(15);
		s.check("nothing recorded yet", client -> module.getStatus() == RespawnPointModule.Status.UNKNOWN);
		s.screenshot("09_respawn_unknown");

		s.run("right-click the bed", Scenarios::useLookedAtBlock);
		s.waitTicks(20);
		s.check("respawn point recorded at the head of the bed after the set-spawn message",
				client -> module.getStatus() == RespawnPointModule.Status.SET && bedHead.equals(module.getRespawnPos()));
		s.screenshot("09_respawn_set");

		// Same bed again: the game stays silent, and that silence must be read as "already yours".
		s.run("forget, then click the same bed again", client -> {
			module.forget();
			useLookedAtBlock(client);
		});
		s.waitTicks(70);
		s.check("silent click on the own bed is recognised",
				client -> module.getStatus() == RespawnPointModule.Status.SET && bedHead.equals(module.getRespawnPos()));

		s.command("setblock 0 " + GROUND_Y + " -4 air");
		s.command("kill @e[type=item]");
		s.waitTicks(25);
		s.clearChat();
		s.check("bed removal is noticed", client -> module.getStatus() == RespawnPointModule.Status.BROKEN);
		s.screenshot("09_respawn_lost");
		s.command("kill @e[type=item]");
	}

	private static void entityCounter(SelfTest.Script s) {
		EntityCounterModule module = module("entity_counter");
		isolate(s, module);
		s.command("kill @e[type=item]");
		s.run("warn at 20 dropped items", client -> module.itemWarningSetting().set(20));
		// Swords do not stack, so 30 of them stay 30 separate item entities.
		for (int i = 0; i < 30; i++) {
			s.command("summon item 5 " + GROUND_Y + " -6 {Item:{id:\"minecraft:diamond_sword\",count:1}}");
		}
		for (int i = 0; i < 4; i++) {
			s.command("summon chicken " + (-4 + i) + " " + GROUND_Y + " -6 {NoAI:1b}");
		}
		s.waitTicks(30);
		s.info("entity counts", client -> "total=" + module.getTotal() + " items=" + module.getCount(EntityCounterModule.Category.ITEM)
				+ " animals=" + module.getCount(EntityCounterModule.Category.ANIMAL) + " pile=" + module.getPilePos());
		s.check("30 dropped items are counted", client -> module.getCount(EntityCounterModule.Category.ITEM) == 30);
		s.check("at least 4 animals are counted", client -> module.getCount(EntityCounterModule.Category.ANIMAL) >= 4);
		s.check("the item pile is located at x=5 z=-6", client -> module.getPilePos() != null
				&& module.getPilePos().getX() == 5 && module.getPilePos().getZ() == -6);
		s.screenshot("10_entity_counter");
		s.command("kill @e[type=item]");
		s.command("kill @e[type=chicken]");
		s.run("restore the warning threshold", client -> module.itemWarningSetting().reset());
	}

	private static void chunkBorders(SelfTest.Script s) {
		ChunkBordersModule module = module("chunk_borders");
		isolate(s, module);
		// Middle of chunk 0,0, a little above the ground, looking at its south-east corner.
		s.command("tp @a 8.5 " + (GROUND_Y + 3) + " 8.5 -45 15");
		s.waitTicks(20);
		s.check("four walls are drawn", client -> module.getWallsDrawn() == 4);
		s.screenshot("11_chunk_borders");
	}

	private static void slimeChunks(SelfTest.Script s) {
		SlimeChunksModule module = module("slime_chunks");
		isolate(s, module);
		s.run("empty seed box: single-player uses the seed of the world", client -> module.seedSetting().set(""));
		s.waitTicks(25);
		s.check("the world seed (0) is picked up automatically",
				client -> module.getActiveSeed().isPresent() && module.getActiveSeed().getAsLong() == 0L);
		s.run("type the seed 12345", client -> module.seedSetting().set("12345"));
		s.waitTicks(5);
		s.check("typed seed is used", client -> module.getActiveSeed().isPresent() && module.getActiveSeed().getAsLong() == 12345L);
		// Stand in the slime chunk nearest to the origin, a few blocks up, looking down across it.
		s.command(client -> {
			int[] chunk = nearestSlimeChunk(12345L);
			return "tp @a " + (chunk[0] * 16 + 2.5) + " " + (GROUND_Y + 6) + " " + (chunk[1] * 16 + 2.5) + " -45 35";
		});
		s.waitTicks(45);
		s.info("slime chunks highlighted", client -> module.getHighlightedChunkCount());
		s.check("player stands in a slime chunk", client -> module.isStandingInSlimeChunk());
		s.check("some slime chunks are highlighted", client -> module.getHighlightedChunkCount() >= 1);
		s.screenshot("12_slime_chunks");
		s.run("clear the seed box again", client -> module.seedSetting().set(""));
	}

	/** Chunk coordinates of the slime chunk closest to chunk 0,0 for the given seed. */
	private static int[] nearestSlimeChunk(long seed) {
		for (int ring = 0; ring <= 12; ring++) {
			for (int x = -ring; x <= ring; x++) {
				for (int z = -ring; z <= ring; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) == ring && SlimeChunksModule.isSlimeChunk(seed, x, z)) {
						return new int[] {x, z};
					}
				}
			}
		}
		throw new IllegalStateException("no slime chunk near the origin");
	}

	private static void elytraDashboard(SelfTest.Script s) {
		ElytraDashboardModule module = module("elytra_dashboard");
		isolate(s, module);
		s.command("gamemode survival @a");
		s.command("item replace entity @a armor.chest with elytra[damage=300]");
		s.command("give @a firework_rocket 23");
		// 90 blocks up, looking north and 40 degrees down (steep enough to come down inside the
		// loaded chunks); then press jump once to open the elytra.
		s.command("tp @a 0.5 " + (GROUND_Y + 90) + " 0.5 180 40");
		s.waitTicks(8);
		s.run("press jump", client -> client.options.jumpKey.setPressed(true));
		s.waitTicks(3);
		s.run("release jump", client -> client.options.jumpKey.setPressed(false));
		s.waitTicks(22);
		s.clearChat();
		s.info("elytra readings", client -> "active=" + module.isActive() + " speed=" + module.getSpeed()
				+ " rockets=" + module.getRockets() + " landing=" + module.getLanding());
		s.check("dashboard is active while gliding", client -> module.isActive());
		s.check("speed is plausible (5..80 blocks/s)", client -> module.getSpeed() > 5 && module.getSpeed() < 80);
		s.check("23 rockets are counted", client -> module.getRockets() == 23);
		s.check("a landing spot ahead (north, on the ground) is predicted", client -> module.getLanding() != null
				&& !module.isLandingOutOfRange() && module.getLanding().z < -5
				&& Math.abs(module.getLanding().y - GROUND_Y) < 1.5);
		s.screenshot("13_elytra_dashboard");

		// Now fly it out and compare the real touchdown with what was predicted in mid-air.
		Vec3d[] predicted = new Vec3d[1];
		s.run("remember the prediction", client -> predicted[0] = module.getLanding());
		s.command("gamemode creative @a");
		s.waitUntil("touchdown", client -> client.player != null && (client.player.isOnGround() || !client.player.isGliding()), 20 * 30);
		s.info("touchdown", client -> "actual=" + client.player.getEntityPos() + " predicted=" + predicted[0]);
		s.check("real touchdown is within 10 blocks of the prediction", client -> predicted[0] != null
				&& Math.hypot(client.player.getX() - predicted[0].x, client.player.getZ() - predicted[0].z) < 10.0);
	}

	private static void soundCompass(SelfTest.Script s) {
		SoundCompassModule module = module("sound_compass");
		isolate(s, module);
		s.waitTicks(20);
		// The player stands at 0.5, 0.5 looking north (-Z). East (+X) is to the right, south is behind.
		s.command("playsound minecraft:entity.creeper.primed hostile @a 10.5 " + GROUND_Y + " 0.5 1");
		s.command("playsound minecraft:entity.cow.ambient neutral @a 0.5 " + GROUND_Y + " 9.5 1");
		s.command("playsound minecraft:entity.zombie.ambient hostile @a -7.5 " + GROUND_Y + " -7.5 1");
		s.waitTicks(12);
		s.info("sounds on the ring", client -> {
			StringBuilder out = new StringBuilder();
			for (SoundCompassModule.Entry entry : module.getEntries()) {
				out.append(entry.text().getString()).append(" @").append(Math.round(soundAngle(client, entry))).append("deg  ");
			}
			return out;
		});
		// Animals wandering near the origin add their own sounds, so "at least".
		s.check("at least the three played sounds are on the ring", client -> module.getEntries().size() >= 3);
		s.check("the creeper hiss points right (about 90 degrees) and is marked dangerous", client -> module.getEntries().stream()
				.anyMatch(entry -> entry.danger() && Math.abs(soundAngle(client, entry) - 90F) < 10F));
		s.check("the cow points behind (about 180 degrees)", client -> module.getEntries().stream()
				.anyMatch(entry -> !entry.danger() && Math.abs(Math.abs(soundAngle(client, entry)) - 180F) < 10F));
		s.check("the zombie points front-left (about -45 degrees)", client -> module.getEntries().stream()
				.anyMatch(entry -> Math.abs(soundAngle(client, entry) + 45F) < 10F));
		s.screenshot("14_sound_compass");
	}

	private static float soundAngle(MinecraftClient client, SoundCompassModule.Entry entry) {
		return SoundCompassModule.relativeAngle(client.gameRenderer.getCamera().getCameraPos(),
				client.gameRenderer.getCamera().getYaw(), entry.pos());
	}

	private static void chatEnhancements(SelfTest.Script s) {
		ChatEnhancementsModule module = module("chat_enhancements");
		isolate(s, module);
		s.run("forget earlier chat", client -> module.forgetAll());
		s.waitTicks(5);
		s.clearChat();
		// Lines as a chat plugin would send them: someone talking, someone mentioning the player,
		// the player's own line (contains the own name but is not a mention), and the same two
		// cases again in a "[rank] name: text" format.
		s.command(client -> "tellraw @a {\"text\":\"<Bob> hello everyone\"}");
		s.command(client -> "tellraw @a {\"text\":\"<Bob> hey " + client.getSession().getUsername() + " are you there?\"}");
		s.command(client -> "tellraw @a {\"text\":\"<" + client.getSession().getUsername() + "> yes, "
				+ client.getSession().getUsername() + " is here\"}");
		s.command(client -> "tellraw @a {\"text\":\"[Admin] " + client.getSession().getUsername() + ": my own line with a rank tag\"}");
		s.command(client -> "tellraw @a {\"text\":\"[Admin] Carol: " + client.getSession().getUsername() + ", come to spawn\"}");
		s.waitTicks(10);
		s.check("five lines were recorded", client -> module.getHistory().size() == 5);
		s.check("exactly two of them count as a mention", client -> module.getMentionCount() == 2);
		s.check("the newest chat line starts with a timestamp", client -> newestChatLine(client).matches("^\\[\\d\\d:\\d\\d\\] .*"));
		s.screenshot("15_chat_timestamps_mention", true);

		s.run("open the search screen and search for 'hey'", client -> {
			ChatSearchScreen screen = new ChatSearchScreen(null, module);
			client.setScreen(screen);
			screen.setQuery("hey");
		});
		s.waitTicks(5);
		s.check("search finds exactly the one line containing 'hey'", client -> client.currentScreen instanceof ChatSearchScreen screen
				&& screen.getResultCount() == 1);
		s.screenshot("15_chat_search");
		s.run("close the search screen", client -> client.setScreen(null));

		// Leave the world and come back: the chat must still be there.
		s.run("leave the world", client -> client.send(() -> client.disconnect(Text.literal("self-test reconnect"))));
		s.waitUntil("back at the title screen", client -> client.world == null && client.currentScreen instanceof TitleScreen, 20 * 60);
		s.run("load the world again", client -> client.send(() ->
				client.createIntegratedServerLoader().start(WORLD_NAME, () -> client.setScreen(new TitleScreen()))));
		s.waitUntil("player is in the world again", Scenarios::inWorld, 20 * 120);
		s.waitTicks(40);
		s.info("chat lines restored", client -> module.getRestoredLines());
		s.check("the five chat lines came back after rejoining", client -> module.getRestoredLines() == 5);
		s.check("the chat window contains the old line again", client -> chatLines(client).stream().anyMatch(line -> line.contains("hello everyone")));
		s.run("open the chat so the restored lines are visible", client -> client.setScreen(new ChatScreen("", false)));
		s.waitTicks(5);
		s.screenshot("15_chat_restored");
		s.run("close the chat", client -> client.setScreen(null));

		// The search button must not take the arrow keys away from the chat box: in the chat,
		// "up" recalls the last thing you typed. (Found while playing, 2026-10-01.)
		s.run("remember a typed message, open the chat", client -> {
			client.inGameHud.getChatHud().addToMessageHistory("remembered line");
			client.setScreen(new ChatScreen("", false));
		});
		s.waitTicks(5);
		s.run("press the up arrow", client -> client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_UP, 0, 0)));
		s.waitTicks(2);
		s.check("up arrow recalls the last typed message, the chat box keeps the keyboard", client ->
				client.currentScreen instanceof ChatScreen screen && screen.getFocused() instanceof TextFieldWidget field
						&& field.getText().equals("remembered line"));
		s.run("press down and tab", client -> {
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_DOWN, 0, 0));
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_TAB, 0, 0));
		});
		s.waitTicks(2);
		s.check("down arrow and tab leave the keyboard in the chat box too", client ->
				client.currentScreen instanceof ChatScreen screen && screen.getFocused() instanceof TextFieldWidget);
		s.run("close the chat", client -> client.setScreen(null));
	}

	/** Last scenario: the same screens and HUD lines in Simplified Chinese, to check font and translations. */
	private static void chineseUi(SelfTest.Script s) {
		CompletableFuture<?>[] reload = new CompletableFuture<?>[1];
		s.run("switch the game language to Simplified Chinese", client -> {
			client.getLanguageManager().setLanguage("zh_cn");
			// options.language is left alone on purpose, so the dev client starts in English next time.
			reload[0] = client.reloadResources();
		});
		s.waitUntil("resources are reloaded", client -> reload[0] != null && reload[0].isDone() && client.getOverlay() == null, 20 * 90);
		resetPlayer(s);
		s.run("switch on a few HUD modules", client -> {
			for (Module module : ModuleRegistry.all()) {
				String id = module.getId();
				module.setEnabled(id.equals("info_hud") || id.equals("respawn_point") || id.equals("slime_chunks")
						|| id.equals("entity_counter") || id.equals("armor_hud"));
			}
		});
		s.command("give @a diamond_pickaxe[damage=700]");
		s.waitTicks(30);
		s.screenshot("16_zh_hud");
		s.run("open module list", client -> client.setScreen(new ModuleListScreen(null)));
		s.waitTicks(5);
		s.screenshot("16_zh_settings_list");
		s.run("scroll the module list to the bottom", client -> {
			if (client.currentScreen != null) {
				client.currentScreen.mouseScrolled(client.currentScreen.width / 2.0, client.currentScreen.height / 2.0, 0, -100);
			}
		});
		s.screenshot("16_zh_settings_list_bottom");
		s.run("open the settings of the chat module",
				client -> client.setScreen(new ModuleSettingsScreen(client.currentScreen, module("chat_enhancements"))));
		s.waitTicks(5);
		s.screenshot("16_zh_settings_module");
		s.run("close the screens", client -> client.setScreen(null));
	}

	private static List<String> chatLines(MinecraftClient client) {
		List<String> lines = new ArrayList<>();
		for (ChatHudLine line : ((ChatHudAccessor) client.inGameHud.getChatHud()).qolbundle$getMessages()) {
			lines.add(line.content().getString());
		}
		return lines;
	}

	private static String newestChatLine(MinecraftClient client) {
		List<String> lines = chatLines(client);
		return lines.isEmpty() ? "" : lines.get(0);
	}

	private static void villagerTrades(SelfTest.Script s) {
		VillagerTradesModule module = module("villager_trades");
		isolate(s, module);
		s.command("kill @e[type=villager]");
		// A level 2 farmer three blocks in front of the player, with two fixed trades.
		s.command("summon villager 0.5 " + GROUND_Y + " -2.5 {NoAI:1b,Rotation:[0f,0f],"
				+ "VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"},"
				+ "Offers:{Recipes:["
				+ "{buy:{id:\"minecraft:emerald\",count:3},sell:{id:\"minecraft:diamond\",count:1},maxUses:5},"
				+ "{buy:{id:\"minecraft:wheat\",count:20},sell:{id:\"minecraft:emerald\",count:1},maxUses:16}]}}");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 5");
		s.waitTicks(20);
		s.check("the crosshair is on the villager", client -> lookedAtVillager(client) != null);
		s.check("nothing is known about it before trading", client -> lookedAtVillager(client) != null
				&& module.getKnownTrades(lookedAtVillager(client).getUuid()) == null);
		s.screenshot("21_villager_unknown");
		// A real press of the use key: that is the path on which the game announces "entity used".
		s.run("right-click the villager", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitUntil("the trading screen opens", client -> client.currentScreen instanceof MerchantScreen, 60);
		s.waitTicks(10);
		s.run("close the trading screen", client -> client.player.closeHandledScreen());
		s.waitTicks(10);
		s.check("both trades are remembered", client -> lookedAtVillager(client) != null
				&& module.getKnownTrades(lookedAtVillager(client).getUuid()) != null
				&& module.getKnownTrades(lookedAtVillager(client).getUuid()).size() == 2);
		s.screenshot("21_villager_trades");
		s.command("kill @e[type=villager]");
	}

	private static VillagerEntity lookedAtVillager(MinecraftClient client) {
		return client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof VillagerEntity villager ? villager : null;
	}

	private static void projectileLanding(SelfTest.Script s) {
		ProjectileLandingModule module = module("projectile_landing");
		isolate(s, module);
		s.command("gamemode survival @a");
		s.command("give @a ender_pearl 4");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -15");
		s.waitTicks(15);
		s.check("a landing spot is predicted north of the player, on the ground, green light", client -> module.getLanding() != null
				&& module.getLanding().z < -10 && Math.abs(module.getLanding().y - GROUND_Y) < 0.1
				&& module.getLight() == ProjectileLandingModule.Light.GREEN);
		s.screenshot("22_projectile_pearl");

		// Throw it for real and compare where the pearl takes the player with the prediction.
		Vec3d[] predicted = new Vec3d[1];
		s.run("remember the prediction and throw", client -> {
			predicted[0] = module.getLanding();
			KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey));
		});
		s.waitUntil("the pearl has teleported the player", client -> client.player.getZ() < -5, 200);
		s.waitTicks(5);
		s.info("pearl", client -> "landed=" + client.player.getEntityPos() + " predicted=" + predicted[0]);
		// The game throws every pearl with a little random spread, which the prediction cannot know:
		// over a 35 block throw that is up to about two blocks.
		s.check("the player arrived within 2.5 blocks of the predicted spot", client -> predicted[0] != null
				&& Math.hypot(client.player.getX() - predicted[0].x, client.player.getZ() - predicted[0].z) < 2.5);

		// A low ceiling ahead: the pearl would hit it, so the light must turn red.
		resetPlayer(s);
		s.command("give @a ender_pearl 4");
		s.command("fill -2 " + (GROUND_Y + 3) + " -4 2 " + (GROUND_Y + 3) + " -1 stone");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -50");
		s.waitTicks(15);
		s.check("a pearl aimed at a low ceiling shows red", client -> module.getLight() == ProjectileLandingModule.Light.RED);
		s.screenshot("22_projectile_pearl_ceiling");

		resetPlayer(s);
		s.command("give @a splash_potion");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -10");
		s.waitTicks(15);
		s.check("a splash potion gets a landing spot too", client -> module.isHoldingThrowable() && module.getLanding() != null);
		s.screenshot("22_projectile_potion");
		s.command("effect clear @a");
	}

	private static void lavaSafety(SelfTest.Script s) {
		LavaSafetyModule module = module("lava_safety");
		isolate(s, module);
		int floor = GROUND_Y + 8;
		// A stone platform in the air with a one-block hole; four blocks under the hole a lava
		// source sits in a stone basin.
		s.command("fill -3 " + floor + " -3 3 " + floor + " 3 stone");
		s.command("fill 0 " + (floor - 4) + " -1 2 " + (floor - 3) + " 1 stone");
		s.command("setblock 1 " + (floor - 3) + " 0 lava");
		s.command("gamemode survival @a");
		// Hurt-proof but not fire-proof: the module must still treat lava as a danger.
		s.command("effect give @a resistance 60 255 true");
		s.command("tp @a -1.5 " + (floor + 1) + " 0.5 -90 30");
		s.waitTicks(20);
		s.check("no warning while standing over solid floor", client -> module.getLavaBelow() == 0);
		s.command("setblock 1 " + floor + " 0 air");
		s.command("tp @a 0.5 " + (floor + 1) + " 0.5 -90 30");
		s.waitTicks(20);
		s.clearChat();
		s.info("lava below", client -> module.getLavaBelow());
		s.check("lava 4 blocks below the hole next to the player is reported", client -> module.getLavaBelow() == 4);
		s.screenshot("23_lava_below");

		s.command("tp @a 1.5 " + (floor - 3) + " 0.5 -90 0");
		s.waitUntil("the player is in the lava", client -> module.isInLava(), 60);
		s.waitTicks(8);
		s.info("escape", client -> module.getEscape());
		s.check("a safe spot within 3 blocks is found", client -> module.getEscape() != null
				&& module.getEscape().getSquaredDistance(client.player.getBlockPos()) <= 9);
		s.screenshot("23_lava_escape");
		s.command("gamemode creative @a");
		s.command("effect clear @a");
		s.command("fill -3 " + (floor - 4) + " -3 3 " + floor + " 3 air");
	}

	private static void effectRange(SelfTest.Script s) {
		EffectRangeModule module = module("effect_range");
		isolate(s, module);
		// A beacon on a two-layer iron pyramid (level 2 = 30 blocks), four blocks in front of the player.
		s.command("fill -2 " + GROUND_Y + " -8 2 " + GROUND_Y + " -4 iron_block");
		s.command("fill -1 " + (GROUND_Y + 1) + " -7 1 " + (GROUND_Y + 1) + " -5 iron_block");
		s.command("setblock 0 " + (GROUND_Y + 2) + " -6 beacon");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -8");
		s.waitTicks(20);
		s.info("beacon", client -> module.getShown());
		s.check("the looked-at beacon is level 2 with a 30 block range", client -> module.getShown() != null
				&& module.getShown().source() == EffectRangeModule.Source.BEACON && module.getShown().level() == 2
				&& module.getShown().range() == 30 && !module.getShown().preview());
		s.screenshot("24_beacon_range");

		// Holding a conduit in front of a prismarine cage with its front open, aiming at the spot in
		// its middle: 33 frame blocks count there, which makes a 64 block radius.
		resetPlayer(s);
		s.command("fill -2 " + (GROUND_Y - 1) + " -8 2 " + (GROUND_Y + 3) + " -4 prismarine hollow");
		s.command("fill -2 " + (GROUND_Y - 1) + " -4 2 " + (GROUND_Y + 3) + " -4 air");
		s.command("setblock 0 " + GROUND_Y + " -6 stone");
		s.command("give @a conduit");
		s.command("tp @a 0.5 " + GROUND_Y + " -2.5 180 12");
		s.waitTicks(20);
		s.info("conduit", client -> module.getShown());
		s.check("holding a conduit previews 33 frame blocks = 64 block radius", client -> module.getShown() != null
				&& module.getShown().source() == EffectRangeModule.Source.CONDUIT && module.getShown().preview()
				&& module.getShown().level() == 33 && module.getShown().range() == 64);
		s.screenshot("24_conduit_preview");
		s.command("fill -2 " + (GROUND_Y - 1) + " -8 2 " + (GROUND_Y + 3) + " -4 air");
		s.command("fill -2 " + (GROUND_Y - 1) + " -8 2 " + (GROUND_Y - 1) + " -4 grass_block");
	}

	private static void fluidVision(SelfTest.Script s) {
		FluidVisionModule module = module("fluid_vision");
		isolate(s, module);
		// A long glass tank full of water with a gold block at the far end, 30 blocks away.
		s.command("fill -2 " + GROUND_Y + " -36 2 " + (GROUND_Y + 4) + " -3 glass hollow");
		s.command("fill -1 " + (GROUND_Y + 1) + " -35 1 " + (GROUND_Y + 3) + " -4 water");
		s.command("fill -1 " + (GROUND_Y + 1) + " -35 1 " + (GROUND_Y + 3) + " -35 gold_block");
		s.command("tp @a 0.5 " + (GROUND_Y + 1) + " -4.5 180 0");
		s.run("vision off", client -> module.setEnabled(false));
		s.waitTicks(30);
		s.screenshot("25_water_off");
		int[] frames = new int[2];
		s.run("vision on", client -> {
			module.setEnabled(true);
			frames[0] = module.getWaterFrames();
		});
		s.waitTicks(20);
		s.check("the water fog is being adjusted", client -> module.getWaterFrames() > frames[0]);
		s.screenshot("25_water_on");
		s.command("fill -2 " + GROUND_Y + " -36 2 " + (GROUND_Y + 4) + " -3 air");

		// A short lava tank with a gold block 6 blocks ahead.
		s.command("fill -2 " + GROUND_Y + " -12 2 " + (GROUND_Y + 4) + " -3 glass hollow");
		s.command("fill -1 " + (GROUND_Y + 1) + " -11 1 " + (GROUND_Y + 3) + " -4 lava");
		s.command("fill -1 " + (GROUND_Y + 1) + " -11 1 " + (GROUND_Y + 3) + " -11 gold_block");
		s.command("tp @a 0.5 " + (GROUND_Y + 1) + " -4.5 180 0");
		s.run("vision off", client -> module.setEnabled(false));
		s.waitTicks(30);
		s.screenshot("25_lava_off");
		s.run("vision on", client -> {
			module.setEnabled(true);
			frames[1] = module.getLavaFrames();
		});
		s.waitTicks(20);
		s.check("the lava fog is being adjusted", client -> module.getLavaFrames() > frames[1]);
		s.screenshot("25_lava_on");
		s.command("fill -2 " + GROUND_Y + " -12 2 " + (GROUND_Y + 4) + " -3 air");
	}

	private static void itemSearch(SelfTest.Script s) {
		ItemSearchModule module = module("item_search");
		isolate(s, module);
		s.command("gamemode survival @a");
		s.command("give @a diamond 3");
		s.command("give @a iron_ingot 5");
		s.command("give @a gold_ingot 2");
		s.command("give @a diamond_pickaxe");
		s.waitTicks(10);
		s.run("open the inventory", client -> client.setScreen(new InventoryScreen(client.player)));
		s.waitTicks(5);
		// The game runs in English here; Chinese names and pinyin initials must work all the same.
		String[][] cases = {
			{"zs", "2"},         // 钻石 and 钻石镐 (zsg)
			{"zsg", "1"},        // 钻石镐
			{"diamond", "2"},
			{"钻石", "2"},
			{"铁锭", "1"},
			{"td", "1"},         // 铁锭
			{"gold ingot", "1"},
			{"xyz", "0"},
		};
		for (String[] testCase : cases) {
			s.run("search for " + testCase[0], client -> module.setQuery(testCase[0]));
			s.check("'" + testCase[0] + "' finds " + testCase[1] + " slot(s)", client -> client.currentScreen instanceof HandledScreen<?> screen
					&& module.countMatches(screen) == Integer.parseInt(testCase[1]));
		}
		s.run("search for zs again for the screenshot", client -> module.setQuery("zs"));
		s.screenshot("26_item_search");
		s.run("close the inventory", client -> client.setScreen(null));
		s.command("gamemode creative @a");
	}

	private static void hotbarLayouts(SelfTest.Script s) {
		HotbarLayoutsModule module = module("hotbar_layouts");
		isolate(s, module);
		s.command("gamemode survival @a");
		// Hotbar: stone, dirt. Backpack: sword, bow. The layout wants sword, bow, (nothing), netherite ingot.
		s.command("item replace entity @a hotbar.0 with stone 8");
		s.command("item replace entity @a hotbar.1 with dirt 8");
		s.command("item replace entity @a inventory.0 with diamond_sword");
		s.command("item replace entity @a inventory.5 with bow");
		s.waitTicks(10);
		String[] before = new String[2];
		s.run("save the current hotbar as layout 2, define layout 1 by hand", client -> {
			before[0] = module.getLayoutName(0);
			before[1] = String.join(",", module.getLayout(0));
			module.saveCurrent(client, 1);
			module.setLayoutName(0, "Fight");
			module.setLayout(0, new String[] {"minecraft:diamond_sword", "minecraft:bow", "", "minecraft:netherite_ingot", "", "", "", "", ""});
		});
		s.check("saving recorded stone and dirt", client -> module.getLayout(1)[0].equals("minecraft:stone")
				&& module.getLayout(1)[1].equals("minecraft:dirt") && module.getLayout(1)[2].isEmpty());
		s.run("open the layout screen", client -> client.setScreen(new HotbarLayoutScreen(null, module)));
		s.screenshot("27_hotbar_layout_screen");
		s.run("close it and apply layout 1", client -> {
			client.setScreen(null);
			module.apply(client, 0);
		});
		s.waitUntil("the layout is applied", client -> !module.isApplying(), 60);
		s.waitTicks(10);
		s.check("sword and bow are now in hotbar slots 1 and 2", client -> client.player.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD)
				&& client.player.getInventory().getStack(1).isOf(Items.BOW));
		s.check("the stone and dirt went to the backpack, nothing was lost", client ->
				client.player.getInventory().count(Items.STONE) == 8 && client.player.getInventory().count(Items.DIRT) == 8);
		s.check("the missing netherite ingot was skipped without fuss", client -> module.getMissing() == 1);
		s.screenshot("27_hotbar_layout_applied");
		s.run("apply layout 2 to get the old hotbar back", client -> module.apply(client, 1));
		s.waitUntil("layout 2 is applied", client -> !module.isApplying(), 60);
		s.waitTicks(10);
		s.check("stone and dirt are back in slots 1 and 2", client -> client.player.getInventory().getStack(0).isOf(Items.STONE)
				&& client.player.getInventory().getStack(1).isOf(Items.DIRT));
		s.run("restore the layouts", client -> {
			module.setLayoutName(0, before[0]);
			module.setLayout(0, before[1].split(",", -1));
			module.setLayout(1, new String[] {"", "", "", "", "", "", "", "", ""});
		});
		s.command("gamemode creative @a");
	}

	private static void netherRoof(SelfTest.Script s) {
		NetherRoofModule module = module("nether_roof");
		isolate(s, module);
		s.check("not active in the Overworld", client -> !module.isActive());
		// On top of the bedrock ceiling (its top layer is y 127).
		s.command("execute in minecraft:the_nether run tp @a 12.5 128 -43.5 135 0");
		s.waitUntil("arrived in the nether", client -> client.world != null
				&& client.world.getRegistryKey() == World.NETHER && client.player != null && client.currentScreen == null, 20 * 30);
		s.run("destination: Overworld 1200, -340", client -> module.destinationSetting().set("1200, -340"));
		s.waitTicks(40);
		s.clearChat();
		s.info("roof", client -> "active=" + module.isActive() + " y=" + client.player.getY()
				+ " overworld=" + module.getOverworldX() + "," + module.getOverworldZ());
		s.check("active on the roof", client -> module.isActive());
		s.check("12.5, -43.5 in the Nether is 100, -348 in the Overworld", client -> module.getOverworldX() == 100 && module.getOverworldZ() == -348);
		s.screenshot("28_nether_roof");
		s.run("clear the destination", client -> module.destinationSetting().reset());
		s.command("execute in minecraft:overworld run tp @a 0.5 " + GROUND_Y + " 0.5 180 0");
		s.waitUntil("back in the overworld", client -> client.world != null
				&& client.world.getRegistryKey() == World.OVERWORLD && client.player != null && client.currentScreen == null, 20 * 30);
		s.waitTicks(20);
	}

	private static void chestMemory(SelfTest.Script s) {
		ChestMemoryModule module = module("chest_memory");
		BlockPos chestPos = new BlockPos(2, GROUND_Y, -3);
		isolate(s, module);
		s.run("forget chests remembered by earlier runs", client -> module.forgetAll());
		// A chest holding 12 diamonds and a shulker box with a diamond pickaxe inside.
		s.command("setblock 2 " + GROUND_Y + " -3 chest[facing=south]{Items:["
				+ "{Slot:0b,id:\"minecraft:diamond\",count:12},"
				+ "{Slot:1b,id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":"
				+ "[{slot:0,item:{id:\"minecraft:diamond_pickaxe\",count:1}}]}}]}");
		s.command("tp @a 2.5 " + GROUND_Y + " -1.0 180 45");
		s.waitTicks(15);
		s.run("right-click the chest", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitUntil("the chest screen opens", client -> client.currentScreen instanceof GenericContainerScreen, 60);
		s.waitTicks(15);
		s.run("close the chest", client -> client.player.closeHandledScreen());
		s.waitTicks(5);
		s.check("the chest is remembered at its position", client -> module.getChests().size() == 1
				&& chestPos.equals(module.getChests().get(0).pos));
		s.check("its 12 diamonds and the box with the pickaxe are recorded", client -> module.getChests().size() == 1
				&& module.getChests().get(0).items.stream().anyMatch(item -> item.id().equals("minecraft:diamond") && item.count() == 12)
				&& module.getChests().get(0).boxes.size() == 1
				&& module.getChests().get(0).boxes.get(0).items().get(0).id().equals("minecraft:diamond_pickaxe"));
		s.check("searching 'zs' finds 13 items there (12 diamonds + the pickaxe in the box)", client -> {
			List<ChestMemoryModule.Hit> hits = module.search("zs", client.player, "minecraft:overworld");
			return hits.size() == 1 && hits.get(0).count() == 13 && hits.get(0).inShulkerBox();
		});
		s.check("searching for something that is not there finds nothing",
				client -> module.search("netherite", client.player, "minecraft:overworld").isEmpty());
		s.run("open the search screen and search for diamond", client -> {
			ChestMemoryScreen screen = new ChestMemoryScreen(null, module);
			client.setScreen(screen);
			screen.setQuery("diamond");
		});
		s.waitTicks(5);
		s.screenshot("29_chest_memory_search");
		s.run("click the result", client -> ((ChestMemoryScreen) client.currentScreen).choose(0));
		s.command("tp @a 6.5 " + GROUND_Y + " 6.5 0 0");
		s.waitTicks(15);
		s.check("the HUD now points at the chest", client -> module.getTarget() != null && chestPos.equals(module.getTarget().pos));
		s.screenshot("29_chest_memory_pointer");
		s.command("setblock 2 " + GROUND_Y + " -3 air");
		s.command("tp @a 2.5 " + GROUND_Y + " -1.0 180 45");
		s.waitTicks(60);
		s.check("a chest that no longer exists is forgotten", client -> module.getChests().isEmpty());
		s.command("kill @e[type=item]");
	}

	/** Clicks a recipe of the recipe book the way the book itself does; the server answers with the preview. */
	private static void clickRecipe(MinecraftClient client, net.minecraft.item.Item result) {
		for (RecipeResultCollection collection : client.player.getRecipeBook().getOrderedResults()) {
			for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
				if (entry.getStacks(SlotDisplayContexts.createParameters(client.world)).stream().anyMatch(stack -> stack.isOf(result))) {
					client.interactionManager.clickRecipe(client.player.currentScreenHandler.syncId, entry.id(), false);
					return;
				}
			}
		}
		throw new IllegalStateException("recipe not in the recipe book: " + result);
	}

	private static void recipeHelper(SelfTest.Script s) {
		RecipeHelperModule module = module("recipe_helper");
		ChestMemoryModule chests = module("chest_memory");
		BlockPos chestPos = new BlockPos(2, GROUND_Y, -3);
		isolate(s, module);
		s.run("chest memory on as well, nothing remembered", client -> {
			chests.setEnabled(true);
			chests.forgetAll();
		});
		// A chest with 20 iron ingots that the player has looked into.
		s.command("setblock 2 " + GROUND_Y + " -3 chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:20}]}");
		s.command("tp @a 2.5 " + GROUND_Y + " -1.0 180 45");
		s.waitTicks(15);
		s.run("right-click the chest", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitUntil("the chest screen opens", client -> client.currentScreen instanceof GenericContainerScreen, 60);
		s.waitTicks(15);
		s.run("close the chest", client -> client.player.closeHandledScreen());
		s.waitTicks(5);
		// Flint and steel = flint + iron ingot. The player has the flint only.
		s.command("gamemode survival @a");
		s.command("recipe give @a minecraft:flint_and_steel");
		s.command("give @a flint 1");
		s.command("tp @a 6.5 " + GROUND_Y + " 6.5 0 0");
		s.waitTicks(15);
		s.run("open the inventory", client -> client.setScreen(new InventoryScreen(client.player)));
		s.waitTicks(10);
		s.check("nothing is shown before a recipe is picked", client -> module.getMissing().isEmpty());
		s.run("pick the flint and steel recipe", client -> clickRecipe(client, Items.FLINT_AND_STEEL));
		s.waitTicks(15);
		s.info("missing", client -> module.getMissing().stream().map(entry -> entry.count + "x" + entry.accepts.get(0).getItem()
				+ " sources=" + entry.sources.size()).toList());
		s.check("exactly one thing is missing: 1 iron ingot (the flint is there)", client -> module.getMissing().size() == 1
				&& module.getMissing().get(0).count == 1 && module.getMissing().get(0).accepts.get(0).isOf(Items.IRON_INGOT));
		s.check("the known chest with 20 iron ingots is named as the place to get it", client -> module.getMissing().size() == 1
				&& module.getMissing().get(0).sources.size() == 1
				&& module.getMissing().get(0).sources.get(0).chest() != null
				&& chestPos.equals(module.getMissing().get(0).sources.get(0).chest().pos)
				&& module.getMissing().get(0).sources.get(0).count() == 20);
		s.screenshot("32_recipe_helper_frames");
		s.run("keep the details open", client -> module.showDetailsFor(0));
		s.waitTicks(3);
		s.screenshot("32_recipe_helper");
		// A shulker box in the backpack that holds iron counts as a place too, and comes first.
		s.command("give @a shulker_box[container=[{slot:0,item:{id:\"minecraft:iron_ingot\",count:5}}]]");
		s.waitTicks(15);
		s.check("a carried shulker box with iron is listed first", client -> module.getMissing().size() == 1
				&& module.getMissing().get(0).sources.size() == 2 && module.getMissing().get(0).sources.get(0).isCarried()
				&& module.getMissing().get(0).sources.get(0).count() == 5);
		s.screenshot("32_recipe_helper_box");
		s.run("click the missing ingredient", client -> module.activate(module.getMissing().get(0)));
		s.waitTicks(10);
		s.check("the inventory closed and chest memory points at the chest", client -> client.currentScreen == null
				&& chests.getTarget() != null && chestPos.equals(chests.getTarget().pos));
		s.screenshot("32_recipe_helper_pointer");
		// With the iron in the backpack the recipe can be made: no preview, nothing reported.
		s.command("give @a iron_ingot 1");
		s.waitTicks(10);
		s.run("open the inventory and pick the recipe again", client -> client.setScreen(new InventoryScreen(client.player)));
		s.waitTicks(10);
		s.run("pick the recipe", client -> clickRecipe(client, Items.FLINT_AND_STEEL));
		s.waitTicks(15);
		s.check("with all ingredients nothing is reported missing", client -> module.getMissing().isEmpty());
		s.run("close the inventory", client -> client.player.closeHandledScreen());
		s.command("setblock 2 " + GROUND_Y + " -3 air");
		s.command("kill @e[type=item]");
		s.command("gamemode creative @a");
		s.run("forget the chest", client -> chests.forgetAll());
	}

	private static void pressUse(SelfTest.Script s) {
		s.run("right click", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitTicks(6);
	}

	private static void placementMaster(SelfTest.Script s) {
		PlacementMasterModule module = module("placement_master");
		isolate(s, module);
		BlockPos first = new BlockPos(0, GROUND_Y, -1);
		// Stairs in hand, looking north and down at the ground one block ahead.
		s.command("item replace entity @a weapon.mainhand with oak_stairs 16");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 60");
		s.waitTicks(15);
		s.info("preview", client -> module.getCurrent() == null ? "none" : module.getCurrent().pos().toShortString() + " " + module.getCurrent().state());
		s.check("the preview shows oak stairs on the ground ahead, facing north, lower half", client -> module.getCurrent() != null
				&& module.isPreviewShown() && first.equals(module.getCurrent().pos())
				&& module.getCurrent().state().isOf(Blocks.OAK_STAIRS)
				&& module.getCurrent().state().get(Properties.HORIZONTAL_FACING) == Direction.NORTH
				&& module.getCurrent().state().get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM);
		s.screenshot("33_placement_preview");
		BlockState[] predicted = new BlockState[1];
		s.run("remember the preview", client -> predicted[0] = module.getCurrent().state());
		pressUse(s);
		s.check("the block that got placed is exactly the previewed one", client -> client.world.getBlockState(first).equals(predicted[0]));
		s.check("its orientation is remembered for the lock", client -> module.getLastOrientation() != null
				&& "north".equals(module.getLastOrientation().get("facing")) && "bottom".equals(module.getLastOrientation().get("half")));

		// Lock held, now facing east: the stairs would face east, so the click must not happen.
		BlockPos east = new BlockPos(1, GROUND_Y, 0);
		s.run("hold the lock key", client -> module.getLockKey().setPressed(true));
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 -90 60");
		s.waitTicks(15);
		s.check("facing east the preview is marked as different from the locked orientation", client -> module.getCurrent() != null
				&& east.equals(module.getCurrent().pos()) && module.isLocking() && module.conflicts(module.getCurrent().state()));
		s.screenshot("33_placement_lock_wrong");
		pressUse(s);
		s.check("the click was stopped: nothing was placed", client -> client.world.getBlockState(east).isAir() && module.getStoppedCount() == 1);
		// Facing north again, somewhere else: same orientation, so it goes through.
		BlockPos again = new BlockPos(2, GROUND_Y, -1);
		s.command("tp @a 2.5 " + GROUND_Y + " 0.5 180 60");
		s.waitTicks(15);
		s.check("facing north again the preview matches the lock", client -> module.getCurrent() != null
				&& again.equals(module.getCurrent().pos()) && module.isLocking() && !module.conflicts(module.getCurrent().state()));
		s.screenshot("33_placement_lock_ok");
		pressUse(s);
		s.check("a matching placement goes through while locked", client -> client.world.getBlockState(again).isOf(Blocks.OAK_STAIRS)
				&& module.getStoppedCount() == 1);
		s.run("release the lock key", client -> module.getLockKey().setPressed(false));

		// Upper / lower half: a slab aimed at the side of a block at eye height.
		s.command("setblock 5 " + GROUND_Y + " -3 stone");
		s.command("setblock 5 " + (GROUND_Y + 1) + " -3 stone");
		s.command("item replace entity @a weapon.mainhand with oak_slab 16");
		s.command("tp @a 5.5 " + GROUND_Y + " -0.5 180 0");
		s.waitTicks(15);
		s.check("aiming at the upper half of the side face previews an upper slab", client -> module.getCurrent() != null
				&& module.halfZone() == 1 && module.getCurrent().state().get(Properties.SLAB_TYPE) == SlabType.TOP);
		s.screenshot("33_placement_half_upper");
		s.command("tp @a 5.5 " + GROUND_Y + " -0.5 180 15");
		s.waitTicks(15);
		s.check("aiming at the lower half previews a lower slab", client -> module.getCurrent() != null
				&& module.halfZone() == -1 && module.getCurrent().state().get(Properties.SLAB_TYPE) == SlabType.BOTTOM);
		s.screenshot("33_placement_half_lower");

		// A torch against a wall becomes a wall torch: the item decides, and the preview must follow it.
		s.command("item replace entity @a weapon.mainhand with torch 16");
		s.waitTicks(10);
		s.check("a torch aimed at a wall previews as a wall torch facing away from it", client -> module.getCurrent() != null
				&& module.getCurrent().state().isOf(Blocks.WALL_TORCH)
				&& module.getCurrent().state().get(Properties.HORIZONTAL_FACING) == Direction.SOUTH);
		// Plain stone has no direction: by default it gets no preview.
		s.command("item replace entity @a weapon.mainhand with stone 16");
		s.waitTicks(10);
		s.check("plain stone is worked out but not previewed", client -> module.getCurrent() != null && !module.isPreviewShown());
	}

	/**
	 * Puts the (creative) player in the air and keeps them there. Flying can only be switched on
	 * while off the ground (the game switches it off again the moment the player stands), hence
	 * the two teleports.
	 */
	private static void hover(SelfTest.Script s, String where) {
		s.command("tp @a " + where);
		s.waitTicks(2);
		s.run("fly", client -> {
			client.player.getAbilities().flying = true;
			client.player.sendAbilitiesUpdate();
		});
		s.command("tp @a " + where);
	}

	private static void diagnostics(SelfTest.Script s) {
		DiagnosticsModule module = module("diagnostics");
		ChestMemoryModule chests = module("chest_memory");
		int y = GROUND_Y;
		isolate(s, module);
		s.run("chest memory on as well, nothing remembered", client -> {
			chests.setEnabled(true);
			chests.forgetAll();
		});
		// A small "machine". Line 1: redstone block and 16 dust; the signal dies at the 16th.
		s.command("setblock -8 " + y + " -4 redstone_block");
		s.command("fill -7 " + y + " -4 8 " + y + " -4 redstone_wire");
		// Line 2 (joined through a stone block): lever, dust, repeater on its 3rd setting, lamp.
		s.command("setblock -7 " + y + " -5 stone");
		s.command("setblock -8 " + y + " -6 lever[face=floor,powered=true]");
		s.command("setblock -7 " + y + " -6 redstone_wire");
		s.command("setblock -6 " + y + " -6 repeater[facing=west,delay=3]");
		s.command("setblock -5 " + y + " -6 redstone_lamp");
		// A hopper locked by a redstone block, a torch one layer up, and an output chest.
		s.command("setblock 0 " + y + " -5 stone");
		s.command("setblock 0 " + y + " -6 hopper");
		s.command("setblock 1 " + y + " -6 redstone_block");
		s.command("setblock 0 " + (y + 1) + " -5 redstone_torch");
		s.command("setblock 4 " + y + " -5 chest[facing=east]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:5}]}");
		hover(s, "0.5 " + (y + 8) + " 3.5 180 50");
		s.waitTicks(20);
		s.check("there is nothing to show before a scan", client -> module.getParts().isEmpty());
		s.check("scanning from a block with no redstone near it finds nothing", client -> !module.scan(client.world, new BlockPos(0, y, 6)));
		s.check("scanning from the first dust takes the whole machine", client -> module.scan(client.world, new BlockPos(-7, y, -4)));
		s.waitTicks(5);
		s.info("machine", client -> module.buildLines().stream().map(Text::getString).toList());
		s.check("all 25 components are found, through the stone blocks too", client -> module.getParts().size() == 25
				&& module.count(DiagnosticsModule.Kind.WIRE) == 17 && module.count(DiagnosticsModule.Kind.SOURCE) == 2
				&& module.count(DiagnosticsModule.Kind.INPUT) == 1 && module.count(DiagnosticsModule.Kind.REPEATER) == 1
				&& module.count(DiagnosticsModule.Kind.LAMP) == 1 && module.count(DiagnosticsModule.Kind.HOPPER) == 1
				&& module.count(DiagnosticsModule.Kind.TORCH) == 1 && module.count(DiagnosticsModule.Kind.CONTAINER) == 1);
		s.check("overview: 16 of the 17 dust are powered, the lamp is lit", client -> module.activeCount(DiagnosticsModule.Kind.WIRE) == 16
				&& module.activeCount(DiagnosticsModule.Kind.LAMP) == 1);
		s.check("signal flow: the one break point is the 16th dust of the long line", client -> module.getBreakPoints().size() == 1
				&& module.getBreakPoints().get(0).pos.equals(new BlockPos(8, y, -4)));
		s.check("bottleneck: the locked hopper is reported", client -> module.getLockedHoppers().size() == 1
				&& module.getLockedHoppers().get(0).pos.equals(new BlockPos(0, y, -6)));
		s.screenshot("34_diagnostics");

		// Switch the lever off and on every 10 ticks: everything behind it repeats every 20 ticks.
		for (int i = 0; i < 4; i++) {
			s.command("setblock -8 " + y + " -6 lever[face=floor,powered=false]");
			s.waitTicks(10);
			s.command("setblock -8 " + y + " -6 lever[face=floor,powered=true]");
			s.waitTicks(10);
		}
		s.info("slowest", client -> module.getSlowest() == null ? "none" : module.getSlowest().kind + " every " + module.getSlowest().getPeriod() + " gt");
		s.check("bottleneck: the repeating part is measured at about 20 game ticks", client -> module.getSlowest() != null
				&& module.getSlowest().getPeriod() >= 16 && module.getSlowest().getPeriod() <= 28);

		// Output: drops appearing at the machine, and what gets added to its chest.
		s.command("summon item -2 " + (y + 1) + " -4 {Item:{id:\"minecraft:wheat\",count:8}}");
		s.command("summon item 3 " + (y + 1) + " -4 {Item:{id:\"minecraft:wheat\",count:8}}");
		s.command("summon item 6 " + (y + 1) + " -4 {Item:{id:\"minecraft:iron_ingot\",count:3}}");
		s.command("summon item 0 " + (y + 1) + " 6 {Item:{id:\"minecraft:diamond\",count:9}}");
		s.waitTicks(20);
		s.check("rate: 16 wheat and 3 iron dropped at the machine are counted, the diamonds far away are not", client ->
				module.getItemTotal("minecraft:wheat") == 16 && module.getItemTotal("minecraft:iron_ingot") == 3
						&& module.getItemTotal("minecraft:diamond") == 0);
		s.command("kill @e[type=item]");
		s.run("land", client -> {
			client.player.getAbilities().flying = false;
			client.player.sendAbilitiesUpdate();
		});
		s.command("tp @a 6.5 " + y + " -4.5 90 30");
		s.waitTicks(15);
		s.run("right-click the chest", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitUntil("the chest screen opens", client -> client.currentScreen instanceof GenericContainerScreen, 60);
		s.waitTicks(15);
		s.run("close the chest", client -> client.player.closeHandledScreen());
		// 21 seconds later the chest holds 32 more iron.
		s.waitTicks(20 * 21);
		s.command("item replace block 4 " + y + " -5 container.0 with iron_ingot 37");
		s.run("right-click the chest again", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitUntil("the chest screen opens", client -> client.currentScreen instanceof GenericContainerScreen, 60);
		s.waitTicks(15);
		s.run("close the chest", client -> client.player.closeHandledScreen());
		s.waitTicks(15);
		s.check("rate: the chest gained 32 iron between the two looks", client -> module.getChestGain("minecraft:iron_ingot") == 32);
		hover(s, "0.5 " + (y + 8) + " 3.5 180 50");
		s.waitTicks(20);
		s.info("panel", client -> module.buildLines().stream().map(Text::getString).toList());
		s.screenshot("34_diagnostics_rates");

		// Slice: layer 1 is everything on the ground, layer 2 only the torch.
		s.run("next layer", client -> module.stepSlice(1));
		s.waitTicks(5);
		s.check("slice 1 holds everything but the torch", client -> module.getSlice() == 0 && module.getShownParts() == 24
				&& module.count(DiagnosticsModule.Kind.TORCH) == 0);
		s.screenshot("34_diagnostics_slice");
		s.run("next layer", client -> module.stepSlice(1));
		s.waitTicks(5);
		s.check("slice 2 holds only the torch", client -> module.getSlice() == 1 && module.getShownParts() == 1
				&& module.count(DiagnosticsModule.Kind.TORCH) == 1);
		s.run("next layer", client -> module.stepSlice(1));
		s.check("after the last layer the whole machine is shown again", client -> module.getSlice() == -1 && module.getShownParts() == 25);

		s.command("setblock -5 " + y + " -6 air");
		s.waitTicks(10);
		s.check("removing a component is noticed (scan again)", client -> module.isChanged());
		s.run("clear", client -> module.clear());
		s.check("cleared", client -> module.getParts().isEmpty());
		s.run("land, forget the chest", client -> {
			client.player.getAbilities().flying = false;
			client.player.sendAbilitiesUpdate();
			chests.forgetAll();
		});
	}

	private static void shulkerManager(SelfTest.Script s) {
		ShulkerManagerModule module = module("shulker_manager");
		isolate(s, module);
		s.run("item search and chest memory on as well", client -> {
			module("item_search").setEnabled(true);
			module("chest_memory").setEnabled(true);
		});
		s.command("gamemode survival @a");
		// Three boxes: building blocks, gear, and a bit of everything.
		s.command("give @a shulker_box[container=[{slot:0,item:{id:\"minecraft:stone\",count:64}},"
				+ "{slot:1,item:{id:\"minecraft:oak_planks\",count:64}},{slot:2,item:{id:\"minecraft:glass\",count:32}}]]");
		s.command("give @a cyan_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond_pickaxe\",count:1}},"
				+ "{slot:1,item:{id:\"minecraft:diamond_sword\",count:1}},{slot:2,item:{id:\"minecraft:iron_chestplate\",count:1}}]]");
		s.command("give @a red_shulker_box[container=[{slot:0,item:{id:\"minecraft:stone\",count:1}},"
				+ "{slot:1,item:{id:\"minecraft:bread\",count:5}},{slot:2,item:{id:\"minecraft:redstone\",count:9}},"
				+ "{slot:3,item:{id:\"minecraft:diamond\",count:2}}]]");
		s.waitTicks(10);
		s.check("the first box is sorted as building blocks", client ->
				ShulkerManagerModule.categoryOf(client.player.getInventory().getStack(0)) == ShulkerManagerModule.BoxCategory.BUILDING);
		s.check("the second as gear", client ->
				ShulkerManagerModule.categoryOf(client.player.getInventory().getStack(1)) == ShulkerManagerModule.BoxCategory.GEAR);
		s.check("the third as mixed", client ->
				ShulkerManagerModule.categoryOf(client.player.getInventory().getStack(2)) == ShulkerManagerModule.BoxCategory.MIXED);
		s.check("searching the carried boxes for '钻石镐' finds the box in hotbar slot 2", client -> {
			List<ShulkerManagerModule.InventoryHit> hits = module.searchInventory(client.player, "钻石镐");
			return hits.size() == 1 && hits.get(0).slot() == 1 && hits.get(0).count() == 1;
		});
		s.run("open the inventory", client -> client.setScreen(new InventoryScreen(client.player)));
		s.waitTicks(5);
		s.screenshot("30_shulker_tags");
		s.run("type zsg into the item search box", client -> ((ItemSearchModule) module("item_search")).setQuery("zsg"));
		s.screenshot("30_shulker_search");
		s.run("open the chest memory search with the same word", client -> {
			ChestMemoryScreen screen = new ChestMemoryScreen(null, module("chest_memory"));
			client.setScreen(screen);
			screen.setQuery("zsg");
		});
		s.waitTicks(5);
		s.check("the chest memory screen lists the carried box", client -> client.currentScreen instanceof ChestMemoryScreen screen
				&& screen.getCarried().size() == 1);
		s.screenshot("30_shulker_carried");
		s.run("close", client -> client.setScreen(null));
		s.command("gamemode creative @a");
	}

	private static void afkClicker(SelfTest.Script s) {
		AfkClickerModule module = module("afk_clicker");
		isolate(s, module);
		s.command("gamemode survival @a");
		s.command("give @a stone 16");
		// Looking down at the ground a little ahead: every right click places one stone.
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 50");
		s.waitTicks(10);
		s.run("custom preset: right click every 5 ticks, then start", client -> {
			module.presetSetting().set(AfkClickerModule.AfkPreset.CUSTOM);
			module.actionSetting().set(AfkClickerModule.AfkAction.RIGHT_CLICK);
			module.intervalSetting().set(5);
			module.start(client);
		});
		s.waitTicks(32);
		s.clearChat();
		s.info("afk clicker", client -> "running=" + module.isRunning() + " clicks=" + module.getClicks()
				+ " stone left=" + client.player.getMainHandStack().getCount());
		s.check("the clicker is running and has clicked at least 5 times", client -> module.isRunning() && module.getClicks() >= 5);
		s.check("the clicks really placed stone (fewer than 16 left)", client -> client.player.getMainHandStack().getCount() < 16);
		s.run("try to turn the view with the mouse", client -> SelfTest.moveMouse(client, 400, 200));
		s.waitTicks(3);
		s.check("the view stayed locked", client -> Math.abs(client.player.getPitch() - 50F) < 0.5F
				&& Math.abs(MathHelper.wrapDegrees(client.player.getYaw() - 180F)) < 0.5F);
		s.screenshot("17_afk_clicker");
		// "damage" takes exactly one entity, so @p rather than @a.
		s.command("damage @p 1");
		// Not a fixed wait: on a busy computer the server may take a moment to get to the command.
		s.waitUntil("the clicker stops after the damage", client -> !module.isRunning(), 100);
		s.check("getting hurt stopped the clicker", client -> !module.isRunning() && module.wasStoppedByDamage());
		s.screenshot("17_afk_clicker_stopped");
		s.run("restore AFK settings", client -> {
			module.presetSetting().reset();
			module.actionSetting().reset();
			module.intervalSetting().reset();
		});
		s.command("gamemode creative @a");
	}

	private static void freecam(SelfTest.Script s) {
		FreecamModule module = module("freecam");
		isolate(s, module);
		s.waitTicks(10);
		s.run("switch freecam on", client -> module.setActive(client, true));
		// The player looks north; "back" and "jump" fly the camera south and up, behind the body.
		s.run("hold back + jump", client -> {
			client.options.backKey.setPressed(true);
			client.options.jumpKey.setPressed(true);
		});
		s.waitTicks(12);
		s.run("release the keys", client -> {
			client.options.backKey.setPressed(false);
			client.options.jumpKey.setPressed(false);
		});
		s.run("move the mouse down a little", client -> SelfTest.moveMouse(client, 0, 150));
		s.waitTicks(5);
		s.info("freecam", client -> "camera=" + module.getCameraPos(1F) + " cameraPitch=" + module.getPitch()
				+ " player=" + client.player.getEntityPos() + " playerPitch=" + client.player.getPitch());
		s.check("freecam is on", client -> module.isActive());
		s.check("the camera flew away (south and up)", client -> module.getCameraPos(1F).z > 3.0
				&& module.getCameraPos(1F).y > GROUND_Y + 4.0);
		s.check("the body did not move or jump", client -> Math.abs(client.player.getX() - 0.5) < 0.01
				&& Math.abs(client.player.getZ() - 0.5) < 0.01 && Math.abs(client.player.getY() - GROUND_Y) < 0.01);
		s.check("the mouse turned the camera, not the body", client -> Math.abs(module.getPitch() - 22.5F) < 0.1F
				&& Math.abs(client.player.getPitch()) < 0.1F);
		s.check("the game camera really is at the freecam position", client ->
				client.gameRenderer.getCamera().getCameraPos().distanceTo(module.getCameraPos(1F)) < 0.5);
		s.screenshot("18_freecam");
		s.run("switch freecam off", client -> module.setActive(client, false));
		s.waitTicks(5);
		s.check("the camera is back at the eyes", client ->
				client.gameRenderer.getCamera().getCameraPos().distanceTo(client.player.getEyePos()) < 0.5);
	}

	/** Leaves the current world and enters another one (created on first use). */
	private static void switchWorld(SelfTest.Script s, String name, boolean flat) {
		s.run("leave the world", client -> client.send(() -> client.disconnect(Text.literal("self-test world switch"))));
		s.waitUntil("back at the title screen", client -> client.world == null && client.currentScreen instanceof TitleScreen, 20 * 60);
		s.run("enter world '" + name + "'", client -> client.send(() -> {
			if (client.getLevelStorage().levelExists(name)) {
				client.createIntegratedServerLoader().start(name, () -> client.setScreen(new TitleScreen()));
			} else {
				LevelInfo info = new LevelInfo(name, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
						new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE);
				client.createIntegratedServerLoader().createAndStart(name, info, new GeneratorOptions(20261001L, false, false),
						flat ? WorldPresets::createTestOptions : WorldPresets::createDemoOptions, client.currentScreen);
			}
		}));
		s.waitUntil("player is in '" + name + "'", Scenarios::inWorld, 20 * 240);
		s.waitTicks(60);
	}

	/**
	 * Freecam in a world with real terrain: fly the camera into the ground and through it.
	 * What matters is that the world keeps being drawn (no flicker, nothing missing) while the
	 * camera is inside rock, which the flat test world cannot show.
	 */
	private static void freecamUnderground(SelfTest.Script s) {
		FreecamModule module = module("freecam");
		switchWorld(s, NORMAL_WORLD_NAME, false);
		s.run("only freecam on", client -> {
			for (Module other : ModuleRegistry.all()) {
				other.setEnabled(other == module);
			}
			module.clearMarkers();
			client.inGameHud.getChatHud().clear(false);
		});
		s.command("gamemode creative @a");
		s.command("time set noon");
		s.waitTicks(100);
		int[] sectionsAbove = new int[1];
		int[][] counts = new int[1][];
		s.run("switch freecam on, remember how much is drawn", client -> {
			sectionsAbove[0] = client.worldRenderer.getCompletedChunkCount();
			module.setActive(client, true);
		});
		s.waitTicks(10);
		s.screenshot("31_freecam_surface");
		// 30 blocks straight down from the player: inside the ground.
		s.run("put the camera 30 blocks under the player, looking ahead", client ->
				module.placeCamera(client.player.getEyePos().add(0, -30, 0), client.player.getYaw(), 10F));
		s.waitTicks(40);
		s.screenshot("31_freecam_underground_1");
		s.run("fly forward through the rock", client -> {
			counts[0] = new int[40];
			client.options.forwardKey.setPressed(true);
		});
		for (int i = 0; i < 40; i++) {
			int index = i;
			s.run("sample " + i, client -> counts[0][index] = client.worldRenderer.getCompletedChunkCount());
			s.waitTicks(1);
		}
		s.run("stop", client -> client.options.forwardKey.setPressed(false));
		s.screenshot("31_freecam_underground_2");
		s.info("chunk sections drawn", client -> {
			int min = Integer.MAX_VALUE;
			int max = 0;
			for (int count : counts[0]) {
				min = Math.min(min, count);
				max = Math.max(max, count);
			}
			return "before freecam=" + sectionsAbove[0] + ", while flying through rock: min=" + min + " max=" + max;
		});
		s.check("the world keeps being drawn while the camera is inside rock (never fewer than 50 chunk sections)", client -> {
			for (int count : counts[0]) {
				if (count < 50) {
					return false;
				}
			}
			return true;
		});
		s.check("what is drawn does not jump around from tick to tick (no flicker)", client -> {
			for (int i = 1; i < counts[0].length; i++) {
				if (Math.abs(counts[0][i] - counts[0][i - 1]) > Math.max(40, counts[0][i - 1] / 4)) {
					return false;
				}
			}
			return true;
		});

		// Mark what the camera looks at, go back to the body, and the marker must lead there.
		s.run("right click: mark", client -> KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.useKey)));
		s.waitTicks(5);
		s.check("one marker was set", client -> module.getMarkers().size() == 1);
		s.run("leave freecam", client -> module.setActive(client, false));
		s.waitTicks(20);
		s.check("the marker is still there after returning to the body", client -> module.getMarkers().size() == 1);
		s.screenshot("31_freecam_marker_from_body");
		s.run("back into freecam at the same spot, left click on the marker", client -> {
			module.setActive(client, true);
			FreecamModule.Marker marker = module.getMarkers().get(0);
			Vec3d from = Vec3d.ofCenter(marker.pos()).add(0, 0, 3);
			module.placeCamera(from, 180F, 0F);
			KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(client.options.attackKey));
		});
		s.waitTicks(5);
		s.check("left click removed the marker", client -> module.getMarkers().isEmpty());
		s.run("leave freecam", client -> module.setActive(client, false));
		switchWorld(s, WORLD_NAME, true);
	}

	private static void elytraTakeoff(SelfTest.Script s) {
		ElytraTakeoffModule module = module("elytra_takeoff");
		isolate(s, module);
		s.command("gamemode survival @a");
		s.command("item replace entity @a armor.chest with elytra");
		// Rockets in hotbar slot 4 while slot 1 is selected: the module has to switch and switch back.
		s.command("item replace entity @a hotbar.3 with firework_rocket 5");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -40");
		s.waitTicks(15);
		int[] before = new int[1];
		s.run("press the take-off key", client -> {
			before[0] = module.getTakeoffs();
			module.trigger(client);
		});
		s.waitTicks(25);
		s.clearChat();
		s.info("take-off", client -> "gliding=" + client.player.isGliding() + " y=" + client.player.getY()
				+ " rockets=" + client.player.getInventory().getStack(3).getCount()
				+ " selectedSlot=" + client.player.getInventory().getSelectedSlot());
		s.check("the sequence completed", client -> module.getTakeoffs() == before[0] + 1 && !module.isBusy());
		s.check("the player is gliding and has climbed", client -> client.player.isGliding() && client.player.getY() > GROUND_Y + 5);
		s.check("exactly one rocket was used", client -> client.player.getInventory().getStack(3).getCount() == 4);
		s.check("the selected hotbar slot is the first one again", client -> client.player.getInventory().getSelectedSlot() == 0);
		s.screenshot("19_elytra_takeoff");
		s.command("gamemode creative @a");

		// Without rockets: still jump and open the elytra.
		resetPlayer(s);
		s.command("gamemode survival @a");
		s.command("item replace entity @a armor.chest with elytra");
		s.command("tp @a 0.5 " + GROUND_Y + " 0.5 180 -40");
		s.waitTicks(15);
		s.run("press the take-off key with no rockets", client -> {
			before[0] = module.getTakeoffs();
			module.trigger(client);
		});
		s.waitUntil("the elytra opens", client -> client.player.isGliding(), 20);
		s.waitTicks(3);
		s.check("without rockets the sequence still completes", client -> module.getTakeoffs() == before[0] + 1 && !module.isBusy());
		s.command("gamemode creative @a");
	}

	private static void useLookedAtBlock(MinecraftClient client) {
		if (client.crosshairTarget instanceof BlockHitResult hit && client.interactionManager != null) {
			client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
		} else {
			throw new IllegalStateException("not looking at a block");
		}
	}

	@SuppressWarnings("unchecked")
	public static <M extends Module> M module(String id) {
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
	public static void resetPlayer(SelfTest.Script s) {
		s.command("gamemode creative @a");
		s.command("clear @a");
		s.command("effect clear @a");
		s.command("kill @e[type=item]");
		s.command("fill -8 " + (GROUND_Y - 1) + " -8 8 " + (GROUND_Y - 1) + " 8 grass_block");
		// Remove anything an earlier scenario built around the origin.
		s.command("fill -8 -60 -8 8 -50 8 air");
		// "execute in overworld" also brings the player back if a scenario left them in the nether.
		s.command("execute in minecraft:overworld run tp @a 0.5 " + GROUND_Y + " 0.5 180 0");
		s.run("select hotbar slot 1 and clear chat", client -> {
			if (client.player != null) {
				client.player.getInventory().setSelectedSlot(0);
			}
			client.inGameHud.getChatHud().clear(false);
		});
		s.waitTicks(10);
	}

	/** Start of a module scenario: known player state, and only this one module switched on. */
	public static void isolate(SelfTest.Script s, Module only) {
		s.run("enable only " + only.getId(), client -> {
			for (Module module : ModuleRegistry.all()) {
				module.setEnabled(module == only);
			}
		});
		resetPlayer(s);
	}
}

package io.github.autyi6969.qolbundle.selftest;

import io.github.autyi6969.qolbundle.gui.ChatSearchScreen;
import io.github.autyi6969.qolbundle.gui.ModuleListScreen;
import io.github.autyi6969.qolbundle.gui.ModuleSettingsScreen;
import io.github.autyi6969.qolbundle.mixin.ChatHudAccessor;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
import io.github.autyi6969.qolbundle.modules.ArmorHudModule;
import io.github.autyi6969.qolbundle.modules.BreakProgressModule;
import io.github.autyi6969.qolbundle.modules.ChatEnhancementsModule;
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
import io.github.autyi6969.qolbundle.modules.SoundCompassModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
		test.scenario("boot", true, s -> boot(test, s));
		test.scenario("settings_screen", false, Scenarios::settingsScreen);
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
		// Three lines as a chat plugin would send them: someone talking, someone mentioning the
		// player, and the player's own line (which contains the own name but is not a mention).
		s.command(client -> "tellraw @a {\"text\":\"<Bob> hello everyone\"}");
		s.command(client -> "tellraw @a {\"text\":\"<Bob> hey " + client.getSession().getUsername() + " are you there?\"}");
		s.command(client -> "tellraw @a {\"text\":\"<" + client.getSession().getUsername() + "> yes, "
				+ client.getSession().getUsername() + " is here\"}");
		s.waitTicks(10);
		s.check("three lines were recorded", client -> module.getHistory().size() == 3);
		s.check("exactly one of them counts as a mention", client -> module.getMentionCount() == 1);
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
		s.check("the three chat lines came back after rejoining", client -> module.getRestoredLines() == 3);
		s.check("the chat window contains the old line again", client -> chatLines(client).stream().anyMatch(line -> line.contains("hello everyone")));
		s.run("open the chat so the restored lines are visible", client -> client.setScreen(new ChatScreen("", false)));
		s.waitTicks(5);
		s.screenshot("15_chat_restored");
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

	private static void useLookedAtBlock(MinecraftClient client) {
		if (client.crosshairTarget instanceof BlockHitResult hit && client.interactionManager != null) {
			client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
		} else {
			throw new IllegalStateException("not looking at a block");
		}
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
		s.command("kill @e[type=item]");
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
	static void isolate(SelfTest.Script s, Module only) {
		s.run("enable only " + only.getId(), client -> {
			for (Module module : ModuleRegistry.all()) {
				module.setEnabled(module == only);
			}
		});
		resetPlayer(s);
	}
}

package io.github.autyism.qolbundle.selftest;

import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.input.ViewHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
//? if <26.3
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Automated self-test, switched on with the JVM flag {@code -Dqol.selftest=true}
 * ({@code .\gradlew runClient -Pselftest}). Without the flag nothing in this package runs.
 *
 * <p>It is a list of small steps executed one after another on the client tick. Steps are grouped
 * into scenarios; every scenario ends with a PASS or FAIL line in the log. All log lines start
 * with {@code [SelfTest]}. The last line is {@code [SelfTest] DONE}, after which the client stops.
 * Screenshots go to {@code run/screenshots/selftest/}. The scenarios themselves are in {@link Scenarios}.
 */
public final class SelfTest {
	public static final String PREFIX = "[SelfTest] ";
	private static final Logger LOGGER = QoLBundleClient.LOGGER;
	/** Hard limit for the whole run; the outside watcher gives up after 6 minutes. */
	private static final long TIMEOUT_NANOS = 720L * 1_000_000_000L;
	private static final String SCREENSHOT_FOLDER = "selftest";

	private final List<Step> steps = new ArrayList<>();
	private final List<String> failedScenarios = new ArrayList<>();
	private final AtomicInteger pendingScreenshots = new AtomicInteger();
	/** Things to put back when the run ends (game options the test had to change). */
	private final List<Consumer<Minecraft>> cleanups = new ArrayList<>();
	private int index;
	private int ticksInStep;
	private long startNanos = -1;
	private String runningScenario;
	private boolean runningScenarioFailed;
	private int passed;
	private boolean finishing;
	private int finishTicks;
	private boolean done;

	/** True only while the test itself moves the "mouse"; see {@link #moveMouse}. */
	private static boolean testIsMovingMouse;

	private SelfTest() {
	}

	/**
	 * Turns the view the way a mouse movement would. During a self-test the real mouse is ignored
	 * (somebody may be using the computer while the test window is open), so scenarios must use this.
	 */
	public static void moveMouse(Minecraft client, double deltaX, double deltaY) {
		if (client.player == null) {
			return;
		}
		testIsMovingMouse = true;
		try {
			client.player.turn(deltaX, deltaY);
		} finally {
			testIsMovingMouse = false;
		}
	}

	/**
	 * Cuts the real mouse buttons, wheel and keyboard off from the game window for this run.
	 * Somebody may be using the computer while the test window is open; a stray click would
	 * otherwise count as a key press in a scenario. Scenarios press keys through the game's own
	 * key bindings, which does not need these callbacks.
	 */
	private static void ignoreRealInput(Minecraft client) {
		//? if >=26.3 {
		/*// SDL: real key presses, typed text, mouse buttons and the wheel are dropped before they reach the game.
		for (int type : new int[] {org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_DOWN, org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_UP,
				org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_EDITING, org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_INPUT,
				org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN, org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP,
				org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_WHEEL}) {
			org.lwjgl.sdl.SDLEvents.SDL_SetEventEnabled(type, false);
		}
		*///?} else {
		long window = client.getWindow().handle();
		GLFW.glfwSetMouseButtonCallback(window, null);
		GLFW.glfwSetScrollCallback(window, null);
		GLFW.glfwSetKeyCallback(window, null);
		GLFW.glfwSetCharModsCallback(window, null);
		//?}
	}

	public static boolean isRequested() {
		return Boolean.getBoolean("qol.selftest");
	}

	public static void install() {
		SelfTest test = new SelfTest();
		// The test flips module switches and settings; never let that reach the real config file.
		ConfigManager.setSavingSuppressed(true);
		// Swallow real mouse movement for the whole run, so it cannot disturb what the scenarios measure.
		ViewHooks.registerFirst((deltaX, deltaY) -> !testIsMovingMouse);
		Scenarios.build(test);
		ClientTickEvents.END_CLIENT_TICK.register(test::tick);
		LOGGER.info(PREFIX + "enabled, {} step(s) queued", test.steps.size());
	}

	/**
	 * Adds a scenario.
	 *
	 * @param fatal if this scenario fails, nothing after it can work (e.g. the world did not load), so the run ends
	 */
	public void scenario(String name, boolean fatal, Consumer<Script> body) {
		// -Dqol.selftest.only=a,b runs just those scenarios (plus the ones everything depends on).
		String only = System.getProperty("qol.selftest.only", "");
		if (!only.isBlank() && !fatal && !List.of(only.split(",")).contains(name)) {
			return;
		}
		body.accept(new Script(name, fatal));
	}

	/** Registers code that runs once at the very end, whether the run passed, failed or timed out. */
	public void onFinish(Consumer<Minecraft> cleanup) {
		cleanups.add(cleanup);
	}

	private void tick(Minecraft client) {
		if (done) {
			return;
		}
		if (startNanos < 0) {
			startNanos = System.nanoTime();
			clearOldScreenshots(client);
			ignoreRealInput(client);
		}
		if (finishing) {
			tickFinish(client);
			return;
		}
		if (System.nanoTime() - startNanos > TIMEOUT_NANOS) {
			String where = index < steps.size() ? steps.get(index).description : "end";
			failScenario("timeout", "whole run exceeded " + TIMEOUT_NANOS / 1_000_000_000L + " s while at step '" + where + "'");
			finishing = true;
			return;
		}
		if (index >= steps.size()) {
			closeScenario();
			finishing = true;
			return;
		}

		Step step = steps.get(index);
		if (!step.scenario.equals(runningScenario)) {
			closeScenario();
			runningScenario = step.scenario;
			runningScenarioFailed = false;
			LOGGER.info(PREFIX + "BEGIN {}", runningScenario);
		}
		try {
			if (step.action.run(client, ticksInStep)) {
				index++;
				ticksInStep = 0;
			} else {
				ticksInStep++;
			}
		} catch (Throwable t) {
			LOGGER.error(PREFIX + "exception in step '{}'", step.description, t);
			failScenario(step.scenario, "step '" + step.description + "': " + t);
			if (step.fatal) {
				LOGGER.error(PREFIX + "scenario '{}' is required by everything after it, ending the run", step.scenario);
				closeScenario();
				finishing = true;
				return;
			}
			// Skip the rest of the broken scenario.
			while (index < steps.size() && steps.get(index).scenario.equals(step.scenario)) {
				index++;
			}
			ticksInStep = 0;
		}
	}

	private void failScenario(String scenario, String reason) {
		LOGGER.error(PREFIX + "FAIL {}: {}", scenario, reason);
		if (!failedScenarios.contains(scenario)) {
			failedScenarios.add(scenario);
		}
		if (scenario.equals(runningScenario)) {
			runningScenarioFailed = true;
		}
	}

	private void closeScenario() {
		if (runningScenario != null && !runningScenarioFailed) {
			LOGGER.info(PREFIX + "PASS {}", runningScenario);
			passed++;
		}
		runningScenario = null;
	}

	private void tickFinish(Minecraft client) {
		// Screenshots are written on a background thread; wait for them (max 10 s) before stopping.
		if (pendingScreenshots.get() > 0 && finishTicks++ < 200) {
			return;
		}
		done = true;
		for (Consumer<Minecraft> cleanup : cleanups) {
			try {
				cleanup.accept(client);
			} catch (RuntimeException e) {
				LOGGER.error(PREFIX + "cleanup failed", e);
			}
		}
		LOGGER.info(PREFIX + "SUMMARY passed={} failed={} {}", passed, failedScenarios.size(), failedScenarios);
		LOGGER.info(PREFIX + "DONE");
		client.stop();
	}

	private static File screenshotDir(Minecraft client) {
		return new File(new File(client.gameDirectory, Screenshot.SCREENSHOT_DIR), SCREENSHOT_FOLDER);
	}

	private void clearOldScreenshots(Minecraft client) {
		File dir = screenshotDir(client);
		File[] old = dir.listFiles((d, name) -> name.endsWith(".png"));
		if (old != null) {
			for (File file : old) {
				if (!file.delete()) {
					LOGGER.warn(PREFIX + "could not delete old screenshot {}", file.getName());
				}
			}
		}
		if (!dir.isDirectory() && !dir.mkdirs()) {
			LOGGER.warn(PREFIX + "could not create {}", dir);
		}
	}

	@FunctionalInterface
	private interface Action {
		/** @return true when the step is finished, false to be called again next tick */
		boolean run(Minecraft client, int ticksInStep) throws Exception;
	}

	private record Step(String scenario, boolean fatal, String description, Action action) {
	}

	/** The little language scenarios are written in. Every call appends one step. */
	public final class Script {
		private final String scenario;
		private final boolean fatal;

		private Script(String scenario, boolean fatal) {
			this.scenario = scenario;
			this.fatal = fatal;
		}

		private void add(String description, Action action) {
			steps.add(new Step(scenario, fatal, description, action));
		}

		/** Runs code on the client thread. */
		public Script run(String description, Consumer<Minecraft> code) {
			add(description, (client, ticks) -> {
				code.accept(client);
				return true;
			});
			return this;
		}

		public Script waitTicks(int count) {
			add("wait " + count + " ticks", (client, ticks) -> ticks >= count);
			return this;
		}

		/** Waits until the condition holds; fails the scenario when it does not within the timeout. */
		public Script waitUntil(String description, Predicate<Minecraft> condition, int timeoutTicks) {
			add("wait until " + description, (client, ticks) -> {
				if (condition.test(client)) {
					return true;
				}
				if (ticks >= timeoutTicks) {
					throw new IllegalStateException("timed out after " + timeoutTicks + " ticks; current screen: "
							+ (client.screen == null ? "none" : client.screen.getClass().getSimpleName()));
				}
				return false;
			});
			return this;
		}

		/** Runs a command as the integrated server (the self-test world is a single-player world). */
		public Script command(String command) {
			add("command /" + command, (client, ticks) -> {
				IntegratedServer server = client.getSingleplayerServer();
				if (server == null) {
					throw new IllegalStateException("no integrated server");
				}
				// Silent: no "[Server: ...]" lines in the chat, they would clutter the screenshots.
				server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
				return true;
			});
			return this;
		}

		/** Like {@link #command(String)}, for commands that are only known while the test runs. */
		public Script command(Function<Minecraft, String> commandSupplier) {
			add("command (computed)", (client, ticks) -> {
				IntegratedServer server = client.getSingleplayerServer();
				if (server == null) {
					throw new IllegalStateException("no integrated server");
				}
				String command = commandSupplier.apply(client);
				LOGGER.info(PREFIX + "computed command: /{}", command);
				server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
				return true;
			});
			return this;
		}

		/** Empties the chat so that server messages ("game mode updated") are not in the screenshot. */
		public Script clearChat() {
			add("clear chat", (client, ticks) -> {
				client.gui.getChat().clearMessages(false);
				return true;
			});
			return this;
		}

		/** Checks a condition and logs it; a failed check fails the scenario but lets it continue. */
		public Script check(String description, Predicate<Minecraft> condition) {
			add("check " + description, (client, ticks) -> {
				if (condition.test(client)) {
					LOGGER.info(PREFIX + "check ok: {}", description);
				} else {
					failScenario(scenario, "check failed: " + description);
				}
				return true;
			});
			return this;
		}

		/** Writes a value into the log so it can be compared with the screenshot. */
		public Script info(String label, Function<Minecraft, Object> value) {
			add("info " + label, (client, ticks) -> {
				LOGGER.info(PREFIX + "info {}: {}", label, value.apply(client));
				return true;
			});
			return this;
		}

		/** Saves run/screenshots/selftest/NAME.png. Waits a few ticks first so the frame is up to date. */
		public Script screenshot(String name) {
			return screenshot(name, false);
		}

		/** @param keepToasts true when the pop-ups in the corner are what the screenshot is about */
		public Script screenshot(String name, boolean keepToasts) {
			add("screenshot " + name, (client, ticks) -> {
				if (ticks == 0 && !keepToasts) {
					// Pop-ups like "Advancement made!" only hide what the screenshot is about.
					client.getToastManager().clear();
				}
				if (ticks < 6) {
					return false;
				}
				pendingScreenshots.incrementAndGet();
				Screenshot.grab(client.gameDirectory, SCREENSHOT_FOLDER + "/" + name + ".png",
						client.getMainRenderTarget(), 1, message -> {
							pendingScreenshots.decrementAndGet();
							LOGGER.info(PREFIX + "screenshot {}: {}", name, message.getString());
						});
				return true;
			});
			return this;
		}

		/** Saves the contents of some other picture buffer (not the screen) as a PNG next to the screenshots. */
		public Script dump(String name, Function<Minecraft, com.mojang.blaze3d.pipeline.RenderTarget> buffer) {
			add("dump " + name, (client, ticks) -> {
				com.mojang.blaze3d.pipeline.RenderTarget framebuffer = buffer.apply(client);
				if (framebuffer == null) {
					throw new IllegalStateException("nothing to dump for " + name);
				}
				pendingScreenshots.incrementAndGet();
				Screenshot.grab(client.gameDirectory, SCREENSHOT_FOLDER + "/" + name + ".png", framebuffer, 1, message -> {
					pendingScreenshots.decrementAndGet();
					LOGGER.info(PREFIX + "dump {}: {}", name, message.getString());
				});
				return true;
			});
			return this;
		}
	}
}

package io.github.autyi6969.qolbundle.selftest;

import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
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
	private static final long TIMEOUT_NANOS = 330L * 1_000_000_000L;
	private static final String SCREENSHOT_FOLDER = "selftest";

	private final List<Step> steps = new ArrayList<>();
	private final List<String> failedScenarios = new ArrayList<>();
	private final AtomicInteger pendingScreenshots = new AtomicInteger();
	private int index;
	private int ticksInStep;
	private long startNanos = -1;
	private String runningScenario;
	private boolean runningScenarioFailed;
	private int passed;
	private boolean finishing;
	private int finishTicks;
	private boolean done;

	private SelfTest() {
	}

	public static boolean isRequested() {
		return Boolean.getBoolean("qol.selftest");
	}

	public static void install() {
		SelfTest test = new SelfTest();
		// The test flips module switches and settings; never let that reach the real config file.
		ConfigManager.setSavingSuppressed(true);
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
		body.accept(new Script(name, fatal));
	}

	private void tick(MinecraftClient client) {
		if (done) {
			return;
		}
		if (startNanos < 0) {
			startNanos = System.nanoTime();
			clearOldScreenshots(client);
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

	private void tickFinish(MinecraftClient client) {
		// Screenshots are written on a background thread; wait for them (max 10 s) before stopping.
		if (pendingScreenshots.get() > 0 && finishTicks++ < 200) {
			return;
		}
		done = true;
		LOGGER.info(PREFIX + "SUMMARY passed={} failed={} {}", passed, failedScenarios.size(), failedScenarios);
		LOGGER.info(PREFIX + "DONE");
		client.scheduleStop();
	}

	private static File screenshotDir(MinecraftClient client) {
		return new File(new File(client.runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY), SCREENSHOT_FOLDER);
	}

	private void clearOldScreenshots(MinecraftClient client) {
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
		boolean run(MinecraftClient client, int ticksInStep) throws Exception;
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
		public Script run(String description, Consumer<MinecraftClient> code) {
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
		public Script waitUntil(String description, Predicate<MinecraftClient> condition, int timeoutTicks) {
			add("wait until " + description, (client, ticks) -> {
				if (condition.test(client)) {
					return true;
				}
				if (ticks >= timeoutTicks) {
					throw new IllegalStateException("timed out after " + timeoutTicks + " ticks; current screen: "
							+ (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName()));
				}
				return false;
			});
			return this;
		}

		/** Runs a command as the integrated server (the self-test world is a single-player world). */
		public Script command(String command) {
			add("command /" + command, (client, ticks) -> {
				IntegratedServer server = client.getServer();
				if (server == null) {
					throw new IllegalStateException("no integrated server");
				}
				// Silent: no "[Server: ...]" lines in the chat, they would clutter the screenshots.
				server.execute(() -> server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), command));
				return true;
			});
			return this;
		}

		/** Like {@link #command(String)}, for commands that are only known while the test runs. */
		public Script command(Function<MinecraftClient, String> commandSupplier) {
			add("command (computed)", (client, ticks) -> {
				IntegratedServer server = client.getServer();
				if (server == null) {
					throw new IllegalStateException("no integrated server");
				}
				String command = commandSupplier.apply(client);
				LOGGER.info(PREFIX + "computed command: /{}", command);
				server.execute(() -> server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), command));
				return true;
			});
			return this;
		}

		/** Empties the chat so that server messages ("game mode updated") are not in the screenshot. */
		public Script clearChat() {
			add("clear chat", (client, ticks) -> {
				client.inGameHud.getChatHud().clear(false);
				return true;
			});
			return this;
		}

		/** Checks a condition and logs it; a failed check fails the scenario but lets it continue. */
		public Script check(String description, Predicate<MinecraftClient> condition) {
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
		public Script info(String label, Function<MinecraftClient, Object> value) {
			add("info " + label, (client, ticks) -> {
				LOGGER.info(PREFIX + "info {}: {}", label, value.apply(client));
				return true;
			});
			return this;
		}

		/** Saves run/screenshots/selftest/NAME.png. Waits a few ticks first so the frame is up to date. */
		public Script screenshot(String name) {
			add("screenshot " + name, (client, ticks) -> {
				if (ticks == 0) {
					// Pop-ups like "Advancement made!" only hide what the screenshot is about.
					client.getToastManager().clear();
				}
				if (ticks < 6) {
					return false;
				}
				pendingScreenshots.incrementAndGet();
				ScreenshotRecorder.saveScreenshot(client.runDirectory, SCREENSHOT_FOLDER + "/" + name + ".png",
						client.getFramebuffer(), 1, message -> {
							pendingScreenshots.decrementAndGet();
							LOGGER.info(PREFIX + "screenshot {}: {}", name, message.getString());
						});
				return true;
			});
			return this;
		}
	}
}

package io.github.autyism.qolbundle.api;

import io.github.autyism.qolbundle.selftest.SelfTest;

/**
 * Lets a separate mod add modules to QoL Bundle (used by the X-ray add-on, which is kept out of
 * the main jar on purpose). Declare it in the add-on's fabric.mod.json under the entrypoint key
 * {@code "qolbundle"}.
 */
public interface QoLBundleAddon {
	String ENTRYPOINT_KEY = "qolbundle";

	/** Called once while QoL Bundle starts, before the config file is read. Register modules here. */
	void registerModules();

	/** Called only when the self-test is switched on. Add the add-on's scenarios here. */
	default void registerSelfTests(SelfTest test) {
	}
}

package io.github.autyi6969.qolbundle;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QoLBundleClient implements ClientModInitializer {
	public static final String MOD_ID = "qolbundle";
	public static final Logger LOGGER = LoggerFactory.getLogger("QoLBundle");

	@Override
	public void onInitializeClient() {
		LOGGER.info("QoL Bundle initialized");
	}
}

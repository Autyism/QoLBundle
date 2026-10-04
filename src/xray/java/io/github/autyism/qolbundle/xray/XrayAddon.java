package io.github.autyism.qolbundle.xray;

import io.github.autyism.qolbundle.api.QoLBundleAddon;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import io.github.autyism.qolbundle.selftest.Scenarios;
import io.github.autyism.qolbundle.selftest.SelfTest;
import net.minecraft.util.math.BlockPos;

/**
 * The X-ray add-on: a separate mod (own jar, own modid) that adds one module to QoL Bundle.
 * Kept out of the main jar on purpose, so the main mod stays clean for people who do not want it.
 */
public class XrayAddon implements QoLBundleAddon {
	@Override
	public void registerModules() {
		ModuleRegistry.register(new XrayModule());
	}

	@Override
	public void registerSelfTests(SelfTest test) {
		test.scenario("xray", false, s -> {
			XrayModule module = Scenarios.module("xray");
			BlockPos diamond = new BlockPos(2, Scenarios.GROUND_Y - 2, -4);
			BlockPos gold = new BlockPos(-2, Scenarios.GROUND_Y - 3, -5);
			Scenarios.isolate(s, module);
			// Two ores buried under the grass, out of sight.
			s.command("setblock 2 " + (Scenarios.GROUND_Y - 2) + " -4 diamond_ore");
			s.command("setblock -2 " + (Scenarios.GROUND_Y - 3) + " -5 deepslate_gold_ore");
			s.command("tp @a 0.5 " + Scenarios.GROUND_Y + " 0.5 180 30");
			s.waitTicks(80);
			s.info("x-ray outlines", client -> module.getVisible());
			s.check("the buried diamond ore is outlined", client -> module.getVisible().stream().anyMatch(found -> found.pos().equals(diamond)));
			s.check("the buried gold ore is outlined", client -> module.getVisible().stream().anyMatch(found -> found.pos().equals(gold)));
			s.screenshot("20_xray");
			s.command("setblock 2 " + (Scenarios.GROUND_Y - 2) + " -4 dirt");
			s.command("setblock -2 " + (Scenarios.GROUND_Y - 3) + " -5 dirt");
		});
	}
}

package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.IntSetting;

/**
 * Makes dark places fully visible, like a permanent Night Vision potion but without the potion:
 * no status effect, nothing sent to the server. The actual change is in
 * {@link io.github.autyism.qolbundle.mixin.LightmapTextureManagerMixin}.
 */
public class FullbrightModule extends Module {
	private static FullbrightModule instance;

	private final IntSetting strength = add(new IntSetting("strength", 100, 10, 100, "%"));

	public FullbrightModule() {
		super("fullbright", ModuleCategory.TECHNICAL, false);
		instance = this;
	}

	/** Used by the mixin: true when the light map should be brightened. */
	public static boolean isActive() {
		return instance != null && instance.isEnabled() || FreecamModule.wantsFullbright();
	}

	/** Used by the mixin: 0..1, how strongly to brighten. */
	public static float getStrength() {
		if (instance == null || !instance.isEnabled()) {
			return 1F; // lit for the free camera, not by this module's own switch
		}
		return instance.strength.get() / 100F;
	}
}

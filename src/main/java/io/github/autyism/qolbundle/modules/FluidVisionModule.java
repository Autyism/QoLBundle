package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.render.fog.FogData;
import org.jspecify.annotations.Nullable;

/**
 * GREY ZONE (same family as Fullbright): see clearly under water, and see the shapes of blocks
 * while inside lava so you can find the way out. Only the fog the client draws is changed; the
 * actual work is in the two fog mixins.
 */
public class FluidVisionModule extends Module {
	@Nullable
	private static FluidVisionModule instance;

	private final BoolSetting water = add(new BoolSetting("water", true));
	private final BoolSetting lava = add(new BoolSetting("lava", true));
	private final IntSetting lavaDistance = add(new IntSetting("lava_distance", 12, 4, 32));

	private int waterFrames;
	private int lavaFrames;

	public FluidVisionModule() {
		super("fluid_vision", ModuleCategory.GREY, false);
		instance = this;
	}

	/** Frames in which the water / lava fog was changed (for the self-test). */
	public int getWaterFrames() {
		return waterFrames;
	}

	public int getLavaFrames() {
		return lavaFrames;
	}

	/** Called by the water fog mixin after the game has set its own values. */
	public static void adjustWaterFog(FogData data, float viewDistance) {
		FluidVisionModule module = instance;
		if (module == null || !module.isEnabled() || !module.water.get()) {
			return;
		}
		// Push the water fog out to where the normal distance fog begins: in effect, no water fog.
		data.environmentalStart = Math.max(data.environmentalStart, viewDistance - 16F);
		data.environmentalEnd = Math.max(data.environmentalEnd, viewDistance);
		data.skyEnd = data.environmentalEnd;
		data.cloudEnd = data.environmentalEnd;
		module.waterFrames++;
	}

	/** Called by the lava fog mixin after the game has set its own values. */
	public static void adjustLavaFog(FogData data) {
		FluidVisionModule module = instance;
		if (module == null || !module.isEnabled() || !module.lava.get()) {
			return;
		}
		// Some glow stays (you should still notice you are in lava), but blocks show through it.
		data.environmentalStart = Math.max(data.environmentalStart, 1F);
		data.environmentalEnd = Math.max(data.environmentalEnd, module.lavaDistance.get());
		data.skyEnd = data.environmentalEnd;
		data.cloudEnd = data.environmentalEnd;
		module.lavaFrames++;
	}
}

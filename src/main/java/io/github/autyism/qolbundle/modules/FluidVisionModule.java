package io.github.autyism.qolbundle.modules;

import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
//? if >=1.21.6 {
import net.minecraft.client.renderer.fog.FogData;
//?} else {
/*import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
*///?}
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

	//? if >=1.21.6 {
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
	//?} else {
	/*/^* Called by the water fog mixin with the fog the game worked out (one call for the sky, one for the terrain). ^/
	public static FogParameters adjustWaterFog(FogParameters fog, Camera camera, FogRenderer.FogMode mode, float viewDistance) {
		FluidVisionModule module = instance;
		if (module == null || !module.isEnabled() || !module.water.get() || !isWaterFog(fog, camera)) {
			return fog;
		}
		// As above: what is left is the normal distance fog (for the sky it starts right at the eye).
		float start = mode == FogRenderer.FogMode.FOG_SKY ? 0F
				: Math.min(viewDistance - 16F, viewDistance - Mth.clamp(viewDistance / 10F, 4F, 64F));
		module.waterFrames++;
		return new FogParameters(Math.max(fog.start(), start), Math.max(fog.end(), viewDistance), FogShape.CYLINDER,
				fog.red(), fog.green(), fog.blue(), fog.alpha());
	}

	/^* Whether the game used its under-water fog: in water and not blinded (blindness and darkness come first). ^/
	private static boolean isWaterFog(FogParameters fog, Camera camera) {
		if (fog == FogParameters.NO_FOG || camera.getFluidInCamera() != FogType.WATER) {
			return false;
		}
		return !(camera.getEntity() instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)));
	}

	/^* Called by the lava fog mixin with the fog the game worked out (one call for the sky, one for the terrain). ^/
	public static FogParameters adjustLavaFog(FogParameters fog, Camera camera) {
		FluidVisionModule module = instance;
		if (module == null || !module.isEnabled() || !module.lava.get() || fog == FogParameters.NO_FOG
				|| camera.getFluidInCamera() != FogType.LAVA) {
			return fog;
		}
		module.lavaFrames++;
		return new FogParameters(Math.max(fog.start(), 1F), Math.max(fog.end(), module.lavaDistance.get()), fog.shape(),
				fog.red(), fog.green(), fog.blue(), fog.alpha());
	}
	*///?}
}

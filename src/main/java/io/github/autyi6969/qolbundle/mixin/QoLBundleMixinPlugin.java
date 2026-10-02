package io.github.autyi6969.qolbundle.mixin;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Leaves out mixins that would clash with other mods. Mixin crashes the game when an injector
 * targets a method another mod has overwritten, and {@code require = 0} does not prevent that.
 */
public class QoLBundleMixinPlugin implements IMixinConfigPlugin {
	private static final String CULLING_MIXIN = "io.github.autyi6969.qolbundle.mixin.WorldRendererCullingMixin";

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (CULLING_MIXIN.equals(mixinClassName)) {
			return !FabricLoader.getInstance().isModLoaded("sodium");
		}
		return true;
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}

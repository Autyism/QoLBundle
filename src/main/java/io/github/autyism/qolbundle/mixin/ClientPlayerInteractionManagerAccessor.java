package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to how far the block currently being mined is broken (0..1). */
@Mixin(MultiPlayerGameMode.class)
public interface ClientPlayerInteractionManagerAccessor {
	@Accessor("destroyProgress")
	float qolbundle$getCurrentBreakingProgress();

	@Accessor("destroyBlockPos")
	BlockPos qolbundle$getCurrentBreakingPos();
}

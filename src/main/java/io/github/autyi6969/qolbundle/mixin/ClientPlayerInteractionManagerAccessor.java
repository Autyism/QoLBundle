package io.github.autyi6969.qolbundle.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to how far the block currently being mined is broken (0..1). */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
	@Accessor("currentBreakingProgress")
	float qolbundle$getCurrentBreakingProgress();

	@Accessor("currentBreakingPos")
	BlockPos qolbundle$getCurrentBreakingPos();
}

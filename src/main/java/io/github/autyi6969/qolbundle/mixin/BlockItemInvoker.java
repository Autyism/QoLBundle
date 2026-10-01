package io.github.autyi6969.qolbundle.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Placement master: asks a block item which block state it would place (torch on the wall or on
 * the floor, which way the stairs face, ...). The game computes this itself on every placement;
 * the method is just not public.
 */
@Mixin(BlockItem.class)
public interface BlockItemInvoker {
	@Invoker("getPlacementState")
	@Nullable
	BlockState qolbundle$getPlacementState(ItemPlacementContext context);
}

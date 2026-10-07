package io.github.autyism.qolbundle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Recipe helper: one slot of the recipe preview, i.e. the items that are accepted there. */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostSlots$GhostSlot")
public interface CyclingItemAccessor {
	@Accessor("items")
	List<ItemStack> qolbundle$getItems();

	@Accessor("isResultSlot")
	boolean qolbundle$isResultSlot();
}

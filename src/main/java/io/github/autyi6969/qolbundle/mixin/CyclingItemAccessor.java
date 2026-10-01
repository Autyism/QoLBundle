package io.github.autyi6969.qolbundle.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Recipe helper: one slot of the recipe preview, i.e. the items that are accepted there. */
@Mixin(targets = "net.minecraft.client.gui.screen.recipebook.GhostRecipe$CyclingItem")
public interface CyclingItemAccessor {
	@Accessor("items")
	List<ItemStack> qolbundle$getItems();

	@Accessor("isResultSlot")
	boolean qolbundle$isResultSlot();
}

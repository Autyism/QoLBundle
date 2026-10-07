package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recipe helper: the faint preview of a recipe that could not be crafted. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookWidgetAccessor {
	@Accessor("ghostSlots")
	GhostSlots qolbundle$getGhostRecipe();
}

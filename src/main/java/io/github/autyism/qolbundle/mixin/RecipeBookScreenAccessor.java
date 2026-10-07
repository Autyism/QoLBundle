package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recipe helper: the recipe book that belongs to a crafting / furnace screen. */
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
	@Accessor("recipeBookComponent")
	RecipeBookComponent<?> qolbundle$getRecipeBook();
}

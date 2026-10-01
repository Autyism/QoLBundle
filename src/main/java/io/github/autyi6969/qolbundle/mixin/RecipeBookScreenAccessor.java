package io.github.autyi6969.qolbundle.mixin;

import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recipe helper: the recipe book that belongs to a crafting / furnace screen. */
@Mixin(RecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
	@Accessor("recipeBook")
	RecipeBookWidget<?> qolbundle$getRecipeBook();
}

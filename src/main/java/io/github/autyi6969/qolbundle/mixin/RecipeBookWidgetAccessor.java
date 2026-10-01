package io.github.autyi6969.qolbundle.mixin;

import net.minecraft.client.gui.screen.recipebook.GhostRecipe;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recipe helper: the faint preview of a recipe that could not be crafted. */
@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetAccessor {
	@Accessor("ghostRecipe")
	GhostRecipe qolbundle$getGhostRecipe();
}

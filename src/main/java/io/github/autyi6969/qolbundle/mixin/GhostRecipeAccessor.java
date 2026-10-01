package io.github.autyi6969.qolbundle.mixin;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.screen.recipebook.GhostRecipe;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Recipe helper: which slot of the preview shows which items. The values are the game's own
 * (non-public) entries; read them through {@link CyclingItemAccessor}.
 */
@Mixin(GhostRecipe.class)
public interface GhostRecipeAccessor {
	@Accessor("items")
	Reference2ObjectMap<Slot, ?> qolbundle$getItems();
}

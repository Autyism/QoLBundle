package io.github.autyism.qolbundle.mixin;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Recipe helper: which slot of the preview shows which items. The values are the game's own
 * (non-public) entries; read them through {@link CyclingItemAccessor}.
 */
@Mixin(GhostSlots.class)
public interface GhostRecipeAccessor {
	@Accessor("ingredients")
	Reference2ObjectMap<Slot, ?> qolbundle$getItems();
}

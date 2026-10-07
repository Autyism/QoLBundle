package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel sits on the screen; slot positions are relative to it. */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
	@Accessor("leftPos")
	int qolbundle$getX();

	@Accessor("topPos")
	int qolbundle$getY();
}

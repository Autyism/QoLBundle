package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel sits on the screen; slot positions are relative to it. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int qolbundle$getX();

	@Accessor("y")
	int qolbundle$getY();
}

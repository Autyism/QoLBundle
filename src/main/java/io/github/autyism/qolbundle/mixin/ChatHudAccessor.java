package io.github.autyism.qolbundle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

/** Read access to the lines currently in the chat window (newest first). */
@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
	@Accessor("allMessages")
	List<GuiMessage> qolbundle$getMessages();
}

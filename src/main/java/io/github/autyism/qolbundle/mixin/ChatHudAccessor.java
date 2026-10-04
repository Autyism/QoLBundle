package io.github.autyism.qolbundle.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Read access to the lines currently in the chat window (newest first). */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
	@Accessor("messages")
	List<ChatHudLine> qolbundle$getMessages();
}

package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.ChatEnhancementsModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Enhancements hooks. Fabric's message events cannot change player chat lines, and nothing
 * announces "the chat is about to be wiped", so both are done here:
 * every line on its way into the chat window passes through the module (timestamp, mention mark),
 * and the module gets a last look at the chat before a disconnect clears it.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {
	@ModifyVariable(
			//? if >=26.1 {
			/*method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			*///?} else
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true)
	private Component qolbundle$decorate(Component message) {
		return ChatEnhancementsModule.decorate(message);
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void qolbundle$beforeClear(boolean clearHistory, CallbackInfo ci) {
		ChatEnhancementsModule.beforeChatCleared((ChatComponent) (Object) this, clearHistory);
	}
}

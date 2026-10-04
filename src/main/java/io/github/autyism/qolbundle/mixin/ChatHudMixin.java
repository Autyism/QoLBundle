package io.github.autyism.qolbundle.mixin;

import io.github.autyism.qolbundle.modules.ChatEnhancementsModule;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
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
@Mixin(ChatHud.class)
public class ChatHudMixin {
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), argsOnly = true)
	private Text qolbundle$decorate(Text message) {
		return ChatEnhancementsModule.decorate(message);
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void qolbundle$beforeClear(boolean clearHistory, CallbackInfo ci) {
		ChatEnhancementsModule.beforeChatCleared((ChatHud) (Object) this, clearHistory);
	}
}

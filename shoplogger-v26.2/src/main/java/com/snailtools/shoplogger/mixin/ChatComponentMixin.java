package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.qol.ChatStacker;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Stacks repeated chat lines (see ChatStacker). The newest line is allMessages[0]. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

	@Shadow @Final private List<GuiMessage> allMessages;

	@Shadow
	private void refreshTrimmedMessages() {
		throw new AssertionError();
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component shoplogger$stackRepeats(Component message) {
		ChatStacker.Result r = ChatStacker.onMessage(message, allMessages);
		// the earlier copy was taken out of chat: redraw it without that line
		if (r.removed()) refreshTrimmedMessages();
		return r.message();
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("TAIL"))
	private void shoplogger$rememberNewest(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		ChatStacker.added(allMessages.isEmpty() ? null : allMessages.getFirst());
	}
}

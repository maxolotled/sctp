package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.gui.HeldItemPage;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every key press, also with a screen open (for X+I on the item under the mouse, see HeldItemPage). */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void shoplogger$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (HeldItemPage.onKey(action, event)) ci.cancel();
		else if (com.snailtools.shoplogger.storage.StorageKeys.onKey(action, event)) ci.cancel();
	}
}

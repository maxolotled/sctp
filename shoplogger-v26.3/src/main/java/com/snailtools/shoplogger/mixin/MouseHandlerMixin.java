package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.qol.QolHookManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {


    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        if (QolHookManager.onMouseEvent(window, mouseButtonInfo, action)) ci.cancel();
    }
}

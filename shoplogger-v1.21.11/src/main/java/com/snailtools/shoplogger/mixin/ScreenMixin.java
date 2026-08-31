package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.RareRentalHighlighter;
import com.snailtools.shoplogger.qol.QolHookManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        QolHookManager.onScreenRender((Screen) (Object) this, graphics, mouseX, mouseY, a);
        RareRentalHighlighter.getInstance().onScreenRender((Screen) (Object) this, graphics, mouseX, mouseY, a);
    }

}

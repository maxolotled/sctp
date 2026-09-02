package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shoplogger$maybeSuppressScreen(Screen screen, CallbackInfo ci) {
		if (!SilentScreenCoordinator.isArmed() || !(screen instanceof HandledScreen<?> handledScreen)) return;

		// A container the PLAYER is opening themselves — their ender chest via
		// /ec being the common real case, since that has no block interaction
		// at all for any of the usual ender-chest safeguards to catch — can
		// land here at the exact same moment our own silently-armed
		// interaction is in flight. Blindly suppressing it and handing it to
		// whatever's currently armed (the code below, unconditionally, before
		// this check existed) would misattribute a completely unrelated
		// container's contents to whatever shop scan happens to be pending.
		// Ender chests specifically can never legitimately be a shop chest
		// (each player only ever sees their own), so this is a safe,
		// zero-false-positive way to recognize "not ours" regardless of how it
		// was triggered — real or command-opened alike, since both funnel
		// through this exact same setScreen() call either way.
		if (isEnderChestScreen(handledScreen)) {
			SilentScreenCoordinator.yieldToManualOpen();
			return;
		}

		SilentScreenCoordinator.onScreenSuppressed(handledScreen.getScreenHandler());
		ci.cancel();
	}

	private static boolean isEnderChestScreen(HandledScreen<?> screen) {
		return screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("ender chest");
	}
}

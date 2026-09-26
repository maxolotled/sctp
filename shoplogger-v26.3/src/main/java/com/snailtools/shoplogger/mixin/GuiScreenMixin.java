package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * In 26.2, server-driven container screens (e.g. from a
 * ClientboundOpenScreenPacket) no longer go through Minecraft#setScreenAndShow
 * at all — MenuScreens.create()'s registered ScreenConstructor.fromPacket()
 * default method now calls Minecraft.gui.setScreen(Screen) directly instead.
 * This is a real behavior change from 26.1 (confirmed by disassembling both
 * versions' jars), not just a rename, which is why this mixin targets Gui
 * instead of Minecraft here — see shoplogger-v26's MinecraftClientMixin for
 * the 26.1.x equivalent.
 *
 * This is now the ONLY place that suppresses a screen while a silent scan is
 * armed — a since-removed Minecraft#setScreen mixin used to carry the
 * ender-chest carve-out below, but was never actually registered in this
 * tree's mixin config, so it did nothing (2.2). Fixed by moving that check
 * here, where suppression genuinely happens.
 */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shoplogger$maybeSuppressScreen(Screen screen, CallbackInfo ci) {
		if (!SilentScreenCoordinator.isArmed() || !(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

		// A container the PLAYER is opening themselves — their ender chest via
		// /ec being the common real case, since that has no block interaction
		// at all for any of the usual ender-chest safeguards to catch — can
		// land here at the exact same moment our own silently-armed
		// interaction is in flight. Blindly suppressing it and handing it to
		// whatever's currently armed would misattribute a completely unrelated
		// container's contents to whatever shop scan happens to be pending.
		// Ender chests specifically can never legitimately be a shop chest
		// (each player only ever sees their own), so this is a safe,
		// zero-false-positive way to recognize "not ours" regardless of how it
		// was triggered — real or command-opened alike, since both funnel
		// through this exact same setScreen() call either way.
		if (isEnderChestScreen(containerScreen)) {
			SilentScreenCoordinator.yieldToManualOpen();
			return;
		}

		// Same idea for every other container: if its shape doesn't match what
		// the armed silent open should produce (e.g. a 3-row chest when a double
		// chest was clicked, a crafting table, a villager, a server GUI), it
		// isn't ours — let the player see it and release the silent open,
		// rather than logging someone else's items as that shop's stock.
		if (!SilentScreenCoordinator.accepts(containerScreen.getMenu())) {
			SilentScreenCoordinator.yieldToManualOpen();
			return;
		}

		SilentScreenCoordinator.onScreenSuppressed(containerScreen.getMenu());
		ci.cancel();
	}

	private static boolean isEnderChestScreen(AbstractContainerScreen<?> screen) {
		return screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("ender chest");
	}
}

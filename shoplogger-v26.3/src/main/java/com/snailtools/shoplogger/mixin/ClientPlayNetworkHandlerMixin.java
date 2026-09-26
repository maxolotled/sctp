package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks:
 *  1. Redirect the Minecraft#setScreenAndShow(...) call inside onOpenScreen so
 *     that, when SilentScreenCoordinator has a listener armed, the container
 *     GUI is simply never shown (the container menu still gets created/
 *     assigned normally by vanilla code before this call, so slot data still
 *     syncs).
 *  2. After the full inventory sync packet for that screen has been applied,
 *     hand control to the armed listener to read the now-populated slots and
 *     close the (invisible) menu again.
 *
 * Manual/player-initiated container opens are completely unaffected — the
 * redirect only triggers while SilentScreenCoordinator.isArmed() is true,
 * which is only ever set right before *we* trigger a silent interaction.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void shoplogger$onInventorySynced(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		if (SilentScreenCoordinator.isArmed()) {
			SilentScreenCoordinator.onInventorySynced(packet.containerId(), (ClientPacketListener) (Object) this);
		}
	}

	// Commands open server GUIs all the time (/ah, /menu, /shop...) — let the
	// auto-scanner step aside first, same as InteractionMixin. Harmless for
	// the mod's own commands: onPlayerMayOpenScreen() only acts when the
	// scanner itself is armed and isn't mid-result.
	@Inject(method = "sendCommand", at = @At("HEAD"))
	private void shoplogger$onSendCommand(String command, CallbackInfo ci) {
		ShopAutoScanner.getInstance().onPlayerMayOpenScreen();
	}

	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"))
	private void shoplogger$onSendUnattendedCommand(String command, Screen screen, CallbackInfo ci) {
		ShopAutoScanner.getInstance().onPlayerMayOpenScreen();
	}
}

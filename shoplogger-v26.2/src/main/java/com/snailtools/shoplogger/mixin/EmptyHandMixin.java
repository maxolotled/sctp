package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.qol.EmptyHandItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Empty-hand items (see EmptyHandItems): clicks a block with another hotbar
 * slot, then switches straight back — the same trick the shop scanner uses.
 * useItemOn() sends the switched slot to the server before the click, and the
 * game's own next tick sends the original one back before anything renders.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class EmptyHandMixin {

	@Unique
	private int shoplogger$restoreSlot = -1;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void shoplogger$swapToEmptyHand(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		shoplogger$restoreSlot = -1;
		if (hand != InteractionHand.MAIN_HAND) return;
		int slot = EmptyHandItems.slotFor(Minecraft.getInstance(), player.level(), hit.getBlockPos());
		var inv = player.getInventory();
		if (slot < 0 || slot == inv.getSelectedSlot()) return;
		shoplogger$restoreSlot = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void shoplogger$restoreSlot(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (shoplogger$restoreSlot < 0) return;
		player.getInventory().setSelectedSlot(shoplogger$restoreSlot);
		shoplogger$restoreSlot = -1;
	}
}

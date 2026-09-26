package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.ShopAutoScanner;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the auto-scanner whenever the player does something that might open
 * a container screen of its own, so it can step aside first — see
 * ShopAutoScanner.onPlayerMayOpenScreen(). Only interactions that can
 * plausibly open a screen count: releasing a silent open for no reason would
 * pop that shop's screen up in the player's face instead of hiding it.
 *
 * Hooked here on MultiPlayerGameMode rather than through Fabric/NeoForge
 * events so the same file works in every tree. The scanner's own silent
 * useItemOn() call also passes through here and is ignored via
 * isSelfInteracting().
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class InteractionMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void shoplogger$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		var level = player.level();
		var pos = hit.getBlockPos();
		// A block entity covers chests, barrels, shulkers, furnaces, hoppers and
		// signs (plugin shop/menu GUIs); a menu provider covers crafting tables,
		// anvils, looms etc., which have no block entity.
		if (level.getBlockEntity(pos) != null || level.getBlockState(pos).getMenuProvider(level, pos) != null) {
			ShopAutoScanner.getInstance().onPlayerMayOpenScreen();
		}
	}

	@Inject(method = "interact", at = @At("HEAD"))
	private void shoplogger$onInteract(Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		// Villagers, NPCs (plugin shop menus), donkeys, chest boats/minecarts...
		// Item frames and armor stands are clicked constantly around shops and
		// never open a container.
		if (target instanceof ItemFrame || target instanceof ArmorStand) return;
		ShopAutoScanner.getInstance().onPlayerMayOpenScreen();
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void shoplogger$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		// Server menu items (compasses, stars...) are custom-named; ordinary
		// items like food or pearls never open a container.
		if (player.getItemInHand(hand).has(DataComponents.CUSTOM_NAME)) {
			ShopAutoScanner.getInstance().onPlayerMayOpenScreen();
		}
	}
}

package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ShopEntryFactory;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Hold X (Open Item Library) and press I: opens the item page for whatever is
 * in your main hand, or, in an inventory or chest, for the item under the mouse. A rare (matched by name against the rare catalog, the
 * same way listings are) opens its rare page; anything else opens the vanilla
 * item page under the name a shop scan would log it as.
 */
public final class HeldItemPage {

	private HeldItemPage() {}

	private static net.minecraft.client.KeyMapping libraryKey, itemKey;
	private static boolean libraryDown; // tracked from key events, so it also works with a screen open

	/** The library key (X) is held down right now (from key events, so other mods sharing keys don't matter). */
	public static boolean libraryHeld() { return libraryDown; }

	/** Called by each loader once its key mappings exist. */
	public static void setKeys(net.minecraft.client.KeyMapping library, net.minecraft.client.KeyMapping item) {
		libraryKey = library;
		itemKey = item;
	}

	/**
	 * Every key press and release (KeyboardHandlerMixin). In an inventory or chest
	 * screen, X held + I opens the page of the item under the mouse. Returns true
	 * when it did, so the screen doesn't also get the key.
	 */
	public static boolean onKey(int action, net.minecraft.client.input.KeyEvent event) {
		if (libraryKey == null || itemKey == null) return false;
		if (libraryKey.matches(event)) { libraryDown = action != 0; return false; }
		if (action != 1 || !libraryDown || !itemKey.matches(event)) return false;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) return false;
		net.minecraft.world.inventory.Slot slot = hoveredSlot(screen);
		if (slot == null || slot.getItem().isEmpty()) return false;
		ItemStack stack = slot.getItem().copy();
		libraryDown = false;
		if (mc.player != null) mc.player.closeContainer(); // close the chest properly before showing the page
		openFor(mc, stack);
		return true;
	}

	private static net.minecraft.world.inventory.Slot hoveredSlot(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
		try {
			java.lang.reflect.Field f = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
			f.setAccessible(true);
			return (net.minecraft.world.inventory.Slot) f.get(screen);
		} catch (Exception e) {
			return null;
		}
	}

	public static void open(Minecraft mc) {
		if (mc.player == null) return;
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) {
			ChatFormat.send(mc, ChatFormat.INFO, "Hold an item to open its page.");
			return;
		}
		openFor(mc, stack);
	}

	private static void openFor(Minecraft mc, ItemStack stack) {
		String name = ShopEntryFactory.displayNameFor(stack);
		String baseItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		WebDataClient.fetchRareCatalog().handle((rares, ex) -> {
			mc.execute(() -> {
				RareItem rare = findRare(rares, name);
				ItemDetailScreen page = rare != null
						? new ItemDetailScreen(null, rare.name, null, rare.texture, true, rare)
						: new ItemDetailScreen(null, name, baseItem, null, false, null);
				page.huntNote(com.snailtools.shoplogger.hunt.HuntClient.note("item:" + baseItem));
				mc.setScreenAndShow(page);
			});
			return null;
		});
	}

	private static RareItem findRare(List<RareItem> rares, String name) {
		if (rares == null) return null; // catalog unreachable: fall back to the vanilla page
		for (RareItem r : rares) {
			if (MatchUtil.isRareNameMatch(name, r.name)) return r;
		}
		return null;
	}
}

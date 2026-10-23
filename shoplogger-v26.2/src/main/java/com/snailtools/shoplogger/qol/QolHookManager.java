package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.TempScanWaitOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;

public class QolHookManager {

    //lil place I can declare a bunch of hooks and just slot new standalone addons into them
    private static ButterflyGarden butterflyGarden;
    private static QuestHelper questHelper;
    private static StorageSwitcher storageSwitcher;
    private static CreepyCrawlyExchange creepyCrawlyExchange;


    public static void onInit(){
        butterflyGarden = new ButterflyGarden();
        questHelper = new QuestHelper();
        storageSwitcher = new StorageSwitcher();
        creepyCrawlyExchange = new CreepyCrawlyExchange();
    }

    public static void onTick(){
        if(butterflyGarden != null) butterflyGarden.tick();
        if(creepyCrawlyExchange != null) creepyCrawlyExchange.tick();
        com.snailtools.shoplogger.auction.DutchClock.tick();
        com.snailtools.shoplogger.hunt.HuntClient.tick(net.minecraft.client.Minecraft.getInstance());
        com.snailtools.shoplogger.ShopScanner.tick(net.minecraft.client.Minecraft.getInstance());
    }

    /** Returns true if an addon used the click, so the game shouldn't also get it. */
    /**
     * The mouse button in the old numbering (0 left, 1 right, 2 middle). 26.3 moved
     * input to SDL, where left is 1 and right is 3, so compare against
     * InputConstants instead of raw numbers.
     */
    public static int legacyButton(MouseButtonInfo info) {
        int b = info.button();
        if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return 100 + b; // other buttons never match
    }

    public static boolean onMouseEvent(long window, MouseButtonInfo mouseButtonInfo, int action){
        if(storageSwitcher != null && storageSwitcher.onMouseEvent(window, mouseButtonInfo, action)) return true;
        if(butterflyGarden != null) butterflyGarden.onMouseEvent(window, mouseButtonInfo, action);
        if(questHelper != null) questHelper.onMouseEvent(window, mouseButtonInfo, action);
        if(creepyCrawlyExchange != null) creepyCrawlyExchange.onMouseEvent(window, mouseButtonInfo, action);
        return false;
    }

    public static void onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker tickDelta){
        if(butterflyGarden != null) butterflyGarden.onHUDRender(guiGraphics);
        if(questHelper != null) questHelper.onHUDRender(guiGraphics);
        TempScanWaitOverlay.onHudRender(guiGraphics); // TEMPORARY — see CHANGELOG 2.2
        com.snailtools.shoplogger.auction.DutchClock.render(guiGraphics);

    }

    public static void onScreenRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a){
        if(butterflyGarden != null) butterflyGarden.onScreenRender(screen, graphics, mouseX, mouseY, a);
        if(questHelper != null) questHelper.onScreenRender(screen, graphics, mouseX, mouseY, a);
        if(storageSwitcher != null) storageSwitcher.onScreenRender(screen, graphics, mouseX, mouseY, a);
        if(creepyCrawlyExchange != null) creepyCrawlyExchange.onScreenRender(screen, graphics, mouseX, mouseY, a);
        BuyConfirm.onScreenRender(screen, graphics, mouseX, mouseY, a);
        com.snailtools.shoplogger.hunt.HuntNotes.onScreenRender(screen, graphics);
    }

    public static void onKeyEvent(){
        // do resetting ui positions to default coords on certain key combo
    }
}

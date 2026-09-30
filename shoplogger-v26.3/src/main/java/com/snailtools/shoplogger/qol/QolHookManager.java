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


    public static void onInit(){
        butterflyGarden = new ButterflyGarden();
        questHelper = new QuestHelper();
        storageSwitcher = new StorageSwitcher();

    }

    public static void onTick(){
        if(butterflyGarden != null) butterflyGarden.tick();
    }

    /** Returns true if an addon used the click, so the game shouldn't also get it. */
    public static boolean onMouseEvent(long window, MouseButtonInfo mouseButtonInfo, int action){
        if(storageSwitcher != null && storageSwitcher.onMouseEvent(window, mouseButtonInfo, action)) return true;
        if(butterflyGarden != null) butterflyGarden.onMouseEvent(window, mouseButtonInfo, action);
        if(questHelper != null) questHelper.onMouseEvent(window, mouseButtonInfo, action);
        return false;
    }

    public static void onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker tickDelta){
        if(butterflyGarden != null) butterflyGarden.onHUDRender(guiGraphics);
        if(questHelper != null) questHelper.onHUDRender(guiGraphics);
        TempScanWaitOverlay.onHudRender(guiGraphics); // TEMPORARY — see CHANGELOG 2.2

    }

    public static void onScreenRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a){
        if(butterflyGarden != null) butterflyGarden.onScreenRender(screen, graphics, mouseX, mouseY, a);
        if(questHelper != null) questHelper.onScreenRender(screen, graphics, mouseX, mouseY, a);
        if(storageSwitcher != null) storageSwitcher.onScreenRender(screen, graphics, mouseX, mouseY, a);
    }

    public static void onKeyEvent(){
        // do resetting ui positions to default coords on certain key combo
    }
}

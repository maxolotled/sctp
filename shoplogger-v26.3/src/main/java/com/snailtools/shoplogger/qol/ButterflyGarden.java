package com.snailtools.shoplogger.qol;

import com.google.common.primitives.Shorts;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.qol.utils.Util;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import com.snailtools.shoplogger.gui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ButterflyGarden {

    private  final String CONFIG_SCALE_PATH = "Garden/scoreboard-scale";
    private  final String CONFIG_VISIBLE_PATH = "Garden/scoreboard-visible";
    private  final String CONFIG_COMPACT_PATH = "Garden/scoreboard-compact";
    private static final String CONFIG_OFFSET_X_PATH = "Garden/scoreboard-offset-x";
    private static final String CONFIG_OFFSET_Y_PATH = "Garden/scoreboard-offset-y";
    private static final String CONFIG_PINNED_PATH = "Garden/scoreboard-pinned";
    private final int PANEL_MARGIN_RIGHT = 8;
    private final int PANEL_MIN_WIDTH = 118;
    private static final int PANEL_COMPACT_MIN_WIDTH = 78;
    private final int PANEL_PADDING = 7;
    private final int HEADER_HEIGHT = 18;
    private final int ROW_HEIGHT = 19;
    private final int FOOTER_HEIGHT = 19;
    private final int SCOREBOARD_TOGGLE_SIZE = 11;
    private final int SCOREBOARD_TOGGLE_MARGIN = 4;
    private static final int SCOREBOARD_TOGGLE_GAP = 3;
    private final int SHADOW_COLOR = 0x60000000;
    private final int ROW_ALT_COLOR = 0x26000000;
    private final int POINTS_COLOR = 0xFFFFD56A;
    private static int BORDER_COLOR, PANEL_COLOR, HEADER_COLOR, TEXT_COLOR, MUTED_TEXT_COLOR,
            CONTROL_BORDER_COLOR, CONTROL_FILL_COLOR, CONTROL_HOVER_COLOR, CONTROL_ACTIVE_COLOR,
            CONTROL_TRACK_COLOR, DRAG_ACTIVE_COLOR, DRAG_FILL_COLOR,
            SELLABLE_SLOT_HIGHLIGHT_COLOR, SELLABLE_SLOT_BORDER_COLOR, WARNING_BG_COLOR;
    private final int WRONG_OWNER_SLOT_HIGHLIGHT_COLOR = 0x66FFD36A;
    private final int WRONG_OWNER_SLOT_BORDER_COLOR = 0xFFFF7A45;
    private final int EXCHANGE_BUTTON_WIDTH = 70;
    private final int EXCHANGE_BUTTON_HEIGHT = 20;
    private final int EXCHANGE_BUTTON_GAP = 6;
    private final int SCALE_SLIDER_WIDTH = EXCHANGE_BUTTON_WIDTH;
    private final int SCALE_SLIDER_HEIGHT = EXCHANGE_BUTTON_HEIGHT;
    private final int SCALE_TRACK_HEIGHT = 3;
    private final int SCALE_KNOB_WIDTH = 5;
    private final float MIN_HUD_SCALE = 0.5f;
    private final float MAX_HUD_SCALE = 2.0f;
    private final long EXCHANGE_CLICK_WAIT_MILLIS = 5000L;
    private final String OWNER_UUID_DATA = "escargold:owner-uuid";
    private final String BUTTERFLY_GARDEN_LEVEL = "minecraft:butterflygarden";
    private  float HUD_SCALE;
    private  boolean scoreboardVisible;
    private boolean scoreboardVerbose;
    private static float panelOffsetX;
    private static float panelOffsetY;
    private static boolean panelPinned;
    private static final int DRAG_HANDLE_SIZE = 9;
    private static final float HEADER_ICON_MODEL_DATA = 999551.0F;
    private ItemStack headerIcon = ItemStack.EMPTY;
    private int dragHandleX = 0;
    private int dragHandleY = 0;
    private int dragHandleSize = 0;
    private boolean panelDragging = false;
    private float panelDragMouseOffsetX = 0.0F;
    private float panelDragMouseOffsetY = 0.0F;
    private boolean exchangeSelling = false;
    private String currentExchangeType = null;
    private int exchangeButtonX = 0;
    private int exchangeButtonY = 0;
    private int scaleSliderX = 0;
    private int scaleSliderY = 0;
    private boolean scaleSliderDragging = false;
    private int scoreboardToggleX = 0;
    private int scoreboardToggleY = 0;
    private int scoreboardToggleSize = 0;
    private int scoreboardVerboseToggleX = 0;
    private int scoreboardVerboseToggleY = 0;
    private String waitingExchangeType = null;
    private int waitingCountBeforeClick = 0;
    private long exchangeClickWaitUntilMillis = 0L;

    public ButterflyGarden() {
        HUD_SCALE = Config.getOrDefault(CONFIG_SCALE_PATH,  Float.class,1.0F);
        scoreboardVisible = Config.getOrDefault(CONFIG_VISIBLE_PATH,  boolean.class,true);
        scoreboardVerbose = Config.getOrDefault(CONFIG_COMPACT_PATH,  boolean.class,true);
        panelOffsetX = Config.getOrDefault(CONFIG_OFFSET_X_PATH,  Float.class,0.0F);
        panelOffsetY = Config.getOrDefault(CONFIG_OFFSET_Y_PATH,  Float.class,0.0F);
        panelPinned = Config.getOrDefault(CONFIG_PINNED_PATH,  Boolean.class,true);

    }

    public void tick() {
        if (!isInButterflyGardenLevel()) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        if (Minecraft.getInstance().gui.screen() == null && exchangeSelling) {
            exchangeSelling = false;
        }

        if (!exchangeSelling) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null) return;
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) || !isButterflyExchangeScreen(screen)) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        if (hasWrongOwnerInventoryButterfly(screen)) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        if (isWaitingForExchangeUpdate(screen)) return;

        Slot slot = findNextExchangeSlot(screen);
        if (slot == null) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        String clickedType = currentExchangeType;
        int countBeforeClick = clickedType == null ? 0 : countInventoryButterflies(screen, clickedType);
        sendExchangeClickPacket(minecraft, screen.getMenu(), slot);
        waitForExchangeUpdate(clickedType, countBeforeClick);
    }

    public void onScreenRender(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        syncTheme();
        if (!isInButterflyGardenLevel()) {
            scaleSliderDragging = false;
            panelDragging = false;
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> containerScreen && isButterflyExchangeScreen(containerScreen)) {
            renderExchangeControls(containerScreen, gui, mouseX, mouseY);
        } else {
            scaleSliderDragging = false;
            panelDragging = false;
        }
    }

    public void onMouseEvent(long window, MouseButtonInfo button, int action) {
        if (button != null) {
            onMouseEvent(window, QolHookManager.legacyButton(button), action);
        }
    }

    public void onMouseEvent(long window, int button, int action) {
        if (!isInButterflyGardenLevel()) return;

        if (action == 0) {
            if (button == 0 && panelDragging) {
                panelDragging = false;
                savePanelOffset();
            }
            scaleSliderDragging = false;
            return;
        }

        if (action == 1 && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen && isButterflyExchangeScreen(screen)) {
            updateExchangeControlBounds(screen);
            double mouseX = scaledMouseX();
            double mouseY = scaledMouseY();
            updateScoreboardToggleBounds(Minecraft.getInstance(), collectButterflies(Minecraft.getInstance().player.getInventory()));

            if (scoreboardVisible && isInside(mouseX, mouseY, dragHandleX, dragHandleY, dragHandleSize, dragHandleSize)) {
                if (button == 1) {
                    resetPanelOffset();
                    return;
                }

                if (button == 0) {
                    panelPinned = !panelPinned;
                    Config.update(CONFIG_PINNED_PATH, panelPinned);
                    return;
                }
            }

            if (button != 0) return;

            if (isInside(mouseX, mouseY, scoreboardToggleX, scoreboardToggleY, scoreboardToggleSize, scoreboardToggleSize)) {
                scoreboardVisible = !scoreboardVisible;
                Config.update(CONFIG_VISIBLE_PATH, scoreboardVisible);
                return;
            }

            if (scoreboardVisible && isInside(mouseX, mouseY, scoreboardVerboseToggleX, scoreboardVerboseToggleY, scoreboardToggleSize, scoreboardToggleSize)) {
                scoreboardVerbose = !scoreboardVerbose;
                Config.update(CONFIG_COMPACT_PATH, scoreboardVerbose);
                return;
            }

            if (scoreboardVisible && isInside(mouseX, mouseY, scaleSliderX, scaleSliderY, SCALE_SLIDER_WIDTH, SCALE_SLIDER_HEIGHT)) {
                scaleSliderDragging = true;
                updateHudScaleFromMouse(mouseX);
                return;
            }

            if (scoreboardVisible && !panelPinned) {
                HudLayout layout = calculateHudLayout(Minecraft.getInstance(), collectButterflies(Minecraft.getInstance().player.getInventory()));
                if (isInsideScaledPanel(mouseX, mouseY, layout)) {
                    panelDragging = true;
                    panelDragMouseOffsetX = (float) (mouseX / HUD_SCALE - layout.panelX());
                    panelDragMouseOffsetY = (float) (mouseY / HUD_SCALE - layout.panelY());
                    return;
                }
            }

            if (mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                    && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT) {
                if (hasWrongOwnerInventoryButterfly(screen)) {
                    exchangeSelling = false;
                    currentExchangeType = null;
                    clearExchangeWait();
                    return;
                }

                exchangeSelling = !exchangeSelling;
                currentExchangeType = null;
                clearExchangeWait();
            }
        }
    }

    /** Takes the colours from the mod's theme (Settings), so this panel matches every other SCTP screen. */
    private static void syncTheme() {
        BORDER_COLOR = Theme.blend(Theme.LINE, Theme.ACCENT, 0.3f, 0xE3);
        PANEL_COLOR = Theme.alpha(Theme.PANEL, 0xD8);
        HEADER_COLOR = Theme.blend(Theme.PANEL_HI, Theme.ACCENT, 0.25f, 0xE0);
        TEXT_COLOR = Theme.TEXT;
        MUTED_TEXT_COLOR = Theme.MUTED;
        CONTROL_BORDER_COLOR = Theme.alpha(Theme.MUTED, 0xE0);
        CONTROL_FILL_COLOR = Theme.alpha(Theme.PANEL_ALT, 0xD8);
        CONTROL_HOVER_COLOR = Theme.alpha(Theme.PANEL_HI, 0xE0);
        CONTROL_ACTIVE_COLOR = Theme.blend(Theme.PANEL_HI, Theme.ACCENT, 0.45f, 0xE0);
        CONTROL_TRACK_COLOR = Theme.alpha(Theme.SLOT, 0xAA);
        DRAG_ACTIVE_COLOR = Theme.blend(Theme.PANEL_HI, Theme.ACCENT, 0.6f, 0xE0);
        DRAG_FILL_COLOR = Theme.alpha(Theme.PANEL_HI, 0xD8);
        SELLABLE_SLOT_HIGHLIGHT_COLOR = Theme.alpha(Theme.ACCENT, 0x55);
        SELLABLE_SLOT_BORDER_COLOR = Theme.ACCENT_HI;
        WARNING_BG_COLOR = Theme.alpha(Theme.BG_DEEP, 0xCC);
    }

    static {
        syncTheme();
    }

    public void onHUDRender(GuiGraphicsExtractor graphics) {
        syncTheme();
        if (!isInButterflyGardenLevel()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.font == null) return;

        List<ButterflyCount> counts = collectButterflies(minecraft.player.getInventory());
        boolean showToggle = isButterflyExchangeScreen(minecraft.gui.screen());
        updateScoreboardToggleBounds(minecraft, counts);

        if (scoreboardVisible && !counts.isEmpty()) {
            renderButterflyHud(graphics, minecraft, counts, showToggle);
        } else if (showToggle) {
            renderCollapsedScoreboardToggle(graphics, minecraft, counts);
        }
    }

    public boolean isExchangeSelling() {
        return exchangeSelling;
    }

    public void setExchangeSelling(boolean exchangeSelling) {
        this.exchangeSelling = exchangeSelling;
        if (!exchangeSelling) {
            currentExchangeType = null;
            clearExchangeWait();
        }
    }


    private void renderExchangeControls(AbstractContainerScreen<?> screen, GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        updateExchangeControlBounds(screen);
        boolean sellingBlocked = hasWrongOwnerInventoryButterfly(screen);

        Minecraft minecraft = Minecraft.getInstance();
        List<ButterflyCount> counts = minecraft.player == null ? List.of() : collectButterflies(minecraft.player.getInventory());
        if (scoreboardVisible && panelDragging) {
            updatePanelOffsetFromMouse(minecraft, counts, mouseX, mouseY);
        }
        updateScoreboardToggleBounds(minecraft, counts);

        if (scoreboardVisible && scaleSliderDragging) {
            updateHudScaleFromMouse(mouseX);
        } else if (!scoreboardVisible) {
            scaleSliderDragging = false;
        }

        if (scaleSliderDragging) {
            updateHudScaleFromMouse(mouseX);
        }

        renderExchangeSlotHighlights(screen, gui);
        renderExchangeButton(gui, mouseX, mouseY, sellingBlocked);
        renderBlockedSellingWarning(gui, mouseX, mouseY, sellingBlocked);

        if (scoreboardVisible) {
            renderScaleSlider(gui, mouseX, mouseY);
        }
    }

    private void renderExchangeSlotHighlights(AbstractContainerScreen<?> screen, GuiGraphicsExtractor gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        int leftPos = getScreenField(screen, "leftPos");
        int topPos = getScreenField(screen, "topPos");
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (slot.container == minecraft.player.getInventory()) {
                if (hasButterflyOrMothName(stack) && !isOwnedByCurrentPlayer(stack)) {
                    int x = leftPos + slot.x;
                    int y = topPos + slot.y;
                    gui.fill(x, y, x + 16, y + 16, WRONG_OWNER_SLOT_HIGHLIGHT_COLOR);
                    drawSlotBorder(gui, x, y, WRONG_OWNER_SLOT_BORDER_COLOR);
                }
                continue;
            }

            if (!hasButterflyOrMothName(stack)) continue;
            boolean sellable = hasInventoryButterfly(screen, typeName(stack));
            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            if (sellable) {
                gui.fill(x, y, x + 16, y + 16, SELLABLE_SLOT_HIGHLIGHT_COLOR);
                drawSlotBorder(gui, x, y, SELLABLE_SLOT_BORDER_COLOR);
            }
        }
    }

    private void drawSlotBorder(GuiGraphicsExtractor gui, int x, int y, int color) {
        gui.fill(x - 1, y - 1, x + 17, y, color);
        gui.fill(x - 1, y + 16, x + 17, y + 17, color);
        gui.fill(x - 1, y, x, y + 16, color);
        gui.fill(x + 16, y, x + 17, y + 16, color);
    }

    private void updateExchangeControlBounds(AbstractContainerScreen<?> screen) {
        int leftPos = getScreenField(screen, "leftPos");
        int topPos = getScreenField(screen, "topPos");
        int uiWidth = 176;
        for (Slot slot : screen.getMenu().slots) {
            uiWidth = Math.max(uiWidth, slot.x + 16);
        }

        exchangeButtonX = leftPos + uiWidth + EXCHANGE_BUTTON_GAP;
        exchangeButtonY = topPos + 4;
        scaleSliderX = exchangeButtonX;
        scaleSliderY = exchangeButtonY + EXCHANGE_BUTTON_HEIGHT + EXCHANGE_BUTTON_GAP;
    }

    private void renderExchangeButton(GuiGraphicsExtractor gui, int mouseX, int mouseY, boolean disabled) {
        boolean hovered = mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT;
        int fillColor = disabled ? CONTROL_FILL_COLOR : exchangeSelling ? CONTROL_ACTIVE_COLOR : hovered ? CONTROL_HOVER_COLOR : CONTROL_FILL_COLOR;
        int borderColor = disabled ? WRONG_OWNER_SLOT_BORDER_COLOR : hovered ? 0xFFFFFFFF : CONTROL_BORDER_COLOR;
        int textColor = disabled ? 0xFFFFD36A : TEXT_COLOR;

        gui.fill(exchangeButtonX, exchangeButtonY, exchangeButtonX + EXCHANGE_BUTTON_WIDTH, exchangeButtonY + EXCHANGE_BUTTON_HEIGHT, borderColor);
        gui.fill(exchangeButtonX + 1, exchangeButtonY + 1, exchangeButtonX + EXCHANGE_BUTTON_WIDTH - 1, exchangeButtonY + EXCHANGE_BUTTON_HEIGHT - 1, fillColor);

        gui.centeredText(
                Minecraft.getInstance().font,
                exchangeButtonLabel(disabled),
                exchangeButtonX + EXCHANGE_BUTTON_WIDTH / 2,
                exchangeButtonY + (EXCHANGE_BUTTON_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2,
                textColor
        );
    }

    private void renderBlockedSellingWarning(GuiGraphicsExtractor gui, int mouseX, int mouseY, boolean sellingBlocked) {
        if (!sellingBlocked || Minecraft.getInstance().font == null) return;

        String warning = "Remove wrong-owner butterflies to sell";
        int warningX = exchangeButtonX;
        int warningY = scaleSliderY + SCALE_SLIDER_HEIGHT + 4;
        int warningWidth = Minecraft.getInstance().font.width(warning);

        gui.fill(warningX - 2, warningY - 2, warningX + warningWidth + 2, warningY + Minecraft.getInstance().font.lineHeight + 2, WARNING_BG_COLOR);
        gui.text(Minecraft.getInstance().font, warning, warningX, warningY, WRONG_OWNER_SLOT_BORDER_COLOR, true);

        if (mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT) {
            gui.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(warning), mouseX, mouseY);
        }
    }

    private void renderScaleSlider(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, scaleSliderX, scaleSliderY, SCALE_SLIDER_WIDTH, SCALE_SLIDER_HEIGHT);
        int borderColor = hovered || scaleSliderDragging ? 0xFFFFFFFF : CONTROL_BORDER_COLOR;
        int fillColor = scaleSliderDragging ? CONTROL_ACTIVE_COLOR : hovered ? CONTROL_HOVER_COLOR : CONTROL_FILL_COLOR;
        int trackX = scaleSliderX + 7;
        int trackY = scaleSliderY + SCALE_SLIDER_HEIGHT - 5;
        int trackWidth = SCALE_SLIDER_WIDTH - 14;
        int knobX = trackX + Math.round((HUD_SCALE - MIN_HUD_SCALE) / (MAX_HUD_SCALE - MIN_HUD_SCALE) * trackWidth) - SCALE_KNOB_WIDTH / 2;
        String label = String.format(Locale.ROOT, "%.1fx", HUD_SCALE);

        gui.fill(scaleSliderX, scaleSliderY, scaleSliderX + SCALE_SLIDER_WIDTH, scaleSliderY + SCALE_SLIDER_HEIGHT, borderColor);
        gui.fill(scaleSliderX + 1, scaleSliderY + 1, scaleSliderX + SCALE_SLIDER_WIDTH - 1, scaleSliderY + SCALE_SLIDER_HEIGHT - 1, fillColor);
        gui.centeredText(Minecraft.getInstance().font, label, scaleSliderX + SCALE_SLIDER_WIDTH / 2, scaleSliderY + 3, TEXT_COLOR);
        gui.fill(trackX, trackY, trackX + trackWidth, trackY + SCALE_TRACK_HEIGHT, CONTROL_TRACK_COLOR);
        gui.fill(trackX, trackY, knobX + SCALE_KNOB_WIDTH / 2, trackY + SCALE_TRACK_HEIGHT, Theme.ACCENT);
        gui.fill(knobX, trackY - 2, knobX + SCALE_KNOB_WIDTH, trackY + SCALE_TRACK_HEIGHT + 2, 0xFFFFFFFF);
    }

    private Component exchangeButtonLabel(boolean disabled) {
        return Component.literal(disabled ? "Blocked" : exchangeSelling ? "Selling" : "Sell");
    }

    private boolean isButterflyExchangeScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getTitle().getString().contains("Butterfly Exchange");
    }

    private Slot findNextExchangeSlot(AbstractContainerScreen<?> screen) {
        if (currentExchangeType != null && hasInventoryButterfly(screen, currentExchangeType)) {
            Slot currentSlot = findTopExchangeSlot(screen, currentExchangeType);
            if (currentSlot != null) return currentSlot;
        }

        currentExchangeType = null;
        Slot bestExchangeSlot = null;
        String bestTypeName = null;
        int bestPointsEach = Integer.MIN_VALUE;

        for (Slot inventorySlot : screen.getMenu().slots) {
            if (inventorySlot.container != Minecraft.getInstance().player.getInventory()) continue;
            ItemStack stack = inventorySlot.getItem();
            if (!isOwnedButterflyOrMoth(stack)) continue;

            String typeName = typeName(stack);
            Slot exchangeSlot = findTopExchangeSlot(screen, typeName);
            if (exchangeSlot == null) continue;

            int pointsEach = pointsPerItem(stack);
            if (pointsEach > bestPointsEach) {
                bestPointsEach = pointsEach;
                bestTypeName = typeName;
                bestExchangeSlot = exchangeSlot;
            }
        }

        currentExchangeType = bestTypeName;
        return bestExchangeSlot;
    }

    private boolean hasInventoryButterfly(AbstractContainerScreen<?> screen, String typeName) {
        return countInventoryButterflies(screen, typeName) > 0;
    }

    private boolean hasWrongOwnerInventoryButterfly(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (hasButterflyOrMothName(stack) && !isOwnedByCurrentPlayer(stack)) {
                return true;
            }
        }

        return false;
    }

    private int countInventoryButterflies(AbstractContainerScreen<?> screen, String typeName) {
        int count = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != Minecraft.getInstance().player.getInventory()) continue;
            if (matchesOwnedButterflyType(slot.getItem(), typeName)) {
                count += slot.getItem().getCount();
            }
        }
        return count;
    }

    private Slot findTopExchangeSlot(AbstractContainerScreen<?> screen, String typeName) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory()) continue;
            if (matchesExchangeButterflyType(slot.getItem(), typeName)) return slot;
        }
        return null;
    }

    private void sendExchangeClickPacket(Minecraft minecraft, AbstractContainerMenu menu, Slot slot) {
        if (minecraft.getConnection() == null) return;

        Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
        HashedStack carriedItem = HashedStack.create(slot.getItem(), minecraft.getConnection().decoratedHashOpsGenenerator());
        minecraft.getConnection().send(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                Shorts.checkedCast(slot.index),
                (byte) 0,
                ContainerInput.PICKUP,
                changedSlots,
                carriedItem
        ));
    }

    private boolean matchesOwnedButterflyType(ItemStack stack, String typeName) {
        if (stack == null || stack.isEmpty() || !isOwnedButterflyOrMoth(stack)) return false;

        String stackName = normalizeName(typeName(stack));
        String targetName = normalizeName(typeName);
        return stackName.equals(targetName)
                || stackName.contains(targetName)
                || targetName.contains(stackName);
    }

    private boolean matchesExchangeButterflyType(ItemStack stack, String typeName) {
        if (stack == null || stack.isEmpty() || !hasButterflyOrMothName(stack)) return false;

        String stackName = normalizeName(typeName(stack));
        String targetName = normalizeName(typeName);
        return stackName.equals(targetName)
                || stackName.contains(targetName)
                || targetName.contains(stackName);
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void updateHudScaleFromMouse(double mouseX) {
        int trackX = scaleSliderX + 7;
        int trackWidth = SCALE_SLIDER_WIDTH - 14;
        float percent = clamp((float) ((mouseX - trackX) / trackWidth), 0.0f, 1.0f);
        HUD_SCALE = MIN_HUD_SCALE + percent * (MAX_HUD_SCALE - MIN_HUD_SCALE);
        Config.update(CONFIG_SCALE_PATH, HUD_SCALE);
    }

    private double scaledMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private double scaledMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isInsideScaledPanel(double mouseX, double mouseY, HudLayout layout) {
        int panelX = Math.round(layout.panelX() * HUD_SCALE);
        int panelY = Math.round(layout.panelY() * HUD_SCALE);
        int panelWidth = Math.round(layout.panelWidth() * HUD_SCALE);
        int panelHeight = Math.round(layout.panelHeight() * HUD_SCALE);
        return isInside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int getScreenField(AbstractContainerScreen<?> screen, String name) {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return (int) field.get(screen);
        } catch (Exception exception) {
            return 0;
        }
    }

    private List<ButterflyCount> collectButterflies(Inventory inventory) {
        List<ButterflyCount> counts = new ArrayList<>();

        for (ItemStack stack : inventory) {
            if (!isOwnedButterflyOrMoth(stack)) continue;

            String typeName = typeName(stack);
            ButterflyCount existing = findCount(counts, typeName);
            if (existing == null) {
                ItemStack displayStack = stack.copy();
                displayStack.setCount(1);
                counts.add(new ButterflyCount(typeName, displayStack, stack.getCount(), pointsPerItem(stack)));
            } else {
                existing.count += stack.getCount();
            }
        }

        counts.sort(Comparator.comparing(ButterflyCount::name, String.CASE_INSENSITIVE_ORDER));
        return counts;
    }

    private ButterflyCount findCount(List<ButterflyCount> counts, String typeName) {
        for (ButterflyCount count : counts) {
            if (count.name.equals(typeName)) {
                return count;
            }
        }
        return null;
    }

    private boolean isOwnedButterflyOrMoth(ItemStack stack) {
        return hasButterflyOrMothName(stack) && isOwnedByCurrentPlayer(stack);
    }

    private boolean hasButterflyOrMothName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCustomName() == null) return false;

        String name = stack.getCustomName().getString();
        return name.contains("Moth")
                || (name.contains("Butterfly") && !name.contains("Net"))
                || name.contains("Cabbage")
                || name.contains("Morpho")
                || name.contains("Swallowtail");
    }

    private boolean isOwnedByCurrentPlayer(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        StringTag owner = Util.getCustomDataVar(stack, OWNER_UUID_DATA, StringTag.class);
        if (owner == null) return false;

        String playerUuid = minecraft.player.getUUID().toString().toLowerCase(Locale.ROOT);
        return owner.value().toLowerCase(Locale.ROOT).equals(playerUuid);
    }

    private boolean isInButterflyGardenLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && BUTTERFLY_GARDEN_LEVEL.equals(minecraft.player.level().dimension().identifier().toString());
    }

    private int pointsPerItem(ItemStack stack) {
        Component customName = stack.getCustomName();
        if (customName == null) return 0;

        String name = customName.getString();
        if (name.contains("Moth")) return 20;
        if (name.contains("Cabbage") || name.contains("Morpho")) return 1;
        return 2;
    }

    private String typeName(ItemStack stack) {
        Component customName = stack.getCustomName();
        if (customName != null && !customName.getString().isBlank()) {
            return customName.getString();
        }
        return stack.getHoverName().getString();
    }

    private HudLayout calculateHudLayout(Minecraft minecraft, List<ButterflyCount> counts) {
        int totalPoints = 0;
        int maxNameWidth = 22 + minecraft.font.width("Butterfly Garden");
        int maxCountWidth = minecraft.font.width("x999");

        for (ButterflyCount count : counts) {
            totalPoints += count.count * count.pointsEach;
            maxNameWidth = Math.max(maxNameWidth, minecraft.font.width(count.name));
            maxCountWidth = Math.max(maxCountWidth, minecraft.font.width("x" + count.count));
        }

        int screenWidth = Math.round(minecraft.getWindow().getGuiScaledWidth() / HUD_SCALE);
        int screenHeight = Math.round(minecraft.getWindow().getGuiScaledHeight() / HUD_SCALE);
        int panelMarginRight = Math.round(PANEL_MARGIN_RIGHT / HUD_SCALE);
        int contentWidth = scoreboardVerbose
                ? 16 + 6 + maxNameWidth + 10 + maxCountWidth
                : 16 + 6 + maxCountWidth;
        int titleControlsWidth = 22 + minecraft.font.width("Butterfly Garden") + PANEL_PADDING * 2
                + SCOREBOARD_TOGGLE_SIZE * 2 + scoreboardToggleGap() + scoreboardToggleMargin();
        int minPanelWidth = scoreboardVerbose
                ? PANEL_MIN_WIDTH
                : Math.max(PANEL_COMPACT_MIN_WIDTH, titleControlsWidth);
        int maxPanelWidth = Math.max(minPanelWidth, screenWidth - Math.round(16 / HUD_SCALE));
        int panelWidth = Math.min(maxPanelWidth, Math.max(minPanelWidth, PANEL_PADDING * 2 + contentWidth));
        int panelHeight = HEADER_HEIGHT + PANEL_PADDING + counts.size() * ROW_HEIGHT + FOOTER_HEIGHT + PANEL_PADDING;
        int defaultPanelX = screenWidth - panelMarginRight - panelWidth;
        int defaultPanelY = Math.max(4, screenHeight / 2 - panelHeight / 2);
        int panelX = clampInt(defaultPanelX + Math.round(panelOffsetX), 0, Math.max(0, screenWidth - panelWidth));
        int panelY = clampInt(defaultPanelY + Math.round(panelOffsetY), 0, Math.max(0, screenHeight - panelHeight));

        return new HudLayout(panelX, panelY, panelWidth, panelHeight, totalPoints);
    }

    private void updateScoreboardToggleBounds(Minecraft minecraft, List<ButterflyCount> counts) {
        HudLayout layout = calculateHudLayout(minecraft, counts);
        scoreboardToggleSize = Math.max(8, Math.round(SCOREBOARD_TOGGLE_SIZE * HUD_SCALE));
        int toggleMargin = SCOREBOARD_TOGGLE_MARGIN;
        int toggleGap = SCOREBOARD_TOGGLE_GAP;
        scoreboardToggleX = Math.round((layout.panelX() + layout.panelWidth() - toggleMargin - SCOREBOARD_TOGGLE_SIZE) * HUD_SCALE);
        scoreboardToggleY = Math.round((layout.panelY() + toggleMargin) * HUD_SCALE);
        scoreboardVerboseToggleX = Math.round((layout.panelX() + layout.panelWidth() - toggleMargin - (SCOREBOARD_TOGGLE_SIZE * 2) - toggleGap) * HUD_SCALE);
        scoreboardVerboseToggleY = scoreboardToggleY;
        dragHandleSize = Math.max(7, Math.round(DRAG_HANDLE_SIZE * HUD_SCALE));
        dragHandleX = Math.round(layout.panelX() * HUD_SCALE);
        dragHandleY = Math.round(layout.panelY() * HUD_SCALE);
    }

    private void updatePanelOffsetFromMouse(Minecraft minecraft, List<ButterflyCount> counts, int mouseX, int mouseY) {
        HudLayout currentLayout = calculateHudLayout(minecraft, counts);
        int screenWidth = Math.round(minecraft.getWindow().getGuiScaledWidth() / HUD_SCALE);
        int screenHeight = Math.round(minecraft.getWindow().getGuiScaledHeight() / HUD_SCALE);
        int panelMarginRight = Math.round(PANEL_MARGIN_RIGHT / HUD_SCALE);
        int defaultPanelX = screenWidth - panelMarginRight - currentLayout.panelWidth();
        int defaultPanelY = Math.max(4, screenHeight / 2 - currentLayout.panelHeight() / 2);
        int targetPanelX = Math.round(mouseX / HUD_SCALE - panelDragMouseOffsetX);
        int targetPanelY = Math.round(mouseY / HUD_SCALE - panelDragMouseOffsetY);
        int clampedPanelX = clampInt(targetPanelX, 0, Math.max(0, screenWidth - currentLayout.panelWidth()));
        int clampedPanelY = clampInt(targetPanelY, 0, Math.max(0, screenHeight - currentLayout.panelHeight()));

        panelOffsetX = clampedPanelX - defaultPanelX;
        panelOffsetY = clampedPanelY - defaultPanelY;
    }

    private void savePanelOffset() {
        Config.update(CONFIG_OFFSET_X_PATH, panelOffsetX);
        Config.update(CONFIG_OFFSET_Y_PATH, panelOffsetY);
    }

    private void resetPanelOffset() {
        panelDragging = false;
        panelOffsetX = 0.0F;
        panelOffsetY = 0.0F;
        savePanelOffset();
    }


    private void renderButterflyHud(GuiGraphicsExtractor graphics, Minecraft minecraft, List<ButterflyCount> counts, boolean showToggle) {
        HudLayout layout = calculateHudLayout(minecraft, counts);
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();

        graphics.pose().pushMatrix();
        graphics.pose().scale(HUD_SCALE, HUD_SCALE);

        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth + 2, panelY + panelHeight + 2, SHADOW_COLOR);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BORDER_COLOR);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, PANEL_COLOR);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + HEADER_HEIGHT, HEADER_COLOR);

        int titleX = panelX + PANEL_PADDING;
        int titleY = panelY + (HEADER_HEIGHT - minecraft.font.lineHeight) / 2;

        if (showToggle) {
            renderDragHandle(graphics, panelX, panelY);
        }
        graphics.item(headerIcon(), titleX + 2, panelY + 1);
        graphics.text(minecraft.font, "Butterfly Garden", titleX + 22, titleY, TEXT_COLOR, true);

        if (showToggle) {
            int toggleMargin = SCOREBOARD_TOGGLE_MARGIN;
            int toggleGap = SCOREBOARD_TOGGLE_GAP;
            renderVerboseScoreboardToggle(graphics, minecraft, panelX + panelWidth - toggleMargin - (SCOREBOARD_TOGGLE_SIZE * 2) - toggleGap, panelY + toggleMargin);
            renderScoreboardToggle(graphics, minecraft, panelX + panelWidth - SCOREBOARD_TOGGLE_MARGIN - SCOREBOARD_TOGGLE_SIZE, panelY + SCOREBOARD_TOGGLE_MARGIN);
        }

        int rowY = panelY + HEADER_HEIGHT + PANEL_PADDING;
        for (int i = 0; i < counts.size(); i++) {
            ButterflyCount count = counts.get(i);
            if (i % 2 == 1) {
                graphics.fill(panelX + 2, rowY, panelX + panelWidth - 2, rowY + ROW_HEIGHT, ROW_ALT_COLOR);
            }

            int iconX = panelX + PANEL_PADDING;
            int iconY = rowY;
            graphics.item(count.displayStack, iconX, iconY);
            int nameX = iconX + 22;
            int textY = rowY + (ROW_HEIGHT - minecraft.font.lineHeight) / 2 + 1;
            int countTextWidth = minecraft.font.width("x" + count.count);
            int countX = panelX + panelWidth - PANEL_PADDING - countTextWidth;
            if (scoreboardVerbose) {
                String displayName = fitText(minecraft, count.name, Math.max(12, countX - nameX - 6));
                graphics.text(minecraft.font, displayName, nameX, textY, MUTED_TEXT_COLOR, false);
            }
            graphics.text(minecraft.font, "x" + count.count, countX, textY, TEXT_COLOR, true);

            rowY += ROW_HEIGHT;
        }

        int footerY = rowY + 3;
        graphics.fill(panelX + PANEL_PADDING, footerY - 4, panelX + panelWidth - PANEL_PADDING, footerY - 3, 0x55FFFFFF);

        String label = "Points";
        String value = Integer.toString(layout.totalPoints());
        graphics.text(minecraft.font, label, panelX + PANEL_PADDING, footerY, TEXT_COLOR, true);
        graphics.text(
                minecraft.font,
                value,
                panelX + panelWidth - PANEL_PADDING - minecraft.font.width(value),
                footerY,
                POINTS_COLOR,
                true
        );

        graphics.pose().popMatrix();
    }

    private void renderCollapsedScoreboardToggle(GuiGraphicsExtractor graphics, Minecraft minecraft, List<ButterflyCount> counts) {
        HudLayout layout = calculateHudLayout(minecraft, counts);

        graphics.pose().pushMatrix();
        graphics.pose().scale(HUD_SCALE, HUD_SCALE);
        renderScoreboardToggle(
                graphics,
                minecraft,
                layout.panelX() + layout.panelWidth() - SCOREBOARD_TOGGLE_MARGIN - SCOREBOARD_TOGGLE_SIZE,
                layout.panelY() + SCOREBOARD_TOGGLE_MARGIN
        );
        graphics.pose().popMatrix();
    }

    private int scoreboardToggleMargin() {
        return SCOREBOARD_TOGGLE_MARGIN;
    }

    private int scoreboardToggleGap() {
        return SCOREBOARD_TOGGLE_GAP;
    }


    private void renderScoreboardToggle(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
        graphics.fill(x, y, x + SCOREBOARD_TOGGLE_SIZE, y + SCOREBOARD_TOGGLE_SIZE, CONTROL_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + SCOREBOARD_TOGGLE_SIZE - 1, y + SCOREBOARD_TOGGLE_SIZE - 1, scoreboardVisible ? CONTROL_FILL_COLOR : CONTROL_HOVER_COLOR);
        int midY = y + SCOREBOARD_TOGGLE_SIZE / 2;
        graphics.fill(x + 3, midY, x + SCOREBOARD_TOGGLE_SIZE - 3, midY + 1, TEXT_COLOR);
        if (!scoreboardVisible) {
            int midX = x + SCOREBOARD_TOGGLE_SIZE / 2;
            graphics.fill(midX, y + 3, midX + 1, y + SCOREBOARD_TOGGLE_SIZE - 3, TEXT_COLOR);
        }
    }

    private void renderDragHandle(GuiGraphicsExtractor graphics, int x, int y) {
        int fillColor = panelDragging || !panelPinned ? DRAG_ACTIVE_COLOR : DRAG_FILL_COLOR;
        graphics.fill(x, y, x + DRAG_HANDLE_SIZE, y + DRAG_HANDLE_SIZE, CONTROL_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + DRAG_HANDLE_SIZE - 1, y + DRAG_HANDLE_SIZE - 1, fillColor);
        String icon = panelPinned ? "\uD83D\uDD12" : "\uD83D\uDD13";
        int iconX = x + (DRAG_HANDLE_SIZE - Minecraft.getInstance().font.width(icon)) / 2;
        int iconY = y + (DRAG_HANDLE_SIZE - Minecraft.getInstance().font.lineHeight) / 2;
        graphics.text(Minecraft.getInstance().font, icon, iconX, iconY, TEXT_COLOR, false);
    }

    private void renderVerboseScoreboardToggle(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
        graphics.fill(x, y, x + SCOREBOARD_TOGGLE_SIZE, y + SCOREBOARD_TOGGLE_SIZE, CONTROL_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + SCOREBOARD_TOGGLE_SIZE - 1, y + SCOREBOARD_TOGGLE_SIZE - 1, scoreboardVerbose ? CONTROL_ACTIVE_COLOR : CONTROL_FILL_COLOR);
        int iconColor = scoreboardVerbose ? TEXT_COLOR : MUTED_TEXT_COLOR;
        int dotX = x + 3;
        int lineX = x + 5;
        int firstY = y + 3;
        int secondY = y + 7;

        graphics.fill(dotX, firstY, dotX + 1, firstY + 1, iconColor);
        graphics.fill(lineX, firstY, x + SCOREBOARD_TOGGLE_SIZE - 3, firstY + 1, iconColor);
        graphics.fill(dotX, secondY, dotX + 1, secondY + 1, iconColor);
        graphics.fill(lineX, secondY, x + SCOREBOARD_TOGGLE_SIZE - 3, secondY + 1, iconColor);
    }

    private String fitText(Minecraft minecraft, String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;

        String suffix = "...";
        int suffixWidth = minecraft.font.width(suffix);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = builder.toString() + text.charAt(i);
            if (minecraft.font.width(candidate) + suffixWidth > maxWidth) break;
            builder.append(text.charAt(i));
        }
        return builder + suffix;
    }

    private ItemStack headerIcon() {
        if (headerIcon.isEmpty()) {
            headerIcon = createHeaderIcon();
        }
        return headerIcon;
    }

    private ItemStack createHeaderIcon() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(HEADER_ICON_MODEL_DATA), List.of(), List.of(), List.of()));
        return stack;
    }

    private boolean isWaitingForExchangeUpdate(AbstractContainerScreen<?> screen) {
        if (waitingExchangeType == null) return false;

        if (countInventoryButterflies(screen, waitingExchangeType) < waitingCountBeforeClick) {
            clearExchangeWait();
            return false;
        }

        if (System.currentTimeMillis() >= exchangeClickWaitUntilMillis) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return true;
        }

        return true;
    }

    private void waitForExchangeUpdate(String clickedType, int countBeforeClick) {
        if (clickedType == null) {
            clearExchangeWait();
            return;
        }

        waitingExchangeType = clickedType;
        waitingCountBeforeClick = countBeforeClick;
        exchangeClickWaitUntilMillis = System.currentTimeMillis() + EXCHANGE_CLICK_WAIT_MILLIS;
    }

    private void clearExchangeWait() {
        waitingExchangeType = null;
        waitingCountBeforeClick = 0;
        exchangeClickWaitUntilMillis = 0L;
    }

    private static final class ButterflyCount {
        private final String name;
        private final ItemStack displayStack;
        private final int pointsEach;
        private int count;

        private ButterflyCount(String name, ItemStack displayStack, int count, int pointsEach) {
            this.name = name;
            this.displayStack = displayStack;
            this.count = count;
            this.pointsEach = pointsEach;
        }

        private String name() {
            return name;
        }
    }

    private record HudLayout(int panelX, int panelY, int panelWidth, int panelHeight, int totalPoints) {
    }


}

package com.snailtools.shoplogger.qol;


import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.qol.utils.Util;
import net.minecraft.client.Minecraft;
import com.snailtools.shoplogger.gui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestHelper {

    private static final String CONFIG_SCALE_PATH = "Quest/scale";
    private static final String CONFIG_VISIBLE_PATH = "Quest/visible";
    private static final String CONFIG_OFFSET_X_PATH = "Quest/offset-x";
    private static final String CONFIG_OFFSET_Y_PATH = "Quest/offset-y";
    private static final String CONFIG_PINNED_PATH = "Quest/pinned";
    private static final String CONFIG_COMPACT_PATH = "Quest/compact";
    private static final String CONFIG_OPACITY_PATH = "Quest/opacity";
    /** How see-through the panel's background is (the text always stays readable): 0.1 to 1. */
    private static float PANEL_OPACITY;
    private static final float MIN_OPACITY = 0.1f;
    private static float HUD_SCALE;
    private static boolean questHelperVisible;
    private static float panelOffsetX;
    private static float panelOffsetY;
    private static boolean panelPinned;
    private static boolean questCompact;

    private static final String QUEST_UUID_DATA = "quests:uuid";
    private static final String BUTTERFLY_GARDEN_LEVEL = "minecraft:butterflygarden";
    private static final int PANEL_MARGIN_RIGHT = 8;
    private static final int PANEL_MIN_WIDTH = 130;
    private static final int PANEL_PADDING = 7;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 14;
    private static final int TOGGLE_SIZE = 11;
    private static final int TOGGLE_MARGIN = 4;
    private static final int DRAG_HANDLE_SIZE = 9;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d[\\d,]*");
    private static final int CONTROL_GAP = 6;
    private static final int SCALE_SLIDER_WIDTH = 70;
    private static final int SCALE_SLIDER_HEIGHT = 20;
    private static final int SCALE_TRACK_HEIGHT = 3;
    private static final int SCALE_KNOB_WIDTH = 5;
    private static final float MIN_HUD_SCALE = 0.5f;
    private static final float MAX_HUD_SCALE = 2.0f;
    private static final int SHADOW_COLOR = 0x60000000;
    private static final int ROW_ALT_COLOR = 0x24000000;
    private static int BORDER_COLOR, PANEL_COLOR, HEADER_COLOR, TEXT_COLOR, MUTED_TEXT_COLOR,
            CONTROL_BORDER_COLOR, CONTROL_FILL_COLOR, CONTROL_HOVER_COLOR, CONTROL_ACTIVE_COLOR,
            CONTROL_TRACK_COLOR, DRAG_ACTIVE_COLOR, DRAG_FILL_COLOR;

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
    }

    static {
        syncTheme();
    }

    private int toggleX = 0;
    private int toggleY = 0;
    private int toggleSize = 0;
    private int compactToggleX = 0;
    private int compactToggleY = 0;
    private int scaleSliderX = 0;
    private int scaleSliderY = 0;
    private boolean scaleSliderDragging = false;
    private boolean opacitySliderDragging = false;
    private int dragHandleX = 0;
    private int dragHandleY = 0;
    private int dragHandleSize = 0;
    private boolean panelDragging = false;
    private float panelDragMouseOffsetX = 0.0F;
    private float panelDragMouseOffsetY = 0.0F;

    public QuestHelper() {
        HUD_SCALE = Config.getOrDefault(CONFIG_SCALE_PATH,  Float.class,1.0F);
        questHelperVisible = Config.getOrDefault(CONFIG_VISIBLE_PATH,  boolean.class,true);
        panelOffsetX = Config.getOrDefault(CONFIG_OFFSET_X_PATH,  Float.class,0.0F);
        panelOffsetY = Config.getOrDefault(CONFIG_OFFSET_Y_PATH,  Float.class,0.0F);
        panelPinned = Config.getOrDefault(CONFIG_PINNED_PATH,  Boolean.class,true);
        questCompact = Config.getOrDefault(CONFIG_COMPACT_PATH,  Boolean.class,false);
        PANEL_OPACITY = clamp(Config.getOrDefault(CONFIG_OPACITY_PATH, Float.class, 1.0F), MIN_OPACITY, 1.0F);
    }

    public void onScreenRender(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        syncTheme();
        if (!isQuestScreen(screen)) {
            scaleSliderDragging = false;
            opacitySliderDragging = false;
            panelDragging = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.font == null || isInButterflyGardenLevel()) return;

        QuestDisplay questDisplay = questDisplayForCurrentPaper(minecraft);
        if (questHelperVisible && panelDragging) {
            updatePanelOffsetFromMouse(minecraft, questDisplay, mouseX, mouseY);
        }
        updateControlBounds(minecraft, questDisplay);
        updateScaleControlBounds(screen);

        if (questHelperVisible && scaleSliderDragging) {
            updateHudScaleFromMouse(mouseX);
        } else if (!questHelperVisible) {
            scaleSliderDragging = false;
        }
        if (questHelperVisible && opacitySliderDragging) {
            updateOpacityFromMouse(mouseX);
        } else if (!questHelperVisible) {
            opacitySliderDragging = false;
        }

        renderQuestControls(gui, minecraft, mouseX, mouseY);
    }

    public void onMouseEvent(long window, MouseButtonInfo button, int action) {
        if (button != null) {
            onMouseEvent(window, QolHookManager.legacyButton(button), action);
        }
    }

    public void onMouseEvent(long window, int button, int action) {
        if (action == 0) {
            if (button == 0 && panelDragging) {
                panelDragging = false;
                savePanelOffset();
            }
            scaleSliderDragging = false;
            opacitySliderDragging = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isQuestScreen(minecraft.gui.screen()) || isInButterflyGardenLevel()) return;

        QuestDisplay questDisplay = questDisplayForCurrentPaper(minecraft);
        List<Component> lines = visibleQuestLines(questDisplay);
        updateControlBounds(minecraft, questDisplay);
        updateScaleControlBounds(minecraft.gui.screen());

        double mouseX = scaledMouseX();
        double mouseY = scaledMouseY();

        if (questHelperVisible && isInside(mouseX, mouseY, dragHandleX, dragHandleY, dragHandleSize, dragHandleSize)) {
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

        if (isInside(mouseX, mouseY, toggleX, toggleY, toggleSize, toggleSize)) {
            questHelperVisible = !questHelperVisible;
            Config.update(CONFIG_VISIBLE_PATH, questHelperVisible);
            return;
        }

        if (questHelperVisible && isInside(mouseX, mouseY, compactToggleX, compactToggleY, toggleSize, toggleSize)) {
            questCompact = !questCompact;
            Config.update(CONFIG_COMPACT_PATH, questCompact);
            return;
        }

        if (questHelperVisible && isInside(mouseX, mouseY, scaleSliderX, scaleSliderY, SCALE_SLIDER_WIDTH, SCALE_SLIDER_HEIGHT)) {
            scaleSliderDragging = true;
            updateHudScaleFromMouse(mouseX);
            return;
        }

        if (questHelperVisible && isInside(mouseX, mouseY, scaleSliderX, opacitySliderY(), SCALE_SLIDER_WIDTH, SCALE_SLIDER_HEIGHT)) {
            opacitySliderDragging = true;
            updateOpacityFromMouse(mouseX);
            return;
        }

        if (questHelperVisible && !panelPinned) {
            QuestLayout layout = calculateLayout(minecraft, questDisplay);
            if (isInsideScaledPanel(mouseX, mouseY, layout)) {
                panelDragging = true;
                panelDragMouseOffsetX = (float) (mouseX / HUD_SCALE - layout.panelX());
                panelDragMouseOffsetY = (float) (mouseY / HUD_SCALE - layout.panelY());
            }
        }
    }

    public void onHUDRender(GuiGraphicsExtractor graphics) {
        syncTheme();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.font == null) return;
        if (isInButterflyGardenLevel()) return;

        QuestDisplay questDisplay = questDisplayForCurrentPaper(minecraft);
        boolean showToggle = isQuestScreen(minecraft.gui.screen());
        List<Component> lines = visibleQuestLines(questDisplay);
        updateControlBounds(minecraft, questDisplay);

        if (questHelperVisible && !lines.isEmpty()) {
            renderQuestTable(graphics, minecraft, minecraft.player.getOffhandItem(), questDisplay, showToggle);
        } else if (showToggle) {
            renderCollapsedToggle(graphics, minecraft, questDisplay);
        }
    }

    private boolean isCurrentPlayerQuestPaper(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || stack == null || stack.isEmpty() || !stack.is(Items.PAPER)) return false;

        StringTag questUuid = Util.getCustomDataVar(stack, QUEST_UUID_DATA, StringTag.class);
        if (questUuid == null) return false;

        String playerUuid = minecraft.player.getUUID().toString().toLowerCase(Locale.ROOT);
        return questUuid.value().toLowerCase(Locale.ROOT).equals(playerUuid);
    }

    private QuestDisplay questDisplay(ItemStack stack) {
        Component title = Component.literal("Quest");
        List<Component> lines = new ArrayList<>();

        Component customName = stack.getCustomName();
        if (customName != null && !customName.getString().isBlank()) {
            title = customName;
        }

        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (Component line : lore.lines()) {
            if (!line.getString().isBlank()) {
                lines.add(line);
            }
        }

        return new QuestDisplay(title, lines);
    }

    private QuestDisplay questDisplayForCurrentPaper(Minecraft minecraft) {
        if (minecraft.player == null) return QuestDisplay.EMPTY;

        ItemStack questPaper = minecraft.player.getOffhandItem();
        if (!isCurrentPlayerQuestPaper(questPaper)) return QuestDisplay.EMPTY;

        return questDisplay(questPaper);
    }

    private QuestLayout calculateLayout(Minecraft minecraft, QuestDisplay questDisplay) {
        List<Component> lines = visibleQuestLines(questDisplay);
        int maxTextWidth = 22 + minecraft.font.width(questDisplay.title());
        for (Component line : lines) {
            maxTextWidth = Math.max(maxTextWidth, questLineWidth(minecraft, line));
        }

        int screenWidth = Math.round(minecraft.getWindow().getGuiScaledWidth() / HUD_SCALE);
        int screenHeight = Math.round(minecraft.getWindow().getGuiScaledHeight() / HUD_SCALE);
        int panelMarginRight = Math.round(PANEL_MARGIN_RIGHT / HUD_SCALE);
        int headerControlsWidth = PANEL_PADDING * 2 + 22 + minecraft.font.width(questDisplay.title()) + TOGGLE_MARGIN + (TOGGLE_SIZE * 2) + 3;
        int panelWidth = Math.max(Math.max(PANEL_MIN_WIDTH, headerControlsWidth), PANEL_PADDING * 2 + maxTextWidth);
        int panelHeight = HEADER_HEIGHT + PANEL_PADDING + lines.size() * ROW_HEIGHT + PANEL_PADDING;
        int defaultPanelX = screenWidth - panelMarginRight - panelWidth;
        int defaultPanelY = Math.max(4, screenHeight / 2 - panelHeight / 2);
        int panelX = clampInt(defaultPanelX + Math.round(panelOffsetX), 0, Math.max(0, screenWidth - panelWidth));
        int panelY = clampInt(defaultPanelY + Math.round(panelOffsetY), 0, Math.max(0, screenHeight - panelHeight));

        return new QuestLayout(panelX, panelY, panelWidth, panelHeight);
    }

    private void updateControlBounds(Minecraft minecraft, QuestDisplay questDisplay) {
        QuestLayout layout = calculateLayout(minecraft, questDisplay);
        toggleSize = Math.max(8, Math.round(TOGGLE_SIZE * HUD_SCALE));
        toggleX = Math.round((layout.panelX() + layout.panelWidth() - TOGGLE_MARGIN - TOGGLE_SIZE) * HUD_SCALE);
        toggleY = Math.round((layout.panelY() + TOGGLE_MARGIN) * HUD_SCALE);
        compactToggleX = Math.round((layout.panelX() + layout.panelWidth() - TOGGLE_MARGIN - (TOGGLE_SIZE * 2) - 3) * HUD_SCALE);
        compactToggleY = toggleY;
        dragHandleSize = Math.max(7, Math.round(DRAG_HANDLE_SIZE * HUD_SCALE));
        dragHandleX = Math.round(layout.panelX() * HUD_SCALE);
        dragHandleY = Math.round(layout.panelY() * HUD_SCALE);
    }

    private void updateScaleControlBounds(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {

            int leftPos = getScreenField(containerScreen, "leftPos");
            int topPos = getScreenField(containerScreen, "topPos");
            int uiWidth = 176;

            for (Slot slot : containerScreen.getMenu().slots) {
                uiWidth = Math.max(uiWidth, slot.x + 16);
            }

            scaleSliderX = leftPos + uiWidth + CONTROL_GAP;
            scaleSliderY = topPos + 4;
            return;
        }

        scaleSliderX = Minecraft.getInstance().getWindow().getGuiScaledWidth() - PANEL_MARGIN_RIGHT - SCALE_SLIDER_WIDTH;
        scaleSliderY = 4;
    }

    private void renderQuestTable(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack questPaper, QuestDisplay questDisplay, boolean showToggle) {
        List<Component> lines = visibleQuestLines(questDisplay);
        QuestLayout layout = calculateLayout(minecraft, questDisplay);
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();

        graphics.pose().pushMatrix();
        graphics.pose().scale(HUD_SCALE, HUD_SCALE);

        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth + 2, panelY + panelHeight + 2, fade(SHADOW_COLOR));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, fade(BORDER_COLOR));
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, fade(PANEL_COLOR));
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + HEADER_HEIGHT, fade(HEADER_COLOR));

        int titleX = panelX + PANEL_PADDING;
        int titleY = panelY + (HEADER_HEIGHT - minecraft.font.lineHeight) / 2;
        if (showToggle) {
            renderDragHandle(graphics, panelX, panelY);
        }
        graphics.item(questPaper, titleX + 2, panelY + 1);
        int titleTextX = titleX + 22;
        graphics.text(minecraft.font, questDisplay.title(), titleTextX, titleY, TEXT_COLOR, true);
        if (showToggle) {
            renderCompactToggle(graphics, panelX + panelWidth - TOGGLE_MARGIN - (TOGGLE_SIZE * 2) - 3, panelY + TOGGLE_MARGIN);
            renderToggle(graphics, panelX + panelWidth - TOGGLE_MARGIN - TOGGLE_SIZE, panelY + TOGGLE_MARGIN);
        }

        int rowY = panelY + HEADER_HEIGHT + PANEL_PADDING;
        for (int i = 0; i < lines.size(); i++) {
            if (i % 2 == 1) {
                graphics.fill(panelX + 2, rowY, panelX + panelWidth - 2, rowY + ROW_HEIGHT, fade(ROW_ALT_COLOR));
            }

            int textY = rowY + (ROW_HEIGHT - minecraft.font.lineHeight) / 2 + 1;
            int fallbackColor = !questCompact && i == 0 ? TEXT_COLOR : MUTED_TEXT_COLOR;
            renderQuestLine(graphics, minecraft, lines.get(i), panelX + PANEL_PADDING, panelX + panelWidth - PANEL_PADDING, textY, fallbackColor);
            rowY += ROW_HEIGHT;
        }

        graphics.pose().popMatrix();
    }

    private void renderQuestLine(GuiGraphicsExtractor graphics, Minecraft minecraft, Component line, int leftX, int rightX, int y, int fallbackColor) {
        String text = line.getString();
        int colonIndex = text.indexOf(':');
        if (colonIndex < 0 || colonIndex == text.length() - 1) {
            graphics.text(minecraft.font, sliceStyled(line, 0, text.length(), fallbackColor), leftX, y, fallbackColor, false);
            return;
        }

        int valueStart = questValueStart(text, colonIndex);
        int valueEnd = questValueEnd(text, valueStart);
        if (valueStart == valueEnd) {
            graphics.text(minecraft.font, sliceStyled(line, 0, text.length(), fallbackColor), leftX, y, fallbackColor, false);
            return;
        }

        Component label = sliceStyled(line, 0, colonIndex + 1, fallbackColor);
        Component value = sliceStyled(line, valueStart, valueEnd, fallbackColor);
        int valueWidth = minecraft.font.width(value);
        graphics.text(minecraft.font, label, leftX, y, fallbackColor, false);
        graphics.text(minecraft.font, value, rightX - valueWidth, y, fallbackColor, false);
    }

    private int questLineWidth(Minecraft minecraft, Component line) {
        String text = line.getString();
        int colonIndex = text.indexOf(':');
        if (colonIndex < 0 || colonIndex == text.length() - 1) {
            return minecraft.font.width(line);
        }

        int valueStart = questValueStart(text, colonIndex);
        int valueEnd = questValueEnd(text, valueStart);
        if (valueStart == valueEnd) {
            return minecraft.font.width(line);
        }

        return minecraft.font.width(sliceStyled(line, 0, colonIndex + 1, MUTED_TEXT_COLOR))
                + 8 + minecraft.font.width(sliceStyled(line, valueStart, valueEnd, MUTED_TEXT_COLOR));
    }

    private int questValueStart(String text, int colonIndex) {
        int start = colonIndex + 1;
        while (start < text.length() && text.charAt(start) <= ' ') {
            start++;
        }
        return start;
    }

    private int questValueEnd(String text, int start) {
        int end = text.length();
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        return end;
    }

    private void renderCollapsedToggle(GuiGraphicsExtractor graphics, Minecraft minecraft, QuestDisplay questDisplay) {
        QuestLayout layout = calculateLayout(minecraft, questDisplay);

        graphics.pose().pushMatrix();
        graphics.pose().scale(HUD_SCALE, HUD_SCALE);
        renderToggle(graphics, layout.panelX() + layout.panelWidth() - TOGGLE_MARGIN - TOGGLE_SIZE, layout.panelY() + TOGGLE_MARGIN);
        graphics.pose().popMatrix();
    }

    private void renderToggle(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, CONTROL_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + TOGGLE_SIZE - 1, y + TOGGLE_SIZE - 1, questHelperVisible ? CONTROL_FILL_COLOR : CONTROL_HOVER_COLOR);
        int midY = y + TOGGLE_SIZE / 2;
        graphics.fill(x + 3, midY, x + TOGGLE_SIZE - 3, midY + 1, TEXT_COLOR);
        if (!questHelperVisible) {
            int midX = x + TOGGLE_SIZE / 2;
            graphics.fill(midX, y + 3, midX + 1, y + TOGGLE_SIZE - 3, TEXT_COLOR);
        }
    }

    private void renderCompactToggle(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, CONTROL_BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + TOGGLE_SIZE - 1, y + TOGGLE_SIZE - 1, questCompact ? CONTROL_ACTIVE_COLOR : CONTROL_FILL_COLOR);
        int iconColor = questCompact ? TEXT_COLOR : MUTED_TEXT_COLOR;
        int dotX = x + 3;
        int lineX = x + 5;
        int firstY = y + 3;
        int secondY = y + 7;

        graphics.fill(dotX, firstY, dotX + 1, firstY + 1, iconColor);
        graphics.fill(lineX, firstY, x + TOGGLE_SIZE - 3, firstY + 1, iconColor);
        graphics.fill(dotX, secondY, dotX + 1, secondY + 1, iconColor);
        graphics.fill(lineX, secondY, x + TOGGLE_SIZE - 3, secondY + 1, iconColor);
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

    private void renderQuestControls(GuiGraphicsExtractor gui, Minecraft minecraft, int mouseX, int mouseY) {
        if (!questHelperVisible) return;

        renderSlider(gui, minecraft, mouseX, mouseY, scaleSliderY, scaleSliderDragging,
                (HUD_SCALE - MIN_HUD_SCALE) / (MAX_HUD_SCALE - MIN_HUD_SCALE), String.format(Locale.ROOT, "%.1fx", HUD_SCALE));
        renderSlider(gui, minecraft, mouseX, mouseY, opacitySliderY(), opacitySliderDragging,
                (PANEL_OPACITY - MIN_OPACITY) / (1.0f - MIN_OPACITY), "Opacity " + Math.round(PANEL_OPACITY * 100) + "%");
    }

    private void renderSlider(GuiGraphicsExtractor gui, Minecraft minecraft, int mouseX, int mouseY, int y, boolean dragging, float value, String label) {
        int x = scaleSliderX;
        boolean hovered = isInside(mouseX, mouseY, x, y, SCALE_SLIDER_WIDTH, SCALE_SLIDER_HEIGHT);
        int borderColor = hovered || dragging ? 0xFFFFFFFF : CONTROL_BORDER_COLOR;
        int fillColor = dragging ? CONTROL_ACTIVE_COLOR : hovered ? CONTROL_HOVER_COLOR : CONTROL_FILL_COLOR;
        int trackX = x + 7;
        int trackY = y + SCALE_SLIDER_HEIGHT - 5;
        int trackWidth = SCALE_SLIDER_WIDTH - 14;
        int knobX = trackX + Math.round(clamp(value, 0.0f, 1.0f) * trackWidth) - SCALE_KNOB_WIDTH / 2;

        gui.fill(x, y, x + SCALE_SLIDER_WIDTH, y + SCALE_SLIDER_HEIGHT, borderColor);
        gui.fill(x + 1, y + 1, x + SCALE_SLIDER_WIDTH - 1, y + SCALE_SLIDER_HEIGHT - 1, fillColor);
        gui.centeredText(minecraft.font, label, x + SCALE_SLIDER_WIDTH / 2, y + 3, TEXT_COLOR);
        gui.fill(trackX, trackY, trackX + trackWidth, trackY + SCALE_TRACK_HEIGHT, CONTROL_TRACK_COLOR);
        gui.fill(trackX, trackY, knobX + SCALE_KNOB_WIDTH / 2, trackY + SCALE_TRACK_HEIGHT, Theme.ACCENT);
        gui.fill(knobX, trackY - 2, knobX + SCALE_KNOB_WIDTH, trackY + SCALE_TRACK_HEIGHT + 2, 0xFFFFFFFF);
    }

    /** The opacity slider sits right under the size slider. */
    private int opacitySliderY() {
        return scaleSliderY + SCALE_SLIDER_HEIGHT + 3;
    }

    private void updateOpacityFromMouse(double mouseX) {
        int trackX = scaleSliderX + 7;
        int trackWidth = SCALE_SLIDER_WIDTH - 14;
        float percent = clamp((float) ((mouseX - trackX) / trackWidth), 0.0f, 1.0f);
        PANEL_OPACITY = Math.round((MIN_OPACITY + percent * (1.0f - MIN_OPACITY)) * 20) / 20.0f; // steps of 5%
        Config.update(CONFIG_OPACITY_PATH, PANEL_OPACITY);
    }

    /** A background colour with the panel's opacity applied (text isn't faded). */
    private static int fade(int argb) {
        int alpha = Math.round((argb >>> 24) * PANEL_OPACITY);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private void updateHudScaleFromMouse(double mouseX) {
        int trackX = scaleSliderX + 7;
        int trackWidth = SCALE_SLIDER_WIDTH - 14;
        float percent = clamp((float) ((mouseX - trackX) / trackWidth), 0.0f, 1.0f);
        HUD_SCALE = MIN_HUD_SCALE + percent * (MAX_HUD_SCALE - MIN_HUD_SCALE);
        Config.update(CONFIG_SCALE_PATH, HUD_SCALE);
    }

    private void updatePanelOffsetFromMouse(Minecraft minecraft, QuestDisplay questDisplay, int mouseX, int mouseY) {
        QuestLayout currentLayout = calculateLayout(minecraft, questDisplay);
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

    private List<Component> visibleQuestLines(QuestDisplay questDisplay) {
        if (!questCompact) return questDisplay.lines();

        List<Component> candidateLines = compactDelimitedLines(questDisplay.lines());
        List<Component> visibleLines = new ArrayList<>();
        for (Component line : candidateLines) {
            if (!isCompactHiddenLine(line)) {
                visibleLines.add(line);
            }
        }
        return visibleLines;
    }

    private List<Component> compactDelimitedLines(List<Component> lines) {
        int firstSeparator = -1;
        int secondSeparator = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (!isSeparatorLine(lines.get(i))) continue;

            if (firstSeparator < 0) {
                firstSeparator = i;
            } else {
                secondSeparator = i;
                break;
            }
        }

        if (firstSeparator < 0 || secondSeparator < 0 || secondSeparator <= firstSeparator + 1) {
            return lines;
        }

        return lines.subList(firstSeparator + 1, secondSeparator);
    }

    private boolean isSeparatorLine(Component line) {
        String text = line.getString();
        if (text.isBlank()) return false;

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character != '-' && character != ' ') {
                return false;
            }
        }
        return text.indexOf('-') >= 0;
    }

    private boolean isCompactHiddenLine(Component line) {
        String text = line.getString();
        int colonIndex = text.indexOf(':');
        if (colonIndex < 0 || colonIndex == text.length() - 1) return false;

        String keyText = text.substring(0, colonIndex);
        String valueText = text.substring(colonIndex + 1).trim();
        if (valueText.equalsIgnoreCase("Complete")) return true;

        Integer target = lastNumber(keyText);
        Integer value = firstNumber(valueText);
        return target != null && value != null && value >= target;
    }

    private Integer firstNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        return parseNumber(matcher.group());
    }

    private Integer lastNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        Integer number = null;
        while (matcher.find()) {
            number = parseNumber(matcher.group());
        }
        return number;
    }

    private Integer parseNumber(String text) {
        try {
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private boolean isInsideScaledPanel(double mouseX, double mouseY, QuestLayout layout) {
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

    private boolean isQuestScreen(Screen screen) {
        return screen != null && screen.getTitle().getString().contains("Quests!");
    }

    private boolean isInButterflyGardenLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && BUTTERFLY_GARDEN_LEVEL.equals(minecraft.player.level().dimension().identifier().toString());
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

    private Component sliceStyled(Component source, int start, int end, int fallbackColor) {
        var result = Component.empty();
        int[] offset = {0};

        source.visit((style, segment) -> {
            int segmentStart = offset[0];
            int segmentEnd = segmentStart + segment.length();
            offset[0] = segmentEnd;

            int from = Math.max(start, segmentStart);
            int to = Math.min(end, segmentEnd);
            if (from < to) {
                Style effectiveStyle = style.getColor() == null
                        ? style.withColor(fallbackColor)
                        : style;
                result.append(Component.literal(
                        segment.substring(from - segmentStart, to - segmentStart)
                ).withStyle(effectiveStyle));
            }
            return java.util.Optional.<Void>empty();
        }, Style.EMPTY);

        return result;
    }


    private record QuestLayout(int panelX, int panelY, int panelWidth, int panelHeight) {
    }

    private record QuestDisplay(Component title, List<Component> lines) {
        private static final QuestDisplay EMPTY = new QuestDisplay(Component.literal("Quest"), List.of());
    }
}

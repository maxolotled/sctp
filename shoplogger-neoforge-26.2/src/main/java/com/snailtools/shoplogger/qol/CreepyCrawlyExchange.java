package com.snailtools.shoplogger.qol;


import com.google.common.primitives.Shorts;
import com.snailtools.shoplogger.qol.utils.Util;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

import java.lang.reflect.Field;
import java.util.Locale;

public class CreepyCrawlyExchange {

    private final int TEXT_COLOR = 0xFFFFE3BC;
    private final int SELLABLE_SLOT_HIGHLIGHT_COLOR = 0x557B4397;
    private final int SELLABLE_SLOT_BORDER_COLOR = 0xFFACD653;
    private final int WRONG_OWNER_SLOT_HIGHLIGHT_COLOR = 0x66E88A24;
    private final int WRONG_OWNER_SLOT_BORDER_COLOR = 0xFFFF6438;
    private final int EXCHANGE_BUTTON_WIDTH = 70;
    private final int EXCHANGE_BUTTON_HEIGHT = 20;
    private final int EXCHANGE_BUTTON_GAP = 6;
    private final long EXCHANGE_CLICK_WAIT_MILLIS = 5000L;
    private final String OWNER_UUID_DATA = "escargold:owner-uuid";
    private final String CREEPY_CRAWLY_LEVEL = "minecraft:spawn";
    private boolean exchangeSelling = false;
    private String currentExchangeType = null;
    private int exchangeButtonX = 0;
    private int exchangeButtonY = 0;
    private String waitingExchangeType = null;
    private int waitingCountBeforeClick = 0;
    private long exchangeClickWaitUntilMillis = 0L;

    public void onScreenRender(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!isInCreepyCrawlyLevel()) return;
        if (screen instanceof AbstractContainerScreen<?> containerScreen && isCreepyCrawlyExchangeScreen(containerScreen)) {
            updateExchangeControlBounds(containerScreen);
            String blockedReason = getSellingBlockedReason(containerScreen);
            renderExchangeSlotHighlights(containerScreen, gui);
            renderExchangeButton(gui, mouseX, mouseY, blockedReason != null);
            renderBlockedSellingWarning(gui, mouseX, mouseY, blockedReason);
        }
    }

    public void onMouseEvent(long window, MouseButtonInfo button, int action) {
        if (button != null) {
            onMouseEvent(window, button.button(), action);
        }
    }

    public void onMouseEvent(long window, int button, int action) {
        if (!isInCreepyCrawlyLevel()) return;
        if (action == 1 && button == 0 && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen
                && isCreepyCrawlyExchangeScreen(screen)) {
            updateExchangeControlBounds(screen);
            double mouseX = scaledMouseX();
            double mouseY = scaledMouseY();
            if (mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                    && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT) {
                String blockedReason = getSellingBlockedReason(screen);
                if (blockedReason != null) {
                    setExchangeSelling(false);
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("[Auto Sell] " + blockedReason));
                    return;
                }
                exchangeSelling = !exchangeSelling;
                currentExchangeType = null;
                clearExchangeWait();
            }
        }
    }

    private boolean isCreepyCrawlyExchangeScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getTitle().getString().contains("Creepy Crawly Exchange");
    }

    private boolean isInCreepyCrawlyLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && CREEPY_CRAWLY_LEVEL.equals(minecraft.player.level().dimension().identifier().toString());
    }

    private Slot findNextExchangeSlot(AbstractContainerScreen<?> screen) {
        Slot slot = peekNextExchangeSlot(screen);
        currentExchangeType = slot == null ? null : typeName(slot.getItem());
        return slot;
    }

    private Slot peekNextExchangeSlot(AbstractContainerScreen<?> screen) {
        if (currentExchangeType != null) {
            Slot currentSlot = findTopExchangeSlot(screen, currentExchangeType);
            if (currentSlot != null) return currentSlot;
        }
        for (Slot inventorySlot : screen.getMenu().slots) {
            if (inventorySlot.container != Minecraft.getInstance().player.getInventory()) continue;
            ItemStack stack = inventorySlot.getItem();
            if (stack.isEmpty() || !isOwnedByCurrentPlayer(stack)) continue;
            Slot exchangeSlot = findTopExchangeSlot(screen, typeName(stack));
            if (exchangeSlot == null) continue;
            return exchangeSlot;
        }
        return null;
    }

    private Slot findTopExchangeSlot(AbstractContainerScreen<?> screen, String typeName) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory()) continue;
            if (matchesBugType(slot.getItem(), typeName)
                    && countInventoryBugs(screen, typeName) >= slot.getItem().getCount()) return slot;
        }
        return null;
    }

    private boolean matchesBugType(ItemStack stack, String typeName) {
        if (stack == null || stack.isEmpty() || stack.getCustomName() == null) return false;
        return normalizeName(typeName(stack)).equals(normalizeName(typeName));
    }

    private boolean matchesOwnedBugType(ItemStack stack, String typeName) {
        return matchesBugType(stack, typeName) && isOwnedByCurrentPlayer(stack);
    }

    private boolean isExchangeBug(AbstractContainerScreen<?> screen, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory()) continue;
            if (matchesBugType(slot.getItem(), typeName(stack))) return true;
        }
        return false;
    }

    private boolean hasWrongOwnerInventoryBug(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (isExchangeBug(screen, stack) && !isOwnedByCurrentPlayer(stack)) return true;
        }
        return false;
    }

    private void renderExchangeSlotHighlights(AbstractContainerScreen<?> screen, GuiGraphicsExtractor gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        int leftPos = getScreenField(screen, "leftPos");
        int topPos = getScreenField(screen, "topPos");
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (slot.container == minecraft.player.getInventory()) {
                if (isExchangeBug(screen, stack) && !isOwnedByCurrentPlayer(stack)) {
                    gui.fill(x, y, x + 16, y + 16, WRONG_OWNER_SLOT_HIGHLIGHT_COLOR);
                    drawSlotBorder(gui, x, y, WRONG_OWNER_SLOT_BORDER_COLOR);
                }
                continue;
            }
            if (!stack.isEmpty() && stack.getCustomName() != null
                    && countInventoryBugs(screen, typeName(stack)) >= stack.getCount()) {
                gui.fill(x, y, x + 16, y + 16, SELLABLE_SLOT_HIGHLIGHT_COLOR);
                drawSlotBorder(gui, x, y, SELLABLE_SLOT_BORDER_COLOR);
            }
        }
    }

    public void tick() {
        if (!isInCreepyCrawlyLevel()) {
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
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) || !isCreepyCrawlyExchangeScreen(screen)) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        if (hasWrongOwnerInventoryBug(screen)) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        if (isWaitingForExchangeUpdate(screen)) return;

        String blockedReason = getSellingBlockedReason(screen);
        if (blockedReason != null) {
            setExchangeSelling(false);
            minecraft.player.sendSystemMessage(Component.literal("[Auto Sell] Stopped: " + blockedReason));
            return;
        }

        Slot slot = findNextExchangeSlot(screen);
        if (slot == null) {
            exchangeSelling = false;
            currentExchangeType = null;
            clearExchangeWait();
            return;
        }

        String clickedType = currentExchangeType;
        int countBeforeClick = clickedType == null ? 0 : countInventoryBugs(screen, clickedType);
        sendExchangeClickPacket(minecraft, screen.getMenu(), slot);
        waitForExchangeUpdate(clickedType, countBeforeClick);
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
    }

    private void renderExchangeButton(GuiGraphicsExtractor gui, int mouseX, int mouseY, boolean disabled) {
        boolean hovered = mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT;
        int fillColor = disabled ? 0xD83A293F : exchangeSelling ? 0xE0486326 : hovered ? 0xE05C3278 : 0xD821172B;
        int borderColor = disabled ? WRONG_OWNER_SLOT_BORDER_COLOR : hovered ? 0xFFFFFFFF : 0xE0D87924;
        int textColor = disabled ? 0xFFFFA438 : TEXT_COLOR;

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

    private void renderBlockedSellingWarning(GuiGraphicsExtractor gui, int mouseX, int mouseY, String blockedReason) {
        if (blockedReason == null || Minecraft.getInstance().font == null) return;

        String warning = blockedReason;
        int warningX = exchangeButtonX;
        int warningY = exchangeButtonY + EXCHANGE_BUTTON_HEIGHT + EXCHANGE_BUTTON_GAP;
        int warningWidth = Minecraft.getInstance().font.width(warning);

        gui.fill(warningX - 2, warningY - 2, warningX + warningWidth + 2, warningY + Minecraft.getInstance().font.lineHeight + 2, 0xCC211529);
        gui.text(Minecraft.getInstance().font, warning, warningX, warningY, WRONG_OWNER_SLOT_BORDER_COLOR, true);

        if (mouseX >= exchangeButtonX && mouseX < exchangeButtonX + EXCHANGE_BUTTON_WIDTH
                && mouseY >= exchangeButtonY && mouseY < exchangeButtonY + EXCHANGE_BUTTON_HEIGHT) {
            gui.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(warning), mouseX, mouseY);
        }
    }

    private String getSellingBlockedReason(AbstractContainerScreen<?> screen) {
        if (hasWrongOwnerInventoryBug(screen)) return "Remove wrong-owner bugs to sell";
        Slot nextSale = peekNextExchangeSlot(screen);
        if (nextSale == null) return null;
        ItemStack offer = nextSale.getItem();
        String type = typeName(offer);
        int payout = getExchangeBatchCoins(type, offer.getCount());
        if (payout < 0) return "Cannot verify coin payout for " + type;
        long capacity = getAvailableCoinCapacity();
        return capacity < payout ? "Not enough inventory space" : null;
    }

    private int getExchangeBatchCoins(String type, int batchSize) {
        String name = normalizeName(type);
        if (name.contains("emperor scorpion") || name.contains("stag beetle")) return batchSize == 1 ? 5 : -1;
        if (name.contains("praying mantis") || name.contains("brown centipede")) return batchSize == 2 ? 3 : -1;
        if (name.contains("black ant") || name.contains("brown ant") || name.contains("cockroach")
                || name.contains("european hornet") || name.contains("tree cricket")
                || name.contains("field cricket") || name.contains("mosquito")) return batchSize == 5 ? 1 : -1;
        return -1;
    }

    private long getAvailableCoinCapacity() {
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        ItemStack coinTemplate = ItemStack.EMPTY;
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
            ItemStack stack = inventory.getItem(index);
            if (isExchangeCoin(stack)) {
                coinTemplate = stack;
                break;
            }
        }
        int stackLimit = coinTemplate.isEmpty() ? Items.RAW_GOLD.getDefaultMaxStackSize() : coinTemplate.getMaxStackSize();
        long capacity = 0;
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty()) capacity += Math.min(inventory.getMaxStackSize(), stackLimit);
            else if (!coinTemplate.isEmpty() && ItemStack.isSameItemSameComponents(stack, coinTemplate)) {
                capacity += Math.max(0, Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize()) - stack.getCount());
            }
        }
        return capacity;
    }

    private boolean isExchangeCoin(ItemStack stack) {
        if (!stack.is(Items.RAW_GOLD)) return false;
        var modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return modelData != null && Float.valueOf(999003.0F).equals(modelData.getFloat(0));
    }

    private Component exchangeButtonLabel(boolean disabled) {
        return Component.literal(disabled ? "Blocked" : exchangeSelling ? "Selling" : "Sell");
    }

    private int countInventoryBugs(AbstractContainerScreen<?> screen, String typeName) {
        int count = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != Minecraft.getInstance().player.getInventory()) continue;
            if (matchesOwnedBugType(slot.getItem(), typeName)) {
                count += slot.getItem().getCount();
            }
        }
        return count;
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

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private double scaledMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private double scaledMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
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

    private boolean isOwnedByCurrentPlayer(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        StringTag owner = Util.getCustomDataVar(stack, OWNER_UUID_DATA, StringTag.class);
        if (owner == null) return false;

        String playerUuid = minecraft.player.getUUID().toString().toLowerCase(Locale.ROOT);
        return owner.value().toLowerCase(Locale.ROOT).equals(playerUuid);
    }

    private String typeName(ItemStack stack) {
        Component customName = stack.getCustomName();
        if (customName != null && !customName.getString().isBlank()) {
            return customName.getString();
        }
        return stack.getHoverName().getString();
    }

    private boolean isWaitingForExchangeUpdate(AbstractContainerScreen<?> screen) {
        if (waitingExchangeType == null) return false;

        if (countInventoryBugs(screen, waitingExchangeType) < waitingCountBeforeClick) {
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


}



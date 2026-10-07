package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * One icon button to the left of your ender chest and backpack screens that
 * jumps to the other one: on the ender chest it shows a backpack (bundle) and
 * runs /bp, on the backpack it shows an ender chest and runs /ec. Screens are
 * recognised by their title.
 */
public class StorageSwitcher {

	private record Page(String titleMatch, String command, String label, ItemStack icon) {}

	private static final Page ENDER_CHEST = new Page("ender chest", "ec", "Ender Chest", new ItemStack(Items.ENDER_CHEST));
	private static final Page BACKPACK = new Page("backpack", "bp", "Backpack", new ItemStack(Items.BUNDLE));
	private static final List<Page> PAGES = List.of(ENDER_CHEST, BACKPACK);

	private static final int BTN_W = 24;
	private static final int BTN_H = 24;
	private static final int GAP = 6;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");

	private static final String CONFIG_ENABLED = "storageSwitcher/enabled";

	private int btnX, btnY;

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	/** Which storage the open screen is, or null if it's something else (or the button is turned off). */
	private static Page pageOf(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>) || !isEnabled()) return null;
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		for (Page p : PAGES) {
			if (title.contains(p.titleMatch())) return p;
		}
		return null;
	}

	/** The storage the button leads to: whichever one you're not on. */
	private static Page other(Page page) {
		return page == ENDER_CHEST ? BACKPACK : ENDER_CHEST;
	}

	private void updateBounds(AbstractContainerScreen<?> screen) {
		int leftPos = field(screen, "leftPos");
		int topPos = field(screen, "topPos");
		int imageHeight = field(screen, "imageHeight");
		btnX = leftPos - GAP - BTN_W;
		btnY = topPos + (imageHeight - BTN_H) / 2;
	}

	public void onScreenRender(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
		Page page = pageOf(screen);
		if (page == null) return;
		updateBounds((AbstractContainerScreen<?>) screen);
		Page target = other(page);
		boolean over = inside(mouseX, mouseY);
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, over ? BUTTON_HOVER : BUTTON, btnX, btnY, BTN_W, BTN_H);
		gui.item(target.icon(), btnX + (BTN_W - 16) / 2, btnY + (BTN_H - 16) / 2);
		if (over) {
			gui.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(target.label() + " (/" + target.command() + ")"), mouseX, mouseY);
		}
	}

	/** Returns true if the click hit the button (so the screen shouldn't also get it). */
	public boolean onMouseEvent(long window, MouseButtonInfo info, int action) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		Page page = pageOf(screen);
		if (page == null || action != 1 || info.button() != 0) return false;
		updateBounds((AbstractContainerScreen<?>) screen);
		double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
		if (!inside(mouseX, mouseY)) return false;
		if (mc.getConnection() != null) {
			AbstractWidget.playButtonClickSound(mc.getSoundManager());
			mc.getConnection().sendCommand(other(page).command());
		}
		return true;
	}

	private boolean inside(double mouseX, double mouseY) {
		return mouseX >= btnX && mouseX < btnX + BTN_W && mouseY >= btnY && mouseY < btnY + BTN_H;
	}

	private static int field(AbstractContainerScreen<?> screen, String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return (int) f.get(screen);
		} catch (Exception e) {
			return 0;
		}
	}
}

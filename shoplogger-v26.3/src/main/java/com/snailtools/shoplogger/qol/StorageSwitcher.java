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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Arrow buttons on either side of your ender chest and backpack screens:
 * the left one runs /bp, the right one runs /ec. The arrow for the screen
 * you're already on is greyed out. Screens are recognised by their title.
 */
public class StorageSwitcher {

	private record Page(String titleMatch, String command, String label) {}

	private static final Page ENDER_CHEST = new Page("ender chest", "ec", "Ender Chest");
	private static final Page BACKPACK = new Page("backpack", "bp", "Backpack");
	private static final List<Page> PAGES = List.of(ENDER_CHEST, BACKPACK);
	private static final Page LEFT = BACKPACK;
	private static final Page RIGHT = ENDER_CHEST;

	private static final int BTN_W = 20;
	private static final int BTN_H = 24;
	private static final int GAP = 6;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");

	private static final String CONFIG_ENABLED = "storageSwitcher/enabled";

	private int leftX, rightX, btnY;

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	/** Which storage the open screen is, or null if it's something else (or the arrows are turned off). */
	private static Page pageOf(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>) || !isEnabled()) return null;
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		for (Page p : PAGES) {
			if (title.contains(p.titleMatch())) return p;
		}
		return null;
	}

	private void updateBounds(AbstractContainerScreen<?> screen) {
		int leftPos = field(screen, "leftPos");
		int topPos = field(screen, "topPos");
		int imageWidth = field(screen, "imageWidth");
		int imageHeight = field(screen, "imageHeight");
		leftX = leftPos - GAP - BTN_W;
		rightX = leftPos + imageWidth + GAP;
		btnY = topPos + (imageHeight - BTN_H) / 2;
	}

	public void onScreenRender(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
		Page page = pageOf(screen);
		if (page == null) return;
		updateBounds((AbstractContainerScreen<?>) screen);
		arrow(gui, leftX, "<", LEFT, page, mouseX, mouseY);
		arrow(gui, rightX, ">", RIGHT, page, mouseX, mouseY);
	}

	/** One arrow; greyed out (and not clickable) when it leads to the screen you're already on. */
	private void arrow(GuiGraphicsExtractor gui, int x, String label, Page target, Page current, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		boolean here = target == current;
		boolean over = !here && inside(mouseX, mouseY, x);
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, here ? BUTTON_DISABLED : over ? BUTTON_HOVER : BUTTON, x, btnY, BTN_W, BTN_H);
		gui.centeredText(font, label, x + BTN_W / 2, btnY + (BTN_H - 8) / 2, here ? 0xFFA0A0A0 : 0xFFFFFFFF);
		if (over) {
			gui.setTooltipForNextFrame(font, Component.literal(target.label() + " (/" + target.command() + ")"), mouseX, mouseY);
		}
	}

	/** Returns true if the click hit an arrow (so the screen shouldn't also get it). */
	public boolean onMouseEvent(long window, MouseButtonInfo info, int action) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		Page page = pageOf(screen);
		if (page == null || action != 1 || info.button() != 0) return false;
		updateBounds((AbstractContainerScreen<?>) screen);
		double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
		Page target = inside(mouseX, mouseY, leftX) ? LEFT : inside(mouseX, mouseY, rightX) ? RIGHT : null;
		if (target == null) return false;
		// the greyed-out arrow: you're already there, so just swallow the click
		if (target != page && mc.getConnection() != null) {
			AbstractWidget.playButtonClickSound(mc.getSoundManager());
			mc.getConnection().sendCommand(target.command());
		}
		return true;
	}

	private boolean inside(double mouseX, double mouseY, int x) {
		return mouseX >= x && mouseX < x + BTN_W && mouseY >= btnY && mouseY < btnY + BTN_H;
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

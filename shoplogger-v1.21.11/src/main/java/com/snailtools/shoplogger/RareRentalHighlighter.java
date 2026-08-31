package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareRentals;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Highlights item slots in an open shop chest/barrel whose item is on the
 * current world's rentable-rare list (/pw RentARare on Firefly, /pw
 * tool_library on Honeybee — GET /rare-items, the same admin-curated list the
 * website's search notice already uses). Purely visual, same slot-fill
 * approach as ButterflyGarden's sellable highlight — never touches/clicks
 * anything. Only fires for containers ShopScanner already confirmed are a
 * real shop (a sign was found nearby when it was opened), never a player's
 * own chest or an ender chest.
 */
public final class RareRentalHighlighter {

	private static final RareRentalHighlighter INSTANCE = new RareRentalHighlighter();
	public static RareRentalHighlighter getInstance() { return INSTANCE; }

	private static final String CONFIG_ENABLED = "rareRentalHighlight/enabled";
	private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L; // 10 minutes
	private static final int HIGHLIGHT_COLOR = 0x55FFD700;
	private static final int BORDER_COLOR = 0xFFFFD700;

	private volatile Set<String> fireflyNames = Set.of();
	private volatile Set<String> honeybeeNames = Set.of();
	private volatile long fetchedAtMillis = -REFRESH_INTERVAL_MS; // forces an immediate first fetch
	private volatile boolean fetching = false;

	private RareRentalHighlighter() {}

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	public void onScreenRender(Screen screen, DrawContext gui, int mouseX, int mouseY, float delta) {
		if (!isEnabled()) return;
		if (!(screen instanceof HandledScreen<?> containerScreen)) return;
		if (!ShopScanner.isCurrentShopScreen(containerScreen.getScreenHandler())) return;

		maybeRefresh();
		ShopWorld world = WorldSelection.get();
		if (world == null) return;
		Set<String> names = world == ShopWorld.FIREFLY ? fireflyNames : honeybeeNames;
		if (names.isEmpty()) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		int leftPos = getScreenField(containerScreen, "field_2776");
		int topPos = getScreenField(containerScreen, "field_2800");

		for (Slot slot : containerScreen.getScreenHandler().slots) {
			if (slot.inventory == client.player.getInventory()) continue; // only the shop's own container side
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) continue;
			if (!names.contains(MatchUtil.alphaOnly(stack.getName().getString()))) continue;

			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			gui.fill(x, y, x + 16, y + 16, HIGHLIGHT_COLOR);
			drawSlotBorder(gui, x, y);
		}
	}

	private void drawSlotBorder(DrawContext gui, int x, int y) {
		gui.fill(x - 1, y - 1, x + 17, y, BORDER_COLOR);
		gui.fill(x - 1, y + 16, x + 17, y + 17, BORDER_COLOR);
		gui.fill(x - 1, y, x, y + 16, BORDER_COLOR);
		gui.fill(x + 16, y, x + 17, y + 16, BORDER_COLOR);
	}

	private void maybeRefresh() {
		if (fetching || System.currentTimeMillis() - fetchedAtMillis < REFRESH_INTERVAL_MS) return;
		fetching = true;
		WebDataClient.fetchRareRentals()
				.thenAccept(r -> {
					fireflyNames = normalize(r.firefly);
					honeybeeNames = normalize(r.honeybee);
					fetchedAtMillis = System.currentTimeMillis();
					fetching = false;
				})
				.exceptionally(ex -> {
					fetchedAtMillis = System.currentTimeMillis(); // don't hammer on repeated failure — retry next interval
					fetching = false;
					return null;
				});
	}

	private static Set<String> normalize(List<String> names) {
		if (names == null) return Set.of();
		Set<String> out = new HashSet<>();
		for (String n : names) {
			String norm = MatchUtil.alphaOnly(n);
			if (!norm.isEmpty()) out.add(norm);
		}
		return out;
	}

	private int getScreenField(HandledScreen<?> screen, String name) {
		try {
			Field field = HandledScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return (int) field.get(screen);
		} catch (Exception exception) {
			return 0;
		}
	}
}

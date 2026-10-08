package com.snailtools.shoplogger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.blaze3d.platform.InputConstants;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.qol.Cooldowns;
import com.snailtools.shoplogger.qol.EmptyHandItems;
import com.snailtools.shoplogger.qol.QolHookManager;
import com.snailtools.shoplogger.qol.RGBPreview;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * NeoForge port of the Fabric entrypoint of the same name — see
 * shoplogger-v26.2's ShopLoggerClient for the original. Business logic below
 * is unchanged; only the wiring layer differs:
 *  - Fabric's ClientModInitializer.onInitializeClient() -> this constructor
 *    (runs at the same "earliest client-only init" point).
 *  - Fabric API event callbacks -> NeoForge events, either on the mod bus
 *    (key mappings / gui layers, both IModBusEvent) or NeoForge.EVENT_BUS
 *    (everything else) via @SubscribeEvent instance methods.
 *  - Fabric client commands (FabricClientCommandSource) -> NeoForge client
 *    commands actually share the same CommandSourceStack as server commands,
 *    so registration uses raw Brigadier builders instead of the ClientCommands
 *    convenience wrappers, and feedback uses sendSuccess(...) not sendFeedback(...).
 */
@Mod(value = "shoplogger", dist = Dist.CLIENT)
public class ShopLoggerClient {

	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("shoplogger", "main"));

	private final ShopScanner manualScanner = new ShopScanner();
	private KeyMapping exportKey;
	private KeyMapping toggleAutoScanKey;
	private KeyMapping uploadKey;
	private KeyMapping toggleMarkersKey;
	private KeyMapping togglePrintKey;
	private KeyMapping openLibraryKey;

	/**
	 * Set by /search when GUI mode is on, consumed on the next client tick.
	 * Opening a Screen synchronously from inside a chat command's dispatch
	 * races with ChatScreen's own close-on-submit logic (it runs right after
	 * and would immediately undo our setScreenAndShow) — deferring to the
	 * next tick, same as the hotkeys below, sidesteps that race.
	 */
	private static volatile String pendingSearchQuery;

	/** Same deferred-open reasoning as pendingSearchQuery, for watchlist alerts' [Options] button. */
	private static volatile String pendingWatchOptionsItem;

	/** How often (in ticks) to auto-upload to the Trading Post, in addition to the manual keybind. 20 ticks = 1s. */
	private static final int AUTO_UPLOAD_INTERVAL_TICKS = 20 * 60 * 15; // 15 minutes
	private int uploadTickCounter = 0;

	public ShopLoggerClient(IEventBus modEventBus) {
		// try load config first — same timing as Fabric's onInitializeClient(),
		// which also ran before any event registration below.
		Config.load();
		QolHookManager.onInit();

		modEventBus.addListener(this::registerKeyMappings);
		modEventBus.addListener(this::registerGuiLayers);
		NeoForge.EVENT_BUS.register(this);
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		exportKey = new KeyMapping(
				"key.shoplogger.export",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		);
		event.register(exportKey);

		toggleAutoScanKey = new KeyMapping(
				"key.shoplogger.toggle_autoscan",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_MINUS, // defaults to '-'; rebind in Controls
				KEY_CATEGORY
		);
		event.register(toggleAutoScanKey);

		uploadKey = new KeyMapping(
				"key.shoplogger.upload",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY
		);
		event.register(uploadKey);

		toggleMarkersKey = new KeyMapping(
				"key.shoplogger.toggle_markers",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		);
		event.register(toggleMarkersKey);

		togglePrintKey = new KeyMapping(
				"key.shoplogger.toggle_print",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		);
		event.register(togglePrintKey);

		openLibraryKey = new KeyMapping(
				"key.shoplogger.open_library",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				KEY_CATEGORY
		);
		event.register(openLibraryKey);
	}

	private void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(Identifier.fromNamespaceAndPath("sctp", "hudlayer"), QolHookManager::onHudRender);
	}

	// Manual path: player right-clicks a chest/barrel themselves.
	@SubscribeEvent
	public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!event.getLevel().isClientSide()) return;

		var pos = event.getPos();
		var world = event.getLevel();
		var be = world.getBlockEntity(pos);
		// This also fires for autoscan's own silent useOn() call —
		// only treat this as a real manual click if that's not what's happening.
		if (!ShopAutoScanner.getInstance().isSelfInteracting()) {
			if (ShopContainers.isShopContainer(be)) {
				// Quarantine other in-flight scans against a race with THIS
				// open, but trust this container's own scan — see ScanQuarantine.
				ScanQuarantine.markManualOpen(pos.immutable());
				// Manual clicking always wins over autoscan's silent background scanning.
				ShopAutoScanner.getInstance().onManualContainerInteract();
				manualScanner.onContainerInteract(pos.immutable());
				// A genuine manual open (never the silent auto-scanner, guarded by
				// isSelfInteracting() above) reads as "found it, done navigating" —
				// clear any active teleport beam. The beam's own tick() already
				// self-clears on arrival/timeout; this just covers opening a
				// container before physically reaching the beam's exact endpoint.
				TeleportHighlight.getInstance().clear();
			} else if (be instanceof EnderChestBlockEntity) {
				// No legitimate ShopLog scan of an ender chest exists to
				// exempt — anything logged nearby in time is suspect.
				ScanQuarantine.markManualOpen(null);
				TeleportHighlight.getInstance().clear();
			}
		}
		// never cancel/alter normal interaction — event is left uncancelled.
	}

	@SubscribeEvent
	public void onScreenInitPost(ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof ContainerScreen containerScreen) {
			manualScanner.onContainerScreenOpened(containerScreen.getMenu());
		}
	}

	@SubscribeEvent
	public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		var dispatcher = event.getDispatcher();

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("setworld")
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("firefly")
						.executes(ctx -> setWorld(ctx, ShopWorld.FIREFLY)))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("honeybee")
						.executes(ctx -> setWorld(ctx, ShopWorld.HONEYBEE))));

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("search")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("item", StringArgumentType.greedyString())
						.executes(ShopLoggerClient::search)));

		// Both client-only, used as the click targets on WatchlistAlert's chat
		// message — never typed by hand, but registered as real commands (same
		// as setworld/search) so a ClickEvent.RunCommand can trigger them.
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchtp")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
						.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("x", IntegerArgumentType.integer())
								.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("y", IntegerArgumentType.integer())
										.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("z", IntegerArgumentType.integer())
												.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("seller", StringArgumentType.greedyString())
														.executes(ShopLoggerClient::watchTp)))))));

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchremove")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("item", StringArgumentType.greedyString())
						.executes(ShopLoggerClient::watchRemove)));

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchoptions")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("item", StringArgumentType.greedyString())
						.executes(ShopLoggerClient::watchOptions)));

		// Click target of the red [Report] button in watchlist alerts (see ShopReporter).
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchreport")
				.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("id", IntegerArgumentType.integer())
						.executes(ShopLoggerClient::watchReport)));

		// Click target of the grey [Ignore] button in watchlist alerts (see WatchlistIgnore), and its undo.
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchignore")
				.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("id", IntegerArgumentType.integer())
						.executes(ShopLoggerClient::watchIgnore)));
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchunignore")
				.executes(ShopLoggerClient::watchUnignore));

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("watchbeam")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
						.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("x", IntegerArgumentType.integer())
								.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("y", IntegerArgumentType.integer())
										.then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("z", IntegerArgumentType.integer())
												.executes(ShopLoggerClient::watchBeam))))));

		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("preview")
				.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.greedyString())
				.executes(ShopLoggerClient::watchUnignore)));


	}

	// Redetect the world on every fresh join (covers singleplayer -> a real
	// server later in the same session too). Also the closest NeoForge
	// equivalent to Fabric's ClientConfigurationConnectionEvents.COMPLETE
	// (fired after a "Reconfiguring..." transition, which is how players
	// switch Snailcraft worlds without a full disconnect/rejoin) — a fresh
	// LocalPlayer/connection is created for that transition too, so this
	// single hook should cover both cases.
	@SubscribeEvent
	public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		WorldDetector.getInstance().requestRedetect();
		WatchlistJoinCheck.requestCheck();
		UpdateNoticeCheck.requestCheck();
		OnboardingLinkCheck.requestCheck();
	}

	// Automatic path: silent proximity scanning (requires the mixin).
	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();

		SilentScreenCoordinator.tickWatchdog();
		ShopAutoScanner.getInstance().tick(client);
			com.snailtools.shoplogger.mapart.MapartScanner.getInstance().tick(client);
		ShopMarkerRenderer.getInstance().tick(client);
		TeleportHighlight.getInstance().tick(client);
		WorldDetector.getInstance().tick(client);
		WatchlistJoinCheck.tick(client);
		UpdateNoticeCheck.tick(client);
		OnboardingLinkCheck.tick(client);
		NoticePopups.tick(client);
		ShopVisitAlert.tick(client);
		QolHookManager.onTick();

		if (exportKey != null && exportKey.consumeClick()) {
			exportBoth(client);
		}
		if (toggleAutoScanKey != null && toggleAutoScanKey.consumeClick()) {
			toggleAutoScan(client);
		}
		if (uploadKey != null && uploadKey.consumeClick()) {
			ShopUploader.uploadAsync(client, true);
		}
		if (toggleMarkersKey != null && toggleMarkersKey.consumeClick()) {
			toggleMarkers(client);
		}
		if (togglePrintKey != null && togglePrintKey.consumeClick()) {
			togglePrint(client);
		}
		EmptyHandItems.tick(client);
		if (openLibraryKey != null && openLibraryKey.consumeClick()) {
			if (EmptyHandItems.isCapturing()) {
				// Settings > Empty hand > Add: X registers what's in your hand
				EmptyHandItems.captureHeld(client);
				client.setScreenAndShow(com.snailtools.shoplogger.gui.SettingsScreen.emptyHand(new com.snailtools.shoplogger.gui.HomeScreen()));
			} else if (TeleportHighlight.getInstance().isArmed()) {
				TeleportHighlight.getInstance().clear();
			} else {
				client.setScreenAndShow(new com.snailtools.shoplogger.gui.HomeScreen());
			}
		}
		if (pendingSearchQuery != null) {
			String query = pendingSearchQuery;
			pendingSearchQuery = null;
			client.setScreenAndShow(new com.snailtools.shoplogger.gui.ListingsScreen(null, query));
		}
		if (pendingWatchOptionsItem != null) {
			String itemName = pendingWatchOptionsItem;
			pendingWatchOptionsItem = null;
			WatchedItem item = WatchlistStore.find(itemName);
			if (item != null) {
				client.setScreenAndShow(new com.snailtools.shoplogger.gui.WatchedItemOptionsScreen(client.gui.screen(), item));
			} else {
				ChatFormat.send(client, ChatFormat.NEUTRAL, "You're no longer watching " + itemName + ".");
			}
		}

		if (client.player != null) {
			uploadTickCounter++;
			if (uploadTickCounter >= AUTO_UPLOAD_INTERVAL_TICKS) {
				uploadTickCounter = 0;
				ShopUploader.uploadAsync(client, false);
			}
		}
	}

	@SubscribeEvent
	public void onItemTooltip(ItemTooltipEvent event) {
		Cooldowns.addQuestRotation(event.getItemStack(), event.getToolTip());
		Cooldowns.addAvailableRareCooldowns(event.getItemStack(), event.getToolTip());
	}

	private static int setWorld(CommandContext<CommandSourceStack> ctx, ShopWorld world) {
		WorldSelection.set(world);
		ctx.getSource().sendSuccess(() -> ChatFormat.prefixed(ChatFormat.SUCCESS, "World set to " + world.label() + "."), false);
		return 1;
	}

	private static int search(CommandContext<CommandSourceStack> ctx) {
		String item = StringArgumentType.getString(ctx, "item");
		if (SearchPreferences.isGuiSearch()) {
			pendingSearchQuery = item;
		} else {
			ShopSearch.searchAsync(Minecraft.getInstance(), item);
		}
		return 1;
	}

	private static int watchTp(CommandContext<CommandSourceStack> ctx) {
		String world = StringArgumentType.getString(ctx, "world");
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int y = IntegerArgumentType.getInteger(ctx, "y");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		String seller = StringArgumentType.getString(ctx, "seller");

		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.getConnection().sendCommand("shop " + seller);
		}
		TeleportHighlight.getInstance().arm(world, new BlockPos(x, y, z));
		return 1;
	}

	private static int watchRemove(CommandContext<CommandSourceStack> ctx) {
		String item = StringArgumentType.getString(ctx, "item");
		WatchlistStore.remove(item);
		ctx.getSource().sendSuccess(() -> ChatFormat.prefixed(ChatFormat.NEUTRAL, "Stopped watching " + item + "."), false);
		return 1;
	}

	private static int watchOptions(CommandContext<CommandSourceStack> ctx) {
		pendingWatchOptionsItem = StringArgumentType.getString(ctx, "item");
		return 1;
	}

	private static int watchIgnore(CommandContext<CommandSourceStack> ctx) {
		WatchlistIgnore.ignorePending(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "id"));
		return 1;
	}

	private static int watchUnignore(CommandContext<CommandSourceStack> ctx) {
		WatchlistIgnore.unignoreAll(Minecraft.getInstance());
		return 1;
	}

	private static int watchReport(CommandContext<CommandSourceStack> ctx) {
		ShopReporter.reportPending(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "id"));
		return 1;
	}

	private static int watchBeam(CommandContext<CommandSourceStack> ctx) {
		String world = StringArgumentType.getString(ctx, "world");
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int y = IntegerArgumentType.getInteger(ctx, "y");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		TeleportHighlight.getInstance().arm(world, new BlockPos(x, y, z));
		return 1;
	}

	private void toggleAutoScan(Minecraft client) {
		ShopAutoScanner scanner = ShopAutoScanner.getInstance();
		scanner.setEnabled(!scanner.isEnabled());
		ChatFormat.send(client, scanner.isEnabled() ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Scanning " + (scanner.isEnabled() ? "enabled" : "disabled") +
						" (" + scanner.knownShopCount() + " known shops)");
	}

	private void toggleMarkers(Minecraft client) {
		ShopMarkerRenderer renderer = ShopMarkerRenderer.getInstance();
		renderer.setEnabled(!renderer.isEnabled());
		ChatFormat.send(client, renderer.isEnabled() ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Recently-scanned markers " + (renderer.isEnabled() ? "ENABLED" : "disabled"));
	}

	private void togglePrint(Minecraft client) {
		boolean enabled = !ScanChatLogger.isEnabled();
		ScanChatLogger.setEnabled(enabled);
		ChatFormat.send(client, enabled ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Chat scan log " + (enabled ? "ENABLED" : "disabled"));
	}

	private void exportBoth(Minecraft client) {
		try {
			Path runDir = client.gameDirectory.toPath();
			Path csvOut = runDir.resolve("shoplogger").resolve("shops.csv");
			Path xlsxOut = runDir.resolve("shoplogger").resolve("shops.xlsx");

			CsvExporter.export(ShopLog.getAll(), csvOut);
			ExcelExporter.export(ShopLog.getAll(), xlsxOut);

			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			ChatFormat.send(client, ChatFormat.SUCCESS,
					"Exported " + ShopLog.size() + " entries at " + time + " -> run/shoplogger/");
		} catch (Exception e) {
			ChatFormat.send(client, ChatFormat.ERROR, "Export failed: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

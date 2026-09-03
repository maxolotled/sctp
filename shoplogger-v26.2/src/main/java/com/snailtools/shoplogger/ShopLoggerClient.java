package com.snailtools.shoplogger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.blaze3d.platform.InputConstants;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.qol.Cooldowns;
import com.snailtools.shoplogger.qol.QolHookManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ShopLoggerClient implements ClientModInitializer {

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

	/** How often (in ticks) to auto-upload to the Trading Post, in addition to the manual keybind. 20 ticks = 1s. */
	private static final int AUTO_UPLOAD_INTERVAL_TICKS = 20 * 60 * 15; // 15 minutes
	private int uploadTickCounter = 0;

	@Override
	public void onInitializeClient() {

		//try load config first
		Config.load();
		QolHookManager.onInit();

		exportKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.export",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		toggleAutoScanKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.toggle_autoscan",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_MINUS, // defaults to '-'; rebind in Controls
				KEY_CATEGORY
		));

		uploadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.upload",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY
		));

		toggleMarkersKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.toggle_markers",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		togglePrintKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.toggle_print",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		openLibraryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.shoplogger.open_library",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				KEY_CATEGORY
		));

		// Manual path: player right-clicks a chest/barrel themselves.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide()) return InteractionResult.PASS;

			var pos = hitResult.getBlockPos();
			var be = world.getBlockEntity(pos);
			// UseBlockCallback also fires for autoscan's own silent useItemOn() call —
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
			return InteractionResult.PASS; // never cancel/alter normal interaction
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof ContainerScreen containerScreen) {
				manualScanner.onContainerScreenOpened(containerScreen.getMenu());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("setworld")
					.then(ClientCommands.literal("firefly")
							.executes(ctx -> setWorld(ctx, ShopWorld.FIREFLY)))
					.then(ClientCommands.literal("honeybee")
							.executes(ctx -> setWorld(ctx, ShopWorld.HONEYBEE))));

			dispatcher.register(ClientCommands.literal("search")
					.then(ClientCommands.argument("item", StringArgumentType.greedyString())
							.executes(ShopLoggerClient::search)));

			// Both client-only, used as the click targets on WatchlistAlert's chat
			// message — never typed by hand, but registered as real commands (same
			// as setworld/search) so a ClickEvent.RunCommand can trigger them.
			dispatcher.register(ClientCommands.literal("watchtp")
					.then(ClientCommands.argument("world", StringArgumentType.word())
							.then(ClientCommands.argument("x", IntegerArgumentType.integer())
									.then(ClientCommands.argument("y", IntegerArgumentType.integer())
											.then(ClientCommands.argument("z", IntegerArgumentType.integer())
													.then(ClientCommands.argument("seller", StringArgumentType.greedyString())
															.executes(ShopLoggerClient::watchTp)))))));

			dispatcher.register(ClientCommands.literal("watchremove")
					.then(ClientCommands.argument("item", StringArgumentType.greedyString())
							.executes(ShopLoggerClient::watchRemove)));
		});

		// Redetect the world on every fresh join (covers singleplayer -> a real
		// server later in the same session too), and again whenever a
		// "Reconfiguring..." transition completes — that's how players switch
		// Snailcraft worlds without a full disconnect/rejoin.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			WorldDetector.getInstance().requestRedetect();
			WatchlistJoinCheck.requestCheck();
			UpdateNoticeCheck.requestCheck();
		});
		ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) -> {
			WorldDetector.getInstance().requestRedetect();
			WatchlistJoinCheck.requestCheck();
			UpdateNoticeCheck.requestCheck();
		});

		// Automatic path: silent proximity scanning (requires the mixin).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SilentScreenCoordinator.tickWatchdog();
			ShopAutoScanner.getInstance().tick(client);
			ShopMarkerRenderer.getInstance().tick(client);
			TeleportHighlight.getInstance().tick(client);
			WorldDetector.getInstance().tick(client);
			WatchlistJoinCheck.tick(client);
			UpdateNoticeCheck.tick(client);
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
			if (openLibraryKey != null && openLibraryKey.consumeClick()) {
				if (TeleportHighlight.getInstance().isArmed()) {
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

			if (client.player != null) {
				uploadTickCounter++;
				if (uploadTickCounter >= AUTO_UPLOAD_INTERVAL_TICKS) {
					uploadTickCounter = 0;
					ShopUploader.uploadAsync(client, false);
				}
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sctp", "hudlayer"), QolHookManager::onHudRender);

		ItemTooltipCallback.EVENT.register(new ItemTooltipCallback() {
			@Override
			public void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipFlag, List<Component> lines) {
				Cooldowns.addQuestRotation(stack, lines);
				Cooldowns.addAvailableRareCooldowns(stack, lines);
			}
		});


	}

	private static int setWorld(CommandContext<FabricClientCommandSource> ctx, ShopWorld world) {
		WorldSelection.set(world);
		ctx.getSource().sendFeedback(ChatFormat.prefixed(ChatFormat.SUCCESS, "World set to " + world.label() + "."));
		return 1;
	}

	private static int search(CommandContext<FabricClientCommandSource> ctx) {
		String item = StringArgumentType.getString(ctx, "item");
		if (SearchPreferences.isGuiSearch()) {
			pendingSearchQuery = item;
		} else {
			ShopSearch.searchAsync(ctx.getSource().getClient(), item);
		}
		return 1;
	}

	private static int watchTp(CommandContext<FabricClientCommandSource> ctx) {
		String world = StringArgumentType.getString(ctx, "world");
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int y = IntegerArgumentType.getInteger(ctx, "y");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		String seller = StringArgumentType.getString(ctx, "seller");

		Minecraft client = ctx.getSource().getClient();
		if (client.getConnection() != null) {
			client.getConnection().sendCommand("shop " + seller);
		}
		TeleportHighlight.getInstance().arm(world, new BlockPos(x, y, z));
		return 1;
	}

	private static int watchRemove(CommandContext<FabricClientCommandSource> ctx) {
		String item = StringArgumentType.getString(ctx, "item");
		WatchlistStore.remove(item);
		ctx.getSource().sendFeedback(ChatFormat.prefixed(ChatFormat.NEUTRAL, "Stopped watching " + item + "."));
		return 1;
	}

	private void toggleAutoScan(Minecraft client) {
		ShopAutoScanner scanner = ShopAutoScanner.getInstance();
		scanner.setEnabled(!scanner.isEnabled());
		ChatFormat.send(client, scanner.isEnabled() ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Auto-scan " + (scanner.isEnabled() ? "ENABLED" : "disabled") +
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

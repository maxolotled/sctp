package com.snailtools.shoplogger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.qol.Cooldowns;
import com.snailtools.shoplogger.qol.QolHookManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ShopLoggerClient implements ClientModInitializer {

	private static final KeyBinding.Category KEY_CATEGORY =
			KeyBinding.Category.create(Identifier.of("shoplogger", "main"));

	private final ShopScanner manualScanner = new ShopScanner();
	private KeyBinding exportKey;
	private KeyBinding toggleAutoScanKey;
	private KeyBinding uploadKey;
	private KeyBinding toggleMarkersKey;
	private KeyBinding togglePrintKey;
	private KeyBinding openLibraryKey;

	/**
	 * Set by /search when GUI mode is on, consumed on the next client tick.
	 * Opening a Screen synchronously from inside a chat command's dispatch
	 * races with ChatScreen's own close-on-submit logic (it runs right after
	 * and would immediately undo our setScreen) — deferring to the next tick,
	 * same as the hotkeys below, sidesteps that race.
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

		exportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.export",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		toggleAutoScanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.toggle_autoscan",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_MINUS, // defaults to '-'; rebind in Controls
				KEY_CATEGORY
		));

		uploadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.upload",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY
		));

		toggleMarkersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.toggle_markers",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		togglePrintKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.toggle_print",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; set it in Controls
				KEY_CATEGORY
		));

		openLibraryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.shoplogger.open_library",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				KEY_CATEGORY
		));

		// Manual path: player right-clicks a chest/barrel themselves.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient()) return ActionResult.PASS;

			var pos = hitResult.getBlockPos();
			var be = world.getBlockEntity(pos);
			// UseBlockCallback also fires for autoscan's own silent interactBlock() call —
			// only treat this as a real manual click if that's not what's happening.
			if (!ShopAutoScanner.getInstance().isSelfInteracting()) {
				if (ShopContainers.isShopContainer(be)) {
					// Quarantine other in-flight scans against a race with THIS
					// open, but trust this container's own scan — see ScanQuarantine.
					ScanQuarantine.markManualOpen(pos.toImmutable());
					// Manual clicking always wins over autoscan's silent background scanning.
					ShopAutoScanner.getInstance().onManualContainerInteract();
					manualScanner.onContainerInteract(pos.toImmutable());
				} else if (be instanceof EnderChestBlockEntity) {
					// No legitimate ShopLog scan of an ender chest exists to
					// exempt — anything logged nearby in time is suspect.
					ScanQuarantine.markManualOpen(null);
				}
			}
			return ActionResult.PASS; // never cancel/alter normal interaction
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof GenericContainerScreen containerScreen) {
				manualScanner.onContainerScreenOpened(containerScreen.getScreenHandler());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("setworld")
					.then(ClientCommandManager.literal("firefly")
							.executes(ctx -> setWorld(ctx, ShopWorld.FIREFLY)))
					.then(ClientCommandManager.literal("honeybee")
							.executes(ctx -> setWorld(ctx, ShopWorld.HONEYBEE))));

			dispatcher.register(ClientCommandManager.literal("search")
					.then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
							.executes(ShopLoggerClient::search)));
		});

		// Redetect the world on every fresh join (covers singleplayer -> a real
		// server later in the same session too), and again whenever a
		// "Reconfiguring..." transition completes — that's how players switch
		// Snailcraft worlds without a full disconnect/rejoin.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				WorldDetector.getInstance().requestRedetect());
		ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) ->
				WorldDetector.getInstance().requestRedetect());

		// Automatic path: silent proximity scanning (requires the mixin).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SilentScreenCoordinator.tickWatchdog();
			ShopAutoScanner.getInstance().tick(client);
			ShopMarkerRenderer.getInstance().tick(client);
			TeleportHighlight.getInstance().tick(client);
			WorldDetector.getInstance().tick(client);
			QolHookManager.onTick();


			if (exportKey != null && exportKey.wasPressed()) {
				exportBoth(client);
			}
			if (toggleAutoScanKey != null && toggleAutoScanKey.wasPressed()) {
				toggleAutoScan(client);
			}
			if (uploadKey != null && uploadKey.wasPressed()) {
				ShopUploader.uploadAsync(client, true);
			}
			if (toggleMarkersKey != null && toggleMarkersKey.wasPressed()) {
				toggleMarkers(client);
			}
			if (togglePrintKey != null && togglePrintKey.wasPressed()) {
				togglePrint(client);
			}
			if (openLibraryKey != null && openLibraryKey.wasPressed() && client.currentScreen == null) {
				client.setScreen(new com.snailtools.shoplogger.gui.HomeScreen());
			}
			if (pendingSearchQuery != null) {
				String query = pendingSearchQuery;
				pendingSearchQuery = null;
				client.setScreen(new com.snailtools.shoplogger.gui.ListingsScreen(null, query));
			}

			if (client.player != null) {
				uploadTickCounter++;
				if (uploadTickCounter >= AUTO_UPLOAD_INTERVAL_TICKS) {
					uploadTickCounter = 0;
					ShopUploader.uploadAsync(client, false);
				}
			}
		});

		HudElementRegistry.addLast(Identifier.of("sctp", "hudlayer"), QolHookManager::onHudRender);


		ItemTooltipCallback.EVENT.register(new ItemTooltipCallback() {
			@Override
			public void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipType tooltipType, List<Text> lines) {
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

	private void toggleAutoScan(MinecraftClient client) {
		ShopAutoScanner scanner = ShopAutoScanner.getInstance();
		scanner.setEnabled(!scanner.isEnabled());
		ChatFormat.send(client, scanner.isEnabled() ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Auto-scan " + (scanner.isEnabled() ? "ENABLED" : "disabled") +
						" (" + scanner.knownShopCount() + " known shops)");
	}

	private void toggleMarkers(MinecraftClient client) {
		ShopMarkerRenderer renderer = ShopMarkerRenderer.getInstance();
		renderer.setEnabled(!renderer.isEnabled());
		ChatFormat.send(client, renderer.isEnabled() ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Recently-scanned markers " + (renderer.isEnabled() ? "ENABLED" : "disabled"));
	}

	private void togglePrint(MinecraftClient client) {
		boolean enabled = !ScanChatLogger.isEnabled();
		ScanChatLogger.setEnabled(enabled);
		ChatFormat.send(client, enabled ? ChatFormat.SUCCESS : ChatFormat.NEUTRAL,
				"Chat scan log " + (enabled ? "ENABLED" : "disabled"));
	}

	private void exportBoth(MinecraftClient client) {
		try {
			Path runDir = client.runDirectory.toPath();
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

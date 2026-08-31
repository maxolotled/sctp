package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Automatically detects which Snailcraft world (Firefly/Honeybee) the player
 * is currently on, without requiring /setworld. Silently runs "/help" (via
 * SilentScreenCoordinator, so the menu never actually shows), and reads the
 * "Current World: X" line out of the Profile item's lore in the resulting
 * screen.
 *
 * Triggered on every ClientPlayConnectionEvents.JOIN (covers both a fresh
 * server join and returning to singleplayer/another server later in the same
 * session), and again every time ClientConfigurationConnectionEvents.COMPLETE
 * fires — that's the event for a server-driven "Reconfiguring..." transition,
 * which is how players switch between worlds without a full disconnect/rejoin.
 */
public final class WorldDetector implements SilentScreenCoordinator.Listener {

	private static final WorldDetector INSTANCE = new WorldDetector();
	public static WorldDetector getInstance() { return INSTANCE; }

	private static final String HELP_COMMAND = "help";
	private static final String WORLD_LABEL = "Current World:";
	/** Minimum time between attempts, so a busy SilentScreenCoordinator just gets retried next tick instead of spammed. */
	private static final long RETRY_COOLDOWN_MS = 2000L;

	private boolean pendingRequest = false; // set by ClientPlayConnectionEvents.JOIN / requestRedetect()
	private boolean armed = false;
	private int armedSyncId = -1;
	private long lastAttemptAt = 0L;

	private WorldDetector() {}

	/** Call whenever we know (or suspect) the player's world may have changed. */
	public void requestRedetect() {
		pendingRequest = true;
		WorldSelection.startDetectionGrace();
	}

	public void tick(Minecraft client) {
		if (!pendingRequest || armed) return;
		if (client.player == null || client.getConnection() == null) return;
		// /help is a real vanilla command — off the Snailcraft server (e.g. singleplayer, or any
		// other server) it just dumps the full command list to chat instead of opening a menu.
		if (client.hasSingleplayerServer()) {
			pendingRequest = false;
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastAttemptAt < RETRY_COOLDOWN_MS) return;
		lastAttemptAt = now;

		if (!SilentScreenCoordinator.arm(this)) return; // something else mid-silent-open; retry next tick

		armed = true;
		armedSyncId = -1;
		pendingRequest = false;
		client.getConnection().sendCommand(HELP_COMMAND);
	}

	// ---- SilentScreenCoordinator.Listener ----

	@Override
	public void onScreenSuppressed(AbstractContainerMenu handler) {
		if (!armed) return;
		armedSyncId = handler.containerId;
	}

	@Override
	public void onInventorySynced(int syncId, ClientPacketListener netHandler) {
		if (!armed || syncId != armedSyncId || armedSyncId == -1) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			disarm();
			return;
		}

		AbstractContainerMenu handler = client.player.containerMenu;
		if (handler != null && handler.containerId == syncId) {
			extractWorld(handler);
		}

		netHandler.send(new ServerboundContainerClosePacket(syncId));
		client.player.containerMenu = client.player.inventoryMenu;

		disarm();
	}

	@Override
	public void onWatchdogTimeout() {
		disarm();
	}

	private void disarm() {
		armed = false;
		armedSyncId = -1;
		SilentScreenCoordinator.disarm(this);
	}

	private void extractWorld(AbstractContainerMenu handler) {
		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) continue;

			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;

			for (Component line : lore.lines()) {
				String plain = line.getString();
				int idx = plain.indexOf(WORLD_LABEL);
				if (idx < 0) continue;

				String name = plain.substring(idx + WORLD_LABEL.length()).trim();
				ShopWorld world = ShopWorld.fromString(name);
				if (world != null) {
					WorldSelection.set(world);
				}
				return;
			}
		}
	}
}

package com.snailtools.shoplogger.gui.data;

/** Mirrors one entry from GET /marketplace/notifications/for-mc — see worker.js's handleGetNotificationsForMc. */
public class MarketplaceNotification {
	public String type;
	public String message;
}

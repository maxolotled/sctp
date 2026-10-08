package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.neoforged.fml.ModList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * One-time-per-situation nudge (a welcome popup for new players, see NoticePopups;
 * a chat line after an update) pointing to the site's onboarding pages, checked right
 * after join alongside UpdateNoticeCheck. Two situations, told apart by comparing the
 * mod's own version against the last version Config remembers seeing:
 *   - never seen before, AND the config file itself is brand new -> a genuinely fresh
 *     install -> link to the mod walkthrough (how everything works)
 *   - never seen before, but the config file already existed (an existing player whose
 *     config just predates this feature) OR seen before but the version changed ->
 *     the mod was just updated -> link to what's new
 * Each only ever fires once — remembering the version (Config, so it survives restarts)
 * is what makes that so.
 */
public final class OnboardingLinkCheck {

	private static final String CONFIG_KEY = "onboarding/lastSeenVersion";
	private static boolean pending = false;

	private OnboardingLinkCheck() {}

	/** Call from ClientPlayerNetworkEvent.LoggingIn. */
	public static void requestCheck() {
		pending = true;
	}

	/** Call every tick — runs the check exactly once per join. */
	public static void tick(Minecraft client) {
		if (!pending || client.player == null) return;
		pending = false;

		String current = modVersion();
		String lastSeen = Config.get(CONFIG_KEY, String.class);

		if (lastSeen == null && !Config.existedBeforeLoad()) {
			NoticePopups.show("Welcome to Shop Logger!", "New here? The walkthrough shows what the mod does and how to get the most out of it. Press X in-game any time to open the menu.", "See how it works", "https://sctp.nl/onboarding/mod");
		} else if (lastSeen == null || !lastSeen.equals(current)) {
			report(client, "Shop Logger updated! ", "See what's new", "https://sctp.nl/onboarding/update");
		}

		if (lastSeen == null || !lastSeen.equals(current)) {
			Config.update(CONFIG_KEY, current);
		}
	}

	private static void report(Minecraft client, String lead, String linkText, String url) {
		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal(lead).withStyle(ChatFormat.SUCCESS))
				.append(Component.literal(linkText).setStyle(Style.EMPTY
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))));
		client.player.sendSystemMessage(msg);
	}

	private static String modVersion() {
		return ModList.get().getModContainerById("shoplogger")
				.map(c -> c.getModInfo().getVersion().toString())
				.orElse("0");
	}
}

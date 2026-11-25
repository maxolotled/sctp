// Beta mode: the site as it will be once live prices, the watchlist's join
// notifications and the marketplace are gone (see /statement/). Visiting any
// page with #beta turns it on for this browser, #nobeta turns it off again.
// Loaded without defer in <head>, so every script on the page can read
// window.sctpBeta (site-nav.js and account-widget.js work it out themselves
// on pages that don't load this file).
(function () {
	"use strict";
	var on = false;
	try {
		if (location.hash === "#beta") localStorage.setItem("sctp_beta", "1");
		else if (location.hash === "#nobeta") localStorage.removeItem("sctp_beta");
		on = localStorage.getItem("sctp_beta") === "1";
	} catch (e) {
		on = location.hash === "#beta";
	}
	window.sctpBeta = on;
})();

// Shared site navigation: one slim app bar at the top of every page that loads
// this script — logo, the main sections, a "More" menu that holds everything
// else, Download, and the account button (account-widget.js still renders into
// #accountWidgetMount, so load this script before it). The page's own header
// (title + tagline) stays where it is as a compact title row (see site.css); its
// old #siteNavMount is left empty and hidden.
(function () {
	"use strict";

	var MAIN = [
		{ href: "/search/", label: "Search", icon: "&#8981;" },
		{ href: "/items/", label: "Items" },
		{ href: "/marketplace/", label: "Marketplace" },
		{ href: "/mapart/", label: "Mapart" },
		{ href: "/rare-dle/", label: "Rare-dle", cta: true }
	];
	var MORE = [
		{ title: "Trade", links: [
			{ href: "/auction/", label: "Rare auctions" },
			{ href: "/list/", label: "Search by material list" },
			{ href: "/store/manage/", label: "Manage my store" },
			{ href: "/stats/", label: "World & shop statistics" }
		] },
		{ title: "Collect & explore", links: [
			{ href: "/collection/", label: "My collection & wishlist" },
			{ href: "/roadmap/", label: "Roadmap" },
			{ href: "/onboarding/update", label: "What's new" }
		] },
		{ title: "Help", links: [
			{ href: "/docs/faq", label: "FAQ" },
			{ href: "/onboarding/mod", label: "Mod features & installing" },
			{ href: "/onboarding/web", label: "Website features" },
			{ href: "/suggest/", label: "Suggest something" },
			{ href: "/bug/", label: "Report a bug" },
			{ href: "/report/", label: "Report a player" }
		] }
	];
	var LOGO = '<img src="/sctp-logo.png" alt="" width="28" height="28">';

	var css =
		".bbar{position:sticky;top:0;z-index:200;background:rgba(var(--bg-rgb,16,27,20),0.86);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);border-bottom:1px solid var(--line,#33453A);}" +
		".bbar-in{max-width:1400px;margin:0 auto;padding:0 20px;height:52px;display:flex;align-items:center;gap:6px;}" +
		".bbar a{text-decoration:none;}" +
		".bbar-logo{display:flex;align-items:center;gap:8px;color:var(--text,#EAEFE7);font-weight:800;font-size:16px;letter-spacing:.02em;margin-right:10px;flex-shrink:0;}" +
		".bbar-logo img{width:28px;height:28px;display:block;}" +
		".bbar-main{display:flex;align-items:center;gap:2px;min-width:0;}" +
		".bbar-main a,.bbar-more-btn{color:var(--muted,#8FA593);font-size:14px;font-weight:600;padding:7px 11px;border-radius:8px;white-space:nowrap;background:none;border:none;cursor:pointer;font-family:inherit;line-height:1.2;}" +
		".bbar-main a:hover,.bbar-more-btn:hover,.bbar-more.open .bbar-more-btn{color:var(--text,#EAEFE7);background:var(--panel-alt,#22332A);}" +
		".bbar-main a.on{color:var(--accent,#B7E23D);background:rgba(var(--accent-rgb,183,226,61),0.10);}" +
		".bbar-main a.cta{color:#2A1408;background:linear-gradient(90deg,#FFB347,#FF7A59,#F0508F);margin-left:4px;padding:6px 12px;border-radius:999px;font-weight:800;}" +
		".bbar-main a.cta:hover,.bbar-main a.cta.on{filter:brightness(1.07);color:#2A1408;}" +
		".bbar-more{position:relative;}" +
		".bbar-more-btn .caret{font-size:9px;margin-left:3px;}" +
		".bbar-panel{display:none;position:absolute;top:calc(100% + 8px);left:0;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:14px;box-shadow:0 16px 40px rgba(0,0,0,.45);gap:18px;grid-template-columns:repeat(3,minmax(170px,1fr));z-index:220;}" +
		".bbar-more.open .bbar-panel{display:grid;}" +
		".bbar-col h4{margin:0 0 6px;padding:0 8px;font-size:10.5px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted,#8FA593);font-weight:700;}" +
		".bbar-col a{display:block;padding:6px 8px;border-radius:7px;color:var(--text,#EAEFE7);font-size:13.5px;white-space:nowrap;}" +
		".bbar-col a:hover{background:var(--panel-alt,#22332A);color:var(--accent,#B7E23D);}" +
		".bbar-col a.on{color:var(--accent,#B7E23D);}" +
		".bbar-right{margin-left:auto;display:flex;align-items:center;gap:8px;flex-shrink:0;}" +
		".bbar-dl{display:inline-flex;align-items:center;gap:6px;background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F)!important;font-weight:800;font-size:13px;padding:7px 13px;border-radius:9px;white-space:nowrap;}" +
		".bbar-dl:hover{filter:brightness(1.07);}" +
		".bbar-burger{display:none;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;width:36px;height:36px;font-size:16px;cursor:pointer;}" +
		".bbar-drawer{display:none;}" +
		"@media (max-width:900px){" +
			".bbar-main,.bbar-dl .t{display:none;}" +
			".bbar-burger{display:inline-flex;align-items:center;justify-content:center;}" +
			".bbar-drawer.open{display:block;position:fixed;top:52px;left:0;right:0;bottom:0;overflow:auto;background:var(--bg,#101B14);padding:12px 16px 40px;z-index:210;}" +
			".bbar-drawer a{display:block;text-decoration:none;padding:10px 8px;color:var(--text,#EAEFE7);font-size:15px;border-bottom:1px solid var(--line,#33453A);}" +
			".bbar-drawer h4{margin:16px 0 4px;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted,#8FA593);}" +
		"}" +
		// the page's own nav mount is replaced by the bar
		"header.site #siteNavMount{display:none!important;}";

	var style = document.createElement("style");
	style.textContent = css;
	document.head.appendChild(style);

	function norm(p) { return String(p || "").replace(/(index)?\.html$/, "").replace(/\/+$/, "") || "/"; }
	function isOn(href) {
		var here = norm(location.pathname), t = norm(href);
		if (t === "/") return here === "/";
		return here === t || here.indexOf(t + "/") === 0;
	}

	function render() {
		var bar = document.createElement("div");
		bar.className = "bbar";
		bar.innerHTML =
			'<div class="bbar-in">' +
				'<a class="bbar-logo" href="/" title="Snailcraft Trading Post">' + LOGO + "SCTP</a>" +
				'<nav class="bbar-main" aria-label="Main">' +
					MAIN.map(function (l) { return '<a href="' + l.href + '" class="' + (l.cta ? "cta " : "") + (isOn(l.href) ? "on" : "") + '">' + l.label + "</a>"; }).join("") +
					'<div class="bbar-more"><button type="button" class="bbar-more-btn" aria-haspopup="true" aria-expanded="false">More<span class="caret">&#9662;</span></button>' +
						'<div class="bbar-panel">' + MORE.map(function (g) {
							return '<div class="bbar-col"><h4>' + g.title + "</h4>" + g.links.map(function (l) { return '<a href="' + l.href + '"' + (isOn(l.href) ? ' class="on"' : "") + ">" + l.label + "</a>"; }).join("") + "</div>";
						}).join("") +
						'</div>' +
					"</div>" +
				"</nav>" +
				'<div class="bbar-right">' +
					'<a class="bbar-dl" href="/#download" title="Download the Shop Logger mod">&#11015;<span class="t"> Download</span></a>' +
					'<div id="accountWidgetMount"></div>' +
					'<button type="button" class="bbar-burger" aria-label="Menu" aria-expanded="false">&#9776;</button>' +
				"</div>" +
			"</div>" +
			'<div class="bbar-drawer">' +
				MAIN.map(function (l) { return '<a href="' + l.href + '">' + l.label + "</a>"; }).join("") +
				MORE.map(function (g) { return "<h4>" + g.title + "</h4>" + g.links.map(function (l) { return '<a href="' + l.href + '">' + l.label + "</a>"; }).join(""); }).join("") +
			"</div>";

		// the page's old nav mount must not keep the id account-widget.js looks for
		var old = document.getElementById("siteNavMount");
		if (old) old.innerHTML = "";
		document.body.insertBefore(bar, document.body.firstChild);

		var more = bar.querySelector(".bbar-more"), moreBtn = bar.querySelector(".bbar-more-btn");
		moreBtn.addEventListener("click", function (e) {
			e.stopPropagation();
			var open = !more.classList.contains("open");
			more.classList.toggle("open", open);
			moreBtn.setAttribute("aria-expanded", open ? "true" : "false");
		});
		document.addEventListener("click", function (e) { if (!more.contains(e.target)) more.classList.remove("open"); });
		document.addEventListener("keydown", function (e) { if (e.key === "Escape") { more.classList.remove("open"); if (drawer.classList.contains("open")) setDrawer(false); } });
		// The phone menu lives outside the bar: the bar's backdrop blur makes it the
		// containing block for position:fixed children, which squeezed the
		// full-screen drawer into the bar's 52px (only "Search" was visible).
		var drawer = bar.querySelector(".bbar-drawer"), burger = bar.querySelector(".bbar-burger");
		bar.parentNode.insertBefore(drawer, bar.nextSibling);
		function setDrawer(open) {
			drawer.classList.toggle("open", open);
			burger.setAttribute("aria-expanded", open ? "true" : "false");
			document.documentElement.style.overflow = open ? "hidden" : ""; // no page scrolling behind the open menu
		}
		burger.addEventListener("click", function () { setDrawer(!drawer.classList.contains("open")); });
		drawer.addEventListener("click", function (e) { if (e.target.closest("a")) setDrawer(false); });
		window.addEventListener("resize", function () { if (window.innerWidth > 900 && drawer.classList.contains("open")) setDrawer(false); });

	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render);
	else render();
})();

// The redesigned site's navigation (the /beta/ preview only). Replaces
// site-nav.js on every page under /beta/:
//  - one slim app bar at the top: logo, the main sections, a "More" menu that
//    holds everything else (grouped), Download, and the account button
//    (account-widget.js still renders into #accountWidgetMount, same as before);
//  - the page's own header (title + tagline) stays where it is, restyled as a
//    compact title row by beta.css — themes keep hooking into it as usual;
//  - keeps you inside the beta: links to pages that have a /beta/ copy are
//    pointed there (see BETA_SECTIONS), now and for anything added later.
(function () {
	"use strict";
	document.documentElement.classList.add("beta");

	// First path segments that have a copy under /beta/ (scripts/build-beta.js
	// writes the same list). "" is the homepage.
	var BETA_SECTIONS = ["", "search", "items", "marketplace", "mapart", "rare-dle", "auction", "collection", "list", "stats",
		"roadmap", "onboarding", "suggest", "bug", "report", "account", "store", "register", "reset-password", "verify-link", "privacy", "f", "s"];

	var MAIN = [
		{ href: "/beta/search/", label: "Search", icon: "&#8981;" },
		{ href: "/beta/items/", label: "Items" },
		{ href: "/beta/marketplace/", label: "Marketplace" },
		{ href: "/beta/mapart/", label: "Mapart" },
		{ href: "/beta/rare-dle/", label: "Rare-dle", cta: true }
	];
	var MORE = [
		{ title: "Trade", links: [
			{ href: "/beta/auction/", label: "Rare auctions" },
			{ href: "/beta/list/", label: "Search by material list" },
			{ href: "/beta/store/manage/", label: "Manage my store" },
			{ href: "/beta/stats/", label: "World & shop statistics" }
		] },
		{ title: "Collect & explore", links: [
			{ href: "/beta/collection/", label: "My collection & wishlist" },
			{ href: "/beta/roadmap/", label: "Roadmap" },
			{ href: "/beta/onboarding/update", label: "What's new" }
		] },
		{ title: "Help", links: [
			{ href: "/docs/faq", label: "FAQ" },
			{ href: "/beta/onboarding/mod", label: "Mod features & installing" },
			{ href: "/beta/onboarding/web", label: "Website features" },
			{ href: "/beta/suggest/", label: "Suggest something" },
			{ href: "/beta/bug/", label: "Report a bug" },
			{ href: "/beta/report/", label: "Report a player" }
		] }
	];
	var LOGO = '<img src="/sctp-logo.png" alt="" width="28" height="28">';

	var css =
		"html.beta body{padding-top:0;}" +
		".bbar{position:sticky;top:0;z-index:200;background:rgba(var(--bg-rgb,16,27,20),0.86);-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);border-bottom:1px solid var(--line,#33453A);}" +
		".bbar-in{max-width:1400px;margin:0 auto;padding:0 20px;height:52px;display:flex;align-items:center;gap:6px;}" +
		".bbar a{text-decoration:none;}" +
		".bbar-logo{display:flex;align-items:center;gap:8px;color:var(--text,#EAEFE7);font-weight:800;font-size:16px;letter-spacing:.02em;margin-right:10px;flex-shrink:0;}" +
		".bbar-logo img{width:28px;height:28px;display:block;}" +
		".bbar-beta{font-size:9.5px;font-weight:800;letter-spacing:.06em;color:var(--accent-ink,#16210F);background:var(--accent,#B7E23D);border-radius:999px;padding:1px 6px;margin-left:-2px;}" +
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
		".bbar-foot{grid-column:1/-1;border-top:1px solid var(--line,#33453A);padding-top:10px;font-size:12.5px;color:var(--muted,#8FA593);display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;}" +
		".bbar-foot a{color:var(--shell,#D9C89A);}" +
		".bbar-right{margin-left:auto;display:flex;align-items:center;gap:8px;flex-shrink:0;}" +
		".bbar-dl{display:inline-flex;align-items:center;gap:6px;background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F)!important;font-weight:800;font-size:13px;padding:7px 13px;border-radius:9px;white-space:nowrap;}" +
		".bbar-dl:hover{filter:brightness(1.07);}" +
		".bbar-burger{display:none;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;width:36px;height:36px;font-size:16px;cursor:pointer;}" +
		".bbar-drawer{display:none;}" +
		"@media (max-width:900px){" +
			".bbar-main,.bbar-dl .t{display:none;}" +
			".bbar-burger{display:inline-flex;align-items:center;justify-content:center;}" +
			".bbar-drawer.open{display:block;position:fixed;top:52px;left:0;right:0;bottom:0;overflow:auto;background:var(--bg,#101B14);padding:12px 16px 40px;z-index:210;}" +
			".bbar-drawer a{display:block;padding:10px 8px;color:var(--text,#EAEFE7);font-size:15px;border-bottom:1px solid var(--line,#33453A);}" +
			".bbar-drawer h4{margin:16px 0 4px;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted,#8FA593);}" +
		"}" +
		// the page's own nav mount is replaced by the bar
		"html.beta header.site #siteNavMount{display:none!important;}";

	var style = document.createElement("style");
	style.textContent = css;
	document.head.appendChild(style);

	function norm(p) { return String(p || "").replace(/(index)?\.html$/, "").replace(/\/+$/, "") || "/"; }
	function isOn(href) {
		var here = norm(location.pathname), t = norm(href);
		if (t === "/beta") return here === "/beta";
		return here === t || here.indexOf(t + "/") === 0;
	}

	// "/items/x" -> "/beta/items/x" when that section has a beta copy.
	function betaHref(href) {
		if (!href || href.charAt(0) !== "/" || href.charAt(1) === "/" || /^\/beta(\/|$|[?#])/.test(href)) return null;
		var path = href.split(/[?#]/)[0];
		if (/\.[a-z0-9]{2,5}$/i.test(path) && !/\.html$/i.test(path)) return null; // files: images, jars, json...
		var first = path.split("/")[1] || "";
		if (first === "index.html") first = "";
		if (BETA_SECTIONS.indexOf(first) === -1) return null;
		return "/beta" + (href === "/" ? "/" : href);
	}
	function fixLinks(root) {
		(root.querySelectorAll ? root.querySelectorAll("a[href^='/']") : []).forEach(function (a) {
			var b = betaHref(a.getAttribute("href"));
			if (b) a.setAttribute("href", b);
		});
	}

	function render() {
		var bar = document.createElement("div");
		bar.className = "bbar";
		var classic = location.pathname.replace(/^\/beta(?=\/|$)/, "") || "/";
		bar.innerHTML =
			'<div class="bbar-in">' +
				'<a class="bbar-logo" href="/beta/" title="Snailcraft Trading Post">' + LOGO + "SCTP</a>" +
				'<span class="bbar-beta" title="You\'re previewing the new design">BETA</span>' +
				'<nav class="bbar-main" aria-label="Main">' +
					MAIN.map(function (l) { return '<a href="' + l.href + '" class="' + (l.cta ? "cta " : "") + (isOn(l.href) ? "on" : "") + '">' + l.label + "</a>"; }).join("") +
					'<div class="bbar-more"><button type="button" class="bbar-more-btn" aria-haspopup="true" aria-expanded="false">More<span class="caret">&#9662;</span></button>' +
						'<div class="bbar-panel">' + MORE.map(function (g) {
							return '<div class="bbar-col"><h4>' + g.title + "</h4>" + g.links.map(function (l) { return '<a href="' + l.href + '"' + (isOn(l.href) ? ' class="on"' : "") + ">" + l.label + "</a>"; }).join("") + "</div>";
						}).join("") +
						'<div class="bbar-foot"><span>You\'re trying the new design.</span><a href="' + classic + '">Back to the classic site &rarr;</a></div></div>' +
					"</div>" +
				"</nav>" +
				'<div class="bbar-right">' +
					'<a class="bbar-dl" href="/beta/#download" title="Download the Shop Logger mod">&#11015;<span class="t"> Download</span></a>' +
					'<div id="accountWidgetMount"></div>' +
					'<button type="button" class="bbar-burger" aria-label="Menu" aria-expanded="false">&#9776;</button>' +
				"</div>" +
			"</div>" +
			'<div class="bbar-drawer">' +
				MAIN.map(function (l) { return '<a href="' + l.href + '">' + l.label + "</a>"; }).join("") +
				MORE.map(function (g) { return "<h4>" + g.title + "</h4>" + g.links.map(function (l) { return '<a href="' + l.href + '">' + l.label + "</a>"; }).join(""); }).join("") +
				'<h4>Beta</h4><a href="' + classic + '">Back to the classic site</a>' +
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
		document.addEventListener("keydown", function (e) { if (e.key === "Escape") { more.classList.remove("open"); drawer.classList.remove("open"); } });
		var drawer = bar.querySelector(".bbar-drawer"), burger = bar.querySelector(".bbar-burger");
		burger.addEventListener("click", function () {
			var open = !drawer.classList.contains("open");
			drawer.classList.toggle("open", open);
			burger.setAttribute("aria-expanded", open ? "true" : "false");
		});

		fixLinks(document);
		new MutationObserver(function (muts) {
			muts.forEach(function (m) {
				m.addedNodes.forEach(function (n) {
					if (n.nodeType !== 1) return;
					if (n.tagName === "A") { var b = betaHref(n.getAttribute("href")); if (b) n.setAttribute("href", b); }
					else fixLinks(n);
				});
			});
		}).observe(document.body, { childList: true, subtree: true });
	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render);
	else render();
})();

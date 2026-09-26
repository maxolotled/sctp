/*
 * SCTP — Lunar New Year theme (pairs with themes/lunarnewyear.css).
 * Firecracker bursts, a dragon flying
 * past, red lanterns across the top, red envelopes, gold ingots and mandarins
 * on the header, a plum-blossom sprig on the snail, festive icons. The
 * footer names the zodiac animal of the coming year automatically.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "lunarnewyear";

	// Lunar New Year falls in Jan/Feb, so from October on, greet the coming year.
	var ZODIAC = ["Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig"];
	var now = new Date();
	var lunarYear = now.getMonth() >= 9 ? now.getFullYear() + 1 : now.getFullYear();
	var animal = ZODIAC[((lunarYear - 2020) % 12 + 12) % 12];

	function lantern(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 32" width="' + w + '" height="' + Math.round(w * 32 / 20) + '">' +
			'<rect x="6" y="0" width="8" height="3" rx="1" fill="#F5C35B"/>' +
			'<ellipse cx="10" cy="12" rx="9.5" ry="9" fill="#E0332E" style="filter:drop-shadow(0 0 5px #FF4D3D)"/>' +
			'<path d="M10 3c-4 2-5 6-5 9s1 7 5 9M10 3c4 2 5 6 5 9s-1 7-5 9" stroke="#B8231E" stroke-width="1" fill="none"/>' +
			'<rect x="5" y="20" width="10" height="2.6" rx="1" fill="#F5C35B"/>' +
			'<path d="M8 23v8M10 23v9M12 23v8" stroke="#F5C35B" stroke-width="1"/></svg>';
	}
	function envelope(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 22 30" width="' + w + '" height="' + Math.round(w * 30 / 22) + '" style="transform:rotate(-10deg)">' +
			'<rect x="1" y="1" width="20" height="28" rx="2" fill="#D62B26"/><path d="M1 8c5 4 15 4 20 0V3c0-1-1-2-2-2H3C2 1 1 2 1 3z" fill="#B8231E"/>' +
			'<circle cx="11" cy="10" r="3.6" fill="#F5C35B"/><rect x="9.4" y="8.4" width="3.2" height="3.2" fill="#B8231E"/>' +
			'<path d="M5 18h12M5 22h12" stroke="#F5C35B" stroke-width="1" opacity=".6"/></svg>';
	}
	function ingot(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 20" width="' + w + '" height="' + Math.round(w * 20 / 34) + '">' +
			'<path d="M1 7c3 1 6 1 8 0h16c2 1 5 1 8 0-1 7-6 12-16 12S2 14 1 7z" fill="#E9A91B"/>' +
			'<ellipse cx="17" cy="7.5" rx="8.5" ry="5.5" fill="#FFD04D"/><ellipse cx="17" cy="6.5" rx="5" ry="3" fill="#FFE4A8"/>' +
			'<path d="M5 11c3 3 8 4 12 4" stroke="#FFE9A8" stroke-width="1.2" fill="none" opacity=".7"/></svg>';
	}
	function mandarin(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '">' +
			'<circle cx="12" cy="14" r="9.5" fill="#FF8A1F"/><circle cx="9" cy="11" r="2.2" fill="#FFB35A" opacity=".7"/>' +
			'<path d="M12 5c0-2 1-3 2-4" stroke="#4E6B2A" stroke-width="1.4"/><path d="M13 4c3-3 7-2 8 0-3 1-5 1-8 0z" fill="#3E9E4A"/></svg>';
	}
	function coin(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><path fill-rule="evenodd" d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm-3 7v6h6V9z" fill="#F5C35B"/>' +
			'<circle cx="12" cy="12" r="7.6" fill="none" stroke="#C98E1E" stroke-width="1"/></svg>';
	}

	var DRAGON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 140 44">' +
		'<path d="M4 26c10-14 20-14 30 0s20 14 30 0 20-14 30 0 14 10 22 4" stroke="#E0332E" stroke-width="9" fill="none" stroke-linecap="round"/>' +
		'<path d="M4 26c10-14 20-14 30 0s20 14 30 0 20-14 30 0 14 10 22 4" stroke="#F5C35B" stroke-width="2" fill="none" stroke-dasharray="2 6" stroke-linecap="round" transform="translate(0 -4)"/>' +
		'<path d="M8 22l-6-8M22 16l-2-10M38 26l-1-10M52 34l1-10M68 24l-1-10M82 16l-2-10M98 24l-1-10" stroke="#F5C35B" stroke-width="2" stroke-linecap="round"/>' +
		'<g transform="translate(112 12)"><path d="M0 16c0-8 6-14 14-14s12 6 12 12c0 4-3 6-6 6H6C3 20 0 19 0 16z" fill="#E0332E"/>' +
		'<path d="M14 3l-4-3M18 3l2-4" stroke="#F5C35B" stroke-width="2" stroke-linecap="round"/>' +
		'<circle cx="17" cy="9" r="2" fill="#FFE4A8"/><circle cx="17.5" cy="9" r="1" fill="#1A0A0A"/>' +
		'<path d="M24 14c6 0 10 4 14 2M22 18c5 2 8 6 12 6" stroke="#F5C35B" stroke-width="1.4" fill="none" stroke-linecap="round"/>' +
		'<path d="M20 20h6l-2 3z" fill="#FFFFFF"/></g></svg>';

	var CONFIG = {
		id: ID,
		titlePrefix: "🧧",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#1A0A0A"/><g transform="translate(10 3) scale(1.05)">' +
			lantern(20).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🐉", "🐛": "🐲", "🏆": "🧧", "🧱": "🥟", "🖼": "🏮", "🛒": "🍊", "✨": "🎆", "🐌": "🐉",
			"🔒": "🪙", "🎯": "🀄", "📝": "🧧", "🚩": "🥢", "🔑": "🗝️", "📊": "📈", "🏪": "🏯", "💡": "🏮", "🎨": "🏮"
		},
		kickerIcons: { "Rare-dle": "🀄" },
		logo: {
			width: "70%", top: "-40%", right: "-20%", rotate: 20,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 24">' +
				'<path d="M2 22C8 16 16 10 28 4" stroke="#6B3A2A" stroke-width="2" fill="none" stroke-linecap="round"/>' +
				'<g fill="#FFB7C9"><circle cx="10" cy="15" r="4.4"/><circle cx="20" cy="9" r="4.8"/><circle cx="27" cy="4" r="3"/></g>' +
				'<g fill="#FF4D3D"><circle cx="10" cy="15" r="1.4"/><circle cx="20" cy="9" r="1.6"/></g></svg>'
		},
		props: { side: "left", items: [envelope(22), ingot(34), mandarin(22), mandarin(18)] },
		garland: {
			spacing: 54, sag: 10, itemWidth: 18, wire: "rgba(245,195,91,0.45)", anim: "lamp",
			colors: ["#E0332E"],
			item: function () { return lantern(18); }
		},
		footer: { left: lantern(14), text: "Happy Lunar New Year from SCTP: Year of the " + animal, right: coin(20), font: '"Ma Shan Zheng", var(--font-display, serif)' },
		bursts: { every: [3000, 7000], first: [1500, 3000], sparks: 26, colors: ["#FF4D3D", "#FFC247", "#FFE4A8", "#E0332E"] },
		flyers: [{ path: "fly", svg: DRAGON, width: 170, height: 54, every: [40000, 70000], first: [6000, 12000], dur: 15 }],
		toggle: { icon: "🧧", noun: "firecrackers" }
	};

	// ---- which theme to show: the live one, or a preview (?theme=<name>; ?theme=live stops it) ----
	var want = null;
	try {
		var q = new URLSearchParams(location.search).get("theme");
		if (q !== null) {
			if (/^[a-z0-9-]+$/.test(q) && q !== "live") sessionStorage.setItem("sctp_theme_preview", q);
			else sessionStorage.removeItem("sctp_theme_preview");
		}
		want = sessionStorage.getItem("sctp_theme_preview");
	} catch (e) {}
	var previewing = !!document.querySelector("script[data-theme-preview]");
	if (want && want !== ID && !previewing) {
		var link = document.querySelector('link[href="/theme.css"]');
		if (link) link.href = "/themes/" + want + ".css";
		var ps = document.createElement("script");
		ps.src = "/themes/" + want + ".js";
		ps.setAttribute("data-theme-preview", want);
		document.head.appendChild(ps);
		return;
	}
	if (previewing) previewPill();
	function go() { window.SCTPThemeFX(CONFIG); }
	if (window.SCTPThemeFX) go();
	else { var fx = document.createElement("script"); fx.src = "/themes/fx.js"; fx.onload = go; document.head.appendChild(fx); }

	function previewPill() {
		function add() {
			var p = document.createElement("div");
			p.style.cssText = "position:fixed;bottom:calc(14px + env(safe-area-inset-bottom,0px));left:50%;transform:translateX(-50%);z-index:500;background:var(--panel);color:var(--text);border:1px solid var(--accent);border-radius:999px;padding:6px 6px 6px 14px;font:600 12.5px/1.2 system-ui,sans-serif;box-shadow:0 6px 20px rgba(0,0,0,.45);display:flex;gap:10px;align-items:center;white-space:nowrap;";
			p.innerHTML = 'Previewing the <b style="color:var(--accent)">' + ID + '</b> theme <a href="?theme=live" style="background:var(--accent);color:var(--accent-ink);border-radius:999px;padding:4px 10px;text-decoration:none;">Stop</a>';
			document.body.appendChild(p);
		}
		if (document.body) add(); else document.addEventListener("DOMContentLoaded", add);
	}
})();

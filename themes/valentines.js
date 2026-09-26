/*
 * SCTP — Valentine's theme (pairs with themes/valentines.css).
 * Hearts floating up, falling rose petals, winged hearts fluttering past,
 * a string of hearts across the top, a love letter, chocolates and a rose
 * on the header, heart boppers on the snail, lovey icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "valentines";

	var HEART = "M12 21C6 16.8 2 13.2 2 8.4 2 5.3 4.3 3 7.2 3c2 0 3.8 1.1 4.8 2.8C13 4.1 14.8 3 16.8 3 19.7 3 22 5.3 22 8.4c0 4.8-4 8.4-10 12.6z";
	function heart(w, c) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><path d="' + HEART + '" fill="' + c + '"/>' +
			'<path d="M6.5 6.5c-1.2.5-1.8 1.6-1.8 2.8" stroke="#FFFFFF" stroke-width="1.4" stroke-linecap="round" fill="none" opacity=".45"/></svg>';
	}
	function letter(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 24" width="' + w + '" height="' + Math.round(w * 24 / 34) + '" style="transform:rotate(-8deg)">' +
			'<rect x="1" y="1" width="32" height="22" rx="2.5" fill="#FFF4EC"/><path d="M1.6 2.4L17 13.5 32.4 2.4" stroke="#E7C9BC" stroke-width="1.4" fill="none"/>' +
			'<path d="M1.6 22L13 12.5M32.4 22L21 12.5" stroke="#EBD4C9" stroke-width="1.2"/>' +
			'<g transform="translate(12 8.6) scale(.42)"><path d="' + HEART + '" fill="#E0335B"/></g></svg>';
	}
	function chocolates(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 30" width="' + w + '" height="' + Math.round(w * 30 / 32) + '">' +
			'<path d="M16 29C7 22.6 1 17.2 1 10.1 1 5.4 4.5 2 8.8 2c3 0 5.7 1.6 7.2 4.2C17.5 3.6 20.2 2 23.2 2 27.5 2 31 5.4 31 10.1c0 7.1-6 12.5-15 18.9z" fill="#C21F45"/>' +
			'<path d="M16 25C9 20 4 15.8 4 10.3 4 7 6.5 4.8 9.3 4.8c2.6 0 4.6 1.6 5.4 3.8h2.6c.8-2.2 2.8-3.8 5.4-3.8 2.8 0 5.3 2.2 5.3 5.5 0 5.5-5 9.7-12 14.7z" fill="#5A2A1A"/>' +
			'<g fill="#7A3B22"><circle cx="10" cy="11" r="2.6"/><circle cx="16" cy="13" r="2.6"/><circle cx="22" cy="11" r="2.6"/><circle cx="13" cy="18" r="2.4"/><circle cx="19" cy="18" r="2.4"/></g>' +
			'<g fill="#F2C6A0" opacity=".7"><circle cx="9.2" cy="10.2" r=".8"/><circle cx="15.2" cy="12.2" r=".8"/><circle cx="21.2" cy="10.2" r=".8"/></g></svg>';
	}
	function rose(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 40" width="' + w + '" height="' + Math.round(w * 2) + '">' +
			'<path d="M10 16v23" stroke="#3F7A45" stroke-width="1.8"/><path d="M10 28c-5-4-8-3-9 0 4 2 6 2 9 0zM10 23c5-3 8-2 9 1-4 1.6-6 1.4-9-1z" fill="#4C8F52"/>' +
			'<circle cx="10" cy="10" r="8" fill="#C21F45"/><path d="M10 3.5c-4 0-6.5 3-6.5 6 2.6-2.4 7-3 10.2-.6.6-3-1.2-5.4-3.7-5.4z" fill="#E0335B"/>' +
			'<path d="M10.6 7c-2 .6-2.6 2.6-2 4 1.2-1.2 3.2-1.2 4.4 0 0-2-1.2-4-2.4-4z" fill="#FF5C80"/></svg>';
	}

	var WINGED = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 44 24">' +
		'<path d="M14 10C9 4 2 4 0 7c4 0 6 2 7 4-3 0-5 2-5 4 3-1 6-1 9 1z" fill="#FFF4F7"/>' +
		'<path d="M30 10c5-6 12-6 14-3-4 0-6 2-7 4 3 0 5 2 5 4-3-1-6-1-9 1z" fill="#FFF4F7"/>' +
		'<g transform="translate(12 2) scale(.84)"><path d="' + HEART + '" fill="#FF4F7B"/></g></svg>';

	var PINKS = ["#FF4F7B", "#FF7A9C", "#FFC2D4", "#E0335B", "#F7B39A"];

	var CONFIG = {
		id: ID,
		titlePrefix: "💘",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#1A0E14"/><g transform="translate(6 7) scale(1.17)">' +
			'<path d="' + HEART + '" fill="#FF4F7B"/></g></svg>',
		icons: {
			"⚙": "💘", "🐛": "🐝", "🏆": "💝", "🧱": "🍫", "🖼": "💌", "🛒": "🌹", "✨": "💖", "🐌": "❤️",
			"🔒": "🔐", "🎯": "🏹", "📝": "💌", "🚩": "📮", "🔑": "🗝️", "📊": "💞", "🏪": "💐", "💡": "🕯️", "🎨": "💌"
		},
		kickerIcons: { "Rare-dle": "🏹" },
		logo: {
			width: "80%", top: "-60%", left: "10%", rotate: -4,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 26">' +
				'<path d="M4 25c4-4 20-4 24 0" stroke="#FF7A9C" stroke-width="2.4" stroke-linecap="round" fill="none"/>' +
				'<path d="M10 22c-1-5-4-8-4-13M22 22c1-5 4-8 4-13" stroke="#FFC2D4" stroke-width="1.2" fill="none"/>' +
				'<g class="fx-bob"><g transform="translate(0 0) scale(.5)"><path d="' + HEART + '" fill="#FF4F7B"/></g></g>' +
				'<g class="fx-bob" style="animation-delay:-1.2s"><g transform="translate(20 0) scale(.5)"><path d="' + HEART + '" fill="#FF4F7B"/></g></g></svg>'
		},
		props: { side: "left", items: [rose(16), letter(34), chocolates(30)] },
		garland: {
			spacing: 40, sag: 8, itemWidth: 16, wire: "rgba(255,194,212,0.4)", anim: "lamp",
			colors: ["#FF4F7B", "#FFC2D4", "#E0335B", "#FFFFFF", "#FF7A9C"],
			item: function (c) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="16" height="16"><path d="M12 0v4" stroke="rgba(255,194,212,.6)"/><g transform="translate(0 3)"><path d="' + HEART + '" fill="' + c + '"/></g></svg>'; }
		},
		footer: { left: heart(18, "#FF4F7B"), text: "Happy Valentine's Day from SCTP", right: heart(18, "#FFC2D4"), font: '"Dancing Script", var(--font-display, serif)' },
		particles: [
			{ type: "rise", count: 14, mobileCount: 7, size: [12, 24], dur: [12, 22], sway: [30, 80], swayDur: [3, 5], spin: 18, opacity: 0.85, colors: PINKS,
				shapes: ['<path d="' + HEART + '"/>'] },
			{ type: "fall", count: 8, mobileCount: 4, size: [9, 14], dur: [10, 18], sway: [40, 100], spin: 120, opacity: 0.8, colors: ["#C21F45", "#E0335B", "#A01436"],
				shapes: ['<path d="M12 2c4 4 5 11 0 20C7 13 8 6 12 2z"/>'] }
		],
		flyers: [{ path: "fly", svg: WINGED, width: 52, height: 28, every: [30000, 60000], first: [5000, 10000], dur: 13, flap: true }],
		toggle: { icon: "💕", noun: "floating hearts" }
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

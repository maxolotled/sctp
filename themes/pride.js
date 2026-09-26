/*
 * SCTP — Pride month theme (pairs with themes/pride.css).
 * Rainbow confetti and hearts, rainbow bursts, a Pride Duck waddling along
 * the bottom, rainbow bunting across the top, rubber ducks and flags on the
 * header, a little rainbow over the snail, rainbow icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "pride";

	var RAINBOW = ["#FF5A5A", "#FF9F43", "#FFD84D", "#5BE37D", "#4FA3FF", "#B57BFF"];
	var HEART = "M12 21C6 16.8 2 13.2 2 8.4 2 5.3 4.3 3 7.2 3c2 0 3.8 1.1 4.8 2.8C13 4.1 14.8 3 16.8 3 19.7 3 22 5.3 22 8.4c0 4.8-4 8.4-10 12.6z";

	// A rubber duck with a rainbow scarf — hello, Pride Ducks.
	function duck(w, flip) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 28" width="' + w + '" height="' + Math.round(w * 28 / 34) + '"' + (flip ? ' style="transform:scaleX(-1)"' : "") + '>' +
			'<path d="M4 16c0-4 4-6 8-5 1-6 5-10 11-10 5 0 8 4 8 8 0 3-2 5-4 6 3 1 5 4 5 7 0 4-4 6-9 6H11C6 28 4 22 4 16z" fill="#FFD84D"/>' +
			'<path d="M31 9c2 0 3 1 3 2s-1 2-3 2h-3z" fill="#FF9F43"/>' +
			'<circle cx="25" cy="7" r="1.5" fill="#1A1A1A"/><circle cx="25.5" cy="6.5" r=".5" fill="#FFFFFF"/>' +
			'<path d="M8 17c3 3 8 3 11 0" stroke="#F2C233" stroke-width="1.4" fill="none"/>' +
			'<g><rect x="16" y="13" width="3" height="3.2" fill="#FF5A5A"/><rect x="19" y="13" width="3" height="3.2" fill="#FFD84D"/><rect x="22" y="13" width="3" height="3.2" fill="#5BE37D"/><rect x="25" y="13" width="3" height="3.2" fill="#4FA3FF"/><rect x="28" y="13" width="2" height="3.2" fill="#B57BFF"/></g>' +
			'</svg>';
	}
	function flag(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 34" width="' + w + '" height="' + Math.round(w * 34 / 26) + '">' +
			'<path d="M2 1v33" stroke="#C9C5DA" stroke-width="2" stroke-linecap="round"/><g class="fx-lamp" style="transform-origin:2px 2px">' +
			RAINBOW.map(function (c, i) { return '<rect x="3" y="' + (2 + i * 3) + '" width="22" height="3" fill="' + c + '"/>'; }).join("") + "</g></svg>";
	}
	function heart(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><defs><linearGradient id="fxPrideHeart" x1="0" y1="0" x2="0" y2="1">' +
			RAINBOW.map(function (c, i) { return '<stop offset="' + (i / 5).toFixed(2) + '" stop-color="' + c + '"/>'; }).join("") +
			'</linearGradient></defs><path d="' + HEART + '" fill="url(#fxPrideHeart)"/></svg>';
	}

	var CONFIG = {
		id: ID,
		titlePrefix: "🏳️‍🌈",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#111018"/><g transform="translate(6 7) scale(1.17)">' +
			heart(24).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🌈", "🐛": "🦋", "🏆": "💎", "🧱": "🧁", "🖼": "🎨", "🛒": "🛍️", "✨": "🌟", "🐌": "🏳️‍🌈",
			"🔒": "💜", "🎯": "🦆", "📝": "💌", "🚩": "📣", "🔑": "🗝️", "📊": "📈", "🏪": "🎪", "💡": "💖", "🎨": "🌈"
		},
		kickerIcons: { "Rare-dle": "🦆" },
		logo: {
			width: "92%", top: "-40%", left: "4%",
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 22" fill="none" stroke-width="2.6">' +
				RAINBOW.map(function (c, i) { var r = 18 - i * 2.6; return '<path d="M' + (20 - r) + ' 21a' + r + " " + r + " 0 0 1 " + (2 * r) + ' 0" stroke="' + c + '"/>'; }).join("") + "</svg>"
		},
		props: { side: "left", items: [flag(22), duck(34), duck(24, true)] },
		garland: {
			spacing: 34, sag: 7, itemWidth: 16, wire: "rgba(240,235,255,0.4)", anim: "lamp",
			colors: RAINBOW,
			item: function (c) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 20" width="16" height="20"><path d="M0 0H16L8 18Z" fill="' + c + '"/></svg>'; }
		},
		footer: { left: heart(20), text: "Happy Pride from SCTP: everyone is welcome here", right: duck(26), font: '"Rubik", system-ui, sans-serif' },
		particles: [
			{ type: "fall", count: 22, mobileCount: 10, size: [8, 14], dur: [8, 15], sway: [20, 70], spin: 180, opacity: 0.9, colors: RAINBOW,
				shapes: ['<rect x="8" y="3" width="8" height="18" rx="1.5"/>', '<circle cx="12" cy="12" r="6"/>', '<path d="' + HEART + '"/>'] }
		],
		bursts: { every: [6000, 12000], first: [2000, 4000], sparks: 30, colors: RAINBOW },
		flyers: [{ path: "hop", svg: duck(34).replace(/ width="34" height="28"/, ""), width: 40, height: 33, every: [30000, 60000], first: [5000, 10000], dur: 14 }],
		toggle: { icon: "🌈", noun: "rainbow confetti" }
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

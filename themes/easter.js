/*
 * SCTP — Easter / spring theme (pairs with themes/easter.css).
 * Falling blossom petals, pastel bunting across the top, a bunny hopping
 * along the bottom, butterflies, painted eggs and a chick on the header,
 * bunny ears on the snail, spring icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "easter";

	// A painted egg: base colour + a pattern ("stripes", "dots" or "zigzag").
	function egg(w, base, trim, pattern, delay) {
		var p = pattern === "dots"
			? '<circle cx="8" cy="12" r="1.8"/><circle cx="14" cy="10" r="1.8"/><circle cx="11" cy="17" r="1.8"/><circle cx="17" cy="16" r="1.6"/><circle cx="6" cy="18" r="1.4"/>'
			: pattern === "zigzag"
				? '<path d="M3 14l3-3 3 3 3-3 3 3 3-3 3 3" stroke="' + trim + '" stroke-width="2" fill="none"/><path d="M4 19l3-3 3 3 3-3 3 3 3-3" stroke="' + trim + '" stroke-width="1.6" fill="none"/>'
				: '<rect x="0" y="10" width="22" height="3" /><rect x="0" y="17" width="22" height="2.4"/>';
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 22 28" width="' + w + '" height="' + Math.round(w * 28 / 22) + '">' +
			'<defs><clipPath id="fxEgg' + base.slice(1) + pattern + '"><path d="M11 1C5 1 1 11 1 17.5 1 23.5 5.5 27 11 27s10-3.5 10-9.5C21 11 17 1 11 1z"/></clipPath></defs>' +
			'<g class="fx-wobble" style="animation-delay:' + (delay || 0) + 's">' +
			'<path d="M11 1C5 1 1 11 1 17.5 1 23.5 5.5 27 11 27s10-3.5 10-9.5C21 11 17 1 11 1z" fill="' + base + '"/>' +
			'<g clip-path="url(#fxEgg' + base.slice(1) + pattern + ')" fill="' + trim + '">' + p + "</g>" +
			'<ellipse cx="7" cy="9" rx="2" ry="3.4" fill="#FFFFFF" opacity=".35"/></g></svg>';
	}
	function chick(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 28" width="' + w + '" height="' + Math.round(w * 28 / 30) + '"><g class="fx-bob">' +
			'<ellipse cx="15" cy="18" rx="11" ry="9" fill="#FFD84D"/><circle cx="15" cy="9" r="7" fill="#FFE070"/>' +
			'<path d="M14 2c1-2 3-2 3 0" stroke="#F2B233" stroke-width="1.4" fill="none"/>' +
			'<circle cx="12.5" cy="8" r="1.1" fill="#3A2A1A"/><circle cx="17.5" cy="8" r="1.1" fill="#3A2A1A"/>' +
			'<path d="M13.4 10.5h3.2L15 13z" fill="#F28A2E"/><path d="M6 17c-2-3 1-5 3-3" fill="#F2C233"/><path d="M24 17c2-3-1-5-3-3" fill="#F2C233"/>' +
			'<circle cx="11" cy="11.5" r="1.4" fill="#FFB7C9" opacity=".7"/><circle cx="19" cy="11.5" r="1.4" fill="#FFB7C9" opacity=".7"/>' +
			'</g><path d="M3 27c4-3 20-3 24 0" stroke="#7CCB7A" stroke-width="2" fill="none"/></svg>';
	}

	var BUNNY = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 32">' +
		'<ellipse cx="28" cy="5" rx="2.2" ry="6.5" fill="#F2E9F5" transform="rotate(-12 28 5)"/><ellipse cx="28" cy="5" rx="1" ry="4.6" fill="#FFB7D5" transform="rotate(-12 28 5)"/>' +
		'<ellipse cx="32.5" cy="5.5" rx="2.2" ry="6.5" fill="#F2E9F5" transform="rotate(14 32.5 5.5)"/><ellipse cx="32.5" cy="5.5" rx="1" ry="4.6" fill="#FFB7D5" transform="rotate(14 32.5 5.5)"/>' +
		'<ellipse cx="17" cy="21" rx="11" ry="8" fill="#F2E9F5"/><circle cx="30" cy="14" r="6.2" fill="#F2E9F5"/>' +
		'<circle cx="5.8" cy="18.5" r="3.6" fill="#FFFFFF"/><ellipse cx="22" cy="28.4" rx="6" ry="2.5" fill="#E6DCEB"/>' +
		'<circle cx="32" cy="13" r="1.1" fill="#2A0F1E"/><circle cx="35.6" cy="15.6" r="1" fill="#FF8FB8"/></svg>';
	var BUTTERFLY = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 16">' +
		'<path d="M10 8C6 0 0 1 1 6s5 4 9 2z" fill="#FFB7D5"/><path d="M10 8c4-8 10-7 9-2s-5 4-9 2z" fill="#FFB7D5"/>' +
		'<path d="M10 8c-3 2-7 6-5 7s4-3 5-7z" fill="#B9A6FF"/><path d="M10 8c3 2 7 6 5 7s-4-3-5-7z" fill="#B9A6FF"/>' +
		'<rect x="9.4" y="4" width="1.2" height="9" rx=".6" fill="#3A2A3A"/></svg>';

	var CONFIG = {
		id: ID,
		titlePrefix: "🐣",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#14161F"/><g transform="translate(9 4) scale(1.1)">' +
			egg(22, "#FF9FCF", "#FFFFFF", "zigzag").replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🐰", "🐛": "🐞", "🏆": "🥚", "🧱": "🥕", "🖼": "🌷", "🛒": "🧺", "✨": "🌸", "🐌": "🐣",
			"🔒": "🌼", "🎯": "🐇", "📝": "🌱", "🚩": "🐤", "🔑": "🗝️", "📊": "🌻", "🏪": "🏡", "💡": "☀️", "🎨": "🌷"
		},
		kickerIcons: { "Rare-dle": "🐇" },
		logo: {
			width: "70%", top: "-64%", left: "14%", rotate: -6,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 30">' +
				'<ellipse cx="10" cy="14" rx="4.6" ry="13" fill="#F7F2FA" transform="rotate(-14 10 14)"/><ellipse cx="10" cy="15" rx="2.2" ry="9.5" fill="#FFB7D5" transform="rotate(-14 10 15)"/>' +
				'<ellipse cx="21" cy="14" rx="4.6" ry="13" fill="#F7F2FA" transform="rotate(12 21 14)"/><ellipse cx="21" cy="15" rx="2.2" ry="9.5" fill="#FFB7D5" transform="rotate(12 21 15)"/>' +
				'<path d="M4 28c6-3 16-3 22 0" stroke="#F7F2FA" stroke-width="3" stroke-linecap="round" fill="none"/></svg>'
		},
		props: { side: "left", items: [egg(20, "#FF9FCF", "#FFFFFF", "stripes", 0), chick(30), egg(24, "#86EEC4", "#FFF3B0", "dots", -1.2), egg(19, "#B9A6FF", "#FFE08A", "zigzag", -0.6)] },
		garland: {
			spacing: 40, sag: 7, itemWidth: 18, wire: "rgba(255,230,240,0.4)", anim: "lamp",
			colors: ["#FF9FCF", "#FFE08A", "#86EEC4", "#B9A6FF", "#FFC6A8", "#9FD8FF"],
			item: function (c) {
				return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 22" width="18" height="22"><path d="M0 0H18L9 20Z" fill="' + c + '"/><path d="M0 0H18L16.4 3H1.6Z" fill="rgba(255,255,255,.35)"/></svg>';
			}
		},
		footer: { left: chick(22), text: "Happy Easter from SCTP", right: egg(16, "#FF9FCF", "#FFFFFF", "stripes"), font: '"Chewy", var(--font-display, serif)' },
		particles: [{
			type: "fall", count: 16, mobileCount: 8, size: [9, 16], dur: [10, 20], sway: [40, 110], swayDur: [3, 5], spin: 120, opacity: 0.85,
			colors: ["#FFC1DA", "#FFD9E8", "#FFFFFF", "#F7A8C8", "#FFE6F0"],
			shapes: [
				'<path d="M12 2c4 4 5 11 0 20C7 13 8 6 12 2z"/>',
				'<path d="M12 3c5 3 6 10 1 18C8 13 7 7 12 3z"/>',
				'<g><circle cx="12" cy="6" r="4.5"/><circle cx="17.7" cy="10.1" r="4.5"/><circle cx="15.5" cy="16.9" r="4.5"/><circle cx="8.5" cy="16.9" r="4.5"/><circle cx="6.3" cy="10.1" r="4.5"/><circle cx="12" cy="12" r="2.6" fill="#FFD35A"/></g>'
			]
		}],
		flyers: [
			{ path: "hop", svg: BUNNY, width: 44, height: 35, every: [30000, 60000], first: [5000, 10000], dur: 13 },
			{ path: "fly", svg: BUTTERFLY, width: 26, height: 21, every: [35000, 70000], first: [12000, 20000], dur: 12, flap: true }
		],
		toggle: { icon: "🌸", noun: "falling petals" }
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

/*
 * SCTP — cosy autumn theme (pairs with themes/autumn.css), for November.
 * Falling leaves with soft rain, a friendly ghost drifting past, a leaf
 * garland across the top, cute mushrooms, a steaming mug of cocoa and a
 * smiling pumpkin on the header, a knitted beanie on the snail, cosy icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "autumn";

	function mushroom(w, cap) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 28 30" width="' + w + '" height="' + Math.round(w * 30 / 28) + '"><g class="fx-wobble">' +
			'<path d="M9 16h10l1.6 11.5c.2 1.4-.8 2.5-2.2 2.5H9.6c-1.4 0-2.4-1.1-2.2-2.5z" fill="#F4E7D3"/>' +
			'<ellipse cx="11.6" cy="21" rx=".9" ry="1.2" fill="#2A1E16"/><ellipse cx="16.4" cy="21" rx=".9" ry="1.2" fill="#2A1E16"/>' +
			'<path d="M12.6 23.4q1.4 1.2 2.8 0" stroke="#2A1E16" stroke-width=".9" fill="none" stroke-linecap="round"/>' +
			'<ellipse cx="10" cy="23" rx="1.4" ry=".8" fill="#FFB7A8" opacity=".8"/><ellipse cx="18" cy="23" rx="1.4" ry=".8" fill="#FFB7A8" opacity=".8"/>' +
			'<path d="M1 16C1 7.5 7 2 14 2s13 5.5 13 14z" fill="' + cap + '"/>' +
			'<g fill="#FFF4E6"><circle cx="8" cy="9" r="2"/><circle cx="16" cy="6" r="1.6"/><circle cx="20" cy="11.5" r="2"/><circle cx="12" cy="13" r="1.3"/></g></g></svg>';
	}
	function cocoa(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 34" width="' + w + '" height="' + Math.round(w * 34 / 30) + '">' +
			'<g class="fx-bob" stroke="#F4E7D3" stroke-width="1.6" fill="none" stroke-linecap="round" opacity=".7">' +
				'<path d="M10 9c-3-3 3-4 0-8"/><path d="M16 9c-3-3 3-4 0-8" style="animation-delay:-.8s"/></g>' +
			'<path d="M23 16c6 0 6 10 0 10" stroke="#9DB77A" stroke-width="3" fill="none"/>' +
			'<rect x="3" y="11" width="21" height="21" rx="4" fill="#9DB77A"/><rect x="3" y="11" width="21" height="4" rx="2" fill="#6B4A34"/>' +
			'<ellipse cx="10" cy="12.6" rx="3" ry="1.2" fill="#FFF4E6"/><ellipse cx="16" cy="12.8" rx="2.4" ry="1" fill="#FFF4E6"/>' +
			'<g transform="translate(9 19) scale(.38)"><path d="M12 21C6 16.8 2 13.2 2 8.4 2 5.3 4.3 3 7.2 3c2 0 3.8 1.1 4.8 2.8C13 4.1 14.8 3 16.8 3 19.7 3 22 5.3 22 8.4c0 4.8-4 8.4-10 12.6z" fill="#E07B39"/></g></svg>';
	}
	function cutePumpkin(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 30" width="' + w + '" height="' + Math.round(w * 30 / 36) + '">' +
			'<path d="M17 6c0-2 .6-4 2.6-5.2l1.4 1.2c-1.2.9-1.6 2.2-1.6 4z" fill="#6B8A3A"/><path d="M20 5c3-3 7-2 8 0-3 1-5 1-8 0z" fill="#9DB77A"/>' +
			'<ellipse cx="18" cy="18" rx="16" ry="11.5" fill="#D9731F"/><ellipse cx="10" cy="18" rx="7" ry="11" fill="#E8862E"/><ellipse cx="26" cy="18" rx="7" ry="11" fill="#E8862E"/>' +
			'<ellipse cx="18" cy="18" rx="6.5" ry="11.5" fill="#F29A40"/>' +
			'<ellipse cx="13.5" cy="17" rx="1.4" ry="1.9" fill="#2A1E16"/><ellipse cx="22.5" cy="17" rx="1.4" ry="1.9" fill="#2A1E16"/>' +
			'<path d="M16 21q2 1.8 4 0" stroke="#2A1E16" stroke-width="1.1" fill="none" stroke-linecap="round"/>' +
			'<ellipse cx="10.5" cy="20.5" rx="2" ry="1.1" fill="#FFB7A8" opacity=".75"/><ellipse cx="25.5" cy="20.5" rx="2" ry="1.1" fill="#FFB7A8" opacity=".75"/></svg>';
	}

	var GHOST = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 34">' +
		'<path d="M15 1C7.5 1 3 7 3 14v18l4-3 4 3 4-3 4 3 4-3 4 3V14C27 7 22.5 1 15 1z" fill="#F7F1EA" opacity=".92"/>' +
		'<ellipse cx="11" cy="13" rx="1.8" ry="2.5" fill="#2A1E16"/><ellipse cx="19" cy="13" rx="1.8" ry="2.5" fill="#2A1E16"/>' +
		'<ellipse cx="8" cy="18" rx="2" ry="1.2" fill="#FFB7A8" opacity=".85"/><ellipse cx="22" cy="18" rx="2" ry="1.2" fill="#FFB7A8" opacity=".85"/>' +
		'<path d="M13.5 18q1.5 1.6 3 0" stroke="#2A1E16" stroke-width="1" fill="none" stroke-linecap="round"/></svg>';

	var LEAF_SHAPES = [
		'<path d="M12 1.5l1.7 4 3.1-1.6-.8 4.2 3.8-.6-2.4 3.2 3.6 1.7-4 1.3 1.1 2.8-3.5-1L13 21h-2l-1.6-5.5-3.5 1 1.1-2.8-4-1.3 3.6-1.7-2.4-3.2 3.8.6-.8-4.2 3.1 1.6z"/>',
		'<path d="M12 2C7 6 5 11 6 16c1 3 3 5 6 6 3-1 5-3 6-6 1-5-1-10-6-14z"/><path d="M12 5v16" stroke="rgba(0,0,0,.3)" stroke-width="1" fill="none"/>'
	];

	var CONFIG = {
		id: ID,
		titlePrefix: "🍂",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#17120E"/><g transform="translate(5 4) scale(.84)">' +
			mushroom(36, "#C2413A").replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🧣", "🐛": "🦔", "🏆": "🌰", "🧱": "🥧", "🖼": "🍄", "🛒": "🧺", "✨": "🍁", "🐌": "🍂",
			"🔒": "🕯️", "🎯": "🦉", "📝": "📖", "🚩": "👻", "🔑": "🗝️", "📊": "🌾", "🏪": "🏡", "💡": "🕯️", "🎨": "🍄"
		},
		kickerIcons: { "Rare-dle": "🦉" },
		logo: {
			width: "74%", top: "-46%", left: "12%", rotate: -8,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 24">' +
				'<path d="M3 20C3 10 8 4 15 4s12 6 12 16z" fill="#D9A441"/>' +
				'<path d="M8 8v12M12 5.4v14.6M16 4.6v15.4M20 5.6v14.4M24 9v11" stroke="rgba(120,80,20,.3)" stroke-width="1.2"/>' +
				'<rect x="1" y="17.5" width="28" height="6" rx="3" fill="#C27F2A"/><path d="M3 20.5h24" stroke="rgba(0,0,0,.18)" stroke-dasharray="2 2"/>' +
				'<circle cx="15" cy="4" r="3.6" fill="#F4E7D3"/></svg>'
		},
		props: { side: "left", items: [mushroom(24, "#C2413A"), cocoa(30), cutePumpkin(36), mushroom(18, "#D9A441")] },
		garland: {
			spacing: 38, sag: 7, itemWidth: 16, wire: "rgba(200,160,110,0.45)", anim: "lamp",
			colors: ["#E07B39", "#D9A441", "#C2413A", "#9DB77A", "#B8561F"],
			item: function (c, i) {
				return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="16" height="16" fill="' + c + '" style="transform:rotate(' + (i % 2 ? 150 : 200) + 'deg)">' + LEAF_SHAPES[i % 2] + "</svg>";
			}
		},
		footer: { left: cocoa(20), text: "Cosy season at SCTP", right: mushroom(18, "#C2413A"), font: '"Caveat", var(--font-display, serif)' },
		particles: [
			{ type: "fall", count: 12, mobileCount: 6, size: [14, 24], dur: [12, 22], sway: [30, 90], spin: 35, opacity: 0.85,
				colors: ["#E07B39", "#D9A441", "#C2413A", "#9DB77A", "#B8561F", "#8A5A36"], shapes: LEAF_SHAPES },
			{ type: "fall", count: 36, mobileCount: 14, size: [2, 3], viewBox: "0 0 2 16", dur: [1.1, 1.9], sway: 0, opacity: 0.28,
				colors: ["#BFD4F0", "#D9E6F7"], shapes: ['<rect x="0" y="0" width="1.4" height="16" rx=".7"/>'] }
		],
		flyers: [{ path: "fly", svg: GHOST, width: 34, height: 38, every: [35000, 70000], first: [6000, 12000], dur: 16 }],
		toggle: { icon: "🍁", noun: "falling leaves and rain" }
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

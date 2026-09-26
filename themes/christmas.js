/*
 * SCTP — winter / Christmas theme (pairs with themes/christmas.css).
 * Snowfall, fairy lights across the top, Santa's sleigh flying past, a snowman
 * and presents on the header, a Santa hat on the snail, festive icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "christmas";

	function tree(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 37" width="' + w + '" height="' + Math.round(w * 37 / 30) + '">' +
			'<rect x="12.5" y="32" width="5" height="5" fill="#7A4A2A"/>' +
			'<path d="M15 6L6 17h5L4 26h6L2 33h26l-8-7h6l-7-9h5z" fill="#2E9E5B"/>' +
			'<path d="M15 6L6 17h5L4 26h6L2 33h13z" fill="#258A4E"/>' +
			'<path d="M15 0l1.6 3.3 3.6.5-2.6 2.5.6 3.6L15 8.2l-3.2 1.7.6-3.6-2.6-2.5 3.6-.5z" fill="#FFD34D" class="fx-twinkle"/>' +
			'<circle cx="12" cy="15" r="1.5" fill="#FF4D4D" class="fx-twinkle"/><circle cx="18" cy="21" r="1.5" fill="#FFD34D" class="fx-twinkle" style="animation-delay:-.8s"/>' +
			'<circle cx="10" cy="28" r="1.5" fill="#4DB8FF" class="fx-twinkle" style="animation-delay:-1.6s"/><circle cx="20" cy="29" r="1.5" fill="#FF4D4D" class="fx-twinkle" style="animation-delay:-.4s"/>' +
			'</svg>';
	}
	function gift(w, body, lid, ribbon) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -2 28 30" width="' + w + '" height="' + Math.round(w * 30 / 28) + '">' +
			'<rect x="2" y="10" width="24" height="17" rx="2" fill="' + body + '"/>' +
			'<rect x="0" y="6" width="28" height="6" rx="1.5" fill="' + lid + '"/>' +
			'<rect x="12" y="6" width="4" height="21" fill="' + ribbon + '"/>' +
			'<path d="M14 6C10-.5 3.5 1.5 7.5 6ZM14 6c4-6.5 10.5-4.5 6.5 0Z" fill="' + ribbon + '"/></svg>';
	}
	function snowman(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -6 30 50" width="' + w + '" height="' + Math.round(w * 50 / 30) + '">' +
			'<path d="M6 19L-1 13M24 19l7-6" stroke="#7A4A2A" stroke-width="1.6" stroke-linecap="round"/>' +
			'<circle cx="15" cy="33" r="10.5" fill="#F4F8FF"/><circle cx="15" cy="19" r="7.5" fill="#F4F8FF"/><circle cx="15" cy="7" r="5.8" fill="#F4F8FF"/>' +
			'<rect x="10.5" y="-5" width="9" height="7" rx="1" fill="#1A1A22"/><rect x="8" y="1" width="14" height="2" rx="1" fill="#1A1A22"/><rect x="10.5" y="-.6" width="9" height="1.6" fill="#E03C45"/>' +
			'<circle cx="13" cy="6" r=".9" fill="#1A1A22"/><circle cx="17" cy="6" r=".9" fill="#1A1A22"/><path d="M15 7.6l5.5 1.2-5.5 1.2z" fill="#F28A2E"/>' +
			'<path d="M9.5 11.5h11v3h-11z" fill="#E03C45"/><path d="M17 13.5h3v6.5h-3z" fill="#E03C45"/>' +
			'<circle cx="15" cy="18" r=".9" fill="#1A1A22"/><circle cx="15" cy="21.5" r=".9" fill="#1A1A22"/><circle cx="15" cy="30" r="1" fill="#1A1A22"/>' +
			'</svg>';
	}

	var SLEIGH = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 112 30"><g fill="#E6EEF8" stroke="#E6EEF8" stroke-linecap="round">' +
		'<circle cx="8" cy="11" r="4.5" stroke="none"/>' +
		'<path d="M11 14c0-6 2.5-9 6-9s5 3 5 9z" stroke="none"/><circle cx="17" cy="4" r="3" stroke="none"/>' +
		'<path d="M4 14h22l5-7h3l-5 11c-1.5 3-3.5 4-6.5 4H6z" stroke="none"/>' +
		'<path d="M2 25h26c3 0 5-1.5 6-4" fill="none" stroke-width="1.6"/>' +
		'<path d="M22 10L52 13M22 10L84 12" fill="none" stroke-width=".7"/>' +
		'<g transform="translate(46 0)"><ellipse cx="14" cy="16" rx="9" ry="4.2" stroke="none"/><path d="M20 14l5-6.5 4.5 1-1 2.2h-3l-3 5z" stroke="none"/>' +
			'<path d="M26 8l-1.5-5M26 6l2.5-3M25 5l-3-2.5" fill="none" stroke-width="1.2"/><path d="M8 19l-4 6M11 19.5l1 6M18 19.5l-2 6M21 18.5l4 5" fill="none" stroke-width="1.6"/></g>' +
		'<g transform="translate(78 -1)"><ellipse cx="14" cy="16" rx="9" ry="4.2" stroke="none"/><path d="M20 14l5-6.5 4.5 1-1 2.2h-3l-3 5z" stroke="none"/>' +
			'<path d="M26 8l-1.5-5M26 6l2.5-3M25 5l-3-2.5" fill="none" stroke-width="1.2"/><path d="M8 19l-4 6M11 19.5l1 6M18 19.5l2 6M21 18.5l4-4" fill="none" stroke-width="1.6"/>' +
			'<circle cx="29.5" cy="10.2" r="1.5" fill="#FF3B3B" stroke="none" style="filter:drop-shadow(0 0 3px #FF3B3B)"/></g>' +
		'</g></svg>';

	var CONFIG = {
		id: ID,
		titlePrefix: "🎄",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#0C1320"/><g transform="translate(5 2)">' +
			tree(30).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🎅", "🐛": "🐧", "🏆": "🧦", "🧱": "🍪", "🖼": "⛄", "🛒": "🎁", "✨": "🌟", "🐌": "🎄",
			"🔒": "🔔", "🎯": "❄️", "📝": "✉️", "🚩": "🦌", "🔑": "🗝️", "📊": "🕯️", "🏪": "🏠", "💡": "🕯️", "🎨": "⛄"
		},
		kickerIcons: { "Rare-dle": "❄️" },
		logo: {
			width: "82%", top: "-52%", right: "-26%", rotate: 16,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 34 28">' +
				'<path d="M5 21C7 11 14 3.5 23 3.5c4.5 0 7 3.5 6.5 8.5-1.5-2.5-4-3.5-6.5-2.5 1.5 3.5 2.5 7.5 2.8 11.5z" fill="#D62839"/>' +
				'<path d="M18 6c-3 2-6 7-7.5 13.5" stroke="rgba(0,0,0,.18)" stroke-width="2" fill="none"/>' +
				'<rect x="2" y="19.5" width="27" height="6.5" rx="3.2" fill="#F5F5F5"/>' +
				'<circle cx="29.5" cy="12.5" r="3.6" fill="#FFFFFF"/></svg>'
		},
		props: { side: "left", items: [gift(26, "#D62839", "#E8474F", "#FFD34D"), snowman(30), gift(22, "#2E9E5B", "#3DB36C", "#F5F5F5"), tree(26)] },
		garland: {
			spacing: 46, sag: 9, itemWidth: 12, wire: "rgba(170,200,180,0.45)", anim: "twinkle",
			colors: ["#FF4D4D", "#FFD34D", "#4DDB7A", "#4DB8FF", "#FF7FD1"],
			item: function (c) {
				return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 12 20" width="12" height="20"><rect x="4" y="0" width="4" height="4" rx="1" fill="#55606E"/>' +
					'<path d="M6 4C1.5 7 1.5 14.5 6 19c4.5-4.5 4.5-12 0-15z" fill="' + c + '" style="filter:drop-shadow(0 0 5px ' + c + ')"/></svg>';
			}
		},
		footer: { left: tree(20), text: "Merry Christmas & happy holidays from SCTP", right: gift(20, "#D62839", "#E8474F", "#FFD34D"), font: '"Mountains of Christmas", var(--font-display, serif)' },
		particles: [
			{ type: "fall", count: 34, mobileCount: 14, size: [4, 9], dur: [9, 18], sway: [20, 60], spin: 0, opacity: 0.8,
				colors: ["#FFFFFF", "#E8F1FF", "#D6E6FF"], shapes: ['<circle cx="12" cy="12" r="10"/>'] },
			{ type: "fall", count: 10, mobileCount: 5, size: [12, 20], dur: [12, 22], sway: [30, 80], spin: 90, opacity: 0.85,
				colors: ["#FFFFFF", "#E8F1FF"],
				shapes: ['<g stroke="currentColor" stroke-width="1.8" stroke-linecap="round" fill="none"><path d="M12 2v20M3.3 7l17.4 10M3.3 17l17.4-10"/><path d="M9 4.5l3 2 3-2M9 19.5l3-2 3 2M4.3 10.4l3.5.3 1.4-3.1M19.7 13.6l-3.5-.3-1.4 3.1M4.3 13.6l3.5-.3 1.4 3.1M19.7 10.4l-3.5.3-1.4-3.1"/></g>'] }
		],
		flyers: [{ path: "fly", svg: SLEIGH, width: 150, height: 40, every: [40000, 80000], first: [6000, 12000], dur: 14 }],
		toggle: { icon: "❄️", noun: "snowfall" }
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

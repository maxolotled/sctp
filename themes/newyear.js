/*
 * SCTP — New Year's theme (pairs with themes/newyear.css).
 * Confetti, fireworks going off, shooting stars, fairy lights across the top,
 * champagne on the header, a top hat on the snail, party icons. The footer
 * wishes a happy new year for the coming year automatically.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "newyear";

	function flute(w, tilt) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 14 38" width="' + w + '" height="' + Math.round(w * 38 / 14) + '"' + (tilt ? ' style="transform:rotate(' + tilt + 'deg)"' : "") + '>' +
			'<path d="M3 13h8l-.8-4.5H3.8z" fill="#FFD66B" opacity=".9"/>' +
			'<path d="M2.4 2h9.2l-1 12c-.3 3-2 4.6-3.6 4.6S3.7 17 3.4 14z" fill="rgba(255,255,255,.12)" stroke="rgba(255,255,255,.75)" stroke-width="1"/>' +
			'<path d="M3.2 8.5h7.6l-.6 5.5c-.3 2.4-1.8 3.8-3.2 3.8s-2.9-1.4-3.2-3.8z" fill="#FFC940"/>' +
			'<circle cx="6" cy="12" r=".8" fill="#FFF6D0" class="fx-blink"/><circle cx="8" cy="10" r=".6" fill="#FFF6D0" class="fx-blink" style="animation-delay:-.7s"/>' +
			'<path d="M7 18.6v14" stroke="rgba(255,255,255,.75)" stroke-width="1.2"/><ellipse cx="7" cy="34" rx="5" ry="1.6" fill="rgba(255,255,255,.75)"/></svg>';
	}
	function bottle(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 46" width="' + w + '" height="' + Math.round(w * 46 / 16) + '">' +
			'<path d="M6 0h4v12c0 2 5 4.5 5 10v21c0 1.6-1.2 3-2.8 3H3.8C2.2 46 1 44.6 1 43V22c0-5.5 5-8 5-10z" fill="#1F4D2E"/>' +
			'<path d="M5.6 0h4.8v9c0 1.4 1.4 2.4 2.6 3.4H3c1.2-1 2.6-2 2.6-3.4z" fill="#E9B949"/>' +
			'<rect x="2.4" y="24" width="11.2" height="10" rx="1.2" fill="#F5EBD0"/><rect x="4" y="27" width="8" height="1.4" fill="#C9A43B"/><rect x="5" y="30" width="6" height="1" fill="#C9A43B"/>' +
			'<path d="M3 20c0-2 1.5-3.5 3-4.5" stroke="rgba(255,255,255,.35)" stroke-width="1.2" fill="none"/></svg>';
	}
	function firework(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><g stroke-linecap="round" stroke-width="2" class="fx-twinkle">' +
			'<path d="M12 2v5M12 17v5M2 12h5M17 12h5" stroke="#FFC940"/><path d="M5 5l3.5 3.5M15.5 15.5L19 19M19 5l-3.5 3.5M8.5 15.5L5 19" stroke="#FF5AD9"/></g>' +
			'<circle cx="12" cy="12" r="2" fill="#FFF6D0"/></svg>';
	}
	function tophat(fill) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 26">' +
			'<path d="M9 2.5h14l-1 16H10z" fill="' + fill + '"/><path d="M10.2 14h11.6l-.3 3.6H10.5z" fill="#FFC940"/>' +
			'<path d="M12 4.5v11" stroke="rgba(255,255,255,.18)" stroke-width="1.6"/>' +
			'<ellipse cx="16" cy="20" rx="14.5" ry="3.6" fill="#111118"/><ellipse cx="16" cy="3" rx="7" ry="1.6" fill="#2A2A38"/></svg>';
	}

	var CONFIG = {
		id: ID,
		titlePrefix: "🎆",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#0A0A14"/><g transform="translate(6 6) scale(1.17)">' +
			firework(24).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🎩", "🐛": "🪩", "🏆": "🍾", "🧱": "🗓️", "🖼": "🎆", "🛒": "🛍️", "✨": "🎇", "🐌": "🥂",
			"🔒": "⏳", "🎯": "🎲", "📝": "📅", "🚩": "📣", "🔑": "🗝️", "📊": "📈", "🏪": "🛍️", "💡": "✨", "🎨": "🎆"
		},
		kickerIcons: { "Rare-dle": "🎲" },
		logo: { width: "72%", top: "-50%", right: "-18%", rotate: 14, svg: tophat("#1B1B26") },
		props: { side: "left", items: [flute(14, -12), bottle(18), flute(14, 12)] },
		garland: {
			spacing: 34, sag: 8, itemWidth: 10, wire: "rgba(255,230,170,0.3)", anim: "twinkle",
			colors: ["#FFC940", "#FFF6D0", "#FF5AD9", "#5CE1FF", "#FFC940"],
			item: function (c) {
				return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10" width="10" height="10"><circle cx="5" cy="5" r="3.4" fill="' + c + '" style="filter:drop-shadow(0 0 4px ' + c + ')"/></svg>';
			}
		},
		footer: { left: flute(9, -10), text: "Happy New Year {year} from SCTP", right: firework(22), font: '"Limelight", var(--font-display, serif)' },
		particles: [{
			type: "fall", count: 24, mobileCount: 10, size: [8, 15], dur: [7, 14], sway: [20, 70], spin: 180, opacity: 0.9,
			colors: ["#FFC940", "#DDE3EE", "#FF5AD9", "#5CE1FF", "#9D7BFF"],
			shapes: [
				'<rect x="8" y="3" width="8" height="18" rx="1.5"/>',
				'<circle cx="12" cy="12" r="6"/>',
				'<path d="M4 12c3-6 5 6 8 0s5 6 8 0" stroke="currentColor" stroke-width="3" fill="none" stroke-linecap="round"/>'
			]
		}],
		bursts: { every: [2500, 6500], first: [800, 2000], sparks: 28, colors: ["#FFC940", "#FF5AD9", "#5CE1FF", "#FFFFFF", "#FF6B6B"] },
		flyers: [{
			path: "streak", width: 120, height: 40, every: [15000, 35000], first: [3000, 8000], dur: 1.4,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 40"><defs><linearGradient id="fxStreakTail" x1="0" y1="0" x2="1" y2="0">' +
				'<stop offset="0" stop-color="#FFFFFF" stop-opacity="0"/><stop offset="1" stop-color="#FFF6D0"/></linearGradient></defs>' +
				'<path d="M0 0L112 34" stroke="url(#fxStreakTail)" stroke-width="2.2" stroke-linecap="round"/>' +
				'<circle cx="113" cy="34.5" r="3.2" fill="#FFF6D0" style="filter:drop-shadow(0 0 6px #FFE39A)"/></svg>'
		}],
		toggle: { icon: "🎊", noun: "confetti and fireworks" }
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

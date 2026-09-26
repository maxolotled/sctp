/*
 * SCTP — summer festival theme (pairs with themes/summer.css).
 * Fireflies and sky lanterns drifting up, fireworks, glowing paper lanterns
 * across the top, a watermelon slice, goldfish bag and paper fan on the
 * header, a straw hat on the snail, festival icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "summer";

	function lantern(w, c) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 28" width="' + w + '" height="' + Math.round(w * 28 / 16) + '">' +
			'<rect x="4" y="0" width="8" height="3" rx="1" fill="#2A1A10"/>' +
			'<ellipse cx="8" cy="12" rx="7" ry="9" fill="' + c + '" style="filter:drop-shadow(0 0 6px ' + c + ')"/>' +
			'<path d="M1.5 8.5h13M1 12h14M1.5 15.5h13" stroke="rgba(0,0,0,.18)" stroke-width="1"/>' +
			'<rect x="4" y="20.5" width="8" height="3" rx="1" fill="#2A1A10"/><path d="M8 23.5v4" stroke="#2A1A10" stroke-width="1.2"/></svg>';
	}
	function watermelon(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 22" width="' + w + '" height="' + Math.round(w * 22 / 36) + '">' +
			'<path d="M1 3a17 17 0 0 0 34 0z" fill="#3E9E4A"/><path d="M3.4 3a14.6 14.6 0 0 0 29.2 0z" fill="#F4F1DC"/><path d="M5 3a13 13 0 0 0 26 0z" fill="#FF5A63"/>' +
			'<g fill="#2A1A1A"><ellipse cx="12" cy="8" rx=".9" ry="1.4"/><ellipse cx="18" cy="11" rx=".9" ry="1.4"/><ellipse cx="24" cy="8" rx=".9" ry="1.4"/><ellipse cx="15" cy="5.5" rx=".8" ry="1.2"/><ellipse cx="21" cy="5.5" rx=".8" ry="1.2"/></g></svg>';
	}
	function goldfish(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 36" width="' + w + '" height="' + Math.round(w * 36 / 26) + '">' +
			'<path d="M13 1v5" stroke="#FF5FA2" stroke-width="1.4"/><path d="M10 6h6l-1 3h-4z" fill="#FF5FA2"/>' +
			'<path d="M11 9C4 13 1 20 2 27c1 5 6 8 11 8s10-3 11-8c1-7-2-14-9-18z" fill="rgba(190,235,255,.22)" stroke="rgba(220,245,255,.7)" stroke-width="1"/>' +
			'<path d="M3 20h20c.6 2.4.6 4.8 0 7-1 5-5.5 8-10 8s-9-3-10-8c-.6-2.2-.6-4.6 0-7z" fill="rgba(90,190,255,.35)"/>' +
			'<g class="fx-bob"><ellipse cx="12" cy="25" rx="5" ry="3" fill="#FF7A2E"/><path d="M16.5 25l4.5-3.4v6.8z" fill="#FF9A4C"/><circle cx="9.4" cy="24.4" r=".8" fill="#1A1A1A"/></g>' +
			'<circle cx="7" cy="17" r=".9" fill="#FFFFFF" opacity=".7" class="fx-blink"/></svg>';
	}
	function fan(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 38" width="' + w + '" height="' + Math.round(w * 38 / 26) + '" style="transform:rotate(12deg)">' +
			'<rect x="11.6" y="22" width="2.8" height="16" rx="1.4" fill="#C98A4A"/>' +
			'<circle cx="13" cy="13" r="12" fill="#F7F3EA"/><circle cx="13" cy="13" r="12" fill="none" stroke="#3ED6C8" stroke-width="1.6"/>' +
			'<path d="M3 16c3-3 5 3 8 0s5 3 8 0 4 2 5 1" stroke="#3E8FE6" stroke-width="1.8" fill="none"/>' +
			'<path d="M4 20c3-3 5 3 8 0s5 3 8 0" stroke="#3E8FE6" stroke-width="1.4" fill="none" opacity=".7"/>' +
			'<ellipse cx="14" cy="8" rx="4" ry="2.2" fill="#FF7A4D"/><path d="M17.6 8l2.6-2v4z" fill="#FF7A4D"/></svg>';
	}
	function firework(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><g stroke-linecap="round" stroke-width="2" class="fx-twinkle">' +
			'<path d="M12 2v5M12 17v5M2 12h5M17 12h5" stroke="#FF7A4D"/><path d="M5 5l3.5 3.5M15.5 15.5L19 19M19 5l-3.5 3.5M8.5 15.5L5 19" stroke="#3ED6C8"/></g>' +
			'<circle cx="12" cy="12" r="2" fill="#FFF3D0"/></svg>';
	}

	var CONFIG = {
		id: ID,
		titlePrefix: "🏮",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#0E1322"/><g transform="translate(10 3) scale(1.25)">' +
			lantern(16, "#FF4D4D").replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "😎", "🐛": "🦋", "🏆": "🎐", "🧱": "🍉", "🖼": "🎆", "🛒": "🍧", "✨": "🎇", "🐌": "🏮",
			"🔒": "🐚", "🎯": "🎡", "📝": "🎋", "🚩": "🎏", "🔑": "🗝️", "📊": "🌻", "🏪": "⛱️", "💡": "☀️", "🎨": "🎆"
		},
		kickerIcons: { "Rare-dle": "🎡" },
		logo: {
			width: "96%", top: "-42%", left: "-4%", rotate: -10,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 20">' +
				'<ellipse cx="20" cy="14" rx="19" ry="5" fill="#E7C47C"/><path d="M10 13c0-7 4.5-11 10-11s10 4 10 11z" fill="#EFD08E"/>' +
				'<path d="M10.4 10h19.2l.3 3H10.1z" fill="#E03C45"/>' +
				'<path d="M6 13.5c4 2 24 2 28 0M14 6c3-1 9-1 12 0" stroke="rgba(150,110,50,.35)" stroke-width=".8" fill="none"/></svg>'
		},
		props: { side: "left", items: [watermelon(34), goldfish(26), fan(22)] },
		garland: {
			spacing: 58, sag: 10, itemWidth: 16, wire: "rgba(255,220,170,0.4)", anim: "lamp",
			colors: ["#FF4D4D", "#FF9A3C", "#FF7FB0", "#FFD35A", "#FFF2DC"],
			item: function (c) { return lantern(16, c); }
		},
		footer: { left: lantern(14, "#FF4D4D"), text: "Happy summer festival from SCTP", right: firework(22), font: '"Pacifico", var(--font-display, serif)' },
		particles: [
			{ type: "rise", count: 18, mobileCount: 8, size: [4, 7], viewBox: "0 0 10 10", dur: [14, 26], sway: [40, 120], swayDur: [3, 6], spin: 0,
				glow: true, blink: true, opacity: 0.95, colors: ["#EFFF8A", "#FFE58A", "#D9FF6B"], shapes: ['<circle cx="5" cy="5" r="4"/>'] },
			{ type: "rise", count: 3, mobileCount: 2, size: [16, 24], viewBox: "0 0 20 26", dur: [26, 40], sway: [20, 40], swayDur: [4, 6], spin: 4,
				glow: true, opacity: 0.9, colors: ["#FFB35A", "#FF8F4D"],
				shapes: ['<path d="M4 2h12l3 20H1z"/><rect x="6" y="22" width="8" height="2.5" rx="1" fill="#6B3A1A"/><ellipse cx="10" cy="20" rx="3" ry="2" fill="#FFF3B0"/>'] }
		],
		bursts: { every: [4000, 9000], first: [1500, 3500], sparks: 30, colors: ["#FF7A4D", "#FF4D4D", "#FF7FB0", "#FFD35A", "#3ED6C8", "#FFFFFF"] },
		toggle: { icon: "🏮", noun: "fireflies and fireworks" }
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

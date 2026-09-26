/*
 * SCTP — summer theme: a tropical beach (pairs with themes/summer.css).
 * A hibiscus flower lei across
 * the top, seagulls gliding past, a crab scuttling along the bottom, a beach
 * umbrella, beach ball, sandcastle and coconut drink on the header,
 * sunglasses on the snail, beach icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "summer";

	function hibiscus(w, c) {
		var petals = "";
		for (var i = 0; i < 5; i++) petals += '<ellipse cx="12" cy="6.5" rx="4.6" ry="6" fill="' + c + '" transform="rotate(' + (i * 72) + ' 12 12)"/>';
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '">' + petals +
			'<circle cx="12" cy="12" r="2.6" fill="#FFE27A"/><path d="M12 12l4-5" stroke="#FFE27A" stroke-width="1.2"/><circle cx="16.2" cy="6.6" r="1.1" fill="#FFB83D"/></svg>';
	}
	function umbrella(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40" width="' + w + '" height="' + w + '">' +
			'<path d="M20 12l2 27" stroke="#E8D2B0" stroke-width="1.8"/>' +
			'<path d="M2 14C6 5 13 1 20 1s14 4 18 13z" fill="#FF5A5A"/>' +
			'<path d="M20 1c-4 3-6 8-6 13h12c0-5-2-10-6-13z" fill="#FFF4E0"/>' +
			'<path d="M20 1C13 3 8 8 8 14M20 1c7 2 12 7 12 13" stroke="rgba(0,0,0,.12)" stroke-width="1" fill="none"/>' +
			'<ellipse cx="22" cy="39" rx="12" ry="1.6" fill="#E8C27A" opacity=".6"/></svg>';
	}
	function beachBall(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><g class="fx-bob">' +
			'<circle cx="12" cy="12" r="11" fill="#FFFFFF"/>' +
			'<path d="M12 1a11 11 0 0 1 10.4 7.5C17 8 14 5 12 1z" fill="#FF5A5A"/><path d="M22.4 8.5A11 11 0 0 1 15 22.6c2-5 2-10 7.4-14.1z" fill="#34A0FF"/>' +
			'<path d="M15 22.6A11 11 0 0 1 2.2 17c5 .4 9 2.2 12.8 5.6z" fill="#FFD84D"/><path d="M2.2 17A11 11 0 0 1 12 1C9.4 5 6 10 2.2 17z" fill="#34E0C4"/>' +
			'<circle cx="12" cy="12" r="2.4" fill="#FFFFFF"/></g></svg>';
	}
	function sandcastle(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 32" width="' + w + '" height="' + Math.round(w * 32 / 36) + '">' +
			'<path d="M18 2v7" stroke="#8A6A4A" stroke-width="1"/><path d="M18 2l6 2-6 2z" fill="#FF5A5A"/>' +
			'<path d="M1 31V18h4v-3h3v3h4V11h3V8h3v3h3V8h3v3h3v7h4v-3h3v3h4v13z" fill="#E8C27A"/>' +
			'<rect x="15" y="22" width="6" height="9" rx="3" fill="#B8904F"/>' +
			'<path d="M4 25h5M27 25h5M13 16h10" stroke="#C9A060" stroke-width="1.2"/></svg>';
	}
	function coconut(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 30" width="' + w + '" height="' + Math.round(w * 30 / 26) + '">' +
			'<path d="M17 2l-4 14" stroke="#FF5FA2" stroke-width="1.6"/><path d="M5 9c3-4 9-4 12 0z" fill="#FFD84D"/><path d="M11 9V5" stroke="#8A6A4A"/>' +
			'<ellipse cx="13" cy="21" rx="11" ry="9" fill="#7A4A2A"/><path d="M3 17c3-3 17-3 20 0" fill="#F4EAD8"/>' +
			'<ellipse cx="13" cy="16.4" rx="10" ry="2" fill="#F4EAD8"/><circle cx="9" cy="24" r="1" fill="#5A341C"/><circle cx="12" cy="26" r="1" fill="#5A341C"/>' +
			'<g transform="translate(15 12) scale(.4)">' + hibiscus(24, "#FF5A8A").replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>";
	}
	function sun(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + w + '" height="' + w + '"><g stroke="#FFD27A" stroke-width="2" stroke-linecap="round" class="fx-twinkle">' +
			'<path d="M12 1v3M12 20v3M1 12h3M20 12h3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M19.8 4.2l-2.1 2.1M6.3 17.7l-2.1 2.1"/></g><circle cx="12" cy="12" r="5.5" fill="#FFB84D"/></svg>';
	}
	function palm(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 28" width="' + w + '" height="' + Math.round(w * 28 / 24) + '">' +
			'<path d="M11 27c1-8 2-14 1-19" stroke="#8A6A4A" stroke-width="2.4" fill="none" stroke-linecap="round"/>' +
			'<g fill="#2E9E6A"><path d="M12 8C8 3 3 4 1 7c4-1 7 0 11 1z"/><path d="M12 8c2-5 7-7 11-5-4 1-7 3-11 5z"/><path d="M12 8c4-1 9 1 10 5-4-2-7-3-10-5z"/><path d="M12 8C8 8 4 11 4 15c2-3 5-5 8-7z"/></g></svg>';
	}

	var GULL = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 12"><path d="M0 4c5-3 10-3 15 3 5-6 10-6 15-3-5 0-9 2-15 7C9 6 5 4 0 4z" fill="#F4FBF8"/></svg>';
	var CRAB = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 26">' +
		'<path d="M8 20l-5 5M12 21l-3 5M28 21l3 5M32 20l5 5" stroke="#E0452E" stroke-width="2" stroke-linecap="round"/>' +
		'<ellipse cx="20" cy="16" rx="12" ry="7.5" fill="#FF5A3D"/>' +
		'<path d="M9 12C5 10 3 6 5 3c1 3 3 4 5 4M31 12c4-2 6-6 4-9-1 3-3 4-5 4" stroke="#FF5A3D" stroke-width="3" fill="none" stroke-linecap="round"/>' +
		'<path d="M16 9V5M24 9V5" stroke="#FF5A3D" stroke-width="1.6"/><circle cx="16" cy="4.4" r="2" fill="#FFFFFF"/><circle cx="24" cy="4.4" r="2" fill="#FFFFFF"/>' +
		'<circle cx="16.4" cy="4.6" r="1" fill="#1A1A1A"/><circle cx="24.4" cy="4.6" r="1" fill="#1A1A1A"/><path d="M17 17q3 2 6 0" stroke="#8A2418" stroke-width="1.2" fill="none"/></svg>';

	var CONFIG = {
		id: ID,
		titlePrefix: "🌴",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#0B1C24"/><g transform="translate(6 4) scale(1.15)">' +
			palm(24).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "😎", "🐛": "🦀", "🏆": "🐚", "🧱": "🍉", "🖼": "🏝️", "🛒": "🍹", "✨": "☀️", "🐌": "🌴",
			"🔒": "🥥", "🎯": "🏐", "📝": "🌺", "🚩": "🐬", "🔑": "🗝️", "📊": "🌊", "🏪": "⛱️", "💡": "☀️", "🎨": "🏝️"
		},
		kickerIcons: { "Rare-dle": "🏐" },
		logo: {
			width: "92%", top: "6%", left: "2%",
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 14">' +
				'<path d="M1 3h38" stroke="#1A1A22" stroke-width="2" stroke-linecap="round"/>' +
				'<path d="M3 3h14c0 6-2 10-7 10S3 9 3 3zM23 3h14c0 6-2 10-7 10s-7-4-7-10z" fill="#1A1A22"/>' +
				'<path d="M6 5c1 3 3 4 5 4M26 5c1 3 3 4 5 4" stroke="#34E0C4" stroke-width="1.4" fill="none" opacity=".8"/></svg>'
		},
		props: { side: "left", items: [umbrella(38), sandcastle(32), beachBall(20), coconut(22)] },
		garland: {
			spacing: 30, sag: 7, itemWidth: 16, wire: "rgba(255,230,190,0.35)", anim: "lamp",
			colors: ["#FF5A8A", "#FFB83D", "#FFFFFF", "#FF7A4D", "#FFD84D"],
			item: function (c) { return hibiscus(16, c); }
		},
		footer: { left: palm(22), text: "Summer vibes from SCTP", right: sun(22), font: '"Pacifico", var(--font-display, serif)' },
		flyers: [
			{ path: "fly", svg: GULL, width: 40, height: 16, every: [25000, 50000], first: [4000, 9000], dur: 12, flap: true },
			{ path: "hop", svg: CRAB, width: 42, height: 27, every: [30000, 60000], first: [10000, 18000], dur: 16 }
		]
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

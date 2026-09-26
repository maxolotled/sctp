/*
 * SCTP — birthday theme (pairs with themes/birthday.css), for Snailcraft's birthday.
 * Confetti pops, HAPPY BIRTHDAY SNAILCRAFT bunting across
 * the top, a cake with flickering candles and presents on the header, a party
 * hat on the snail, party icons.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "birthday";

	function cake(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -4 40 42" width="' + w + '" height="' + Math.round(w * 42 / 40) + '">' +
			'<ellipse cx="20" cy="36" rx="19" ry="2.6" fill="#E8DDF5"/>' +
			'<rect x="4" y="22" width="32" height="13" rx="2.5" fill="#FF7EB6"/>' +
			'<rect x="9" y="12" width="22" height="11" rx="2.5" fill="#FFE3F0"/>' +
			'<path d="M4 24c3 3 5 0 8 2s5-1 8 1 5-1 8 1 5-1 8 0v-3.5H4z" fill="#FFF6FB"/>' +
			'<path d="M9 14c2.5 2.5 4 0 6.5 1.6s4-1 6.5.6 4-1 6.5.4 1.5-.4 2.5-.6V12H9z" fill="#FF5FA2"/>' +
			'<g fill="#5CE1E6"><circle cx="10" cy="29" r="1.2"/><circle cx="20" cy="31" r="1.2"/><circle cx="30" cy="29" r="1.2"/></g>' +
			'<rect x="13.2" y="4" width="2.2" height="8" fill="#5CE1E6"/><rect x="19" y="3" width="2.2" height="9" fill="#FFD34D"/><rect x="24.6" y="4" width="2.2" height="8" fill="#A57BFF"/>' +
			'<g class="fx-flicker" fill="#FFB347" style="filter:drop-shadow(0 0 3px #FFD35A)">' +
				'<path d="M14.3 0c1.4 1.4 1.6 3 0 4-1.6-1-1.4-2.6 0-4z"/><path d="M20.1-1c1.4 1.4 1.6 3 0 4-1.6-1-1.4-2.6 0-4z"/><path d="M25.7 0c1.4 1.4 1.6 3 0 4-1.6-1-1.4-2.6 0-4z"/></g>' +
			'</svg>';
	}
	function present(w, body, ribbon) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -2 28 30" width="' + w + '" height="' + Math.round(w * 30 / 28) + '">' +
			'<rect x="2" y="10" width="24" height="17" rx="2" fill="' + body + '"/><rect x="0" y="6" width="28" height="6" rx="1.5" fill="' + body + '" style="filter:brightness(1.15)"/>' +
			'<rect x="12" y="6" width="4" height="21" fill="' + ribbon + '"/><path d="M14 6C10-.5 3.5 1.5 7.5 6ZM14 6c4-6.5 10.5-4.5 6.5 0Z" fill="' + ribbon + '"/></svg>';
	}
	function balloon(w, c) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 44" width="' + w + '" height="' + Math.round(w * 44 / 24) + '">' +
			'<path d="M12 2c6.5 0 10 5.5 10 11 0 7-5.5 12-10 13C7.5 25 2 20 2 13 2 7.5 5.5 2 12 2z" fill="' + c + '"/><path d="M10.6 25.8h2.8L12 28.2z" fill="' + c + '"/>' +
			'<path d="M12 28.2c-2.5 3.5 2.5 6-.5 9.5s1.5 5 0 6" stroke="rgba(255,255,255,.55)" stroke-width=".8" fill="none"/><ellipse cx="8" cy="9" rx="2.2" ry="3.6" fill="#FFFFFF" opacity=".35"/></svg>';
	}

	var CONFETTI_COLORS = ["#FF5FA2", "#5CE1E6", "#FFD34D", "#A57BFF", "#FF8A4C", "#7CF29A"];

	var CONFIG = {
		id: ID,
		titlePrefix: "🎂",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#140E1E"/><g transform="translate(2 4) scale(.9)">' +
			cake(40).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🥳", "🐛": "🪅", "🏆": "🎁", "🧱": "🧁", "🖼": "🎈", "🛒": "🛍️", "✨": "🎉", "🐌": "🎂",
			"🔒": "🕯️", "🎯": "🎲", "📝": "💌", "🚩": "📣", "🔑": "🗝️", "📊": "📈", "🏪": "🎪", "💡": "✨", "🎨": "🎈"
		},
		kickerIcons: { "Rare-dle": "🎲" },
		logo: {
			width: "62%", top: "-66%", right: "-4%", rotate: 16,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 30">' +
				'<defs><clipPath id="fxPartyHat"><path d="M12 4L22 28H2z"/></clipPath></defs>' +
				'<path d="M12 4L22 28H2z" fill="#FF5FA2"/>' +
				'<g clip-path="url(#fxPartyHat)" fill="#FFD34D"><path d="M0 14l24-6v4L0 18zM0 22l24-6v4L0 26z"/></g>' +
				'<g fill="#5CE1E6"><circle cx="9" cy="20" r="1.2"/><circle cx="15" cy="15" r="1.2"/></g>' +
				'<ellipse cx="12" cy="28" rx="11" ry="2" fill="#E03E84"/><circle cx="12" cy="3.6" r="3.4" fill="#5CE1E6"/></svg>'
		},
		props: { side: "left", items: [present(22, "#5CE1E6", "#FF5FA2"), cake(46), present(26, "#A57BFF", "#FFD34D")] },
		garland: {
			spacing: 38, sag: 7, itemWidth: 20, wire: "rgba(255,220,240,0.45)", anim: "lamp",
			colors: CONFETTI_COLORS,
			item: function (c, i, n) {
				var text = n >= 28 ? "HAPPY BIRTHDAY SNAILCRAFT" : n >= 16 ? "HAPPY BIRTHDAY" : n >= 12 ? "SNAILCRAFT" : "";
				var start = Math.floor((n - text.length) / 2);
				var ch = i >= start && i < start + text.length ? text.charAt(i - start) : "";
				if (ch === " ") return ""; // a gap between words
				return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 24" width="20" height="24"><path d="M0 0H20L10 22Z" fill="' + c + '"/>' +
					(ch ? '<text x="10" y="11" font-size="10" font-family="Fredoka, system-ui, sans-serif" font-weight="700" text-anchor="middle" fill="#FFFFFF" stroke="rgba(0,0,0,.35)" stroke-width=".6" paint-order="stroke">' + ch + "</text>" : "") +
					"</svg>";
			}
		},
		footer: { left: cake(24), text: "Happy birthday, Snailcraft! Love from all of us at SCTP", right: balloon(14, "#FF5FA2"), font: '"Fredoka", system-ui, sans-serif' },
		bursts: { every: [5000, 11000], first: [2000, 4000], sparks: 26, shape: "rect", colors: CONFETTI_COLORS },
		toggle: { icon: "🎉", noun: "confetti pops" }
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

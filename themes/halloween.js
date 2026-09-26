/*
 * SCTP — Halloween / fall theme (pairs with themes/halloween.css).
 * Jack-o-lanterns, falling leaves, a bat now and then, a witch hat on the snail.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "halloween";

	function pumpkin(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 36" width="' + w + '" height="' + Math.round(w * 0.9) + '">' +
			'<path d="M18.5 7.5c0-2.5.8-4.8 3.2-6.3l1.6 1.4c-1.5 1-2 2.6-2 4.9z" fill="#5E7A2E"/>' +
			'<ellipse cx="20" cy="21" rx="17.5" ry="13.5" fill="#D9651A"/>' +
			'<ellipse cx="11" cy="21" rx="8" ry="12.5" fill="#E8741E"/><ellipse cx="29" cy="21" rx="8" ry="12.5" fill="#E8741E"/>' +
			'<ellipse cx="20" cy="21" rx="7.5" ry="13.5" fill="#F28A2E"/>' +
			'<g class="fx-flicker" fill="#FFD35A" style="filter:drop-shadow(0 0 3px #FFB347);animation-delay:-' + (w % 3) + 's">' +
				'<path d="M9.5 18l4.3-5.2 4.2 5.2z"/><path d="M22 18l4.2-5.2 4.3 5.2z"/><path d="M18.4 22.2l1.6-2.6 1.6 2.6z"/>' +
				'<path d="M8.5 24.5c3 4.5 7 6.5 11.5 6.5s8.5-2 11.5-6.5l-3.4 1.3-1.9-2-1.9 2.6-2.1-2.6-2.1 2.6-2.1-2.6-1.9 2.6-1.9-2.6-1.9 2z"/>' +
			'</g></svg>';
	}

	var CONFIG = {
		id: ID,
		titlePrefix: "🎃",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#140E12"/><g transform="translate(0 2)">' +
			pumpkin(40).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + "</g></svg>",
		icons: {
			"⚙": "🧙", "🐛": "🕷️", "🏆": "🍬", "🧱": "🧹", "🖼": "🦇", "🛒": "🧺", "✨": "🍂", "🐌": "🎃",
			"🔒": "🕯️", "🎯": "🔮", "📝": "📜", "🚩": "👻", "🔑": "🗝️", "📊": "🦉", "🏪": "🏚️", "💡": "🕯️", "🎨": "🎃"
		},
		kickerIcons: { "Rare-dle": "🔮" },
		logo: {
			width: "78%", top: "-50%", right: "-22%", rotate: 18,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 28">' +
				'<path d="M8 23.5L16.6 2.4c.6-1.4 2.6-1.2 2.9.3L24.5 23.5z" fill="#3A1F47"/>' +
				'<path d="M16.6 2.4c-1.8.3-3.2 1.6-3.6 3.6l3.2-.6z" fill="#4B2A5C"/>' +
				'<path d="M9.4 19.2h13.8l.8 3.4H8.6z" fill="#FF8C2B"/>' +
				'<rect x="14.2" y="19.2" width="3.6" height="3.4" fill="none" stroke="#FFD35A" stroke-width="1"/>' +
				'<ellipse cx="16" cy="24.3" rx="15.5" ry="3.4" fill="#2A1633"/></svg>'
		},
		props: { side: "left", items: [pumpkin(30), pumpkin(42), pumpkin(24)] },
		footer: { left: pumpkin(22), text: "Happy Halloween from SCTP", right: pumpkin(22), font: '"Creepster", var(--font-display, serif)' },
		particles: [{
			type: "fall", count: 14, mobileCount: 7, size: [14, 26], dur: [11, 22], sway: [30, 90], swayDur: [2.6, 4.6], spin: 35,
			colors: ["#E8672A", "#F29B38", "#C2410C", "#A3471B", "#D9A441", "#B8561F", "#E0892E"],
			shapes: [
				'<path d="M12 1.5l1.7 4 3.1-1.6-.8 4.2 3.8-.6-2.4 3.2 3.6 1.7-4 1.3 1.1 2.8-3.5-1L13 21h-2l-1.6-5.5-3.5 1 1.1-2.8-4-1.3 3.6-1.7-2.4-3.2 3.8.6-.8-4.2 3.1 1.6z"/><path d="M12 21v2.5" stroke="rgba(0,0,0,.35)" stroke-width="1" fill="none"/>',
				'<path d="M12 2c-1.6 1.3-1 2.9-2.6 3.6-1.5.7-2.3-.6-3.4.6.7 1.5 2.3 1.7 1.8 3.4-.5 1.6-2.4 1.3-2.6 2.9 1.3.8 2.8.1 3.4 1.7.5 1.4-.9 2.5.1 3.6 1.2-.2 1.8-1.6 3.3-1.3V22h2v-5.5c1.5-.3 2.1 1.1 3.3 1.3 1-1.1-.4-2.2.1-3.6.6-1.6 2.1-.9 3.4-1.7-.2-1.6-2.1-1.3-2.6-2.9-.5-1.7 1.1-1.9 1.8-3.4-1.1-1.2-1.9.1-3.4-.6C13 4.9 13.6 3.3 12 2z"/>',
				'<path d="M12 2C7 6 5 11 6 16c1 3 3 5 6 6 3-1 5-3 6-6 1-5-1-10-6-14z"/><path d="M12 5v16" stroke="rgba(0,0,0,.3)" stroke-width="1" fill="none"/>'
			]
		}],
		flyers: [{
			path: "fly", width: 34, height: 18, every: [25000, 55000], first: [4000, 9000], dur: 9, flap: true,
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 19 10"><path d="M0 6C3 2 6 2 8 5C9 3 10 3 11 5C13 2 16 2 19 6C16 5 14 7 13 9C12 7 10.5 7.5 9.5 9C8.5 7.5 7 7 6 9C5 7 3 5 0 6Z" fill="#0B070A"/></svg>'
		}],
		toggle: { icon: "🍂", noun: "falling leaves" }
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

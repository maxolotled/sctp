/*
 * SCTP — Halloween / fall theme decorations (pairs with themes/halloween.css).
 *
 * Active while copied to /theme.js. Everything here is decoration only: it
 * never touches page content, every element is aria-hidden and ignores the
 * mouse, and motion stops for people who ask their system for reduced motion.
 * To revert, copy themes/original.js over /theme.js. See themes/README.md.
 */
(function () {
	"use strict";
	if (window.__sctpThemeLoaded) return;
	window.__sctpThemeLoaded = true;

	var SVGNS = "http://www.w3.org/2000/svg";
	var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

	function el(tag, cls, html) {
		var e = document.createElement(tag);
		if (cls) e.className = cls;
		if (html) e.innerHTML = html;
		e.setAttribute("aria-hidden", "true");
		return e;
	}

	// ---- artwork ----------------------------------------------------------

	function pumpkinSvg(w) {
		return '<svg xmlns="' + SVGNS + '" viewBox="0 0 40 36" width="' + w + '" height="' + Math.round(w * 0.9) + '">' +
			'<path d="M18.5 7.5c0-2.5.8-4.8 3.2-6.3l1.6 1.4c-1.5 1-2 2.6-2 4.9z" fill="#5E7A2E"/>' +
			'<ellipse cx="20" cy="21" rx="17.5" ry="13.5" fill="#D9651A"/>' +
			'<ellipse cx="11" cy="21" rx="8" ry="12.5" fill="#E8741E"/>' +
			'<ellipse cx="29" cy="21" rx="8" ry="12.5" fill="#E8741E"/>' +
			'<ellipse cx="20" cy="21" rx="7.5" ry="13.5" fill="#F28A2E"/>' +
			'<g class="hw-face" fill="#FFD35A">' +
				'<path d="M9.5 18l4.3-5.2 4.2 5.2z"/><path d="M22 18l4.2-5.2 4.3 5.2z"/>' +
				'<path d="M18.4 22.2l1.6-2.6 1.6 2.6z"/>' +
				'<path d="M8.5 24.5c3 4.5 7 6.5 11.5 6.5s8.5-2 11.5-6.5l-3.4 1.3-1.9-2-1.9 2.6-2.1-2.6-2.1 2.6-2.1-2.6-1.9 2.6-1.9-2.6-1.9 2z"/>' +
			'</g></svg>';
	}

	var LEAF_PATHS = [
		// maple
		'<path d="M12 1.5l1.7 4 3.1-1.6-.8 4.2 3.8-.6-2.4 3.2 3.6 1.7-4 1.3 1.1 2.8-3.5-1L13 21h-2l-1.6-5.5-3.5 1 1.1-2.8-4-1.3 3.6-1.7-2.4-3.2 3.8.6-.8-4.2 3.1 1.6z"/><path d="M12 21v2.5" stroke="rgba(0,0,0,.35)" stroke-width="1" fill="none"/>',
		// oak-ish
		'<path d="M12 2c-1.6 1.3-1 2.9-2.6 3.6-1.5.7-2.3-.6-3.4.6.7 1.5 2.3 1.7 1.8 3.4-.5 1.6-2.4 1.3-2.6 2.9 1.3.8 2.8.1 3.4 1.7.5 1.4-.9 2.5.1 3.6 1.2-.2 1.8-1.6 3.3-1.3V22h2v-5.5c1.5-.3 2.1 1.1 3.3 1.3 1-1.1-.4-2.2.1-3.6.6-1.6 2.1-.9 3.4-1.7-.2-1.6-2.1-1.3-2.6-2.9-.5-1.7 1.1-1.9 1.8-3.4-1.1-1.2-1.9.1-3.4-.6C13 4.9 13.6 3.3 12 2z"/>',
		// plain leaf
		'<path d="M12 2C7 6 5 11 6 16c1 3 3 5 6 6 3-1 5-3 6-6 1-5-1-10-6-14z"/><path d="M12 5v16" stroke="rgba(0,0,0,.3)" stroke-width="1" fill="none"/>'
	];
	var LEAF_COLORS = ["#E8672A", "#F29B38", "#C2410C", "#A3471B", "#D9A441", "#B8561F", "#E0892E"];

	var BAT_PATH = "M0 6C3 2 6 2 8 5C9 3 10 3 11 5C13 2 16 2 19 6C16 5 14 7 13 9C12 7 10.5 7.5 9.5 9C8.5 7.5 7 7 6 9C5 7 3 5 0 6Z";

	var HAT_SVG = '<svg xmlns="' + SVGNS + '" viewBox="0 0 32 28" width="100%" height="100%">' +
		'<path d="M8 23.5L16.6 2.4c.6-1.4 2.6-1.2 2.9.3L24.5 23.5z" fill="#3A1F47"/>' +
		'<path d="M16.6 2.4c-1.8.3-3.2 1.6-3.6 3.6l3.2-.6z" fill="#4B2A5C"/>' +
		'<path d="M9.4 19.2h13.8l.8 3.4H8.6z" fill="#FF8C2B"/>' +
		'<rect x="14.2" y="19.2" width="3.6" height="3.4" fill="none" stroke="#FFD35A" stroke-width="1"/>' +
		'<ellipse cx="16" cy="24.3" rx="15.5" ry="3.4" fill="#2A1633"/></svg>';

	// ---- icons ------------------------------------------------------------

	// Page badge emoji -> a Halloween counterpart (looked up without the U+FE0F variation selector).
	var ICONS = {
		"⚙": "🧙",          // ⚙ settings/account -> 🧙
		"🐛": "🕷️",    // 🐛 bug -> 🕷
		"🏆": "🍬",    // 🏆 collection -> 🍬
		"🧱": "🧹",    // 🧱 material list -> 🧹
		"🖼": "🦇",    // 🖼 mapart -> 🦇
		"🛒": "🧺",    // 🛒 marketplace -> 🧺
		"✨": "🍂",          // ✨ what's new -> 🍂
		"🐌": "🎃",    // 🐌 onboarding -> 🎃
		"🔒": "🕯️",    // 🔒 privacy -> 🕯
		"🎯": "🔮",    // 🎯 rare-dle -> 🔮
		"📝": "📜",    // 📝 register -> 📜
		"🚩": "👻",    // 🚩 report -> 👻
		"🔑": "🗝️",    // 🔑 reset password -> 🗝
		"📊": "🦉",    // 📊 stats -> 🦉
		"🏪": "🏚️",    // 🏪 store -> 🏚
		"💡": "🕯️",    // 💡 suggest -> 🕯
		"🎨": "🎃"     // 🎨 mapart of the day -> 🎃
	};
	function swapEmojiIn(node) {
		if (!node) return;
		var walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null);
		var t;
		while ((t = walker.nextNode())) {
			var s = t.nodeValue, out = s;
			for (var k in ICONS) if (out.indexOf(k) !== -1) out = out.split(k + "️").join(ICONS[k]).split(k).join(ICONS[k]);
			if (out !== s) t.nodeValue = out;
		}
	}
	function themeIcons() {
		var marks = document.querySelectorAll("div.brand-mark, span.brand-mark, .promo-kicker");
		for (var i = 0; i < marks.length; i++) swapEmojiIn(marks[i]);

		// The Rare-dle card on the home page has no icon of its own.
		var kickers = document.querySelectorAll(".promo-kicker");
		for (var j = 0; j < kickers.length; j++) {
			if (/^\s*Rare-dle\s*$/i.test(kickers[j].textContent)) kickers[j].textContent = "🔮 Rare-dle";
		}

		// A witch hat on the snail logo.
		var snails = document.querySelectorAll("svg.brand-mark");
		for (var n = 0; n < snails.length; n++) {
			var svg = snails[n];
			if (svg.parentNode && svg.parentNode.classList && svg.parentNode.classList.contains("hw-hat-wrap")) continue;
			var wrap = el("span", "hw-hat-wrap");
			svg.parentNode.insertBefore(wrap, svg);
			wrap.appendChild(svg);
			wrap.appendChild(el("span", "hw-hat", HAT_SVG));
		}

		// Jack-o-lantern favicon.
		var fav = '<svg xmlns="' + SVGNS + '" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#140E12"/><g transform="translate(0 2)">' +
			pumpkinSvg(40).replace(/^<svg[^>]*>/, "").replace(/<\/svg>$/, "") + '</g></svg>';
		var links = document.querySelectorAll('link[rel~="icon"]');
		var href = "data:image/svg+xml," + encodeURIComponent(fav);
		if (links.length) for (var l = 0; l < links.length; l++) links[l].href = href;
		else { var ln = document.createElement("link"); ln.rel = "icon"; ln.href = href; document.head.appendChild(ln); }

		if (document.title.indexOf("🎃") === -1) document.title = "🎃 " + document.title;
	}

	// ---- jack-o-lanterns --------------------------------------------------

	function porch() {
		var header = document.querySelector("header.site");
		if (header && !header.querySelector(".hw-porch")) {
			header.appendChild(el("div", "hw-porch", pumpkinSvg(30) + pumpkinSvg(42) + pumpkinSvg(24)));
		}
		var footer = document.querySelector("footer.site");
		if (footer && !footer.querySelector(".hw-footer-porch")) {
			footer.insertBefore(el("div", "hw-footer-porch",
				pumpkinSvg(22) + '<span>Happy Halloween from SCTP</span>' + pumpkinSvg(22)), footer.firstChild);
		}
	}

	// ---- falling leaves ---------------------------------------------------

	var LEAVES_KEY = "sctp_fall_leaves";
	var leafLayer = null;

	function leavesWanted() {
		try {
			var v = localStorage.getItem(LEAVES_KEY);
			if (v === "on") return true;
			if (v === "off") return false;
		} catch (e) {}
		return !reduceMotion;
	}

	function rand(a, b) { return a + Math.random() * (b - a); }

	function startLeaves() {
		if (leafLayer) return;
		leafLayer = el("div", "hw-leaves");
		var count = window.innerWidth < 700 ? 7 : 14;
		for (var i = 0; i < count; i++) {
			var dur = rand(11, 22);
			var size = rand(14, 26);
			var fall = el("div", "hw-leaf");
			fall.style.left = rand(-3, 97).toFixed(1) + "vw";
			fall.style.animationDuration = dur.toFixed(1) + "s";
			// negative delay: leaves are already mid-air when the page loads
			fall.style.animationDelay = (-rand(0, dur)).toFixed(1) + "s";
			var sway = el("div", "hw-leaf-sway");
			sway.style.animationDuration = rand(2.6, 4.6).toFixed(1) + "s";
			sway.style.setProperty("--hw-drift", rand(30, 90).toFixed(0) + "px");
			sway.innerHTML = '<svg xmlns="' + SVGNS + '" viewBox="0 0 24 24" width="' + size.toFixed(0) + '" height="' + size.toFixed(0) + '" fill="' +
				LEAF_COLORS[i % LEAF_COLORS.length] + '" style="transform:rotate(' + rand(0, 360).toFixed(0) + 'deg)">' +
				LEAF_PATHS[i % LEAF_PATHS.length] + "</svg>";
			fall.appendChild(sway);
			leafLayer.appendChild(fall);
		}
		document.body.appendChild(leafLayer);
	}
	function stopLeaves() {
		if (leafLayer && leafLayer.parentNode) leafLayer.parentNode.removeChild(leafLayer);
		leafLayer = null;
	}

	function leafToggle() {
		var btn = document.createElement("button");
		btn.type = "button";
		btn.className = "hw-leaf-toggle";
		btn.textContent = "🍂";
		function sync() {
			var on = !!leafLayer;
			btn.setAttribute("aria-pressed", on ? "true" : "false");
			btn.title = on ? "Turn falling leaves off" : "Turn falling leaves on";
			btn.setAttribute("aria-label", btn.title);
			btn.classList.toggle("off", !on);
		}
		btn.addEventListener("click", function () {
			if (leafLayer) stopLeaves(); else startLeaves();
			try { localStorage.setItem(LEAVES_KEY, leafLayer ? "on" : "off"); } catch (e) {}
			sync();
		});
		if (leavesWanted()) startLeaves();
		sync();
		document.body.appendChild(btn);
	}

	// ---- the occasional bat ----------------------------------------------

	function bats() {
		if (reduceMotion) return;
		function fly() {
			if (document.hidden) return schedule();
			var bat = el("div", "hw-bat", '<svg xmlns="' + SVGNS + '" viewBox="0 0 19 10" width="34" height="18"><path d="' + BAT_PATH + '" fill="#0B070A"/></svg>');
			bat.style.top = rand(6, 30).toFixed(0) + "vh";
			var rtl = Math.random() < 0.5;
			if (rtl) bat.classList.add("rtl");
			document.body.appendChild(bat);
			bat.addEventListener("animationend", function () { if (bat.parentNode) bat.parentNode.removeChild(bat); });
			schedule();
		}
		function schedule() { setTimeout(fly, rand(25000, 55000)); }
		setTimeout(fly, rand(4000, 9000));
	}

	function init() {
		themeIcons();
		porch();
		leafToggle();
		bats();
	}
	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
	else init();
})();

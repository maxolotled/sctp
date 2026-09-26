/*
 * SCTP — April Fools' theme (pairs with themes/aprilfools.css).
 * The pranks (see pranks() below): a fake "SCTP NEWS" ticker, a one-time
 * "SCTP Premium™" upsell that turns out to be a joke, silly nav names, a
 * "Download more RAM" button, a "come back!" tab title, and cards that
 * randomly shake. Plus the decoration: confetti pops, a rubber chicken flying
 * past, the snail speedrunning along the bottom, clown bunting, a whoopee
 * cushion and jack-in-the-box,
 * googly eyes on the (upside-down) snail, and deliberately wrong icons.
 * Nothing actually breaks: every link and button still does its normal job.
 * The effects themselves live in themes/fx.js. See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "aprilfools";

	var SILLY = ["#FF4DA6", "#FFE14D", "#4DD8FF", "#7CF29A", "#FF8A4C", "#B57BFF"];

	function chicken(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 44 22" width="' + w + '" height="' + Math.round(w / 2) + '">' +
			'<path d="M6 12c0-4 4-6 10-6h14c3 0 5-1 7-3 2-1 4 0 4 2 0 3-3 5-6 6-2 5-8 8-16 8H13c-4 0-7-3-7-7z" fill="#FFD84D"/>' +
			'<path d="M38 4c0-2 1-4 3-3 0 1 1 2 0 3 1 0 2 1 1 2z" fill="#E0332E"/><path d="M41.5 7l2.5 1-2.6 1.2z" fill="#FF8A2E"/>' +
			'<circle cx="38.6" cy="6" r=".9" fill="#1A1A1A"/><path d="M40 9c0 1.6-.6 2.6-1.4 3" stroke="#E0332E" stroke-width="1.2" fill="none"/>' +
			'<path d="M6 12L1 10M6 13L0 14M6 14l-4 3" stroke="#F2C233" stroke-width="1.5" stroke-linecap="round"/>' +
			'<path d="M18 19v3M24 19v3" stroke="#FF8A2E" stroke-width="1.4"/></svg>';
	}
	function cushion(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 22" width="' + w + '" height="' + Math.round(w * 22 / 36) + '">' +
			'<path d="M4 14c0-6 6-10 13-10s13 4 13 10-6 8-13 8-13-2-13-8z" fill="#FF6FB5"/><path d="M29 12l6-2v5l-6-1z" fill="#FF4DA6"/>' +
			'<path d="M9 9c3-3 8-3 11-2" stroke="#FFFFFF" stroke-width="1.6" fill="none" opacity=".5" stroke-linecap="round"/></svg>';
	}
	function jackbox(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -4 30 44" width="' + w + '" height="' + Math.round(w * 44 / 30) + '">' +
			'<rect x="3" y="24" width="24" height="16" rx="2" fill="#4DD8FF"/><path d="M3 24h24l-4-5H7z" fill="#FF4DA6"/>' +
			'<path d="M9 29l3 4 3-4 3 4 3-4" stroke="#FFE14D" stroke-width="1.8" fill="none"/>' +
			'<g class="fx-bob"><path d="M15 20l-4-2 8-2-8-2 8-2-4-2" stroke="#C9C5DA" stroke-width="1.4" fill="none"/>' +
			'<circle cx="15" cy="4" r="6.5" fill="#FFF4E6"/><circle cx="15" cy="5" r="1.8" fill="#E0332E"/>' +
			'<circle cx="12.6" cy="2.6" r=".9" fill="#1A1A1A"/><circle cx="17.4" cy="2.6" r=".9" fill="#1A1A1A"/>' +
			'<path d="M12 7.4q3 2.4 6 0" stroke="#E0332E" stroke-width="1" fill="none"/>' +
			'<circle cx="8.6" cy="1" r="2.6" fill="#FF8A4C"/><circle cx="21.4" cy="1" r="2.6" fill="#FF8A4C"/></g></svg>';
	}
	function clownNose(w) {
		return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" width="' + w + '" height="' + w + '"><circle cx="10" cy="10" r="8.5" fill="#E0332E"/><ellipse cx="7" cy="6.5" rx="2.6" ry="1.8" fill="#FFFFFF" opacity=".55"/></svg>';
	}

	var SPEEDY_SNAIL = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 52 26">' +
		'<path d="M0 12h9M2 17h8M4 22h7" stroke="rgba(255,255,255,.55)" stroke-width="1.6" stroke-linecap="round"/>' +
		'<path d="M12 24c0-2 2-3 5-3h22c4 0 6-3 7-7l1-4h2l-1 5c-1 6-4 9-9 9z" fill="#E8D2B0"/>' +
		'<path d="M46 10l-2-7M48.4 10.4l1.6-7" stroke="#E8D2B0" stroke-width="1.5" stroke-linecap="round"/><circle cx="44" cy="3" r="1.4" fill="#1A1A1A"/><circle cx="50" cy="3.4" r="1.4" fill="#1A1A1A"/>' +
		'<circle cx="28" cy="13" r="10" fill="#B8742A"/><path d="M28 13m-2.4 0a2.4 2.4 0 1 1 4.8 0 4.8 4.8 0 1 1-9.6 0 7.2 7.2 0 1 1 14.4 0" stroke="#7A4A1A" stroke-width="1.6" fill="none"/></svg>';

	var CONFIG = {
		id: ID,
		titlePrefix: "🤡",
		favicon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="8" fill="#14101E"/>' +
			'<circle cx="13" cy="20" r="9" fill="#FFFFFF" stroke="#1A1A1A" stroke-width="1.6"/><circle cx="28" cy="20" r="9" fill="#FFFFFF" stroke="#1A1A1A" stroke-width="1.6"/>' +
			'<circle cx="10" cy="24" r="4.4" fill="#1A1A1A"/><circle cx="31" cy="16" r="4.4" fill="#1A1A1A"/></svg>',
		icons: {
			"⚙": "🤡", "🐛": "🦆", "🏆": "🍌", "🧱": "🧀", "🖼": "🙃", "🛒": "🥸", "✨": "🎭", "🐌": "🐢",
			"🔒": "🪤", "🎯": "🎪", "📝": "🤪", "🚩": "🐔", "🔑": "🔧", "📊": "📉", "🏪": "🎪", "💡": "🤔", "🎨": "🙃"
		},
		kickerIcons: { "Rare-dle": "🎪" },
		logo: {
			width: "74%", top: "4%", left: "12%",
			svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 30 16">' +
				'<circle cx="8" cy="8" r="7" fill="#FFFFFF" stroke="#1A1A1A" stroke-width="1.2"/><circle cx="22" cy="8" r="7" fill="#FFFFFF" stroke="#1A1A1A" stroke-width="1.2"/>' +
				'<circle class="af-pupil" cx="9" cy="10" r="3.4" fill="#1A1A1A"/><circle class="af-pupil" cx="21" cy="9" r="3.4" fill="#1A1A1A" style="animation-delay:-1.3s"/></svg>'
		},
		props: { side: "left", items: [cushion(32), jackbox(26), chicken(40)] },
		garland: {
			spacing: 30, sag: 6, itemWidth: 14, wire: "rgba(255,255,255,0.35)", anim: "lamp",
			colors: ["#FF4DA6", "#FFE14D", "#4DD8FF", "#7CF29A"],
			item: function (c) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 14 22" width="14" height="22"><path d="M7 0l7 11-7 11-7-11z" fill="' + c + '"/><circle cx="7" cy="11" r="1.6" fill="#FFFFFF" opacity=".7"/></svg>'; }
		},
		footer: { left: clownNose(18), text: "Happy April Fools' from SCTP. Nothing is broken. Probably.", right: chicken(34), font: '"Comic Neue", "Comic Sans MS", system-ui, sans-serif' },
		bursts: { every: [9000, 16000], first: [3000, 6000], sparks: 22, shape: "rect", colors: SILLY },
		flyers: [
			{ path: "fly", svg: chicken(44).replace(/ width="44" height="22"/, ""), width: 60, height: 30, every: [30000, 60000], first: [6000, 12000], dur: 8 },
			{ path: "hop", svg: SPEEDY_SNAIL, width: 58, height: 29, every: [25000, 50000], first: [12000, 20000], dur: 3.2 }
		],
		toggle: { icon: "🤡", noun: "confetti pops" },
		extra: pranks
	};

	function pranks(h) {
		// 1. A fake breaking-news ticker across the very top of the page.
		var closedKey = "sctp_af_ticker_closed";
		var closed = false;
		try { closed = sessionStorage.getItem(closedKey) === "1"; } catch (e) {}
		if (!closed) {
			var headlines = [
				"BREAKING: diamonds now officially worth 3 dirt",
				"The snail has been promoted to CEO of SCTP",
				"Today's Rare-dle answer: yes",
				"All shops will now close at 5pm for the snail's nap",
				"New currency just dropped: carrots",
				"Scientists confirm the Pride Ducks are, in fact, ducks",
				"The Marketplace now accepts payment in compliments",
				"Honeybee and Firefly announce merger into Honeyfly",
				"Happy April Fools' from SCTP 🤡"
			];
			var run = headlines.map(function (t) { return "<span>" + t + "</span>"; }).join("");
			var bar = document.createElement("div");
			bar.className = "af-ticker";
			bar.innerHTML = '<span class="af-ticker-label">SCTP NEWS</span>' +
				'<div class="af-ticker-track"><div class="af-ticker-run">' + run + run + "</div></div>" +
				'<button type="button" class="af-ticker-x" aria-label="Hide the fake news">✕</button>';
			bar.querySelector(".af-ticker-x").addEventListener("click", function () {
				bar.remove();
				try { sessionStorage.setItem(closedKey, "1"); } catch (e) {}
			});
			document.body.insertBefore(bar, document.body.firstChild);
		}

		// 2. "SCTP Premium™", once per visitor, a few seconds in.
		var seenKey = "sctp_af_premium_seen";
		var seen = false;
		try { seen = localStorage.getItem(seenKey) === "1"; } catch (e) {}
		if (!seen) setTimeout(premium, 5000);
		function premium() {
			try { localStorage.setItem(seenKey, "1"); } catch (e) {}
			var bg = document.createElement("div");
			bg.className = "af-modal-bg";
			bg.innerHTML = '<div class="af-modal" role="dialog" aria-modal="true" aria-labelledby="afPremiumTitle">' +
				'<h2 id="afPremiumTitle">✨ Introducing SCTP Premium™</h2>' +
				"<p>Upgrade today and get:</p><ul>" +
				"<li>Shops load 0.2 seconds faster (we think)</li><li>A second snail</li>" +
				"<li>Exclusive access to the Log out button</li><li>Rare-dle hints from the snail's cousin</li></ul>" +
				'<p class="af-price">Just <b>64 diamonds a month</b> (or 1 carrot)</p>' +
				'<div class="af-modal-btns"><button type="button" class="af-btn yes">Subscribe now</button>' +
				'<button type="button" class="af-btn no">No thanks, I like being poor</button></div></div>';
			function close() { bg.remove(); document.removeEventListener("keydown", onKey); }
			function onKey(e) { if (e.key === "Escape") close(); }
			function reveal() {
				bg.querySelector(".af-modal").innerHTML = '<h2 id="afPremiumTitle">🤡 April Fools!</h2>' +
					"<p>SCTP is free and always will be. No premium, no carrots. Carry on!</p>" +
					'<div class="af-modal-btns"><button type="button" class="af-btn yes">Fine 🙄</button></div>';
				bg.querySelector(".af-btn").addEventListener("click", close);
				bg.querySelector(".af-btn").focus();
			}
			bg.querySelector(".af-btn.yes").addEventListener("click", reveal);
			bg.querySelector(".af-btn.no").addEventListener("click", reveal);
			bg.addEventListener("click", function (e) { if (e.target === bg) close(); });
			document.addEventListener("keydown", onKey);
			document.body.appendChild(bg);
			bg.querySelector(".af-btn.yes").focus();
		}

		// 3. Silly nav names (the links themselves still go where they always did).
		var NAV = { "Home": "Hoem", "Items": "Stuff", "Marketplace": "Flea Market", "More": "Moar", "Info": "Lore", "Rare-dle": "Rare-dle 2" };
		function renameNav() {
			var mount = document.getElementById("siteNavMount");
			if (!mount) return;
			var changed = false;
			var walker = document.createTreeWalker(mount, NodeFilter.SHOW_TEXT, null);
			var t;
			while ((t = walker.nextNode())) {
				if (t.parentNode.closest(".site-nav-dd-panel, .site-nav-new-popup, #accountWidgetMount")) continue;
				var key = t.nodeValue.trim();
				if (NAV[key]) { t.nodeValue = t.nodeValue.replace(key, NAV[key]); changed = true; }
			}
			if (changed) window.dispatchEvent(new Event("resize")); // lets the nav re-check whether it still fits
		}
		renameNav();
		setTimeout(renameNav, 600);
		setTimeout(renameNav, 1600);

		// 4. The home page's Download button.
		var dl = document.getElementById("downloadMod");
		if (dl) {
			for (var i = 0; i < dl.childNodes.length; i++) {
				var n = dl.childNodes[i];
				if (n.nodeType === 3 && /Download mod/.test(n.nodeValue)) { n.nodeValue = n.nodeValue.replace("Download mod", "Download more RAM"); break; }
			}
		}

		// 5. Switch tabs and the page begs you to come back.
		var realTitle = document.title;
		document.addEventListener("visibilitychange", function () {
			if (document.hidden) { realTitle = document.title; document.title = "🤡 hey, come back!"; }
			else document.title = realTitle;
		});

		// 6. Every so often, a random card on screen does a little shake.
		if (!h.reduceMotion) {
			(function shake() {
				setTimeout(function () {
					if (!document.hidden) {
						var cards = Array.prototype.filter.call(document.querySelectorAll('[class*="card"]'), function (c) {
							var r = c.getBoundingClientRect();
							return r.width > 120 && r.height > 50 && r.top > 0 && r.bottom < window.innerHeight && !c.closest(".af-modal-bg");
						});
						if (cards.length) {
							var c = h.pick(cards);
							c.classList.add("af-shake");
							setTimeout(function () { c.classList.remove("af-shake"); }, 800);
						}
					}
					shake();
				}, h.rand(12000, 25000));
			})();
		}
	}

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

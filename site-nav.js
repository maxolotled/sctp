// Shared site navigation — the second deliberate "shared JS" exception
// alongside account-widget.js (see that file's own comment for the
// rationale). Every page needed the exact same link set, but each one kept
// its own hand-copied version — different link orders, a mix of "../",
// bare-relative, and root-absolute hrefs, some pages missing links others
// had, and one broken link to a "docs.html" that doesn't exist (the real
// docs site lives at /docs/). Copies drifting out of sync like that is
// exactly what made the nav "a mess" — one shared source fixes it for good.
//
// Usage: a page just needs an empty <nav id="siteNavMount"></nav> in its
// header, plus this script (before account-widget.js, both deferred) — this
// renders the link list AND leaves an #accountWidgetMount div inside it for
// account-widget.js to find, same as it always has.
//
// Below MOBILE_BREAKPOINT the link list collapses behind a hamburger button
// that opens a floating dropdown (not an in-flow block) — the nav always
// stays on the same line as the page's title/logo, never pushed below it,
// same as it naturally does on wider screens. The account button/login state
// stays visible either way; only the link list itself collapses.
(function () {
	"use strict";

	// Top-level nav: plain links, dropdown menus ("menu") and one call-to-action
	// button ("cta"). Menus open on click; inside the mobile drawer they are
	// simply listed under their heading.
	var LINKS = [
		{ href: "/", label: "Home" },
		{ href: "/items/", label: "Items" },
		{ href: "/marketplace/", label: "Marketplace", isNew: true,
			newPopup: "<strong>&#10024; New: the Marketplace!</strong>Buy, sell &amp; trade directly with other players &mdash; post listings, take bids, get notified.<span class=\"site-nav-new-cta-row\"><a href=\"/register/\">Register your account &rarr;</a></span>" },
		{ menu: "more", label: "More", items: [
			{ href: "/roadmap/", label: "Roadmap" },
			{ href: "/list/", label: "Search by material list" },
			{ href: "/stats/", label: "World/Shop statistics" }
		] },
		{ menu: "info", label: "Info", items: [
			{ href: "/docs/faq", label: "FAQ" },
			{ href: "/onboarding/mod", label: "Mod Features & Installation" },
			{ href: "/onboarding/web", label: "Website Features" },
			{ href: "/onboarding/update", label : "Changelog" }
		] },
		{ cta: true, href: "/rare-dle/", label: "Rare-dle" }
	];
	var MOBILE_BREAKPOINT = 760;

	var style = document.createElement("style");
	style.textContent =
		// flex-shrink:0 keeps the nav cluster itself always full-size — the
		// page's own title/brand block is what should shrink/wrap first, not
		// this (see the min-width:0 note on .brand-row et al in each page).
		// position:relative anchors the mobile dropdown below (see .site-nav-links).
		"#siteNavMount{display:flex;align-items:center;gap:16px;flex-shrink:0;position:relative;}" +
		"#siteNavMount a{color:var(--shell,#D9C89A);text-decoration:none;font-size:14px;font-weight:600;}" +
		"#siteNavMount a:hover,#siteNavMount a.active{color:var(--accent,#B7E23D);}" +
		// Base (desktop) layout for the link list — the mobile media query
		// below overrides this to a floating dropdown instead.
		".site-nav-links{display:flex;align-items:center;gap:16px;}" +
		".site-nav-row{display:flex;align-items:center;gap:12px;}" +
		".site-nav-toggle{display:none;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;width:38px;height:38px;font-size:17px;line-height:1;cursor:pointer;align-items:center;justify-content:center;flex-shrink:0;}" +
		"@media (max-width:" + MOBILE_BREAKPOINT + "px){" +
			".site-nav-toggle{display:inline-flex;}" +
			// A floating dropdown (same treatment as account-widget.js's own
			// .acct-menu) rather than an in-flow block — this is what keeps the
			// header row itself always on one line, nav staying to the right of
			// the title instead of ever pushing below it.
			".site-nav-links{display:none;position:absolute;top:calc(100% + 8px);right:0;flex-direction:column;align-items:stretch;min-width:200px;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:10px;padding:6px;z-index:90;box-shadow:0 8px 24px rgba(0,0,0,.35);}" +
			".site-nav-links.open{display:flex;}" +
			".site-nav-links a{padding:10px 12px;border-radius:6px;font-size:14px;}" +
			".site-nav-links a:hover{background:var(--panel-alt,#22332A);}" +
		"}" +
		// "New feature" highlight — a pulsing badge that's always animating (not
		// just on hover) so it actually catches the eye while scanning the nav,
		// plus a rich hover popup explaining what's new and nudging toward
		// registering. The popup is a sibling of the <a>, not nested inside it —
		// anchors can't nest, and this one needs its own real, independently
		// clickable "Register" link.
		".site-nav-new-wrap{position:relative;display:inline-flex;align-items:center;}" +
		// The link text itself gets an animated gradient fill (not just the
		// badge) — #siteNavMount-prefixed so this beats the plain a:hover/
		// a.active color rule above regardless of hover/active state.
		"#siteNavMount a.site-nav-new-link,#siteNavMount a.site-nav-new-link:hover,#siteNavMount a.site-nav-new-link.active{" +
			"background:linear-gradient(90deg,#B7E23D,#6FE3C8,#4FC3F7,#B7E23D);background-size:300% auto;" +
			"-webkit-background-clip:text;background-clip:text;color:transparent;animation:siteNavGradientShift 3s linear infinite;}" +
		"@keyframes siteNavGradientShift{0%{background-position:0% center;}100%{background-position:300% center;}}" +
		".site-nav-new-badge{display:inline-block;margin-left:6px;padding:2px 6px;border-radius:999px;background:linear-gradient(135deg,var(--accent,#B7E23D),#6FE3C8);color:var(--accent-ink,#16210F);font-size:10px;font-weight:800;letter-spacing:0.04em;line-height:1.3;vertical-align:2px;box-shadow:0 0 0 rgba(var(--accent-rgb,183,226,61),0.6);animation:siteNavNewPulse 1.8s ease-in-out infinite;}" +
		"@keyframes siteNavNewPulse{" +
			"0%{transform:scale(1);box-shadow:0 0 0 0 rgba(var(--accent-rgb,183,226,61),0.55);}" +
			"50%{transform:scale(1.12);box-shadow:0 0 8px 3px rgba(var(--accent-rgb,183,226,61),0.35);}" +
			"100%{transform:scale(1);box-shadow:0 0 0 0 rgba(var(--accent-rgb,183,226,61),0);}" +
		"}" +
		// The outer .site-nav-new-popup sits flush against the wrap (top:100%,
		// zero gap) and reaches the visible card only via padding-top — that
		// padding is still part of the element's own hoverable box, so the
		// mouse crossing from the link down into the card never passes through
		// dead space with no element underneath it (which is what made the
		// popup vanish the instant you tried to move into it — :hover was lost
		// in that gap before the pointer ever reached the card).
		".site-nav-new-popup{position:absolute;top:100%;right:0;padding-top:10px;width:250px;max-width:calc(100vw - 40px);opacity:0;visibility:hidden;transition:opacity .18s ease,visibility .18s;z-index:100;pointer-events:none;}" +
		".site-nav-new-wrap:hover .site-nav-new-popup{opacity:1;visibility:visible;pointer-events:auto;}" +
		".site-nav-new-popup-card{background:var(--panel,#1B2A20);border:1px solid var(--accent,#B7E23D);border-radius:12px;padding:14px;font-size:12.5px;line-height:1.5;color:var(--text,#EAEFE7);box-shadow:0 10px 30px rgba(0,0,0,0.45),0 0 22px rgba(var(--accent-rgb,183,226,61),0.25);text-align:left;white-space:normal;transform:translateY(-6px);transition:transform .18s ease;}" +
		".site-nav-new-wrap:hover .site-nav-new-popup-card{transform:translateY(0);}" +
		".site-nav-new-popup strong{display:block;margin-bottom:6px;color:var(--accent,#B7E23D);font-size:13.5px;}" +
		".site-nav-new-cta-row{display:block;margin-top:10px;}" +
		".site-nav-new-cta-row a{display:inline-block;padding:6px 10px;border-radius:8px;background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F)!important;font-weight:700;font-size:12px;}" +
		".site-nav-new-cta-row a:hover{background:var(--accent-dim,#87AE29);}" +
		// Mobile dropdown: the wrap needs to behave as a full-width row like
		// every other link there, and the popup anchors below it the same way.
		"@media (max-width:" + MOBILE_BREAKPOINT + "px){" +
			".site-nav-new-wrap{display:flex;width:100%;}" +
			".site-nav-new-popup{right:6px;}" +
		"}" +
		// ---- dropdown menus (More, Info) ----
		".site-nav-dd{position:relative;display:inline-flex;align-items:center;}" +
		".site-nav-dd-btn{background:none;border:none;color:var(--shell,#D9C89A);font:inherit;font-size:14px;font-weight:600;cursor:pointer;padding:0;display:inline-flex;align-items:center;gap:5px;font-family:inherit;}" +
		".site-nav-dd-btn:hover,.site-nav-dd-btn.active,.site-nav-dd.open .site-nav-dd-btn{color:var(--accent,#B7E23D);}" +
		".site-nav-dd-btn .caret{font-size:9px;transition:transform .15s ease;display:inline-block;}" +
		".site-nav-dd.open .site-nav-dd-btn .caret{transform:rotate(180deg);}" +
		".site-nav-dd-panel{position:absolute;top:calc(100% + 12px);left:50%;transform:translateX(-50%);min-width:220px;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:12px;padding:6px;box-shadow:0 12px 32px rgba(0,0,0,0.45);display:none;z-index:120;}" +
		".site-nav-dd.open .site-nav-dd-panel{display:block;}" +
		".site-nav-dd-panel a{display:block;padding:9px 12px;border-radius:8px;white-space:nowrap;}" +
		".site-nav-dd-panel a:hover{background:var(--panel-alt,#22332A);}" +
		"@media (max-width:" + MOBILE_BREAKPOINT + "px){" +
			".site-nav-dd{display:block;width:100%;}" +
			".site-nav-dd-btn{width:100%;padding:10px 12px 2px;font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:var(--muted,#8FA593);cursor:default;pointer-events:none;}" +
			".site-nav-dd-btn .caret{display:none;}" +
			".site-nav-dd-panel{position:static;transform:none;display:block;min-width:0;border:none;box-shadow:none;background:transparent;padding:0 0 4px 8px;}" +
			".site-nav-dd-panel a{white-space:normal;}" +
			".site-nav-links{max-width:calc(100vw - 24px);}" +
		"}" +
		// ---- Rare-dle call-to-action: same "flashy pill" family as the home page's
		// Download button (site-cta-pill, in index.html) — moving gradient, soft
		// glow, a light sweep — but thin, fit tightly to its text, in warm colours.
		"#siteNavMount a.site-nav-cta{position:relative;overflow:hidden;display:inline-block;padding:4px 12px;border-radius:999px;color:#2A1408;font-weight:800;font-size:13px;letter-spacing:.01em;white-space:nowrap;" +
			"background:linear-gradient(90deg,#FFB347,#FF7A59,#F0508F,#FFB347);background-size:300% auto;animation:siteNavCtaShift 7s linear infinite,siteNavCtaGlow 3.6s ease-in-out infinite;}" +
		"#siteNavMount a.site-nav-cta:hover,#siteNavMount a.site-nav-cta.active{color:#2A1408;filter:brightness(1.06);animation-play-state:paused,paused;}" +
		"#siteNavMount a.site-nav-cta::after{content:'';position:absolute;top:0;left:-60%;width:30%;height:100%;background:linear-gradient(120deg,transparent,rgba(255,255,255,0.45),transparent);transform:skewX(-20deg);animation:siteNavCtaShine 5.5s ease-in-out infinite;pointer-events:none;}" +
		"@keyframes siteNavCtaShift{0%{background-position:0% center;}100%{background-position:300% center;}}" +
		"@keyframes siteNavCtaGlow{0%{box-shadow:0 0 0 0 rgba(255,122,89,0.40);}50%{box-shadow:0 0 12px 3px rgba(255,122,89,0.28);}100%{box-shadow:0 0 0 0 rgba(255,122,89,0);}}" +
		"@keyframes siteNavCtaShine{0%{left:-60%;}55%{left:130%;}100%{left:130%;}}" +
		"@media (max-width:" + MOBILE_BREAKPOINT + "px){#siteNavMount a.site-nav-cta{display:inline-block;margin:6px 4px 2px;}}";
	document.head.appendChild(style);

	// "/foo", "/foo/", and "/foo/index.html" are all the same page for
	// highlighting purposes.
	function normalize(path) {
		return path.replace(/(index)?\.html$/, "").replace(/\/+$/, "") || "/";
	}
	function isActive(href) {
		var here = normalize(location.pathname);
		var target = normalize(href);
		if (target === "/") return here === "/";
		return here === target || here.indexOf(target + "/") === 0;
	}

	function menuActive(l) {
		return l.items.some(function (it) { return isActive(it.href); });
	}

	function render() {
		var mount = document.getElementById("siteNavMount");
		if (!mount) return;
		var linksHtml = LINKS.map(function (l) {
			if (l.menu) {
				return '<div class="site-nav-dd" data-menu="' + l.menu + '">' +
					'<button type="button" class="site-nav-dd-btn' + (menuActive(l) ? " active" : "") + '" aria-haspopup="true" aria-expanded="false">' + l.label + ' <span class="caret">&#9662;</span></button>' +
					'<div class="site-nav-dd-panel">' + l.items.map(function (it) {
						return '<a href="' + it.href + '"' + (isActive(it.href) ? ' class="active"' : "") + ">" + it.label + "</a>";
					}).join("") + "</div></div>";
			}
			if (l.cta) {
				return '<a class="site-nav-cta' + (isActive(l.href) ? " active" : "") + '" href="' + l.href + '">' + l.label + "</a>";
			}
			var attrs = l.requireAuth ? " data-require-auth hidden" : "";
			var classes = (l.isNew ? "site-nav-new-link " : "") + (isActive(l.href) ? "active" : "");
			var link = '<a href="' + l.href + '"' + (classes.trim() ? ' class="' + classes.trim() + '"' : "") + attrs + ">" + l.label
				+ (l.isNew ? '<span class="site-nav-new-badge">NEW</span>' : "") + "</a>";
			if (!l.isNew) return link;
			// Wrapped (not nested — anchors can't nest) so the popup's own
			// "Register" link stays independently clickable. See the CSS above.
			return '<span class="site-nav-new-wrap"' + (l.requireAuth ? " data-require-auth hidden" : "") + '>' + link
				+ '<div class="site-nav-new-popup"><div class="site-nav-new-popup-card">' + (l.newPopup || "") + '</div></div></span>';
		}).join("");

		mount.innerHTML =
			'<div class="site-nav-links" id="siteNavLinks">' + linksHtml + '</div>' +
			'<div class="site-nav-row">' +
				'<button type="button" class="site-nav-toggle" aria-label="Toggle menu" aria-expanded="false">&#9776;</button>' +
				'<div id="accountWidgetMount"></div>' +
			'</div>';

		var toggle = mount.querySelector(".site-nav-toggle");
		var linksEl = document.getElementById("siteNavLinks");
		function setOpen(open) {
			linksEl.classList.toggle("open", open);
			toggle.setAttribute("aria-expanded", open ? "true" : "false");
		}
		toggle.addEventListener("click", function () { setOpen(!linksEl.classList.contains("open")); });
		// Tapping a link closes the drawer immediately rather than leaving it
		// open behind the page that just navigated (matters most for same-page
		// anchors, and avoids a jarring "still open" flash on slow loads).
		linksEl.querySelectorAll("a").forEach(function (a) {
			a.addEventListener("click", function () { setOpen(false); });
		});
		// Dropdown menus (desktop): click the heading to open, click elsewhere / Esc / a link to close.
		var menus = mount.querySelectorAll(".site-nav-dd");
		function closeMenus(except) {
			menus.forEach(function (dd) {
				if (dd === except) return;
				dd.classList.remove("open");
				dd.querySelector(".site-nav-dd-btn").setAttribute("aria-expanded", "false");
			});
		}
		menus.forEach(function (dd) {
			var btn = dd.querySelector(".site-nav-dd-btn");
			btn.addEventListener("click", function (e) {
				e.stopPropagation();
				var open = !dd.classList.contains("open");
				closeMenus(dd);
				dd.classList.toggle("open", open);
				btn.setAttribute("aria-expanded", open ? "true" : "false");
			});
			dd.querySelectorAll("a").forEach(function (a) { a.addEventListener("click", function () { closeMenus(); }); });
		});
		document.addEventListener("click", function (e) { if (!mount.contains(e.target)) closeMenus(); });
		document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeMenus(); });

		// Resizing past the breakpoint (rotating a tablet, restoring a
		// dev-tools panel) shouldn't leave the drawer stuck open once it's
		// no longer collapsed.
		window.addEventListener("resize", function () {
			if (window.innerWidth > MOBILE_BREAKPOINT) setOpen(false);
		});
	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render);
	else render();
})();

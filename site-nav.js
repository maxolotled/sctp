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
(function () {
	"use strict";

	var LINKS = [
		{ href: "/", label: "Home" },
		{ href: "/items/", label: "Items" },
		{ href: "/list/", label: "Build List" },
		{ href: "/marketplace/", label: "Marketplace", requireAuth: true },
		{ href: "/stats/", label: "Stats" },
		{ href: "/roadmap/", label: "Roadmap" },
		{ href: "/docs/", label: "Info" },
	];

	var style = document.createElement("style");
	style.textContent =
		"#siteNavMount{display:flex;align-items:center;gap:16px;flex-wrap:wrap;}" +
		"#siteNavMount a{color:var(--shell,#D9C89A);text-decoration:none;font-size:14px;font-weight:600;}" +
		"#siteNavMount a:hover,#siteNavMount a.active{color:var(--accent,#B7E23D);}";
	document.head.appendChild(style);

	// "/foo", "/foo/", and "/foo/index.html" are all the same page for
	// highlighting purposes.
	function normalize(path) {
		return path.replace(/index\.html$/, "").replace(/\/+$/, "") || "/";
	}
	function isActive(href) {
		var here = normalize(location.pathname);
		var target = normalize(href);
		if (target === "/") return here === "/";
		return here === target || here.indexOf(target + "/") === 0;
	}

	function render() {
		var mount = document.getElementById("siteNavMount");
		if (!mount) return;
		mount.innerHTML = LINKS.map(function (l) {
			var attrs = l.requireAuth ? " data-require-auth hidden" : "";
			return '<a href="' + l.href + '"' + (isActive(l.href) ? ' class="active"' : "") + attrs + ">" + l.label + "</a>";
		}).join("") + '<div id="accountWidgetMount"></div>';
	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render);
	else render();
})();

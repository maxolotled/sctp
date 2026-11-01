/*
 * SCTP — original theme: no decorations. Every page loads /theme.js, so this
 * is what /theme.js holds when no seasonal theme is live. It only handles
 * previews (?theme=<name>; ?theme=live stops it). See themes/README.md.
 */
(function () {
	"use strict";
	var ID = "original";

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

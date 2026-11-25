// Average prices instead of live ones. The Snailcraft admins required live
// shop prices to be removed, so a listing shows the average price of one of
// its item, across every current listing of that item in that world (the
// same grouping as the daily price snapshot). The GET /listings data itself is
// unchanged (older mod versions still read it); the pages just don't show it.
//
//   sctpAvgPrice.build(rows, perItemFn, nameFn)  -> lookup(row) => {each, n} | null
//   sctpAvgPrice.info(n)                         -> HTML for the little (i) button
//
// Clicking (i) opens a small popover explaining why, with a button to the
// statement. It's appended to <body>, so table scrolling never clips it.
(function () {
	"use strict";
	var STATEMENT = "/statement/";
	var EXCLUDED_SELLERS = { theshellkeeper: true }; // the treasure hunt's puzzle shop, also left out of the price stats

	function key(world, baseItem, name) {
		return (String(world) + "|" + String(baseItem) + "|" + String(name)).toLowerCase();
	}

	/**
	 * rows: every listing. perItemFn(row): its price for one item, in diamonds.
	 * nameFn(row): the English item name (localized names are grouped together).
	 */
	function build(rows, perItemFn, nameFn) {
		var sums = {};
		rows.forEach(function (r) {
			if (r.marketplace || r.mapartGallery) return;
			if (String(r.currency || "").toLowerCase() === "display") return;
			if (EXCLUDED_SELLERS[String(r.seller || "").toLowerCase()]) return;
			var each = perItemFn(r);
			if (!isFinite(each) || each < 0) return;
			var k = key(r.world, r.baseItem, nameFn(r));
			var s = sums[k] || (sums[k] = { sum: 0, n: 0 });
			s.sum += each;
			s.n++;
		});
		return function (r) {
			if (r.marketplace || r.mapartGallery || String(r.currency || "").toLowerCase() === "display") return null;
			var s = sums[key(r.world, r.baseItem, nameFn(r))];
			return s ? { each: s.sum / s.n, n: s.n } : null;
		};
	}

	/** n: how many listings the average is based on (shown in the popover). */
	function info(n) {
		return '<button type="button" class="avg-info"' + (n ? ' data-n="' + Number(n) + '"' : "") + ' aria-label="Why is this an average price?" title="Why an average?">i</button>';
	}

	var CSS =
		".avg-info{display:inline-flex;align-items:center;justify-content:center;width:15px;height:15px;padding:0;margin-left:6px;border:0;border-radius:50%;background:var(--info,#8C93E8);color:var(--accent-ink,#16210F);font:700 10px/1 var(--font-body,system-ui,sans-serif);cursor:pointer;vertical-align:1px;}" +
		".avg-info:hover{filter:brightness(1.15);}" +
		".avg-info:focus-visible{outline:2px solid var(--accent,#B7E23D);outline-offset:2px;}" +
		".avg-pop{position:fixed;z-index:9000;width:min(290px,calc(100vw - 24px));background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);border-radius:10px;padding:12px 14px;color:var(--text,#EAEFE7);font:13px/1.5 var(--font-body,system-ui,sans-serif);box-shadow:0 12px 32px rgba(0,0,0,.45);}" +
		".avg-pop b{display:block;font-size:13.5px;margin-bottom:4px;}" +
		".avg-pop p{margin:0 0 10px;color:var(--muted,#8FA593);}" +
		".avg-pop a{display:inline-block;background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);font-weight:700;text-decoration:none;border-radius:8px;padding:7px 12px;font-size:13px;}" +
		".avg-pop a:focus-visible{outline:2px solid var(--text,#EAEFE7);outline-offset:2px;}";

	var pop = null, opener = null;
	function close() {
		if (!pop) return;
		pop.remove();
		pop = null;
		if (opener) { try { opener.focus({ preventScroll: true }); } catch (e) { /* fine */ } }
		opener = null;
	}
	function open(btn) {
		close();
		opener = btn;
		pop = document.createElement("div");
		pop.className = "avg-pop";
		pop.setAttribute("role", "dialog");
		pop.setAttribute("aria-label", "About average prices");
		var n = btn.getAttribute("data-n");
		pop.innerHTML = "<b>Average price, not the shop's own</b>" +
			"<p>The Snailcraft admins required us to remove live shop prices. This is the average price of one of this item across every shop listing it in this world" +
			(n ? " (" + n + " listing" + (n === "1" ? "" : "s") + ")" : "") + ".</p>" +
			'<a href="' + STATEMENT + '">Read our statement</a>';
		document.body.appendChild(pop);
		var r = btn.getBoundingClientRect(), pw = pop.offsetWidth, ph = pop.offsetHeight;
		var left = Math.min(Math.max(12, r.left + r.width / 2 - pw / 2), window.innerWidth - pw - 12);
		var top = r.top - ph - 8;
		if (top < 12) top = r.bottom + 8;
		pop.style.left = left + "px";
		pop.style.top = top + "px";
		pop.querySelector("a").focus({ preventScroll: true });
	}

	function setup() {
		var st = document.createElement("style");
		st.textContent = CSS;
		document.head.appendChild(st);
		document.addEventListener("click", function (e) {
			var b = e.target.closest ? e.target.closest(".avg-info") : null;
			if (b) { e.preventDefault(); e.stopPropagation(); if (pop && opener === b) close(); else open(b); return; }
			if (pop && !pop.contains(e.target)) close();
		}, true);
		document.addEventListener("keydown", function (e) { if (e.key === "Escape") close(); });
		window.addEventListener("scroll", close, true);
		window.addEventListener("resize", close);
	}
	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", setup);
	else setup();

	window.sctpAvgPrice = { build: build, info: info };
})();

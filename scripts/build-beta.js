#!/usr/bin/env node
"use strict";

/**
 * Builds the /beta/ preview of the redesigned site from the live pages.
 *
 *   node scripts/build-beta.js
 *
 * Every live page is copied to beta/<same path> with:
 *  - beta/nav.js (the new app bar) instead of site-nav.js,
 *  - beta/beta.css (tighter spacing, compact title row) after the page's styles,
 *  - relative src/href attributes made absolute, so images etc. still load from
 *    the live folder,
 *  - noindex, so search engines don't index the duplicate pages.
 * Links between pages are pointed at their /beta/ copies at runtime by nav.js.
 *
 * Two pages are rebuilt rather than copied:
 *  - the homepage index.html becomes beta/search/index.html: the same listings
 *    browser, with a compact filter bar, the most important columns first, and
 *    small mapart-of-the-day / download banners instead of the promo strip;
 *  - 404.html becomes beta/404.html (item, seller, mapart and collection
 *    pages), reading its path from ?p= — the live 404.html forwards /beta/...
 *    misses there.
 * The beta homepage itself (beta/index.html, the navigation hub) and the
 * nav/css files are hand-written and never touched here.
 *
 * Re-run after changing a live page to bring the beta copy up to date.
 */

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const BETA = path.join(ROOT, "beta");

// live page -> beta page (paths relative to the repo root)
const PAGES = [
	"account/index.html", "auction/index.html", "auction/admin/index.html", "auction/me/index.html", "auctionadmin/index.html",
	"bug/index.html", "collection/index.html", "f/index.html", "items/index.html", "list/index.html", "mapart/index.html",
	"mapart/manage/index.html", "marketplace/index.html", "onboarding/mod/index.html", "onboarding/update/index.html",
	"onboarding/web/index.html", "privacy/index.html", "rare-dle/index.html", "register/index.html", "report/index.html",
	"reset-password/index.html", "roadmap/index.html", "stats/index.html", "stats/mine/index.html", "store/manage/index.html",
	"suggest/index.html", "verify-link/index.html",
].map((p) => [p, p]).concat([["index.html", "search/index.html"], ["404.html", "404.html"]]);

function read(p) { return fs.readFileSync(path.join(ROOT, p), "utf8").replace(/\r\n/g, "\n"); }
function write(p, s) {
	const out = path.join(BETA, p);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	fs.writeFileSync(out, s);
}
function rep(s, a, b, where) {
	if (!s.includes(a)) throw new Error(where + ": missing " + JSON.stringify(a.slice(0, 80)));
	return s.replace(a, () => b);
}

// "img/x.png" on /onboarding/mod/ -> "/onboarding/mod/img/x.png"
function absolutize(html, srcPage) {
	const dir = "/" + path.posix.dirname(srcPage).replace(/^\.$/, "");
	const base = dir.endsWith("/") ? dir : dir + "/";
	return html.replace(/\s(src|href|poster)="([^"]*)"/g, (m, attr, url) => {
		if (!url || /^(\/|#|\?|[a-z][a-z0-9+.-]*:|\{|')/i.test(url) || url.includes("'+") || url.includes('"+')) return m;
		return " " + attr + '="' + path.posix.normalize(base + url) + '"';
	});
}

function common(html, src) {
	html = absolutize(html, src);
	// "../data/x.json" in scripts (one level up from the live page = the site root)
	html = html.replace(/(["'])\.\.\/data\//g, "$1/data/");
	if (html.includes('<script src="/site-nav.js" defer></script>')) {
		html = html.replace('<script src="/site-nav.js" defer></script>', '<script src="/beta/nav.js" defer></script>');
	} else {
		html = html.replace("</body>", '<script src="/beta/nav.js" defer></script>\n</body>');
	}
	html = html.replace("</head>", '<meta name="robots" content="noindex">\n<link rel="stylesheet" href="/beta/beta.css">\n</head>');
	return html;
}

// ---------------------------------------------------------------- search page
const SEARCH_CSS = `<style>
	/* beta search page: compact banners, a filter bar instead of a sidebar */
	html.beta .search-band{padding:12px 0 4px;}
	html.beta .stats-band{margin:6px 0 10px;}
	html.beta .promo-strip{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px;margin:12px 0 0;}
	html.beta #promoRaredle,html.beta #promoMarket{display:none;}
	html.beta .promo-card{min-height:0;padding:10px 12px;border-radius:12px;}
	html.beta .promo-motd{flex-direction:row;align-items:center;gap:14px;background:linear-gradient(120deg,rgba(var(--accent-rgb),0.08),transparent 55%),var(--panel);}
	html.beta .promo-motd .motd-img{flex:0 0 96px;max-width:96px;height:96px;padding:5px;border-radius:10px;}
	html.beta .promo-motd .motd-img img{max-height:86px;box-shadow:0 4px 14px rgba(0,0,0,.45);}
	html.beta .promo-motd .motd-body{min-width:0;}
	html.beta .promo-motd .promo-kicker{margin:0 0 2px;font-size:10px;}
	html.beta .promo-motd .promo-title{font-size:17px;margin:0 0 1px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
	html.beta .promo-motd .motd-by{font-size:12.5px;}
	html.beta .promo-motd .motd-by + .motd-by{display:none;}
	html.beta .promo-motd .motd-meta{display:flex;flex-wrap:wrap;gap:4px;margin:4px 0 0;}
	html.beta .promo-motd .motd-pill{font-size:10.5px;padding:1px 7px;}
	html.beta .promo-motd .promo-actions{margin-top:7px;gap:6px;}
	html.beta .promo-motd .promo-btn{padding:4px 10px;font-size:12px;}
	html.beta .promo-dl{flex-direction:row;align-items:center;gap:12px;background:linear-gradient(135deg,rgba(var(--accent-rgb),0.10),transparent 60%),var(--panel);}
	html.beta .promo-dl .dl-copy{flex:1;min-width:0;}
	html.beta .promo-dl .dl-copy b{display:block;font-size:15px;}
	html.beta .promo-dl .dl-copy span{color:var(--muted);font-size:12.5px;}
	html.beta .promo-dl .dl-btns{display:flex;gap:6px;flex-wrap:wrap;justify-content:flex-end;}
	html.beta .promo-dl .dl-btns a,html.beta .promo-dl .dl-btns button{font:inherit;font-size:12.5px;font-weight:700;padding:6px 11px;border-radius:8px;text-decoration:none;border:1px solid var(--line);background:transparent;color:var(--text);cursor:pointer;white-space:nowrap;}
	html.beta .promo-dl .dl-btns .mr{background:#1BD96A;color:#06210F;border-color:transparent;}
	html.beta .promo-dl .dl-btns .cf{background:#F16436;color:#fff;border-color:transparent;}
	@media (max-width:760px){html.beta .promo-strip{grid-template-columns:1fr;}html.beta .promo-dl{flex-wrap:wrap;}}

	html.beta .layout{display:block;padding-bottom:40px;}
	html.beta aside.filters{display:flex;flex-wrap:wrap;align-items:flex-end;gap:8px 12px;padding:10px 12px;margin-bottom:10px;border-radius:12px;position:relative;}
	html.beta aside.filters h2{display:none;}
	html.beta aside.filters .f-group{margin:0;flex:0 1 auto;min-width:150px;}
	html.beta aside.filters .f-group label{margin-bottom:3px;font-size:11.5px;}
	html.beta aside.filters .f-group select,html.beta aside.filters .f-group input[type=number],html.beta aside.filters .f-group input[type=text]{padding:6px 9px;font-size:13px;}
	html.beta aside.filters .f-group.price-grp{min-width:240px;}
	html.beta aside.filters .price-grp .f-range{display:inline-flex;width:150px;vertical-align:middle;}
	html.beta aside.filters .price-grp select{display:inline-block;width:auto;margin-left:6px;vertical-align:middle;}
	html.beta aside.filters .f-adv{display:none;}
	html.beta aside.filters.show-adv .f-adv{display:block;}
	html.beta aside.filters .f-reset{margin:0;width:auto;padding:6px 10px;}
	html.beta .f-more{background:var(--panel-alt);border:1px solid var(--line);color:var(--text);border-radius:8px;padding:6px 11px;font:inherit;font-size:13px;font-weight:600;cursor:pointer;}
	html.beta .f-more:hover{border-color:var(--accent-dim);}
	html.beta .f-more b{color:var(--accent);}
	html.beta aside.filters .f-break{flex-basis:100%;height:0;}
	html.beta .table-card thead th{padding-top:8px;padding-bottom:8px;}
	html.beta .table-card td{padding-top:7px;padding-bottom:7px;}
</style>`;

const SEARCH_JS = `<script>
// beta search page: primary filters stay in the bar, the rest go behind "More filters".
(function(){
	var bar = document.querySelector("aside.filters");
	if(!bar) return;
	function grp(id){ var el = document.getElementById(id); return el ? el.closest(".f-group") : null; }
	var adv = ["fHideDisplay", "fConvert", "currencyDropdownBtn", "fMinStock", "fExcludeItems", "fExcludeSellers"].map(grp).filter(Boolean);
	adv.forEach(function(g){ g.classList.add("f-adv"); });
	var price = grp("fPriceMin"); if(price) price.classList.add("price-grp");
	var btn = document.createElement("button");
	btn.type = "button"; btn.className = "f-more";
	var reset = document.getElementById("resetFilters");
	// bar: World, Type, Price, Sort, [More filters] — then the extra filters on their own line, then Reset
	var brk = document.createElement("div"); brk.className = "f-break f-adv";
	var sort = grp("sortDropdownBtn");
	bar.insertBefore(btn, sort ? sort.nextSibling : reset);
	bar.insertBefore(brk, btn.nextSibling);
	adv.forEach(function(g){ bar.insertBefore(g, reset); });
	if(reset){ reset.classList.add("f-adv"); bar.appendChild(reset); }
	function activeAdv(){
		var n = 0;
		["fMinStock","fExcludeItems","fExcludeSellers"].forEach(function(id){ var el = document.getElementById(id); if(el && el.value.trim()) n++; });
		var hd = document.getElementById("fHideDisplay"); if(hd && hd.checked) n++;
		var cv = document.getElementById("fConvert"); if(cv && !cv.checked) n++;
		var cb = document.getElementById("currencyDropdownBtn"); if(cb && !/^All currencies/.test(cb.textContent)) n++;
		return n;
	}
	function label(){
		var open = bar.classList.contains("show-adv"), n = activeAdv();
		btn.innerHTML = (open ? "Fewer filters &#9652;" : "More filters &#9662;") + (n ? " <b>(" + n + ")</b>" : "");
	}
	btn.addEventListener("click", function(){ bar.classList.toggle("show-adv"); label(); });
	bar.addEventListener("input", label); bar.addEventListener("change", label); bar.addEventListener("click", function(){ setTimeout(label, 0); });
	label();
})();
</script>`;

function buildSearch(html) {
	const W = "search page";
	html = rep(html, "<title>", "<title>Search listings · ", W);
	// header: this page is "Search", not the site's front door
	html = rep(html, "<h1>Snailcraft Trading Post</h1>", "<h1>Search listings</h1>", W);
	html = rep(html, '<button class="upload-cta" id="downloadMod" type="button">Download mod</button>', "", W);
	// no Modrinth announcement here (kept in the DOM, hidden: its script expects it)
	html = rep(html, '<section class="mr-ad" id="modrinthAd" aria-label="Shop Logger is now on Modrinth">', '<section class="mr-ad" id="modrinthAd" aria-label="Shop Logger is now on Modrinth" hidden>', W);
	// promo strip -> mapart of the day + a small download banner
	html = rep(html, '<article class="promo-card promo-raredle" id="promoRaredle">',
		'<article class="promo-card promo-dl"><div class="dl-copy"><b>Get the Shop Logger mod</b><span>Scan shops as you walk past and search everything in-game.</span></div>' +
		'<div class="dl-btns"><a class="mr" href="https://modrinth.com/mod/sc-shoplogger" target="_blank" rel="noopener">Modrinth</a>' +
		'<a class="cf" href="https://www.curseforge.com/minecraft/mc-mods/sctp" target="_blank" rel="noopener">CurseForge</a>' +
		'<button type="button" id="downloadMod">Download file</button></div></article>\n\t\t<article class="promo-card promo-raredle" id="promoRaredle">', W);
	// ?q= prefills the search (the beta homepage's search box links here)
	html = rep(html, '<div class="rare-notice" id="rareNotice" hidden></div>',
		'<div class="rare-notice" id="rareNotice" hidden></div>\n\t\t<script>(function(){ var q = new URLSearchParams(location.search).get("q"); if(q) document.getElementById("searchInput").value = q; })();</script>', W);
	// most important columns first; the rarely used ones start hidden
	html = rep(html, 'var COLS = ["icon","item","base","type","price","avg","stock","seller","world","position","recent","since","actions"];',
		'var ORIG_COLS = ["icon","item","base","type","price","avg","stock","seller","world","position","recent","since","actions"];\n' +
		'\tvar COLS = ["icon","item","price","stock","seller","world","type","position","recent","avg","base","since","actions"];\n' +
		'\t// beta: rows are built in ORIG_COLS order, then shuffled into COLS order\n' +
		'\tfunction betaReorderColumns(area){\n' +
		'\t\tarea.querySelectorAll("tr").forEach(function(tr){\n' +
		'\t\t\tvar cells = Array.prototype.slice.call(tr.children);\n' +
		'\t\t\tif(cells.length !== ORIG_COLS.length) return;\n' +
		'\t\t\tCOLS.forEach(function(k){ tr.appendChild(cells[ORIG_COLS.indexOf(k)]); });\n' +
		'\t\t});\n' +
		'\t}', W);
	html = rep(html, 'var HIDDEN_COLS_KEY = "sctp_hidden_cols";', 'var HIDDEN_COLS_KEY = "sctp_beta_hidden_cols";', W);
	html = rep(html, 'JSON.parse(localStorage.getItem(HIDDEN_COLS_KEY) || "[]")', 'JSON.parse(localStorage.getItem(HIDDEN_COLS_KEY) || \'["base","since","avg"]\')', W);
	html = rep(html, "\t\tarea.innerHTML = html;\n\t\tdecorateColumns(area);", "\t\tarea.innerHTML = html;\n\t\tbetaReorderColumns(area);\n\t\tdecorateColumns(area);", W);
	// the live site sends Download to Modrinth for now; the beta keeps the file download
	html = rep(html, "var DIRECT_DOWNLOADS = false;", "var DIRECT_DOWNLOADS = true;", W);
	// download files live at the site root
	html = html.replace(/("(?:fabric|neoforge)\|26\.\d": ")(shoplogger-)/g, "$1/$2");
	// the live page uses page-relative URLs for its data files and item links;
	// one folder deeper they'd miss, so make them root-relative (nav.js then
	// points the item links at /beta/items/)
	html = html.replace(/= "data\//g, '= "/data/');
	html = html.replace(/return (name \? |slug \? )?"items\//g, (m, pre) => "return " + (pre || "") + '"/items/');
	html = html.replace("</head>", SEARCH_CSS + "\n</head>");
	html = html.replace("</body>", SEARCH_JS + "\n</body>");
	return html;
}

// ---------------------------------------------------------------- 404 router
function build404(html) {
	const W = "404";
	// drop the live page's own "forward /beta/ here" line (we ARE that page)
	html = html.replace(/\n\t\/\/ \/beta\/\.\.\. \(the redesign preview\)[^\n]*\n\t\/\/[^\n]*\n\tif \(\/\^\\\/beta[^\n]*\n/, "\n");
	// the live 404.html forwards /beta/<path> here as ?p=<path>: show that URL
	// again, and route on it without the /beta prefix
	html = rep(html, "<head>\n<meta charset=\"utf-8\">\n<script>",
		"<head>\n<meta charset=\"utf-8\">\n<script>\n\t(function(){ var p = new URLSearchParams(location.search).get(\"p\"); if(p) try { history.replaceState(null, \"\", p); } catch(e){} })();\n" +
		"\twindow.__BETA_PATH = location.pathname.replace(/^\\/beta(?=\\/|$)/, \"\") || \"/\";", W);
	html = html.replace(/\.(exec|test)\((?:window\.)?location\.pathname\)/g, ".$1(window.__BETA_PATH)");
	html = html.replace(/location\.replace\("\//g, 'location.replace("/beta/');
	return html;
}

// ---------------------------------------------------------------- homepage hub
// scripts/beta-hub.html -> beta/index.html, with the logo mark and the download
// files taken from the live homepage so they never drift apart.
function buildHub() {
	const live = read("index.html");
	const mark = /<svg class="brand-mark"[\s\S]*?<\/svg>/.exec(live);
	const jars = /var DOWNLOAD_JARS = (\{[\s\S]*?\});/.exec(live);
	if (!mark || !jars) throw new Error("hub: couldn't find the logo mark or DOWNLOAD_JARS in index.html");
	const version = (/-(\d+(?:\.\d+)*)\.jar"/.exec(jars[1]) || [])[1] || "";
	let html = read("scripts/beta-hub.html");
	html = html.replace("{{BRAND_MARK}}", mark[0]).replace("{{DOWNLOAD_JARS}}", jars[1].replace(/\n\s*/g, " ")).replace("{{MOD_VERSION}}", version);
	if (/\{\{[A-Z_]+\}\}/.test(html)) throw new Error("hub: unfilled marker " + /\{\{[A-Z_]+\}\}/.exec(html)[0]);
	write("index.html", html);
}

buildHub();
let n = 1;
for (const [src, dest] of PAGES) {
	let html = read(src);
	if (src === "index.html") html = buildSearch(html);
	if (src === "404.html") html = build404(html);
	html = common(html, src);
	write(dest, html);
	n++;
}
console.log("Built " + n + " beta pages into beta/.");

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
	html.beta .promo-dl{flex-direction:row;align-items:center;gap:18px;padding:14px 18px;border-color:rgba(27,217,106,.35);background:radial-gradient(420px 160px at 100% 50%,rgba(27,217,106,.16),transparent 70%),linear-gradient(135deg,rgba(var(--accent-rgb),0.06),transparent 60%),var(--panel);}
	html.beta .promo-dl .dl-copy{flex:1;min-width:0;}
	html.beta .promo-dl .dl-copy .promo-kicker{margin:0 0 3px;font-size:10px;color:#1BD96A;}
	html.beta .promo-dl .dl-copy b{display:block;font-family:var(--font-display);font-weight:600;font-size:19px;line-height:1.2;}
	html.beta .promo-dl .dl-copy span{display:block;color:var(--muted);font-size:12.5px;margin-top:3px;}
	html.beta .promo-dl .dl-cta{flex:0 0 auto;display:flex;flex-direction:column;align-items:center;gap:6px;}
	html.beta .promo-dl .mr-big{display:flex;align-items:center;gap:10px;background:#1BD96A;color:#06210F;text-decoration:none;border-radius:12px;padding:10px 18px;box-shadow:0 6px 18px rgba(27,217,106,.25);transition:transform .12s ease,filter .12s ease;}
	html.beta .promo-dl .mr-big:hover{filter:brightness(1.06);transform:translateY(-1px);}
	html.beta .promo-dl .mr-big svg{width:26px;height:26px;flex:0 0 auto;}
	html.beta .promo-dl .mr-big b{display:block;font-size:16px;font-weight:800;line-height:1.15;}
	html.beta .promo-dl .mr-big small{display:block;font-size:11px;font-weight:600;opacity:.8;}
	html.beta .promo-dl .dl-file{font-size:12px;color:var(--muted);text-decoration:underline;text-underline-offset:2px;cursor:pointer;background:none;border:none;font-family:inherit;padding:0;}
	html.beta .promo-dl .dl-file:hover{color:var(--text);}
	@media (max-width:760px){html.beta .promo-strip{grid-template-columns:1fr;}html.beta .promo-dl{flex-wrap:wrap;}html.beta .promo-dl .dl-cta{width:100%;}html.beta .promo-dl .mr-big{width:100%;justify-content:center;}}

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
		'<article class="promo-card promo-dl"><div class="dl-copy"><div class="promo-kicker">Shop Logger mod</div><b>Get the mod</b><span>Scans shops as you walk past, alerts you about watched items, and puts every listing in-game.</span></div>' +
		'<div class="dl-cta"><a class="mr-big" href="https://modrinth.com/mod/sc-shoplogger" target="_blank" rel="noopener">' +
		'<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12.25.004a11.78 11.77 0 0 0-8.92 3.73A11.37 11.36 0 0 0 .003 11.85c0 1.42.17 2.5.6 3.77.24.76.77 1.9 1.17 2.53a12.3 12.3 0 0 0 8.85 5.64c.44.05 2.54.07 2.76.02.2-.04.22.1-.26-1.7l-.36-1.37-1.01-.06a8.5 8.49 0 0 1-5.18-1.8 5.34 5.34 0 0 1-1.3-1.26c0-.05.34-.28.74-.5a37.57 37.55 0 0 1 2.88-1.63c.03 0 .5.45 1.06.98l1 .97 2.07-.43 2.06-.43 1.47-1.47c.8-.8 1.48-1.5 1.48-1.52 0-.09-.42-1.63-.46-1.7-.04-.06-.2-.03-1.02.18-.53.13-1.2.3-1.45.4l-.48.15-.53.53-.53.53-.93.1-.93.07-.52-.5a2.7 2.7 0 0 1-.96-1.7l-.13-.6.43-.57c.68-.9.68-.9 1.46-1.1.4-.1.65-.2.83-.33.13-.1.65-.58 1.14-1.07l.9-.9-.7-.7-.7-.7-1.95.54c-1.07.3-1.96.53-1.97.53-.03 0-2.23 2.48-2.63 2.97l-.29.35.28 1.03c.16.56.3 1.16.31 1.34l.03.3-.34.23c-.37.23-2.22 1.3-2.84 1.63-.36.2-.37.2-.44.1-.08-.1-.23-.6-.32-1.03-.18-.86-.17-2.75.02-3.73a8.84 8.84 0 0 1 7.9-6.93c.43-.03.77-.08.78-.1.06-.17.5-3 .47-3.04-.01-.02-.1-.02-.2-.03Zm3.68.67c-.2 0-.3.1-.37.38-.06.23-.46 2.42-.46 2.52 0 .04.1.11.22.16a8.51 8.5 0 0 1 2.99 2 8.38 8.38 0 0 1 2.16 3.45 6.9 6.9 0 0 1 .4 2.8c0 1.07 0 1.27-.1 1.73a9.37 9.37 0 0 1-1.76 3.77c-.32.4-.98 1.06-1.37 1.38-.38.32-1.54 1.1-1.7 1.14-.1.03-.1.06-.07.26.03.18.64 2.56.7 2.78l.06.06a12.07 12.06 0 0 0 7.27-9.4c.13-.77.13-2.58 0-3.4a11.96 11.95 0 0 0-5.73-8.58c-.7-.42-2.05-1.06-2.25-1.06Z"/></svg>' +
		'<span><b>Get it on Modrinth</b><small>Installs and updates for you</small></span></a>' +
		// the page's script opens the download popup from #downloadMod; themes style that id as a big pill, so it stays hidden and the text link clicks it
		'<button type="button" class="dl-file" onclick="document.getElementById(\'downloadMod\').click()">or download the file from here</button>' +
		'<button type="button" id="downloadMod" hidden></button></div></article>\n\t\t<article class="promo-card promo-raredle" id="promoRaredle">', W);
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

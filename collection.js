// Shared collection view: the checklist + stats for one account's rare items
// and mapart. Editable on /collection/ (My Collection), read-only on the
// public /collection/<username> page — shared like account-widget.js since
// both need the identical UI.
//
//   window.sctpCollection.mount(hostEl, { username?, editable })
//     editable -> the logged-in account's own collection (checkboxes, privacy switch)
//     otherwise -> public view of `username` (404.html routes /collection/<username> here)
//
// Collections are per world (Firefly / Honeybee). Each card also has a star
// for the wishlist (things you want, shared on the same public page; owning an
// item takes its star away — the server does the same). The catalogs come from
// /data/rare-items.json and GET /mapart; ownership from the /collection API.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var WORLDS = ["Firefly", "Honeybee"];
	var PAGE = 120;
	var CHUNK = 1500; // ids per /collection/set call (server max 2000)

	var style = document.createElement("style");
	style.textContent =
		".col-wrap{font-family:inherit;color:var(--text,#EAEFE7);}" +
		".col-top{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin-bottom:14px;}" +
		".col-pills{display:flex;gap:8px;flex-wrap:wrap;}" +
		".col-pill{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--muted,#8FA593);border-radius:999px;padding:6px 16px;font-size:13.5px;font-weight:600;cursor:pointer;font-family:inherit;}" +
		".col-pill.on{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;}" +
		".col-privacy{display:flex;align-items:center;gap:8px;font-size:13px;color:var(--muted,#8FA593);flex-wrap:wrap;}" +
		".col-privacy a{color:var(--accent,#B7E23D);}" +
		".col-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin-bottom:18px;}" +
		".col-card{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:12px;padding:14px 16px;}" +
		".col-card .k{font-size:11.5px;color:var(--muted,#8FA593);text-transform:uppercase;letter-spacing:.04em;font-weight:600;}" +
		".col-card .v{font-size:26px;font-weight:700;font-family:var(--font-display,inherit);margin:2px 0 6px;}" +
		".col-card .v small{font-size:14px;color:var(--muted,#8FA593);font-weight:500;}" +
		".col-card .sub{font-size:12.5px;color:var(--muted,#8FA593);margin-top:6px;}" +
		".col-bar{height:7px;background:var(--panel-alt,#22332A);border-radius:999px;overflow:hidden;}" +
		".col-bar>i{display:block;height:100%;background:var(--accent,#B7E23D);border-radius:999px;}" +
		".col-h{font-family:var(--font-display,inherit);font-size:16px;margin:22px 0 10px;}" +
		".col-break{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:8px 22px;margin-bottom:6px;}" +
		".col-brow .top{display:flex;justify-content:space-between;font-size:13px;margin-bottom:3px;gap:8px;}" +
		".col-brow .top span:last-child{color:var(--muted,#8FA593);font-family:var(--font-mono,monospace);font-size:12px;white-space:nowrap;}" +
		".col-tools{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 12px;align-items:center;}" +
		".col-tools input,.col-tools select{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 11px;font-size:13.5px;font-family:inherit;}" +
		".col-tools input[type=search]{flex:1;min-width:150px;}" +
		".col-tools button{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 12px;font-size:12.5px;cursor:pointer;font-family:inherit;}" +
		".col-tools button:hover{border-color:var(--accent-dim,#87AE29);}" +
		".col-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px;}" +
		".col-item{position:relative;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:10px;padding:8px;text-align:center;font-size:12.5px;}" +
		".col-item.own{border-color:var(--accent-dim,#87AE29);background:rgba(var(--accent-rgb,183,226,61),0.07);}" +
		".col-item.editable,.col-item.editable *{cursor:pointer;-webkit-user-select:none;user-select:none;}" +
		".col-item button.ck:hover{border-color:var(--accent-dim,#87AE29);}" +
		".col-item .im{height:74px;display:flex;align-items:center;justify-content:center;margin-bottom:6px;}" +
		".col-item .im img{max-width:100%;max-height:74px;image-rendering:pixelated;}" +
		".col-item.miss .im img{opacity:.55;filter:grayscale(.6);}" +
		".col-item .nm{line-height:1.25;overflow-wrap:anywhere;}" +
		".col-item .nm small{display:block;color:var(--muted,#8FA593);font-size:11px;margin-top:1px;}" +
		".col-item .ck{padding:0;font-family:inherit;text-align:center;-webkit-user-select:none;user-select:none;position:absolute;top:6px;right:6px;width:20px;height:20px;border-radius:50%;border:1px solid var(--line,#33453A);background:var(--panel-alt,#22332A);font-size:12px;line-height:18px;color:transparent;}" +
		".col-item.own .ck{background:var(--accent,#B7E23D);border-color:transparent;color:var(--accent-ink,#16210F);font-weight:700;}" +
		".col-item .st{padding:0;font-family:inherit;position:absolute;top:6px;left:6px;width:20px;height:20px;border-radius:50%;border:1px solid var(--line,#33453A);background:var(--panel-alt,#22332A);font-size:12px;line-height:18px;color:var(--muted,#8FA593);text-align:center;}" +
		".col-item button.st:hover{border-color:#E8B93A;color:#E8B93A;}" +
		".col-item.wish .st{background:#E8B93A;border-color:transparent;color:#2A1F05;}" +
		".col-item.wish{border-color:rgba(232,185,58,0.55);}" +
		".col-item.own .st{display:none;}" +
		".col-more{display:block;margin:14px auto 0;background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 20px;cursor:pointer;font-family:inherit;}" +
		".col-msg{color:var(--muted,#8FA593);font-size:13.5px;padding:10px 0;}" +
		".col-msg.err{color:#E27D6B;}" +
		".nc-bg{position:fixed;inset:0;background:rgba(0,0,0,0.7);display:flex;align-items:flex-start;justify-content:center;z-index:400;padding:20px;box-sizing:border-box;overflow:auto;}" +
		".nc-modal{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:20px;width:100%;max-width:880px;margin:auto;box-sizing:border-box;color:var(--text,#EAEFE7);font-family:inherit;}" +
		".nc-modal h2{margin:0 0 4px;font-size:20px;font-family:var(--font-display,inherit);}" +
		".nc-sub{color:var(--muted,#8FA593);font-size:13px;margin:0 0 14px;}" +
		".nc-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:10px 12px;margin-bottom:14px;}" +
		".nc-grid label{display:block;font-size:11.5px;color:var(--muted,#8FA593);font-weight:600;text-transform:uppercase;letter-spacing:.03em;margin-bottom:4px;}" +
		".nc-grid input,.nc-grid select{width:100%;box-sizing:border-box;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 10px;font-size:13.5px;font-family:inherit;}" +
		".nc-count{font-size:13px;color:var(--muted,#8FA593);margin:0 0 10px;}" +
		".nc-out{text-align:center;}" +
		".nc-out canvas{max-width:100%;height:auto;border-radius:10px;border:1px solid var(--line,#33453A);}" +
		".nc-actions{display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap;margin-top:12px;}" +
		".nc-actions button,.nc-actions a{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 16px;font-size:13.5px;cursor:pointer;font-family:inherit;text-decoration:none;}" +
		".nc-actions .primary{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;font-weight:700;}" +
		".col-recent{display:flex;flex-wrap:wrap;gap:8px;}" +
		".col-recent span{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);border-radius:999px;padding:3px 11px;font-size:12.5px;}";
	document.head.appendChild(style);

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function pct(a, b) { return b ? Math.round((a / b) * 1000) / 10 : 0; }
	function bar(a, b) { return '<div class="col-bar"><i style="width:' + pct(a, b) + '%"></i></div>'; }
	function session() { return window.sctpAccount ? window.sctpAccount.getSession() : null; }
	function api(path, opts) {
		opts = opts || {};
		var headers = {};
		var s = session();
		if (s) headers["Authorization"] = "Bearer " + s.token;
		if (opts.body) headers["Content-Type"] = "application/json";
		return fetch(API_BASE + path, { method: opts.method || "GET", headers: headers, body: opts.body })
			.then(function (r) { return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, status: r.status, data: d }; }); });
	}
	function yearOf(rd) {
		var m = /(\d{4})/.exec(String(rd || ""));
		return m ? m[1] : "Unknown";
	}

	var catalogPromise = null;
	function loadCatalogs() {
		if (!catalogPromise) {
			catalogPromise = Promise.all([
				fetch("/data/rare-items.json").then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
				fetch(API_BASE + "/mapart").then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
			]).then(function (a) { return { rare: a[0], mapart: a[1] }; });
		}
		return catalogPromise;
	}

	function mount(host, opts) {
		var editable = !!opts.editable;
		var st = {
			world: "Firefly", kind: "rare", status: "all", cat: "", q: "", shown: PAGE,
			owned: {},       // "kind|world|id" -> addedAt
			wished: {},      // same keys: starred (wishlist)
			private: false, username: opts.username || "", cat_: null,
		};
		function key(kind, world, id) { return kind + "|" + world + "|" + id; }
		host.innerHTML = '<div class="col-wrap"><div class="col-msg">Loading collection…</div></div>';

		var load = editable ? api("/collection/mine") : api("/collection/public?username=" + encodeURIComponent(opts.username));
		Promise.all([load, loadCatalogs()]).then(function (res) {
			var r = res[0], cat = res[1];
			if (!r.ok) { host.innerHTML = '<div class="col-wrap"><div class="col-msg err">' + esc(r.data.error || "Couldn't load this collection.") + "</div></div>"; return; }
			if (r.data.private && !editable) {
				host.innerHTML = '<div class="col-wrap"><div class="col-msg">' + esc(r.data.username) + "'s collection is private.</div></div>";
				return;
			}
			st.cat_ = cat;
			st.username = r.data.username || st.username;
			st.private = !!r.data.private;
			(r.data.items || []).forEach(function (i) { st.owned[key(i.kind, i.world, i.itemId)] = i.addedAt || ""; });
			(r.data.wishes || []).forEach(function (i) { st.wished[key(i.kind, i.world, i.itemId)] = i.addedAt || ""; });
			// /collection/<name>#wishlist (the "Copy wishlist link" button) opens on the stars
			if (location.hash === "#wishlist") st.status = "wish";
			// Mapart ids never seen in the catalog (deleted since) just don't count.
			st.mapartById = {};
			cat.mapart.forEach(function (m) { st.mapartById[m.id] = m; });
			render();
		});

		function rareList() { return st.cat_.rare; }
		function mapartList() { return st.cat_.mapart.filter(function (m) { return m.world === st.world; }); }
		function isOwned(kind, id) { return Object.prototype.hasOwnProperty.call(st.owned, key(kind, st.world, id)); }
		function isWished(kind, id) { return !isOwned(kind, id) && Object.prototype.hasOwnProperty.call(st.wished, key(kind, st.world, id)); }
		function wishCount(world) {
			return Object.keys(st.wished).filter(function (k) {
				var p = k.split("|");
				return p[1] === world && !Object.prototype.hasOwnProperty.call(st.owned, k) && (p[0] === "rare" || st.mapartById[p.slice(2).join("|")]);
			}).length;
		}
		function catOfRare(it) { return it.category || "Other"; }
		function catOfMapart(m) { return m.category || "No category"; }

		function counts(kind) {
			var list = kind === "rare" ? rareList() : mapartList();
			var have = 0;
			list.forEach(function (x) { if (isOwned(kind, x.id)) have++; });
			return { have: have, total: list.length };
		}

		function breakdown(list, kind, keyFn, sortByTotal) {
			var map = {};
			list.forEach(function (x) {
				var k = keyFn(x);
				if (!map[k]) map[k] = { have: 0, total: 0 };
				map[k].total++;
				if (isOwned(kind, x.id)) map[k].have++;
			});
			var keys = Object.keys(map);
			keys.sort(function (a, b) { return sortByTotal ? map[b].total - map[a].total || a.localeCompare(b) : a.localeCompare(b); });
			return keys.map(function (k) { return { k: k, have: map[k].have, total: map[k].total }; });
		}
		function breakHtml(rows) {
			return '<div class="col-break">' + rows.map(function (r) {
				return '<div class="col-brow"><div class="top"><span>' + esc(r.k) + "</span><span>" + r.have + " / " + r.total + " &middot; " + pct(r.have, r.total) + "%</span></div>" + bar(r.have, r.total) + "</div>";
			}).join("") + "</div>";
		}

		function statsHtml() {
			var rc = counts("rare"), mc = counts("mapart");
			var have = rc.have + mc.have, total = rc.total + mc.total;
			var other = WORLDS.filter(function (w) { return w !== st.world; })[0];
			var otherHave = 0;
			Object.keys(st.owned).forEach(function (k) { if (k.split("|")[1] === other && (k.split("|")[0] === "rare" || st.mapartById[k.split("|").slice(2).join("|")])) otherHave++; });
			var rareList_ = rareList(), mapList = mapartList();

			// Mapart artists: how much of each artist's work you own (top 12 by pieces in this world).
			var artistRows = breakdown(mapList.filter(function (m) { return m.artist; }), "mapart", function (m) { return m.artist; }, true).slice(0, 12);
			var sizeRows = breakdown(mapList, "mapart", function (m) { return m.width * m.height > 1 ? m.width + "×" + m.height : "Single map"; }, true);
			var recent = Object.keys(st.owned).map(function (k) {
				var p = k.split("|"); return { kind: p[0], world: p[1], id: p.slice(2).join("|"), at: st.owned[k] };
			}).filter(function (x) { return x.world === st.world; }).sort(function (a, b) { return a.at < b.at ? 1 : -1; }).slice(0, 8).map(function (x) {
				var it = x.kind === "rare" ? rareList_.filter(function (r) { return r.id === x.id; })[0] : st.mapartById[x.id];
				return it ? (x.kind === "rare" ? it.name : it.title) : null;
			}).filter(Boolean);

			return '<div class="col-cards">' +
				'<div class="col-card"><div class="k">Rare items</div><div class="v">' + rc.have + " <small>/ " + rc.total + "</small></div>" + bar(rc.have, rc.total) + '<div class="sub">' + pct(rc.have, rc.total) + "% &middot; " + (rc.total - rc.have) + " still needed</div></div>" +
				'<div class="col-card"><div class="k">Mapart (' + esc(st.world) + ')</div><div class="v">' + mc.have + " <small>/ " + mc.total + "</small></div>" + bar(mc.have, mc.total) + '<div class="sub">' + pct(mc.have, mc.total) + "% &middot; " + (mc.total - mc.have) + " still needed</div></div>" +
				'<div class="col-card"><div class="k">Overall (' + esc(st.world) + ')</div><div class="v">' + pct(have, total) + "<small>%</small></div>" + bar(have, total) + '<div class="sub">' + have + " of " + total + " collected</div></div>" +
				'<div class="col-card"><div class="k">Wishlist (' + esc(st.world) + ')</div><div class="v">' + wishCount(st.world) + ' <small>starred</small></div><div class="sub">' +
					(editable ? "Star (&#9734;) what you want. " + (st.private ? "Turn off \"keep private\" to share it." : '<button type="button" id="colWishCopy" class="col-pill" style="padding:3px 10px;font-size:12px;">Copy wishlist link</button>') : '<a href="#wishlist" id="colWishShow" style="color:var(--accent,#B7E23D);">Show the wishlist</a>') + "</div></div>" +
				'<div class="col-card"><div class="k">' + esc(other) + '</div><div class="v">' + otherHave + ' <small>collected</small></div><div class="sub">Switch world above to see its full breakdown.</div></div>' +
				"</div>" +
				(recent.length ? '<div class="col-h">Recently added</div><div class="col-recent">' + recent.map(function (n) { return "<span>" + esc(n) + "</span>"; }).join("") + "</div>" : "") +
				'<div class="col-h">Rare items by category</div>' + breakHtml(breakdown(rareList_, "rare", catOfRare, false)) +
				'<div class="col-h">Rare items by release year</div>' + breakHtml(breakdown(rareList_, "rare", function (x) { return yearOf(x.releaseDate); }, false)) +
				'<div class="col-h">Mapart by category</div>' + (mapList.length ? breakHtml(breakdown(mapList, "mapart", catOfMapart, false)) : '<div class="col-msg">No mapart in this world yet.</div>') +
				(mapList.length ? '<div class="col-h">Mapart by size</div>' + breakHtml(sizeRows) : "") +
				(artistRows.length ? '<div class="col-h">Mapart by artist <span style="font-size:12px;color:var(--muted,#8FA593);font-family:inherit;">(top 12 by pieces)</span></div>' + breakHtml(artistRows) : "");
		}

		function filtered() {
			var list = st.kind === "rare" ? rareList() : mapartList();
			var q = st.q.trim().toLowerCase();
			return list.filter(function (x) {
				var own = isOwned(st.kind, x.id);
				if (st.status === "owned" && !own) return false;
				if (st.status === "missing" && own) return false;
				if (st.status === "wish" && !isWished(st.kind, x.id)) return false;
				var c = st.kind === "rare" ? catOfRare(x) : catOfMapart(x);
				if (st.cat && c !== st.cat) return false;
				if (q) {
					var hay = st.kind === "rare" ? (x.name + " " + (x.effect || "")) : (x.title + " " + (x.artist || ""));
					if (hay.toLowerCase().indexOf(q) === -1) return false;
				}
				return true;
			});
		}

		function itemHtml(x) {
			var own = isOwned(st.kind, x.id), wish = isWished(st.kind, x.id);
			var img = st.kind === "rare" ? x.texture : API_BASE + "/mapart/image?id=" + encodeURIComponent(x.id) + "&v=" + encodeURIComponent(x.imageHash || "");
			var name = st.kind === "rare" ? x.name : x.title;
			var sub = st.kind === "rare" ? (x.category || "") : (x.artist ? "by " + x.artist : "");
			return '<div class="col-item ' + (own ? "own" : "miss") + (wish ? " wish" : "") + (editable ? " editable" : "") + '" data-id="' + esc(x.id) + '" title="' + esc(name) + '">' +
				(editable ? '<button type="button" class="st" title="' + (wish ? "On your wishlist — click to remove" : "Add to wishlist") + '">' + (wish ? "&#9733;" : "&#9734;") + "</button>" : (wish ? '<span class="st" title="On the wishlist">&#9733;</span>' : "")) +
				(editable ? '<button type="button" class="ck" title="' + (own ? "Owned — click to remove" : "Mark as owned") + '">&#10003;</button>' : '<span class="ck">&#10003;</span>') + '<div class="im"><img src="' + esc(img) + '" alt="" loading="lazy" onerror="this.style.visibility=\'hidden\'"></div>' +
				'<div class="nm">' + esc(name) + (sub ? "<small>" + esc(sub) + "</small>" : "") + "</div></div>";
		}

		function render() {
			var s = session();
			var link = location.origin + "/collection/" + encodeURIComponent(st.username);
			var cats = st.kind === "rare"
				? breakdown(rareList(), "rare", catOfRare, false).map(function (r) { return r.k; })
				: breakdown(mapartList(), "mapart", catOfMapart, false).map(function (r) { return r.k; });
			var list = filtered();
			host.innerHTML =
				'<div class="col-wrap">' +
				(editable ? "" : '<h2 style="font-family:var(--font-display,inherit);margin:0 0 4px;">' + esc(st.username) + "'s collection</h2>") +
				'<div class="col-top"><div class="col-pills">' +
					WORLDS.map(function (w) { return '<button type="button" class="col-pill' + (w === st.world ? " on" : "") + '" data-world="' + w + '">' + w + "</button>"; }).join("") +
				"</div>" +
				(editable ? '<label class="col-privacy"><input type="checkbox" id="colPrivate"' + (st.private ? " checked" : "") + '> Keep my collection private' +
					(st.private ? "" : ' &middot; <a href="' + esc(link) + '" target="_blank" rel="noopener">public page</a> <button type="button" id="colCopy" class="col-pill" style="padding:3px 10px;font-size:12px;">Copy link</button>') + "</label>" : "") +
				"</div>" +
				(editable && !s ? "" : "") +
				'<div id="colStats">' + statsHtml() + "</div>" +
				'<div class="col-h">Checklist</div>' +
				'<div class="col-pills" style="margin-bottom:10px;">' +
					'<button type="button" class="col-pill' + (st.kind === "rare" ? " on" : "") + '" data-kind="rare">Rare items</button>' +
					'<button type="button" class="col-pill' + (st.kind === "mapart" ? " on" : "") + '" data-kind="mapart">Mapart</button>' +
				"</div>" +
				'<div class="col-tools"><input type="search" id="colQ" placeholder="Search…" value="' + esc(st.q) + '">' +
					'<select id="colCat"><option value="">All categories</option>' + cats.map(function (c) { return '<option value="' + esc(c) + '"' + (st.cat === c ? " selected" : "") + ">" + esc(c) + "</option>"; }).join("") + "</select>" +
					'<select id="colStatus">' + [["all", "All"], ["owned", "Have"], ["missing", "Need"], ["wish", "Wishlist \u2605"]].map(function (o) { return '<option value="' + o[0] + '"' + (st.status === o[0] ? " selected" : "") + ">" + o[1] + "</option>"; }).join("") + "</select>" +
					(editable ? '<button type="button" id="colAll">Mark all matching as owned</button><button type="button" id="colNone">Unmark all matching</button><button type="button" id="colShare" title="Make a picture of what you still need, to share on Discord">&#128444; Share what I still need</button>' : "") +
				"</div>" +
				'<div class="col-msg" id="colCount"></div>' +
				'<div class="col-grid" id="colGrid"></div><div id="colMoreHost"></div><div class="col-msg err" id="colErr" hidden></div>' +
				"</div>";
			bind();
			paintGrid(list);
		}

		function paintGrid(list) {
			list = list || filtered();
			var grid = host.querySelector("#colGrid");
			grid.innerHTML = list.slice(0, st.shown).map(itemHtml).join("");
			host.querySelector("#colCount").textContent = list.length + " " + (st.kind === "rare" ? "rare item" : "mapart") + (list.length === 1 ? "" : "s") + (list.length > st.shown ? " (showing " + st.shown + ")" : "");
			var more = host.querySelector("#colMoreHost");
			more.innerHTML = list.length > st.shown
				? '<button type="button" class="col-more" id="colMore">Show more</button><button type="button" class="col-more" id="colAllShown" style="margin-top:8px;">Show all ' + list.length + "</button>" : "";
			var mb = host.querySelector("#colMore");
			if (mb) mb.onclick = function () { st.shown += PAGE; paintGrid(); };
			var ma = host.querySelector("#colAllShown");
			if (ma) ma.onclick = function () { st.shown = list.length; paintGrid(); };
		}

		function refreshStats() { host.querySelector("#colStats").innerHTML = statsHtml(); }
		function showErr(t) { var e = host.querySelector("#colErr"); e.textContent = t; e.hidden = !t; }

		// Optimistic: flip locally, tell the server, roll back if it refuses.
		function setOwned(ids, own, inPlace) {
			var kind = st.kind, world = st.world, now = new Date().toISOString();
			var before = {};
			ids.forEach(function (id) {
				var k = key(kind, world, id);
				before[id] = Object.prototype.hasOwnProperty.call(st.owned, k) ? st.owned[k] : undefined;
				if (own) st.owned[k] = st.owned[k] || now; else delete st.owned[k];
				if (own) delete st.wished[k];
			});
			showErr("");
			if (inPlace) { paintOwnedState(ids, kind, world); refreshStats(); }
			var chunks = [];
			for (var i = 0; i < ids.length; i += CHUNK) chunks.push(ids.slice(i, i + CHUNK));
			return chunks.reduce(function (p, chunk) {
				return p.then(function (ok) {
					if (!ok) return false;
					return api("/collection/set", { method: "POST", body: JSON.stringify({ kind: kind, world: world, ids: chunk, owned: own }) }).then(function (r) {
						if (!r.ok) showErr(r.data.error || "Couldn't save that change.");
						return r.ok;
					});
				});
			}, Promise.resolve(true)).then(function (ok) {
				if (!ok) {
					ids.forEach(function (id) {
						var k = key(kind, world, id);
						if (before[id] === undefined) delete st.owned[k]; else st.owned[k] = before[id];
					});
				}
				refreshStats();
				if (inPlace && ok) paintOwnedState(ids, kind, world); else paintGrid();
			});
		}
		// Flip just the touched cards so the grid doesn't reshuffle under the cursor
		// (e.g. an item vanishing from a "Need" filter the moment you tick it).
		function paintOwnedState(ids, kind, world) {
			if (kind !== st.kind || world !== st.world) return;
			var want = {};
			ids.forEach(function (id) { want[id] = true; });
			host.querySelectorAll(".col-item").forEach(function (el) {
				var id = el.getAttribute("data-id");
				if (!want[id]) return;
				var own = isOwned(kind, id);
				el.classList.toggle("own", own);
				el.classList.toggle("miss", !own);
				paintStar(el, kind, id);
			});
		}

		function paintStar(el, kind, id) {
			var wish = isWished(kind, id), b = el.querySelector(".st");
			el.classList.toggle("wish", wish);
			if (b) { b.innerHTML = wish ? "&#9733;" : "&#9734;"; b.title = wish ? "On your wishlist — click to remove" : "Add to wishlist"; }
		}

		// Same optimistic pattern as setOwned, for the wishlist.
		function setWished(ids, wish) {
			var kind = st.kind, world = st.world, now = new Date().toISOString();
			ids.forEach(function (id) { var k = key(kind, world, id); if (wish) st.wished[k] = st.wished[k] || now; else delete st.wished[k]; });
			showErr("");
			paintOwnedState(ids, kind, world);
			refreshStats();
			api("/collection/set", { method: "POST", body: JSON.stringify({ kind: kind, world: world, ids: ids, owned: wish, list: "wish" }) }).then(function (r) {
				if (r.ok) return;
				showErr(r.data.error || "Couldn't save that change.");
				ids.forEach(function (id) { var k = key(kind, world, id); if (wish) delete st.wished[k]; else st.wished[k] = now; });
				paintOwnedState(ids, kind, world);
				refreshStats();
			});
		}

		function bind() {
			host.querySelectorAll("[data-world]").forEach(function (b) {
				b.onclick = function () { st.world = b.getAttribute("data-world"); st.cat = ""; st.shown = PAGE; render(); };
			});
			host.querySelectorAll("[data-kind]").forEach(function (b) {
				b.onclick = function () { st.kind = b.getAttribute("data-kind"); st.cat = ""; st.shown = PAGE; render(); };
			});
			host.querySelector("#colQ").oninput = function (e) { st.q = e.target.value; st.shown = PAGE; paintGrid(); };
			host.querySelector("#colCat").onchange = function (e) { st.cat = e.target.value; st.shown = PAGE; paintGrid(); };
			host.querySelector("#colStatus").onchange = function (e) { st.status = e.target.value; st.shown = PAGE; paintGrid(); };
			host.querySelector("#colStats").onclick = function (e) {
				if (e.target.id === "colWishShow") { e.preventDefault(); st.status = "wish"; st.shown = PAGE; render(); host.querySelector("#colGrid").scrollIntoView({ behavior: "smooth", block: "start" }); }
				if (e.target.id === "colWishCopy") {
					var link = location.origin + "/collection/" + encodeURIComponent(st.username) + "#wishlist";
					if (navigator.clipboard) navigator.clipboard.writeText(link).then(function () { e.target.textContent = "Copied!"; });
				}
			};
			if (!editable) return;
			host.querySelector("#colGrid").onclick = function (e) {
				var el = e.target.closest ? e.target.closest(".col-item") : null;
				if (!el) return;
				var id = el.getAttribute("data-id");
				if (e.target.closest(".st")) { setWished([id], !isWished(st.kind, id)); return; }
				setOwned([id], !isOwned(st.kind, id), true);
			};
			function bulk(own) {
				var ids = filtered().filter(function (x) { return isOwned(st.kind, x.id) !== own; }).map(function (x) { return x.id; });
				if (!ids.length) return;
				if (!confirm((own ? "Mark " : "Unmark ") + ids.length + " matching item" + (ids.length === 1 ? "" : "s") + (own ? " as owned" : " as not owned") + " in " + st.world + "? (This covers everything matching your current search/filters, not just what's on screen.)")) return;
				setOwned(ids, own);
			}
			host.querySelector("#colShare").onclick = function () {
				openNeedCard({ world: st.world, kind: st.kind, rare: rareList(), mapart: mapartList(), username: st.username, isOwned: function (k, id) { return isOwned(k, id); }, isWished: function (k, id) { return isWished(k, id); } });
			};
			host.querySelector("#colAll").onclick = function () { bulk(true); };
			host.querySelector("#colNone").onclick = function () { bulk(false); };
			host.querySelector("#colPrivate").onchange = function (e) {
				var want = e.target.checked;
				api("/collection/privacy", { method: "POST", body: JSON.stringify({ private: want }) }).then(function (r) {
					if (r.ok) { st.private = want; render(); } else { e.target.checked = !want; showErr(r.data.error || "Couldn't change privacy."); }
				});
			};
			var copy = host.querySelector("#colCopy");
			if (copy) copy.onclick = function () {
				var link = location.origin + "/collection/" + encodeURIComponent(st.username);
				if (navigator.clipboard) navigator.clipboard.writeText(link).then(function () { copy.textContent = "Copied!"; });
			};
		}
	}

	// ---------------- "what I still need" picture ----------------
	// Builds a shareable PNG (for Discord etc.) of the items still missing from
	// a collection, narrowed by any mix of release year/month, where it's
	// obtained (e.g. "aquatic crate"), category and type/slot (e.g. "consumeable").
	var NC_MAX_ITEMS = 300;

	function uniqueSorted(list, key) {
		var seen = {};
		list.forEach(function (x) { var v = x[key]; if (v) seen[v] = true; });
		return Object.keys(seen).sort(function (a, b) { return a.localeCompare(b); });
	}
	function releaseOptions(list) {
		var years = {}, exact = {};
		list.forEach(function (x) {
			if (!x.releaseDate) return;
			exact[x.releaseDate] = true;
			var m = /(\d{4})/.exec(x.releaseDate); if (m) years[m[1]] = true;
		});
		function key(s) { var m = /([A-Za-z]{3})\w*\s+(\d{4})/.exec(s); var mo = m ? "JanFebMarAprMayJunJulAugSepOctNovDec".indexOf(m[1]) / 3 : 0; var y = /(\d{4})/.exec(s); return (y ? +y[1] : 0) * 12 + mo; }
		var ys = Object.keys(years).sort(function (a, b) { return b - a; });
		var ex = Object.keys(exact).filter(function (s) { return !/^\d{4}$/.test(s); }).sort(function (a, b) { return key(b) - key(a); });
		return { years: ys, months: ex };
	}

	function loadImg(src, cors) {
		return new Promise(function (resolve) {
			var im = new Image();
			if (cors) im.crossOrigin = "anonymous";
			im.onload = function () { resolve(im); };
			im.onerror = function () { resolve(null); };
			im.src = src;
		});
	}

	function openNeedCard(ctx) {
		var isRare = ctx.kind === "rare";
		var pool = (isRare ? ctx.rare : ctx.mapart);
		var bg = document.createElement("div");
		bg.className = "nc-bg";
		var rel = releaseOptions(ctx.rare);
		var filters;
		if (isRare) {
			filters =
				'<div><label>Category</label><select id="ncCat"><option value="">Any</option>' + uniqueSorted(pool, "category").map(function (c) { return "<option>" + esc(c) + "</option>"; }).join("") + "</select></div>" +
				'<div><label>Released</label><select id="ncRel"><option value="">Any time</option>' +
					rel.years.map(function (y) { return '<option value="' + y + '">All of ' + y + "</option>"; }).join("") +
					rel.months.map(function (m) { return '<option value="' + esc(m) + '">' + esc(m) + "</option>"; }).join("") + "</select></div>" +
				'<div><label>Obtained from</label><input id="ncFrom" list="ncFromList" placeholder="e.g. aquatic crate"><datalist id="ncFromList">' + uniqueSorted(pool, "obtainedFrom").map(function (v) { return '<option value="' + esc(v) + '">'; }).join("") + "</datalist></div>" +
				'<div><label>Type / slot</label><input id="ncSlot" list="ncSlotList" placeholder="e.g. consumeable"><datalist id="ncSlotList">' + uniqueSorted(pool, "typeSlot").map(function (v) { return '<option value="' + esc(v) + '">'; }).join("") + "</datalist></div>";
		} else {
			var cats = {}; pool.forEach(function (m) { cats[m.category || "No category"] = true; });
			filters =
				'<div><label>Category</label><select id="ncCat"><option value="">Any</option>' + Object.keys(cats).sort().map(function (c) { return "<option>" + esc(c) + "</option>"; }).join("") + "</select></div>" +
				'<div><label>Artist contains</label><input id="ncFrom" placeholder="e.g. Colrr"></div>' +
				'<div><label>Size</label><select id="ncSize"><option value="">Any</option><option value="1">Single map</option><option value="2">Bigger than 1 map</option></select></div>';
		}
		bg.innerHTML =
			'<div class="nc-modal"><h2>What I still need</h2>' +
			'<p class="nc-sub">' + (isRare ? "Rare items" : "Mapart") + " in " + esc(ctx.world) + " that you don't own yet. Narrow it down, then download or copy the picture to share.</p>" +
			'<div class="nc-grid"><div><label>Show</label><select id="ncMode"><option value="need">What I still need</option><option value="wish">My wishlist (starred)</option><option value="own">What I own (showcase)</option></select></div>' + filters + '<div style="grid-column:1/-1;"><label>Title on the picture</label><input id="ncTitle" maxlength="90"></div></div>' +
			'<div id="ncStudio" style="margin:0 0 12px;"></div>' +
			'<div class="nc-count" id="ncCount"></div><div class="nc-out" id="ncOut"></div>' +
			'<div class="nc-actions"><button type="button" id="ncClose">Close</button><button type="button" id="ncCopy">Copy image</button><a id="ncDl" download="what-i-still-need.png" class="primary">Download PNG</a></div></div>';
		document.body.appendChild(bg);
		bg.addEventListener("click", function (e) { if (e.target === bg) bg.remove(); });
		bg.querySelector("#ncClose").onclick = function () { bg.remove(); };
		function q(s) { return bg.querySelector(s); }
		var titleTouched = false, token = 0, lastCanvas = null;
		q("#ncTitle").addEventListener("input", function () { titleTouched = true; schedule(); });

		function selected() {
			var cat = q("#ncCat").value;
			var from = q("#ncFrom") ? q("#ncFrom").value.trim().toLowerCase() : "";
			var relV = q("#ncRel") ? q("#ncRel").value : "";
			var slot = q("#ncSlot") ? q("#ncSlot").value.trim().toLowerCase() : "";
			var size = q("#ncSize") ? q("#ncSize").value : "";
			return pool.filter(function (x) {
				var mode = q("#ncMode").value;
				if (mode === "wish" ? !ctx.isWished(ctx.kind, x.id) : ctx.isOwned(ctx.kind, x.id) !== (mode === "own")) return false;
				if (isRare) {
					if (cat && x.category !== cat) return false;
					if (relV) { if (/^\d{4}$/.test(relV) ? String(x.releaseDate || "").indexOf(relV) === -1 : x.releaseDate !== relV) return false; }
					if (from && String(x.obtainedFrom || "").toLowerCase().indexOf(from) === -1) return false;
					if (slot && String(x.typeSlot || "").toLowerCase().indexOf(slot) === -1) return false;
				} else {
					if (cat && (x.category || "No category") !== cat) return false;
					if (from && String(x.artist || "").toLowerCase().indexOf(from) === -1) return false;
					if (size === "1" && x.width * x.height !== 1) return false;
					if (size === "2" && x.width * x.height === 1) return false;
				}
				return true;
			});
		}
		function autoTitle() {
			var bits = [];
			if (q("#ncRel") && q("#ncRel").value) bits.push(q("#ncRel").value);
			if (q("#ncFrom") && q("#ncFrom").value.trim()) bits.push((isRare ? "" : "by ") + q("#ncFrom").value.trim());
			if (q("#ncSlot") && q("#ncSlot").value.trim()) bits.push(q("#ncSlot").value.trim());
			if (q("#ncCat").value) bits.push(q("#ncCat").value);
			return (q("#ncMode").value === "own" ? "My collection" : q("#ncMode").value === "wish" ? "Wishlist" : "Still needed") + (bits.length ? ": " + bits.join(" \u00b7 ") : (isRare ? ": rare items" : ": mapart"));
		}
		var timer = null;
		function schedule() { clearTimeout(timer); timer = setTimeout(draw, 250); }
		bg.querySelectorAll(".nc-grid select, .nc-grid input").forEach(function (el) {
			if (el.id !== "ncTitle") el.addEventListener("input", function () { if (!titleTouched) q("#ncTitle").value = autoTitle(); schedule(); });
		});
		q("#ncTitle").value = autoTitle();

		var studio = null;
		function draw() {
			var own = q("#ncMode").value === "own", wishMode = q("#ncMode").value === "wish";
			var items = selected().sort(function (a, b) { return String(isRare ? a.name : a.title).localeCompare(String(isRare ? b.name : b.title)); });
			q("#ncCount").textContent = items.length + (own ? " owned" : wishMode ? " starred" : " still needed") + " out of " + pool.length + " in " + ctx.world;
			var my = ++token;
			if (!items.length) { q("#ncOut").innerHTML = '<div class="col-msg">Nothing matches' + (own ? "." : wishMode ? ". Star items (&#9734;) to add them to your wishlist." : " \u2014 you own everything here!") + '</div>'; lastCanvas = null; return; }
			if (!window.sctpShare) { q("#ncOut").innerHTML = '<div class="col-msg">The image maker failed to load \u2014 refresh the page.</div>'; return; }
			var spec = {
				type: "grid", eyebrow: (ctx.username ? ctx.username + "'s " : "") + (own ? "collection" : wishMode ? "wishlist" : "still needed") + " \u00b7 " + ctx.world,
				title: q("#ncTitle").value, subtitle: items.length + (own ? " collected" : wishMode ? " wanted" : " to go"),
				footerUrl: "sctp.nl/collection", gridAspect: isRare ? 0.86 : 1.0,
				items: items.map(function (x) {
					return isRare ? { name: x.name, imageUrl: x.texture, pixel: true }
						: { name: x.title, imageUrl: API_BASE + "/mapart/image?id=" + encodeURIComponent(x.id) + "&v=" + encodeURIComponent(x.imageHash || ""), pixel: true };
				})
			};
			var canvas = document.createElement("canvas");
			window.sctpShare.render(canvas, spec, studio ? studio.get() : {}).then(function () {
				if (my !== token) return;
				lastCanvas = canvas;
				q("#ncOut").innerHTML = ""; q("#ncOut").appendChild(canvas);
				try { q("#ncDl").href = canvas.toDataURL("image/png"); q("#ncDl").textContent = "Download PNG"; } catch (e) { q("#ncDl").removeAttribute("href"); q("#ncDl").textContent = "Can't export (image blocked)"; }
			});
		}
		if (window.sctpShare) studio = window.sctpShare.controls(q("#ncStudio"), schedule, { noLayout: true, textOverrides: false });
		q("#ncMode").addEventListener("change", function () { if (!titleTouched) q("#ncTitle").value = autoTitle(); schedule(); });
		q("#ncCopy").onclick = function () {
			var b = this;
			if (!lastCanvas) return;
			lastCanvas.toBlob(function (blob) {
				try { navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]).then(function () { b.textContent = "Copied!"; }, function () { b.textContent = "Copy failed"; }); }
				catch (e) { b.textContent = "Copy not supported"; }
			});
		};
		(document.fonts && document.fonts.load ? Promise.all([document.fonts.load("600 34px Fraunces"), document.fonts.load("600 13px Inter")]).catch(function () {}) : Promise.resolve()).then(draw);
	}

	window.sctpCollection = { mount: mount };
})();

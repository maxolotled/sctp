// Fast auction entry: search a rare and it's added as a table row straight
// away (Enter adds the top match), with its prices pre-filled from SCTP's
// suggestions and editable in the row. Shared by /auction (players) and
// /auction/admin ("add items for a player").
//
//   var entry = SctpAuctionEntry.create(rootEl, {
//     api: "https://…workers.dev",
//     getAuction: function(){ return auction; },   // {id, world, type: "dutch"|"regular"}
//     limit: function(){ return 5; },              // how many rows may be added (Infinity for admins)
//     onChange: function(){ … }                    // rows added/removed/edited
//   });
//   entry.count()      -> number of rows
//   entry.validate()   -> {error} or {items: [{rareId, startDia, minDia}]}, and marks bad rows
//   entry.clear(), entry.focus()
(function(){
	"use strict";

	var style = document.createElement("style");
	style.textContent =
		".ae-picker{position:relative;}" +
		".ae-search{width:100%;}" +
		".ae-results{position:absolute;left:0;right:0;top:calc(100% + 4px);background:var(--panel);border:1px solid var(--line);border-radius:10px;z-index:30;max-height:300px;overflow-y:auto;box-shadow:0 10px 28px rgba(0,0,0,0.45);}" +
		".ae-results button{display:flex;align-items:center;gap:10px;width:100%;background:transparent;border:none;color:var(--text);padding:7px 12px;text-align:left;cursor:pointer;font-family:inherit;font-size:14px;}" +
		".ae-results button:hover,.ae-results button.active{background:var(--panel-alt);}" +
		".ae-results .cat{margin-left:auto;font-size:11.5px;color:var(--muted);}" +
		".ae-results .none{padding:10px 12px;color:var(--muted);font-size:13px;}" +
		".ae-ico{width:30px;height:30px;flex:0 0 auto;image-rendering:pixelated;object-fit:contain;background:#0C140F;border-radius:6px;padding:2px;}" +
		".ae-wrap{overflow-x:auto;margin-top:12px;border:1px solid var(--line);border-radius:12px;}" +
		".ae-table{width:100%;border-collapse:collapse;font-size:13.5px;min-width:560px;}" +
		".ae-table th{text-align:left;font-size:11px;font-weight:600;letter-spacing:.04em;text-transform:uppercase;color:var(--muted);padding:8px 10px;border-bottom:1px solid var(--line);background:var(--panel-alt);white-space:nowrap;}" +
		".ae-table td{padding:7px 10px;border-bottom:1px solid var(--line);vertical-align:top;}" +
		".ae-table tr:last-child td{border-bottom:none;}" +
		".ae-item{display:flex;align-items:center;gap:9px;font-weight:600;min-width:150px;padding-top:2px;}" +
		".ae-val{font-size:12.5px;color:var(--shell);white-space:nowrap;padding-top:8px;}" +
		".ae-val small{display:block;color:var(--muted);font-size:11px;white-space:normal;max-width:150px;}" +
		".ae-money{display:flex;gap:4px;}" +
		".ae-money input{width:76px;min-width:0;padding:7px 8px;}" +
		".ae-money select{width:60px;padding:7px 4px;}" +
		".ae-note{font-size:11.5px;margin-top:3px;max-width:190px;}" +
		".ae-note.warn{color:var(--warn);}" +
		".ae-note.bad{color:var(--bad);font-weight:600;}" +
		".ae-x{background:transparent;border:none;color:var(--muted);font-size:19px;cursor:pointer;padding:4px 6px;line-height:1;}" +
		".ae-x:hover{color:var(--bad);}" +
		".ae-empty{color:var(--muted);font-size:13.5px;padding:14px;text-align:center;}" +
		".ae-msg{font-size:12.5px;margin:8px 0 0;min-height:1em;color:var(--warn);}";
	document.head.appendChild(style);

	var raresPromise = null;
	function loadRares(){
		if(!raresPromise) raresPromise = fetch("/data/rare-items.json").then(function(r){ return r.json(); }).catch(function(){ raresPromise = null; return []; });
		return raresPromise;
	}
	function esc(s){ return String(s == null ? "" : s).replace(/[&<>"']/g, function(c){ return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function norm(s){ return String(s || "").toLowerCase().replace(/[^a-z0-9']/g, ""); }
	function fmtDia(d){
		if(d == null) return "";
		if(Math.round(d) !== d) return d >= 9 ? (Math.round(d / 9 * 10) / 10) + " DB" : (Math.round(d * 10) / 10) + " dia";
		var db = Math.floor(d / 9), dia = d % 9;
		if(!db) return dia + " dia";
		return dia ? db + " DB " + dia + " dia" : db + " DB";
	}
	function toDia(value, unit){ var n = Number(value); if(!(n > 0)) return null; return Math.round(unit === "db" ? n * 9 : n); }
	function money(dia){
		if(dia == null) return { val: "", unit: "db" };
		if(dia >= 9) return { val: dia % 9 === 0 ? String(dia / 9) : String(Math.round(dia / 9 * 100) / 100), unit: "db" };
		return { val: String(dia), unit: "dia" };
	}

	function create(root, opts){
		var rows = [];       // {key, rare, est, start:{val,unit}, min:{val,unit}, touched}
		var seq = 0, activeIdx = -1, rares = [];
		loadRares().then(function(r){ rares = r; });

		root.innerHTML =
			'<div class="ae-picker"><input type="text" class="ae-search" placeholder="Search rare items… press Enter to add the top match" autocomplete="off" spellcheck="false" aria-label="Add a rare"><div class="ae-results" hidden></div></div>' +
			'<div class="ae-wrap"><table class="ae-table"><thead></thead><tbody></tbody></table></div>' +
			'<p class="ae-msg"></p>';
		var search = root.querySelector(".ae-search"), results = root.querySelector(".ae-results");
		var thead = root.querySelector("thead"), tbody = root.querySelector("tbody"), msg = root.querySelector(".ae-msg");

		function auction(){ return opts.getAuction() || {}; }
		function regular(){ return auction().type === "regular"; }
		function changed(){ if(opts.onChange) opts.onChange(); }

		// ---- search ----
		function hits(){
			var q = norm(search.value);
			if(!q) return [];
			return rares.filter(function(r){ return norm(r.name).indexOf(q) !== -1; })
				.sort(function(a, b){ return (norm(a.name).indexOf(q) === 0 ? 0 : 1) - (norm(b.name).indexOf(q) === 0 ? 0 : 1) || a.name.localeCompare(b.name); })
				.slice(0, 10);
		}
		function showResults(){
			var h = hits();
			activeIdx = -1;
			if(!search.value.trim()){ results.hidden = true; return; }
			results.innerHTML = h.length ? h.map(function(r){
				return '<button type="button" data-id="' + esc(r.id) + '"><img class="ae-ico" src="' + esc(r.texture || "") + '" alt="" loading="lazy">' + esc(r.name) + '<span class="cat">' + esc(r.category || "") + '</span></button>';
			}).join("") : '<div class="none">No rare matches that</div>';
			results.hidden = false;
		}
		search.addEventListener("input", showResults);
		search.addEventListener("focus", function(){ loadRares().then(function(r){ rares = r; }); });
		search.addEventListener("keydown", function(e){
			var btns = results.querySelectorAll("button");
			if(e.key === "ArrowDown" || e.key === "ArrowUp"){
				e.preventDefault();
				if(!btns.length) return;
				activeIdx = (activeIdx + (e.key === "ArrowDown" ? 1 : -1) + btns.length) % btns.length;
				btns.forEach(function(b, i){ b.classList.toggle("active", i === activeIdx); });
			} else if(e.key === "Enter"){
				e.preventDefault();
				if(btns.length) (btns[activeIdx] || btns[0]).click();
			} else if(e.key === "Escape" && !results.hidden){
				e.stopPropagation();
				results.hidden = true;
			}
		});
		results.addEventListener("click", function(e){
			var b = e.target.closest("button[data-id]");
			if(!b) return;
			var rare = rares.filter(function(r){ return r.id === b.getAttribute("data-id"); })[0];
			if(rare) add(rare);
			search.value = "";
			results.hidden = true;
			search.focus();
		});
		document.addEventListener("click", function(e){ if(!root.contains(e.target)) results.hidden = true; });

		// ---- rows ----
		function add(rare){
			var limit = opts.limit ? opts.limit() : Infinity;
			if(rows.length >= limit){
				msg.textContent = limit ? "You can enter " + limit + " item" + (limit === 1 ? "" : "s") + " in this auction." : "You've reached the item limit for this auction.";
				return;
			}
			msg.textContent = "";
			var row = { key: ++seq, rare: rare, est: null, start: { val: "", unit: "db" }, min: { val: "", unit: "db" }, touched: false };
			rows.push(row);
			render();
			changed();
			var a = auction();
			fetch(opts.api + "/auction/estimate?rareId=" + encodeURIComponent(rare.id) + "&world=" + encodeURIComponent(a.world || "Firefly"))
				.then(function(r){ return r.json(); })
				.catch(function(){ return { value: null, failed: true }; })
				.then(function(est){
					row.est = est;
					// fill in the suggestions unless the player already typed their own
					if(!row.touched && est.value != null){
						if(regular()) row.start = money(est.suggestedRegularDia);
						else { row.start = money(est.suggestedStartDia); row.min = money(est.suggestedMinDia); }
					}
					if(rows.indexOf(row) !== -1) render();
					changed();
				});
		}

		function vals(row){ return { start: toDia(row.start.val, row.start.unit), min: regular() ? toDia(row.start.val, row.start.unit) : toDia(row.min.val, row.min.unit) }; }

		// returns {bad} (blocks Continue) and/or {warn} (advice only) for one row
		function check(row){
			var v = vals(row), value = row.est && row.est.value != null ? row.est.value : null;
			var out = { start: null, min: null };
			if(regular()){
				if(v.start == null) out.start = { bad: "Set a starting price" };
				else if(value != null && v.start > value) out.start = { warn: "Above its value: may not get bids" };
				return out;
			}
			if(v.start == null) out.start = { bad: "Set a starting bid" };
			if(v.min == null) out.min = { bad: "Set a lowest limit" };
			if(v.start != null && v.min != null && v.min >= v.start) out.min = { bad: "Must be below the starting bid" };
			if(value != null){
				if(!out.start && v.start <= value) out.start = { warn: "Below its value: starts cheap" };
				if(!out.min && v.min != null && value > 1 && v.min >= value) out.min = { warn: "Above its value: may not sell" };
			}
			return out;
		}

		function noteHtml(n, showBad){
			if(!n) return "";
			if(n.bad) return showBad ? '<div class="ae-note bad">' + esc(n.bad) + '</div>' : "";
			return '<div class="ae-note warn">' + esc(n.warn) + '</div>';
		}
		function moneyHtml(field, m){
			return '<div class="ae-money"><input type="number" min="0" step="any" data-f="' + field + '" value="' + esc(m.val) + '" aria-label="' + field + '">' +
				'<select data-u="' + field + '" aria-label="' + field + ' unit"><option value="db"' + (m.unit === "db" ? " selected" : "") + '>DB</option><option value="dia"' + (m.unit === "dia" ? " selected" : "") + '>dia</option></select></div>';
		}
		function valueHtml(row){
			if(!row.est) return '<span class="ae-val">…</span>';
			if(row.est.value == null) return '<div class="ae-val">–<small>' + (row.est.failed ? "couldn't load" : "no price data") + '</small></div>';
			return '<div class="ae-val">≈ ' + esc(fmtDia(row.est.value)) + '<small>' + esc(row.est.basis || "") + '</small></div>';
		}

		var showBadFor = {}; // row keys that should show their errors (after a Continue attempt)
		function rowHtml(row){
			var c = check(row);
			return '<tr data-k="' + row.key + '">' +
				'<td><div class="ae-item"><img class="ae-ico" src="' + esc(row.rare.texture || "") + '" alt="">' + esc(row.rare.name) + '</div></td>' +
				'<td>' + valueHtml(row) + '</td>' +
				'<td>' + moneyHtml("start", row.start) + '<div data-n="start">' + noteHtml(c.start, showBadFor[row.key]) + '</div></td>' +
				(regular() ? '' : '<td>' + moneyHtml("min", row.min) + '<div data-n="min">' + noteHtml(c.min, showBadFor[row.key]) + '</div></td>') +
				'<td style="width:1%;"><button type="button" class="ae-x" data-rm="' + row.key + '" title="Remove" aria-label="Remove ' + esc(row.rare.name) + '">&times;</button></td></tr>';
		}
		function render(){
			thead.innerHTML = '<tr><th>Item</th><th>SCTP value</th><th>' + (regular() ? "Starting price" : "Starting bid") + '</th>' + (regular() ? '' : '<th>Lowest limit</th>') + '<th></th></tr>';
			tbody.innerHTML = rows.length ? rows.map(rowHtml).join("") :
				'<tr><td colspan="5"><div class="ae-empty">Nothing yet. Search above: each rare you pick shows up here with suggested prices.</div></td></tr>';
		}
		function rowOf(el){ var tr = el.closest("tr[data-k]"); if(!tr) return null; var k = Number(tr.getAttribute("data-k")); return rows.filter(function(r){ return r.key === k; })[0] || null; }
		function refreshNotes(tr, row){
			var c = check(row);
			var s = tr.querySelector('[data-n="start"]'), m = tr.querySelector('[data-n="min"]');
			if(s) s.innerHTML = noteHtml(c.start, showBadFor[row.key]);
			if(m) m.innerHTML = noteHtml(c.min, showBadFor[row.key]);
		}
		tbody.addEventListener("input", function(e){
			var row = rowOf(e.target); if(!row) return;
			var f = e.target.getAttribute("data-f"), u = e.target.getAttribute("data-u");
			if(f){ row[f].val = e.target.value; row.touched = true; }
			if(u){ row[u].unit = e.target.value; row.touched = true; }
			refreshNotes(e.target.closest("tr"), row);
			changed();
		});
		tbody.addEventListener("change", function(e){ if(e.target.getAttribute("data-u")){ var row = rowOf(e.target); if(row){ row[e.target.getAttribute("data-u")].unit = e.target.value; refreshNotes(e.target.closest("tr"), row); changed(); } } });
		tbody.addEventListener("click", function(e){
			var b = e.target.closest("[data-rm]"); if(!b) return;
			var k = Number(b.getAttribute("data-rm"));
			rows = rows.filter(function(r){ return r.key !== k; });
			msg.textContent = "";
			render();
			changed();
		});
		render();

		return {
			count: function(){ return rows.length; },
			clear: function(){ rows = []; showBadFor = {}; msg.textContent = ""; render(); changed(); },
			focus: function(){ search.focus(); },
			rerender: render,
			validate: function(){
				var firstBad = null;
				rows.forEach(function(row){
					var c = check(row);
					if((c.start && c.start.bad) || (c.min && c.min.bad)){
						showBadFor[row.key] = true;
						if(!firstBad) firstBad = row.rare.name + ": " + ((c.start && c.start.bad) || c.min.bad).toLowerCase();
					}
				});
				render();
				if(!rows.length) return { error: "Add at least one item first." };
				if(firstBad) return { error: "Fix the prices marked in red (" + firstBad + ")." };
				return { items: rows.map(function(row){ var v = vals(row); return { rareId: row.rare.id, startDia: v.start, minDia: v.min }; }) };
			},
		};
	}

	window.SctpAuctionEntry = { create: create, fmtDia: fmtDia };
})();

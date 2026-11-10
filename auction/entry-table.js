// Fast auction entry: search a rare and it's added as a table row straight
// away (Enter adds the top match), with its prices pre-filled from SCTP's
// suggestions and editable in the row. An item that isn't in the catalog can
// be added by name, with no picture, another item's picture or an uploaded one.
// Shared by /auction (players) and /auction/admin ("add items for a player").
//
//   var entry = SctpAuctionEntry.create(rootEl, {
//     api: "https://…workers.dev",
//     getAuction: function(){ return auction; },   // {id, world, type: "dutch"|"regular"}
//     limit: function(){ return 5; },              // how many rows may be added (Infinity for admins)
//     onChange: function(){ … }                    // rows added/removed/edited
//   });
//   entry.count()      -> number of rows
//   entry.validate()   -> {error} or {items: [{rareId | custom: {name, texture?, image?}, quantity, startDia, minDia, hostPicks}]}, and marks bad rows
//                         (a row is a lot: quantity of the same rare, priced as a whole)
//                         (hostPicks rows: "let the host pick" is ticked, no prices sent)
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
		".ae-money select{width:64px;padding:7px 4px;}" +
		".ae-note{font-size:11.5px;margin-top:3px;max-width:190px;}" +
		".ae-note.warn{color:var(--warn);}" +
		".ae-note.bad{color:var(--bad);font-weight:600;}" +
		".ae-host{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:var(--muted);margin-top:5px;cursor:pointer;user-select:none;white-space:nowrap;}" +
		".ae-host input{width:auto;margin:0;accent-color:var(--accent);cursor:pointer;}" +
		".ae-host.on{color:var(--accent);}" +
		".ae-hostnote{font-size:12px;color:var(--muted);padding-top:8px;max-width:190px;}" +
		".ae-qty{width:54px;min-width:0;padding:7px 6px;}" +
		".ae-x{background:transparent;border:none;color:var(--muted);font-size:19px;cursor:pointer;padding:4px 6px;line-height:1;}" +
		".ae-x:hover{color:var(--bad);}" +
		".ae-empty{color:var(--muted);font-size:13.5px;padding:14px;text-align:center;}" +
		".ae-link{background:transparent;border:none;color:var(--accent);font:inherit;font-size:13px;padding:6px 0 0;cursor:pointer;text-decoration:underline;}" +
		".ae-custom{margin-top:10px;padding:12px;border:1px solid var(--line);border-radius:12px;background:var(--panel-alt);display:flex;flex-direction:column;gap:10px;}" +
		".ae-custom label.ae-lb,.ae-custom .ae-lb{display:block;font-size:12.5px;font-weight:600;margin-bottom:4px;}" +
		".ae-custom input[type=text]{width:100%;}" +
		".ae-pics{display:flex;flex-wrap:wrap;gap:6px 16px;}" +
		".ae-pics label{display:inline-flex;align-items:center;gap:6px;font-size:13px;cursor:pointer;}" +
		".ae-pics input{width:auto;margin:0;accent-color:var(--accent);}" +
		".ae-texpick{position:relative;}" +
		".ae-chosen{display:flex;align-items:center;gap:9px;font-size:13px;}" +
		".ae-cbtns{display:flex;flex-wrap:wrap;align-items:center;gap:8px;}" +
		".ae-cerr{color:var(--bad);font-size:12.5px;font-weight:600;margin:0;}" +
		".ae-msg{font-size:12.5px;margin:8px 0 0;min-height:1em;color:var(--warn);}";
	document.head.appendChild(style);

	var raresPromise = null;
	function loadRares(){
		if(!raresPromise) raresPromise = fetch("/data/rare-items.json").then(function(r){ return r.json(); }).catch(function(){ raresPromise = null; return []; });
		return raresPromise;
	}
	// The items whose picture can be borrowed for an own item: every rare and vanilla item that has a real one.
	var picturesPromise = null;
	function loadPictures(){
		if(!picturesPromise) picturesPromise = Promise.all([
			loadRares(),
			fetch("/data/vanilla-items.json").then(function(r){ return r.json(); }).catch(function(){ picturesPromise = null; return []; })
		]).then(function(res){
			return res[0].concat(res[1]).filter(function(i){ return /\.png$/.test(i.texture || ""); });
		});
		return picturesPromise;
	}
	// An uploaded picture, shrunk to fit 128x128 and turned into a PNG, as a data: URL.
	var PICTURE_SIDE = 128;
	function pictureToPng(file){
		return new Promise(function(resolve, reject){
			var url = URL.createObjectURL(file), img = new Image();
			img.onload = function(){
				URL.revokeObjectURL(url);
				var w = img.naturalWidth, h = img.naturalHeight;
				if(!w || !h){ reject(); return; }
				var scale = Math.min(1, PICTURE_SIDE / Math.max(w, h));
				var c = document.createElement("canvas");
				c.width = Math.max(1, Math.round(w * scale));
				c.height = Math.max(1, Math.round(h * scale));
				var ctx = c.getContext("2d");
				ctx.imageSmoothingEnabled = scale < 1; // small textures stay crisp
				ctx.drawImage(img, 0, 0, c.width, c.height);
				resolve(c.toDataURL("image/png"));
			};
			img.onerror = function(){ URL.revokeObjectURL(url); reject(); };
			img.src = url;
		});
	}
	function esc(s){ return String(s == null ? "" : s).replace(/[&<>"']/g, function(c){ return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function norm(s){ return String(s || "").toLowerCase().replace(/[^a-z0-9']/g, ""); }
	function fmtDia(d){
		if(d == null) return "";
		if(Math.round(d) !== d) return d >= 576 ? (Math.round(d / 576 * 10) / 10) + " STX" : d >= 9 ? (Math.round(d / 9 * 10) / 10) + " DB" : (Math.round(d * 10) / 10) + " dia";
		// 1 STX = 64 DB = 576 dia
		var stx = Math.floor(d / 576), db = Math.floor((d % 576) / 9), dia = d % 9, parts = [];
		if(stx) parts.push(stx + " STX");
		if(db) parts.push(db + " DB");
		if(dia || !parts.length) parts.push(dia + " dia");
		return parts.join(" ");
	}
	function toDia(value, unit){ var n = Number(value); if(!(n > 0)) return null; return Math.round(unit === "stx" ? n * 576 : unit === "db" ? n * 9 : n); }
	function money(dia){
		if(dia == null) return { val: "", unit: "db" };
		if(dia >= 576 && dia % 576 === 0) return { val: String(dia / 576), unit: "stx" };
		if(dia >= 9) return { val: dia % 9 === 0 ? String(dia / 9) : String(Math.round(dia / 9 * 100) / 100), unit: "db" };
		return { val: String(dia), unit: "dia" };
	}

	var pickerSeq = 0; // keeps each table's radio group apart

	function create(root, opts){
		var rows = [];       // {key, rare, est, start:{val,unit}, min:{val,unit}, touched}
		var seq = 0, activeIdx = -1, rares = [];
		loadRares().then(function(r){ rares = r; });

		root.innerHTML =
			'<div class="ae-picker"><input type="text" class="ae-search" placeholder="Search rare items… press Enter to add the top match" autocomplete="off" spellcheck="false" aria-label="Add a rare"><div class="ae-results" hidden></div></div>' +
			'<button type="button" class="ae-link" data-open-custom>Item not listed? Add your own</button>' +
			'<div class="ae-custom" hidden>' +
				'<div><label class="ae-lb">Item name<input type="text" class="ae-cname" maxlength="60" autocomplete="off" spellcheck="false"></label></div>' +
				'<div><div class="ae-lb">Picture</div><div class="ae-pics" role="radiogroup" aria-label="Picture">' +
					'<label><input type="radio" name="aePic' + (++pickerSeq) + '" value="none" checked>No picture</label>' +
					'<label><input type="radio" name="aePic' + pickerSeq + '" value="item">Another item\'s picture</label>' +
					'<label><input type="radio" name="aePic' + pickerSeq + '" value="upload">Upload a picture</label>' +
				'</div></div>' +
				'<div class="ae-texpick" hidden><input type="text" class="ae-texsearch" placeholder="Search any item…" autocomplete="off" spellcheck="false" aria-label="Find an item to borrow its picture"><div class="ae-results ae-texresults" hidden></div></div>' +
				'<div class="ae-upload" hidden><input type="file" class="ae-file" accept="image/*" aria-label="Your picture"></div>' +
				'<div class="ae-chosen" hidden></div>' +
				'<p class="ae-cerr" hidden></p>' +
				'<div class="ae-cbtns"><button type="button" class="btn" data-custom-add>Add item</button><button type="button" class="btn-ghost" data-custom-cancel>Cancel</button></div>' +
			'</div>' +
			'<div class="ae-wrap"><table class="ae-table"><thead></thead><tbody></tbody></table></div>' +
			'<p class="ae-msg"></p>';
		var search = root.querySelector(".ae-search"), results = root.querySelector(".ae-results");
		var thead = root.querySelector("thead"), tbody = root.querySelector("tbody"), msg = root.querySelector(".ae-msg");
		var customBox = root.querySelector(".ae-custom"), customOpen = root.querySelector("[data-open-custom]");
		var cname = root.querySelector(".ae-cname"), cerr = root.querySelector(".ae-cerr"), chosen = root.querySelector(".ae-chosen");
		var texPick = root.querySelector(".ae-texpick"), texSearch = root.querySelector(".ae-texsearch"), texResults = root.querySelector(".ae-texresults");
		var upload = root.querySelector(".ae-upload"), file = root.querySelector(".ae-file");
		var picture = null; // the own item's picture so far: {texture} (another item's) or {dataUrl} (uploaded)

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
			}).join("") : '<div class="none">No rare matches that</div><button type="button" data-own>Add "' + esc(search.value.trim()) + '" as your own item</button>';
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
			if(e.target.closest("button[data-own]")){
				openCustom(search.value.trim());
				search.value = "";
				results.hidden = true;
				return;
			}
			var b = e.target.closest("button[data-id]");
			if(!b) return;
			var rare = rares.filter(function(r){ return r.id === b.getAttribute("data-id"); })[0];
			if(rare) add(rare);
			search.value = "";
			results.hidden = true;
			search.focus();
		});
		document.addEventListener("click", function(e){
			if(!root.contains(e.target)) results.hidden = true;
			if(!texPick.contains(e.target)) texResults.hidden = true;
		});

		// ---- your own item ----
		function picMode(){ return customBox.querySelector("input[type=radio]:checked").value; }
		function showChosen(){
			var src = picture && (picture.dataUrl || picture.texture);
			chosen.hidden = !src;
			chosen.innerHTML = src ? '<img class="ae-ico" src="' + esc(src) + '" alt="">' + esc(picture.label) : "";
		}
		function setPicture(p){ picture = p; cerr.hidden = true; showChosen(); }
		function resetCustom(){
			cname.value = "";
			customBox.querySelector('input[value="none"]').checked = true;
			texSearch.value = "";
			texResults.hidden = true;
			file.value = "";
			cerr.hidden = true;
			syncPicMode();
		}
		function syncPicMode(){
			var mode = picMode();
			texPick.hidden = mode !== "item";
			upload.hidden = mode !== "upload";
			setPicture(null);
			texSearch.value = "";
			file.value = "";
		}
		function openCustom(name){
			customBox.hidden = false;
			customOpen.hidden = true;
			if(name) cname.value = name;
			cname.focus();
		}
		function closeCustom(){
			resetCustom();
			customBox.hidden = true;
			customOpen.hidden = false;
		}
		function customError(text){ cerr.textContent = text; cerr.hidden = false; }
		function addCustom(){
			var name = cname.value.replace(/\s+/g, " ").trim();
			if(norm(name).length < 2){ customError("Give your item a name."); cname.focus(); return; }
			var known = rares.filter(function(r){ return norm(r.name) === norm(name); })[0];
			if(known){ customError(known.name + " is already a rare: pick it from the search instead."); return; }
			var mode = picMode();
			if(mode !== "none" && !picture){ customError(mode === "item" ? "Pick an item, or choose No picture." : "Choose a picture, or choose No picture."); return; }
			var custom = { name: name };
			if(picture && picture.texture) custom.texture = picture.texture;
			if(picture && picture.dataUrl) custom.image = picture.dataUrl.split(",")[1];
			if(add({ custom: custom, name: name, texture: picture ? picture.dataUrl || picture.texture : "" })) closeCustom();
		}
		customOpen.addEventListener("click", function(){ openCustom(""); });
		root.querySelector("[data-custom-cancel]").addEventListener("click", function(){ msg.textContent = ""; closeCustom(); });
		root.querySelector("[data-custom-add]").addEventListener("click", addCustom);
		customBox.querySelector(".ae-pics").addEventListener("change", function(){
			syncPicMode();
			if(picMode() === "item") texSearch.focus();
		});
		cname.addEventListener("keydown", function(e){ if(e.key === "Enter"){ e.preventDefault(); addCustom(); } });
		function showTexResults(){
			var q = norm(texSearch.value);
			if(!q){ texResults.hidden = true; return; }
			loadPictures().then(function(all){
				if(norm(texSearch.value) !== q) return;
				var h = all.filter(function(i){ return norm(i.name).indexOf(q) !== -1; })
					.sort(function(a, b){ return (norm(a.name).indexOf(q) === 0 ? 0 : 1) - (norm(b.name).indexOf(q) === 0 ? 0 : 1) || a.name.localeCompare(b.name); })
					.slice(0, 10);
				texResults.innerHTML = h.length ? h.map(function(i){
					return '<button type="button" data-tex="' + esc(i.texture) + '" data-name="' + esc(i.name) + '"><img class="ae-ico" src="' + esc(i.texture) + '" alt="" loading="lazy">' + esc(i.name) + '</button>';
				}).join("") : '<div class="none">No item matches that</div>';
				texResults.hidden = false;
			});
		}
		texSearch.addEventListener("input", showTexResults);
		texSearch.addEventListener("keydown", function(e){
			if(e.key === "Enter"){
				e.preventDefault();
				var first = texResults.querySelector("button");
				if(first) first.click();
			} else if(e.key === "Escape" && !texResults.hidden){
				e.stopPropagation();
				texResults.hidden = true;
			}
		});
		texResults.addEventListener("click", function(e){
			var b = e.target.closest("button[data-tex]");
			if(!b) return;
			setPicture({ texture: b.getAttribute("data-tex"), label: "Picture of " + b.getAttribute("data-name") });
			texSearch.value = "";
			texResults.hidden = true;
		});
		file.addEventListener("change", function(){
			var chosenFile = file.files[0];
			setPicture(null);
			if(!chosenFile) return;
			pictureToPng(chosenFile).then(function(dataUrl){
				setPicture({ dataUrl: dataUrl, label: chosenFile.name });
			}).catch(function(){
				file.value = "";
				customError("That file couldn't be read as a picture. Try a PNG or JPG.");
			});
		});

		// ---- rows ----
		function add(rare){
			var limit = opts.limit ? opts.limit() : Infinity;
			if(rows.length >= limit){
				msg.textContent = limit ? "You can enter " + limit + " item" + (limit === 1 ? "" : "s") + " in this auction." : "You've reached the item limit for this auction.";
				return false;
			}
			msg.textContent = "";
			var row = { key: ++seq, rare: rare, est: null, start: { val: "", unit: "db" }, min: { val: "", unit: "db" }, touched: false, host: false, qty: 1 };
			rows.push(row);
			if(rare.custom) row.est = { value: null, own: true }; // SCTP has no value for it
			render();
			changed();
			if(rare.custom) return true;
			var a = auction();
			fetch(opts.api + "/auction/estimate?rareId=" + encodeURIComponent(rare.id) + "&world=" + encodeURIComponent(a.world || "Firefly"))
				.then(function(r){ return r.json(); })
				.catch(function(){ return { value: null, failed: true }; })
				.then(function(est){
					row.est = est;
					// fill in the suggestions unless the player already typed their own
					if(!row.touched) suggest(row);
					if(rows.indexOf(row) !== -1) render();
					changed();
				});
			return true;
		}

		// fill in SCTP's suggested prices for the whole lot
		function suggest(row){
			var est = row.est;
			if(!est || est.value == null) return;
			if(regular()) row.start = money(est.suggestedRegularDia * row.qty);
			else { row.start = money(est.suggestedStartDia * row.qty); row.min = money(est.suggestedMinDia * row.qty); }
		}
		function lotValue(row){ return row.est && row.est.value != null ? row.est.value * row.qty : null; }

		function vals(row){ return { start: toDia(row.start.val, row.start.unit), min: regular() ? toDia(row.start.val, row.start.unit) : toDia(row.min.val, row.min.unit) }; }

		// returns {bad} (blocks Continue) and/or {warn} (advice only) for one row
		function check(row){
			var v = vals(row), value = lotValue(row);
			var out = { start: null, min: null };
			if(row.host) return out;
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
				'<select data-u="' + field + '" aria-label="' + field + ' unit"><option value="db"' + (m.unit === "db" ? " selected" : "") + '>DB</option><option value="stx"' + (m.unit === "stx" ? " selected" : "") + '>STX</option><option value="dia"' + (m.unit === "dia" ? " selected" : "") + '>dia</option></select></div>';
		}
		function valueHtml(row){
			if(!row.est) return '<span class="ae-val">…</span>';
			if(row.est.value == null) return '<div class="ae-val">–<small>' + (row.est.own ? "your own item" : row.est.failed ? "couldn't load" : "no price data") + '</small></div>';
			if(row.qty > 1) return '<div class="ae-val">≈ ' + esc(fmtDia(lotValue(row))) + '<small>' + esc(fmtDia(row.est.value)) + ' each · ' + esc(row.est.basis || "") + '</small></div>';
			return '<div class="ae-val">≈ ' + esc(fmtDia(row.est.value)) + '<small>' + esc(row.est.basis || "") + '</small></div>';
		}

		var showBadFor = {}; // row keys that should show their errors (after a Continue attempt)
		function rowHtml(row){
			var c = check(row);
			return '<tr data-k="' + row.key + '">' +
				'<td><div class="ae-item"><img class="ae-ico" src="' + esc(row.rare.texture || "/items/textures/_placeholder.svg") + '" alt="">' + esc(row.rare.name) + '</div></td>' +
				'<td><input type="number" class="ae-qty" min="1" max="999" step="1" data-qty value="' + row.qty + '" aria-label="How many ' + esc(row.rare.name) + '" title="How many in this lot (priced together)"></td>' +
				'<td>' + valueHtml(row) + '</td>' +
				'<td>' + (row.host ? '<div class="ae-hostnote">The host sets the price' + (regular() ? '' : 's') + '</div>' : moneyHtml("start", row.start) + '<div data-n="start">' + noteHtml(c.start, showBadFor[row.key]) + '</div>') +
					'<label class="ae-host' + (row.host ? ' on' : '') + '"><input type="checkbox" data-host' + (row.host ? ' checked' : '') + '>Let the host pick</label></td>' +
				(regular() ? '' : '<td>' + (row.host ? '<div class="ae-hostnote">–</div>' : moneyHtml("min", row.min) + '<div data-n="min">' + noteHtml(c.min, showBadFor[row.key]) + '</div>') + '</td>') +
				'<td style="width:1%;"><button type="button" class="ae-x" data-rm="' + row.key + '" title="Remove" aria-label="Remove ' + esc(row.rare.name) + '">&times;</button></td></tr>';
		}
		function render(){
			thead.innerHTML = '<tr><th>Item</th><th title="How many in this lot. Prices are for the whole lot.">Qty</th><th>SCTP value</th><th>' + (regular() ? "Starting price" : "Starting bid") + '</th>' + (regular() ? '' : '<th>Lowest limit</th>') + '<th></th></tr>';
			tbody.innerHTML = rows.length ? rows.map(rowHtml).join("") :
				'<tr><td colspan="6"><div class="ae-empty">Nothing yet. Search above: each rare you pick shows up here with suggested prices.</div></td></tr>';
		}
		function rowOf(el){ var tr = el.closest("tr[data-k]"); if(!tr) return null; var k = Number(tr.getAttribute("data-k")); return rows.filter(function(r){ return r.key === k; })[0] || null; }
		function refreshNotes(tr, row){
			var c = check(row);
			var s = tr.querySelector('[data-n="start"]'), m = tr.querySelector('[data-n="min"]');
			if(s) s.innerHTML = noteHtml(c.start, showBadFor[row.key]);
			if(m) m.innerHTML = noteHtml(c.min, showBadFor[row.key]);
		}
		tbody.addEventListener("input", function(e){
			var row = rowOf(e.target); if(!row || e.target.hasAttribute("data-host")) return;
			if(e.target.hasAttribute("data-qty")){
				var q = Math.round(Number(e.target.value));
				if(!(q >= 1)) return; // mid-typing (empty): keep the last good amount
				row.qty = Math.min(999, q);
				if(!row.touched) suggest(row);
				var qtr = e.target.closest("tr"), qtmp = document.createElement("tbody");
				qtmp.innerHTML = rowHtml(row);
				qtr.replaceWith(qtmp.firstChild);
				var qin = tbody.querySelector('tr[data-k="' + row.key + '"] [data-qty]');
				if(qin){ qin.focus(); try { qin.setSelectionRange(qin.value.length, qin.value.length); } catch(err){} }
				changed();
				return;
			}
			var f = e.target.getAttribute("data-f"), u = e.target.getAttribute("data-u");
			if(f){ row[f].val = e.target.value; row.touched = true; }
			if(u){ row[u].unit = e.target.value; row.touched = true; }
			refreshNotes(e.target.closest("tr"), row);
			changed();
		});
		tbody.addEventListener("change", function(e){
			if(e.target.hasAttribute("data-host")){
				var hr = rowOf(e.target); if(!hr) return;
				hr.host = e.target.checked;
				var tr = e.target.closest("tr"), tmp = document.createElement("tbody");
				tmp.innerHTML = rowHtml(hr);
				tr.replaceWith(tmp.firstChild);
				var cb = tbody.querySelector('tr[data-k="' + hr.key + '"] [data-host]');
				if(cb) cb.focus();
				changed();
				return;
			}
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
			clear: function(){ rows = []; showBadFor = {}; msg.textContent = ""; closeCustom(); render(); changed(); },
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
				return { items: rows.map(function(row){
					var what = row.rare.custom ? { custom: row.rare.custom, quantity: row.qty } : { rareId: row.rare.id, quantity: row.qty };
					if(row.host){ what.hostPicks = true; return what; }
					var v = vals(row);
					what.startDia = v.start;
					what.minDia = v.min;
					return what;
				}) };
			},
		};
	}

	window.SctpAuctionEntry = { create: create, fmtDia: fmtDia };
})();

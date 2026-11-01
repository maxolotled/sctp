// Little "I own this" toggles (and a wishlist star) for rare items and mapart, shared by the item
// library, the mapart catalog and the two detail pages (the full checklist
// lives in collection.js on /collection/). Only shown to logged-in accounts.
//
//   sctpCol.circle(kind, id, world)        -> HTML of a card's round top-right toggle plus its top-left wishlist star
//   sctpCol.chip(kind, id, world, label?)  -> HTML of a labelled toggle for detail pages
//   sctpCol.bind(root)                     -> makes the toggles inside root clickable (call once per container)
//   sctpCol.paint(root)                    -> re-syncs every toggle inside root with the owned set
//   sctpCol.world() / setWorld(w)          -> the world the item-library cards collect for (remembered)
//   sctpCol.worldPicker(host, onChange)    -> "Collecting in: Firefly | Honeybee" pills
//   sctpCol.ready(cb)                      -> cb() once the owned set is loaded (or right away when logged out)
//
// kind is "rare" | "mapart"; ids are the rare-items.json id / the mapart id.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var WORLD_KEY = "sctp_col_world";

	var style = document.createElement("style");
	style.textContent =
		".col-tg{position:absolute;top:8px;right:8px;z-index:3;width:24px;height:24px;border-radius:50%;padding:0;border:1px solid var(--line,#33453A);background:rgba(27,42,32,0.92);color:transparent;font-size:13px;line-height:22px;text-align:center;cursor:pointer;font-family:inherit;}" +
		".col-tg:hover{border-color:var(--accent-dim,#87AE29);}" +
		".col-tg.on{background:var(--accent,#B7E23D);border-color:transparent;color:var(--accent-ink,#16210F);font-weight:700;}" +
		".col-tg:disabled{opacity:.6;cursor:default;}" +
		".col-st{position:absolute;top:8px;left:8px;z-index:3;width:24px;height:24px;border-radius:50%;padding:0;border:1px solid var(--line,#33453A);background:rgba(27,42,32,0.92);color:var(--muted,#8FA593);font-size:13px;line-height:22px;text-align:center;cursor:pointer;font-family:inherit;}" +
		".col-st:hover{border-color:#E8B93A;color:#E8B93A;}" +
		".col-st.on{background:#E8B93A;border-color:transparent;color:#2A1F05;}" +
		".col-st:disabled{opacity:.6;cursor:default;}" +
		".col-chip{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:999px;padding:5px 12px;font-size:12.5px;font-weight:600;cursor:pointer;font-family:inherit;}" +
		".col-chip:hover{border-color:var(--accent-dim,#87AE29);}" +
		".col-chip.on{background:var(--accent,#B7E23D);border-color:transparent;color:var(--accent-ink,#16210F);}" +
		".col-chip:disabled{opacity:.6;cursor:default;}" +
		".col-wp{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:13px;color:var(--muted,#8FA593);margin:0 0 12px;}" +
		".col-wp button{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--muted,#8FA593);border-radius:999px;padding:4px 13px;font-size:12.5px;font-weight:600;cursor:pointer;font-family:inherit;}" +
		".col-wp button.on{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;}" +
		".col-wp a{color:var(--accent,#B7E23D);margin-left:6px;}";
	document.head.appendChild(style);

	var owned = {};          // "kind|world|id" -> true
	var wished = {};         // "kind|world|id" -> true (the wishlist)
	var loaded = false, loading = false, waiting = [];
	var watchers = [];       // roots to repaint whenever the set changes

	function session() { return window.sctpAccount ? window.sctpAccount.getSession() : null; }
	function key(kind, world, id) { return kind + "|" + world + "|" + id; }
	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	function world() {
		try { var w = localStorage.getItem(WORLD_KEY); if (w === "Firefly" || w === "Honeybee") return w; } catch (e) { /* private mode */ }
		return "Firefly";
	}
	function setWorld(w) { try { localStorage.setItem(WORLD_KEY, w); } catch (e) { /* ignore */ } }

	function load() {
		var s = session();
		if (!s) { owned = {}; loaded = true; flush(); return; }
		if (loading) return;
		loading = true;
		fetch(API_BASE + "/collection/mine", { headers: { Authorization: "Bearer " + s.token } })
			.then(function (r) { return r.ok ? r.json() : { items: [] }; })
			.catch(function () { return { items: [] }; })
			.then(function (d) {
				owned = {}; wished = {};
				(d.items || []).forEach(function (i) { owned[key(i.kind, i.world, i.itemId)] = true; });
				(d.wishes || []).forEach(function (i) { wished[key(i.kind, i.world, i.itemId)] = true; });
				loaded = true; loading = false; flush();
			});
	}
	function flush() {
		var cbs = waiting; waiting = [];
		cbs.forEach(function (cb) { try { cb(); } catch (e) { /* keep going */ } });
		watchers.forEach(paint);
	}
	function ready(cb) {
		if (loaded) cb(); else { waiting.push(cb); load(); }
	}

	function has(kind, id, w) { return !!owned[key(kind, w, id)]; }
	function isWished(kind, id, w) { return !!wished[key(kind, w, id)]; }

	// list: "own" (the collection) or "wish" (the wishlist), saved the same way as on /collection/
	function toggle(kind, id, w, list) {
		var s = session();
		if (!s) return Promise.resolve(false);
		var set = list === "wish" ? wished : owned;
		var k = key(kind, w, id), want = !set[k];
		if (want) set[k] = true; else delete set[k];
		watchers.forEach(paint);
		var body = { kind: kind, world: w, ids: [id], owned: want };
		if (list === "wish") body.list = "wish";
		return fetch(API_BASE + "/collection/set", {
			method: "POST",
			headers: { "Content-Type": "application/json", Authorization: "Bearer " + s.token },
			body: JSON.stringify(body),
		}).then(function (r) { return r.ok; }).catch(function () { return false; }).then(function (ok) {
			if (!ok) { if (want) delete set[k]; else set[k] = true; watchers.forEach(paint); }
			return ok;
		});
	}

	function attrs(kind, id, w, label) {
		return ' data-ckind="' + esc(kind) + '" data-cid="' + esc(id) + '" data-cworld="' + esc(w) + '"' + (label ? ' data-clabel="' + esc(label) + '"' : "");
	}
	function circle(kind, id, w) {
		if (!session()) return "";
		var on = has(kind, id, w), star = isWished(kind, id, w);
		return '<button type="button" class="col-tg' + (on ? " on" : "") + '"' + attrs(kind, id, w) +
			' title="' + (on ? "In your " + esc(w) + " collection — click to remove" : "Add to your " + esc(w) + " collection") + '">&#10003;</button>' +
			'<button type="button" class="col-st' + (star ? " on" : "") + '" data-cwish="1"' + attrs(kind, id, w) +
			' title="' + (star ? "On your " + esc(w) + " wishlist — click to remove" : "Add to your " + esc(w) + " wishlist") + '">' + (star ? "&#9733;" : "&#9734;") + "</button>";
	}
	function chip(kind, id, w, label) {
		if (!session()) return "";
		var on = has(kind, id, w);
		return '<button type="button" class="col-chip' + (on ? " on" : "") + '"' + attrs(kind, id, w, label || w) + ">" + (on ? "&#10003; " : "+ ") + esc(label || w) + "</button>";
	}

	function paint(root) {
		if (!root || !root.querySelectorAll) return;
		root.querySelectorAll("[data-ckind]").forEach(function (b) {
			if (b.hasAttribute("data-cwish")) {
				var star = isWished(b.getAttribute("data-ckind"), b.getAttribute("data-cid"), b.getAttribute("data-cworld"));
				b.classList.toggle("on", star);
				b.innerHTML = star ? "&#9733;" : "&#9734;";
				b.title = star ? "On your " + b.getAttribute("data-cworld") + " wishlist — click to remove" : "Add to your " + b.getAttribute("data-cworld") + " wishlist";
				return;
			}
			var on = has(b.getAttribute("data-ckind"), b.getAttribute("data-cid"), b.getAttribute("data-cworld"));
			b.classList.toggle("on", on);
			var label = b.getAttribute("data-clabel");
			if (label) b.innerHTML = (on ? "&#10003; " : "+ ") + esc(label);
			else b.title = on ? "In your " + b.getAttribute("data-cworld") + " collection — click to remove" : "Add to your " + b.getAttribute("data-cworld") + " collection";
		});
	}

	// One delegated listener per container; the button swallows the click so a
	// card that is itself a link doesn't navigate.
	function bind(root) {
		if (watchers.indexOf(root) === -1) watchers.push(root);
		root.addEventListener("click", function (e) {
			var b = e.target.closest ? e.target.closest("[data-ckind]") : null;
			if (!b || !root.contains(b)) return;
			e.preventDefault(); e.stopPropagation();
			b.disabled = true;
			toggle(b.getAttribute("data-ckind"), b.getAttribute("data-cid"), b.getAttribute("data-cworld"), b.hasAttribute("data-cwish") ? "wish" : "own").then(function () { b.disabled = false; });
		});
	}

	function worldPicker(host, onChange) {
		function draw() {
			if (!session()) { host.innerHTML = ""; return; }
			var w = world();
			host.innerHTML = '<div class="col-wp"><span>Ticking items off for:</span>' +
				["Firefly", "Honeybee"].map(function (x) { return '<button type="button" data-pw="' + x + '" class="' + (x === w ? "on" : "") + '">' + x + "</button>"; }).join("") +
				'<a href="/collection/">My collection &rarr;</a></div>';
			host.querySelectorAll("[data-pw]").forEach(function (b) {
				b.onclick = function () { setWorld(b.getAttribute("data-pw")); draw(); if (onChange) onChange(); };
			});
		}
		draw();
		return draw;
	}

	window.sctpCol = { circle: circle, chip: chip, bind: bind, paint: paint, world: world, setWorld: setWorld, worldPicker: worldPicker, ready: ready, has: has, isWished: isWished,
		// Pages call this after login/logout to refetch the owned set.
		reload: function () { loaded = false; loading = false; owned = {}; wished = {}; load(); } };
})();

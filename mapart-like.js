// "Like" hearts for mapart, shared by the mapart catalog, the mapart pages
// and the home page's mapart of the day. Everyone sees the counts; liking
// needs an account (one like per piece).
//
//   sctpLike.button(m, big?)  -> HTML of a heart button with the count (m = a mapart from the API)
//   sctpLike.ready(cb)        -> cb() once your own likes are loaded (right away when logged out)
//   sctpLike.has(id)          -> whether you've liked that piece
//   sctpLike.count(id, n?)    -> the current count (the API's number, adjusted by your clicks)
//
// Clicks are handled with one document-level listener, so buttons can live
// inside card links and be re-rendered freely.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";

	var style = document.createElement("style");
	style.textContent =
		".like-btn{display:inline-flex;align-items:center;gap:5px;background:rgba(27,42,32,0.92);border:1px solid var(--line,#33453A);color:var(--muted,#8FA593);border-radius:999px;padding:3px 10px 3px 8px;font-size:12.5px;font-weight:700;cursor:pointer;font-family:inherit;line-height:1.4;}" +
		".like-btn:hover{border-color:#E2647A;color:var(--text,#EAEFE7);}" +
		".like-btn .hrt{font-size:13px;line-height:1;color:inherit;}" +
		".like-btn.on{background:rgba(226,100,122,0.16);border-color:#E2647A;color:#F08A9C;}" +
		".like-btn.on .hrt{color:#F0647E;}" +
		".like-btn.big{padding:6px 14px 6px 12px;font-size:14px;}" +
		".like-btn.big .hrt{font-size:16px;}" +
		".like-btn.pop .hrt{animation:likePop .35s ease;}" +
		"@keyframes likePop{0%{transform:scale(1)}40%{transform:scale(1.45)}100%{transform:scale(1)}}" +
		"@media (prefers-reduced-motion: reduce){.like-btn.pop .hrt{animation:none}}" +
		".like-toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:10px;padding:10px 16px;font-size:13.5px;z-index:300;box-shadow:0 8px 24px rgba(0,0,0,0.35);}";
	document.head.appendChild(style);

	var liked = {};    // id -> true
	var counts = {};   // id -> count as last known
	var loaded = false, loading = false, waiting = [];

	// account-widget.js may not have run yet; read the same stored session directly then
	function session() {
		if (window.sctpAccount) return window.sctpAccount.getSession();
		try {
			var s = JSON.parse(localStorage.getItem("sctp_session") || "null");
			return s && s.token && Date.parse(s.expiresAt) >= Date.now() ? s : null;
		} catch (e) { return null; }
	}
	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	function flush() { var w = waiting; waiting = []; w.forEach(function (cb) { try { cb(); } catch (e) { /* keep going */ } }); }

	function load() {
		var s = session();
		if (!s) { liked = {}; loaded = true; flush(); return; }
		if (loading) return;
		loading = true;
		fetch(API_BASE + "/mapart/my-likes", { headers: { Authorization: "Bearer " + s.token } })
			.then(function (r) { return r.ok ? r.json() : { ids: [] }; })
			.catch(function () { return { ids: [] }; })
			.then(function (d) {
				liked = {};
				(d.ids || []).forEach(function (id) { liked[id] = true; });
				loaded = true; loading = false; flush(); paintAll();
			});
	}

	function ready(cb) { if (loaded) cb(); else { waiting.push(cb); load(); } }
	function has(id) { return !!liked[id]; }
	function count(id, n) { if (counts[id] == null && n != null) counts[id] = n; return counts[id] || 0; }

	function button(m, big) {
		var n = count(m.id, m.likes || 0);
		var on = has(m.id);
		return '<button type="button" class="like-btn' + (big ? " big" : "") + (on ? " on" : "") + '" data-like="' + esc(m.id) + '" aria-pressed="' + on + '" title="' + (on ? "You like this · click to undo" : "Like this mapart") + '">' +
			'<span class="hrt" aria-hidden="true">' + (on ? "&#9829;" : "&#9825;") + '</span><span class="n">' + n + '</span></button>';
	}

	function paint(btn) {
		var id = btn.getAttribute("data-like");
		var on = has(id);
		btn.classList.toggle("on", on);
		btn.setAttribute("aria-pressed", String(on));
		btn.title = on ? "You like this · click to undo" : "Like this mapart";
		btn.querySelector(".hrt").innerHTML = on ? "&#9829;" : "&#9825;";
		btn.querySelector(".n").textContent = count(id);
	}
	function paintAll(id) {
		document.querySelectorAll(id ? '[data-like="' + CSS.escape(id) + '"]' : "[data-like]").forEach(paint);
	}

	function toast(text) {
		var t = document.createElement("div");
		t.className = "like-toast";
		t.textContent = text;
		document.body.appendChild(t);
		setTimeout(function () { t.remove(); }, 2600);
	}

	// capture phase: runs before a surrounding card link or its container's own click handler
	document.addEventListener("click", function (e) {
		var btn = e.target.closest ? e.target.closest("[data-like]") : null;
		if (!btn) return;
		e.preventDefault();
		e.stopPropagation();
		var s = session();
		if (!s) { toast("Log in (top right) to like mapart."); return; }
		if (btn.disabled) return;
		var id = btn.getAttribute("data-like");
		var want = !has(id);
		// optimistic: flip right away, settle on the server's count
		if (want) liked[id] = true; else delete liked[id];
		counts[id] = Math.max(0, count(id) + (want ? 1 : -1));
		paintAll(id);
		if (want) { btn.classList.remove("pop"); void btn.offsetWidth; btn.classList.add("pop"); }
		document.querySelectorAll('[data-like="' + CSS.escape(id) + '"]').forEach(function (b) { b.disabled = true; });
		fetch(API_BASE + "/mapart/like", {
			method: "POST",
			headers: { "Content-Type": "application/json", Authorization: "Bearer " + s.token },
			body: JSON.stringify({ id: id, like: want }),
		}).then(function (r) { return r.json().then(function (d) { return { ok: r.ok, d: d }; }); })
			.then(function (res) {
				if (!res.ok) throw new Error(res.d && res.d.error);
				counts[id] = res.d.likes;
			})
			.catch(function () {
				if (want) delete liked[id]; else liked[id] = true;
				counts[id] = Math.max(0, count(id) + (want ? -1 : 1));
				toast("Couldn't save that like. Try again in a moment.");
			})
			.then(function () {
				document.querySelectorAll('[data-like="' + CSS.escape(id) + '"]').forEach(function (b) { b.disabled = false; });
				paintAll(id);
			});
	}, true);

	window.sctpLike = { button: button, ready: ready, has: has, count: count, reload: function () { loaded = false; load(); } };

	// log in / out elsewhere on the page
	var prev = window.sctpOnAccountChange;
	window.sctpOnAccountChange = function () {
		if (typeof prev === "function") prev.apply(this, arguments);
		loaded = false; load();
	};
})();

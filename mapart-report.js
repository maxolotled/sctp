// Shared "report / edit a mapart" popup, used by the mapart catalog, a single
// mapart's page and the mapart rows in the main listings table. Like
// account-widget.js it's shared on purpose: three pages need the identical
// modal and it talks to the same two endpoints.
//
//   window.sctpMapartReport.open({id, slug, title, artist?, world?, category?}, onDone?)
//   window.sctpMapartReport.canManage()  -> true for head admins / "manageMapart"
//
// Everyone gets a Report popup (wrong artist / world / category, inappropriate
// image). Anyone with the "manageMapart" permission gets an Edit popup instead
// that changes those fields directly (no approval step), plus a Remove button.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var API_KEY = "JjabYIfRtghvBJNoy6857TFVHbjknlMOi6754E5dcfvhgBHNI6b564"; // same public site key index.html uses for listing reports
	var CATEGORIES = ["Pets", "Anime", "TV/Animation", "Games", "Art", "Memes", "Nature", "Photography", "Letters", "Seasonal", "Advertisement", "Misc", "Flags"];
	var REASONS = [
		["wrong_artist", "Wrong artist"],
		["wrong_world", "Wrong world"],
		["wrong_category", "Wrong category"],
		["inappropriate_image", "Inappropriate image"],
	];

	var style = document.createElement("style");
	style.textContent =
		".mr-bg{position:fixed;inset:0;background:rgba(0,0,0,0.65);display:flex;align-items:center;justify-content:center;z-index:400;padding:20px;box-sizing:border-box;}" +
		".mr-modal{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:22px;width:100%;max-width:400px;box-sizing:border-box;color:var(--text,#EAEFE7);font-family:inherit;max-height:92vh;overflow:auto;}" +
		".mr-modal h2{margin:0 0 4px;font-size:20px;font-family:var(--font-display,inherit);}" +
		".mr-modal .mr-sub{color:var(--muted,#8FA593);font-size:13px;margin:0 0 16px;overflow-wrap:anywhere;}" +
		".mr-modal label.mr-l{display:block;font-size:12px;color:var(--muted,#8FA593);font-weight:600;text-transform:uppercase;letter-spacing:.03em;margin:0 0 5px;}" +
		".mr-modal .mr-reasons{display:flex;flex-direction:column;gap:9px;margin-bottom:14px;}" +
		".mr-modal .mr-reasons label{display:flex;align-items:center;gap:8px;font-size:14px;cursor:pointer;}" +
		".mr-modal input[type=text],.mr-modal select,.mr-modal textarea{width:100%;box-sizing:border-box;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:9px 12px;font-size:14px;font-family:inherit;margin-bottom:12px;}" +
		".mr-modal textarea{resize:vertical;min-height:70px;}" +
		".mr-modal .mr-status{min-height:18px;font-size:13px;margin-bottom:10px;}" +
		".mr-modal .mr-status.err{color:#E27D6B;}.mr-modal .mr-status.ok{color:var(--accent,#B7E23D);}" +
		".mr-modal .mr-actions{display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;}" +
		".mr-modal .mr-actions button{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 16px;font-size:13.5px;cursor:pointer;font-family:inherit;}" +
		".mr-modal .mr-actions button.primary{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;font-weight:700;}" +
		".mr-modal .mr-actions button.danger{color:#E27D6B;border-color:#E27D6B;margin-right:auto;}" +
		".mr-modal .mr-actions button:disabled{opacity:.5;cursor:default;}";
	document.head.appendChild(style);

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function session() { return window.sctpAccount ? window.sctpAccount.getSession() : null; }
	function canManage() {
		var s = session();
		return !!s && (!!s.isHeadAdmin || (s.permissions || []).indexOf("manageMapart") !== -1);
	}

	function modal(html) {
		var bg = document.createElement("div");
		bg.className = "mr-bg";
		bg.innerHTML = '<div class="mr-modal">' + html + "</div>";
		document.body.appendChild(bg);
		bg.addEventListener("click", function (e) { if (e.target === bg) bg.remove(); });
		return bg;
	}
	function post(path, body, authed) {
		var headers = { "Content-Type": "application/json", "Authorization": "Bearer " + (authed ? session().token : API_KEY) };
		return fetch(API_BASE + path, { method: "POST", headers: headers, body: JSON.stringify(body) })
			.then(function (r) { return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, data: d }; }); });
	}

	function openReport(m, onDone) {
		var bg = modal(
			"<h2>Report this mapart</h2>" +
			'<p class="mr-sub">' + esc(m.title || "Mapart") + (m.artist ? " — by " + esc(m.artist) : "") + "</p>" +
			'<div class="mr-reasons">' + REASONS.map(function (r) { return '<label><input type="radio" name="mrReason" value="' + r[0] + '"> ' + r[1] + "</label>"; }).join("") + "</div>" +
			'<label class="mr-l" for="mrDetails">Details (optional)</label>' +
			'<textarea id="mrDetails" maxlength="500" placeholder="e.g. the real artist is…, it\'s actually in Honeybee, it belongs under Anime…"></textarea>' +
			'<div class="mr-status" id="mrStatus"></div>' +
			'<div class="mr-actions"><button type="button" id="mrCancel">Cancel</button><button type="button" class="primary" id="mrSend">Submit report</button></div>'
		);
		var st = bg.querySelector("#mrStatus");
		bg.querySelector("#mrCancel").onclick = function () { bg.remove(); };
		bg.querySelector("#mrSend").onclick = function () {
			var sel = bg.querySelector('input[name="mrReason"]:checked');
			if (!sel) { st.className = "mr-status err"; st.textContent = "Pick a reason first."; return; }
			st.className = "mr-status"; st.textContent = "Sending…";
			post("/mapart/report", { id: m.id, reason: sel.value, details: bg.querySelector("#mrDetails").value.trim() }, false).then(function (res) {
				if (!res.ok) { st.className = "mr-status err"; st.textContent = res.data.error || "Couldn't send the report."; return; }
				st.className = "mr-status ok"; st.textContent = "Report sent — thanks for flagging it.";
				bg.querySelector("#mrSend").disabled = true;
				setTimeout(function () { bg.remove(); }, 1400);
				if (onDone) onDone("reported");
			}).catch(function () { st.className = "mr-status err"; st.textContent = "Network error."; });
		};
	}

	// artist-field.js is loaded on demand so pages don't each need another tag.
	function withArtistField(cb) {
		if (window.sctpArtistField) return cb();
		var sc = document.createElement("script");
		sc.src = "/artist-field.js";
		sc.onload = cb;
		document.head.appendChild(sc);
	}

	function openEdit(m, onDone) {
		var bg = modal(
			"<h2>Edit mapart</h2>" +
			'<p class="mr-sub">' + esc(m.title || "Mapart") + " — changes apply immediately.</p>" +
			'<label class="mr-l">Artist(s) — arrow for collabs</label><div id="mrArtistHost" style="margin-bottom:12px;"><input type="text" placeholder="Loading…" disabled></div>' +
			'<label class="mr-l" for="mrWorld">World</label><select id="mrWorld" disabled><option value="Firefly">Firefly</option><option value="Honeybee">Honeybee</option></select>' +
			'<label class="mr-l" for="mrCat">Category</label><select id="mrCat" disabled><option value="">No category</option>' +
				CATEGORIES.map(function (c) { return '<option value="' + c + '">' + c + "</option>"; }).join("") + "</select>" +
			'<div class="mr-status" id="mrStatus"></div>' +
			'<div class="mr-actions"><button type="button" class="danger" id="mrDelete" title="Removes the mapart for good (inappropriate image)">Remove mapart</button>' +
				'<button type="button" id="mrCancel">Cancel</button><button type="button" class="primary" id="mrSave" disabled>Save</button></div>'
		);
		var st = bg.querySelector("#mrStatus"), artistHost = bg.querySelector("#mrArtistHost"), artistField = null, world = bg.querySelector("#mrWorld"), cat = bg.querySelector("#mrCat"), save = bg.querySelector("#mrSave");
		var orig = null;
		bg.querySelector("#mrCancel").onclick = function () { bg.remove(); };

		function fill(cur) {
			orig = { artist: cur.artist || "", world: cur.world || "Firefly", category: cur.category || "" };
			world.value = orig.world; cat.value = orig.category;
			withArtistField(function () {
				artistField = window.sctpArtistField.create(artistHost, { value: orig.artist });
				[world, cat, save].forEach(function (el) { el.disabled = false; });
			});
		}
		// The listing rows don't carry category/artist, so always read the live piece.
		fetch(API_BASE + "/mapart/by-slug?slug=" + encodeURIComponent(m.slug || ""))
			.then(function (r) { return r.ok ? r.json() : null; })
			.then(function (cur) { if (cur && !cur.error) fill(cur); else { st.className = "mr-status err"; st.textContent = "Couldn't load the current values."; } })
			.catch(function () { st.className = "mr-status err"; st.textContent = "Couldn't load the current values."; });

		save.onclick = function () {
			var body = { id: m.id };
			if (artistField && artistField.getValue() !== orig.artist) body.artist = artistField.getValue();
			if (world.value !== orig.world) body.world = world.value;
			if (cat.value !== orig.category) body.category = cat.value || null;
			if (Object.keys(body).length === 1) { st.className = "mr-status"; st.textContent = "Nothing changed."; return; }
			save.disabled = true; st.className = "mr-status"; st.textContent = "Saving…";
			post("/mapart/update", body, true).then(function (res) {
				if (!res.ok) { save.disabled = false; st.className = "mr-status err"; st.textContent = res.data.error || "Couldn't save."; return; }
				st.className = "mr-status ok"; st.textContent = "Saved.";
				setTimeout(function () { bg.remove(); }, 700);
				if (onDone) onDone("edited");
			}).catch(function () { save.disabled = false; st.className = "mr-status err"; st.textContent = "Network error."; });
		};
		bg.querySelector("#mrDelete").onclick = function () {
			if (!confirm('Remove "' + (m.title || "this mapart") + '" from SCTP for good?\nRescans will not bring it back.')) return;
			post("/admin/mapart/delete", { id: m.id }, true).then(function (res) {
				if (!res.ok) { st.className = "mr-status err"; st.textContent = res.data.error || "Couldn't remove it."; return; }
				bg.remove();
				if (onDone) onDone("removed");
			}).catch(function () { st.className = "mr-status err"; st.textContent = "Network error."; });
		};
	}

	window.sctpMapartReport = {
		canManage: canManage,
		label: function () { return canManage() ? "Edit" : "Report"; },
		open: function (m, onDone) { (canManage() ? openEdit : openReport)(m, onDone); },
	};
})();

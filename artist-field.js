// Shared artist input for mapart: one field for the head artist, plus an arrow
// that drops down extra fields for collaborators (click "+ Add artist" for
// another one). Shared by the mapart management page and the report/edit popup.
//
//   var f = window.sctpArtistField.create(hostEl, { value: "Head & Second", placeholder?: "…" });
//   f.getValue()  -> "Head & Second"  ("" when empty; joined the way the API stores it)
//
// The first field is the head artist: the one the catalog shows until a
// visitor expands the artist list. The API stores artists as "A & B & C".
(function () {
	"use strict";
	var MAX = 16, NAME_MAX = 40;

	var style = document.createElement("style");
	style.textContent =
		".af{display:flex;flex-direction:column;gap:6px;}" +
		".af-row{display:flex;gap:6px;align-items:stretch;}" +
		".af-row input[type=text]{flex:1;min-width:0;width:auto!important;margin:0!important;}" +
		".af-btn{flex:0 0 auto;min-width:36px;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;cursor:pointer;font-size:13px;font-family:inherit;padding:0 10px;}" +
		".af-btn:hover{border-color:var(--accent-dim,#87AE29);}" +
		".af-toggle .af-arrow{display:inline-block;transition:transform .12s;}" +
		".af.open .af-toggle .af-arrow{transform:rotate(180deg);}" +
		".af-more{display:flex;flex-direction:column;gap:6px;}" +
		".af-more[hidden]{display:none!important;}" +
		".af-add{align-self:flex-start;background:transparent;border:1px dashed var(--line,#33453A);color:var(--accent,#B7E23D);border-radius:8px;padding:5px 12px;font-size:12.5px;cursor:pointer;font-family:inherit;}" +
		".af-add:hover{border-color:var(--accent-dim,#87AE29);}" +
		".af-hint{font-size:11.5px;color:var(--muted,#8FA593);}";
	document.head.appendChild(style);

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	function create(host, opts) {
		opts = opts || {};
		var names = String(opts.value || "").split("&").map(function (s) { return s.trim(); }).filter(Boolean);
		var head = names.shift() || "";
		var headPlaceholder = opts.placeholder || "Head artist (main artist — shown first)";
		var open = names.length > 0;

		host.innerHTML =
			'<div class="af' + (open ? " open" : "") + '">' +
				'<div class="af-row"><input type="text" class="af-head" maxlength="' + NAME_MAX + '" placeholder="' + esc(headPlaceholder) + '" value="' + esc(head) + '">' +
					'<button type="button" class="af-btn af-toggle" title="Collab? Add more artists"><span class="af-arrow">&#9662;</span> <span class="af-count"></span></button></div>' +
				'<div class="af-more"' + (open ? "" : " hidden") + '><div class="af-list"></div>' +
					'<button type="button" class="af-add">+ Add artist</button>' +
					'<div class="af-hint">The top field is the head artist and is shown first on the catalog; the others appear when the list is expanded.</div></div>' +
			"</div>";
		var root = host.querySelector(".af"), more = host.querySelector(".af-more"), list = host.querySelector(".af-list"), count = host.querySelector(".af-count");

		function extras() { return list.querySelectorAll("input"); }
		function refreshCount() {
			var n = 0;
			extras().forEach(function (i) { if (i.value.trim()) n++; });
			count.textContent = !open && n ? "+" + n : "";
		}
		function addRow(v) {
			if (extras().length >= MAX - 1) return null;
			var row = document.createElement("div");
			row.className = "af-row";
			row.innerHTML = '<input type="text" maxlength="' + NAME_MAX + '" placeholder="Collaborator" value="' + esc(v || "") + '"><button type="button" class="af-btn af-x" title="Remove this artist">&times;</button>';
			row.querySelector("input").addEventListener("input", refreshCount);
			row.querySelector(".af-x").addEventListener("click", function () { row.remove(); refreshCount(); });
			list.appendChild(row);
			return row.querySelector("input");
		}
		names.forEach(addRow);

		host.querySelector(".af-toggle").addEventListener("click", function () {
			open = !open;
			root.classList.toggle("open", open);
			more.hidden = !open;
			if (open && !extras().length) { var i = addRow(""); if (i) i.focus(); }
			refreshCount();
		});
		host.querySelector(".af-add").addEventListener("click", function () {
			var i = addRow("");
			if (i) i.focus();
		});
		refreshCount();

		return {
			getValue: function () {
				var all = [host.querySelector(".af-head").value];
				extras().forEach(function (i) { all.push(i.value); });
				return all.map(function (s) { return s.replace(/&/g, " ").trim(); }).filter(Boolean).join(" & ");
			},
		};
	}

	window.sctpArtistField = { create: create };
})();

// Reverse image search for mapart: pick a picture, get the pieces that look
// like it. The browser computes a 256-bit difference hash of the picture
// (same maths as dHashFromGray in the Worker) and the Worker compares it with
// the stored hash of every piece. Also used on the upload form to warn about
// duplicates before uploading.
//
//   sctpMapartSearch.hashFile(file)  -> Promise<64-char hex>
//   sctpMapartSearch.search(file)    -> Promise<[{id, slug, title, artist, world, similarity, distance}]>
//   sctpMapartSearch.open()          -> the "Search by image" popup
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var COLS = 17, ROWS = 16, MAX_SIDE = 1600;

	var style = document.createElement("style");
	style.textContent =
		".ms-bg{position:fixed;inset:0;background:rgba(0,0,0,0.65);display:flex;align-items:center;justify-content:center;z-index:400;padding:20px;box-sizing:border-box;}" +
		".ms-modal{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:22px;width:100%;max-width:560px;max-height:92vh;overflow:auto;color:var(--text,#EAEFE7);font-family:inherit;box-sizing:border-box;}" +
		".ms-modal h2{margin:0 0 4px;font-size:20px;font-family:var(--font-display,inherit);}" +
		".ms-sub{color:var(--muted,#8FA593);font-size:13px;margin:0 0 14px;}" +
		".ms-drop{border:2px dashed var(--line,#33453A);border-radius:12px;padding:22px;text-align:center;color:var(--muted,#8FA593);font-size:13.5px;cursor:pointer;}" +
		".ms-drop.over{border-color:var(--accent,#B7E23D);color:var(--text,#EAEFE7);}" +
		".ms-prev{max-width:100%;max-height:150px;image-rendering:pixelated;border-radius:6px;margin:12px auto 0;display:block;}" +
		".ms-res{margin-top:14px;display:flex;flex-direction:column;gap:8px;}" +
		".ms-hit{display:flex;gap:12px;align-items:center;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);border-radius:10px;padding:8px;text-decoration:none;color:inherit;}" +
		".ms-hit:hover{border-color:var(--accent-dim,#87AE29);}" +
		".ms-hit img{width:64px;height:64px;object-fit:contain;image-rendering:pixelated;background:var(--img-bg,#0C140F);border-radius:6px;flex:0 0 auto;}" +
		".ms-hit .t{font-weight:600;}" +
		".ms-hit .a{font-size:12.5px;color:var(--muted,#8FA593);}" +
		".ms-badge{margin-left:auto;font-size:12px;font-family:var(--font-mono,monospace);padding:2px 9px;border-radius:999px;border:1px solid var(--line,#33453A);white-space:nowrap;}" +
		".ms-badge.hi{color:var(--accent,#B7E23D);border-color:rgba(var(--accent-rgb,183,226,61),0.5);}" +
		".ms-msg{color:var(--muted,#8FA593);font-size:13.5px;padding:8px 0;}" +
		".ms-actions{display:flex;justify-content:flex-end;margin-top:14px;}" +
		".ms-actions button{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 16px;cursor:pointer;font-family:inherit;}";
	document.head.appendChild(style);

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	function loadBitmap(file) {
		return new Promise(function (resolve, reject) {
			var url = URL.createObjectURL(file);
			var img = new Image();
			img.onload = function () { URL.revokeObjectURL(url); resolve(img); };
			img.onerror = function () { URL.revokeObjectURL(url); reject(new Error("That file isn't an image the browser can read.")); };
			img.src = url;
		});
	}

	function hashFile(file) {
		return loadBitmap(file).then(function (img) {
			var w = img.naturalWidth, h = img.naturalHeight;
			var scale = Math.min(1, MAX_SIDE / Math.max(w, h));
			var cw = Math.max(1, Math.round(w * scale)), ch = Math.max(1, Math.round(h * scale));
			var canvas = document.createElement("canvas");
			canvas.width = cw; canvas.height = ch;
			var ctx = canvas.getContext("2d", { willReadFrequently: true });
			ctx.imageSmoothingEnabled = false;
			ctx.fillStyle = "#000"; ctx.fillRect(0, 0, cw, ch); // transparent counts as black, like the server
			ctx.drawImage(img, 0, 0, cw, ch);
			var d = ctx.getImageData(0, 0, cw, ch).data;
			var sum = new Float64Array(COLS * ROWS), cnt = new Uint32Array(COLS * ROWS);
			for (var y = 0; y < ch; y++) {
				var cy = Math.min(ROWS - 1, Math.floor(y * ROWS / ch));
				for (var x = 0; x < cw; x++) {
					var cx = Math.min(COLS - 1, Math.floor(x * COLS / cw));
					var i = (y * cw + x) * 4;
					var g = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
					var k = cy * COLS + cx;
					sum[k] += g; cnt[k]++;
				}
			}
			var hex = "", nib = 0, nbits = 0;
			for (var r = 0; r < ROWS; r++) {
				for (var c = 0; c < COLS - 1; c++) {
					var a = sum[r * COLS + c] / (cnt[r * COLS + c] || 1);
					var b = sum[r * COLS + c + 1] / (cnt[r * COLS + c + 1] || 1);
					nib = (nib << 1) | (a > b ? 1 : 0);
					if (++nbits === 4) { hex += nib.toString(16); nib = 0; nbits = 0; }
				}
			}
			return hex;
		});
	}

	function search(file) {
		return hashFile(file).then(function (hash) {
			return fetch(API_BASE + "/mapart/search-image", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ hash: hash }) });
		}).then(function (r) { return r.json().then(function (d) { if (!r.ok) throw new Error(d.error || "Search failed"); return d.matches || []; }); });
	}

	function hitHtml(m) {
		var img = API_BASE + "/mapart/image?id=" + encodeURIComponent(m.id);
		return '<a class="ms-hit" href="/mapart/' + encodeURIComponent(m.slug) + '" target="_blank" rel="noopener"><img src="' + esc(img) + '" alt="" loading="lazy">' +
			'<div><div class="t">' + esc(m.title) + '</div><div class="a">' + (m.artist ? "by " + esc(m.artist) + " · " : "") + esc(m.world) + " · " + m.width + "×" + m.height + "</div></div>" +
			'<span class="ms-badge' + (m.similarity >= 92 ? " hi" : "") + '">' + m.similarity + "% match</span></a>";
	}

	function open() {
		var bg = document.createElement("div");
		bg.className = "ms-bg";
		bg.innerHTML =
			'<div class="ms-modal"><h2>Search mapart by image</h2>' +
			'<p class="ms-sub">Upload a picture or screenshot and we\'ll find the mapart that looks like it. Also handy to check whether a piece is already in the gallery.</p>' +
			'<div class="ms-drop" id="msDrop">Click to choose an image, or drop one here<input type="file" id="msFile" accept="image/*" hidden></div>' +
			'<img class="ms-prev" id="msPrev" hidden alt=""><div class="ms-res" id="msRes"></div>' +
			'<div class="ms-actions"><button type="button" id="msClose">Close</button></div></div>';
		document.body.appendChild(bg);
		bg.addEventListener("click", function (e) { if (e.target === bg) bg.remove(); });
		bg.querySelector("#msClose").onclick = function () { bg.remove(); };
		var drop = bg.querySelector("#msDrop"), input = bg.querySelector("#msFile"), res = bg.querySelector("#msRes"), prev = bg.querySelector("#msPrev");
		function run(file) {
			if (!file) return;
			prev.src = URL.createObjectURL(file); prev.hidden = false;
			res.innerHTML = '<div class="ms-msg">Searching…</div>';
			search(file).then(function (ms) {
				res.innerHTML = ms.length ? ms.map(hitHtml).join("") : '<div class="ms-msg">No similar mapart found — it looks new.</div>';
			}).catch(function (e) { res.innerHTML = '<div class="ms-msg">' + esc(e.message || "Search failed.") + "</div>"; });
		}
		drop.onclick = function () { input.click(); };
		input.onchange = function () { run(input.files[0]); };
		drop.addEventListener("dragover", function (e) { e.preventDefault(); drop.classList.add("over"); });
		drop.addEventListener("dragleave", function () { drop.classList.remove("over"); });
		drop.addEventListener("drop", function (e) { e.preventDefault(); drop.classList.remove("over"); run(e.dataTransfer.files[0]); });
	}

	window.sctpMapartSearch = { hashFile: hashFile, search: search, open: open, hitHtml: hitHtml };
})();

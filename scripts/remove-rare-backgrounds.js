#!/usr/bin/env node
"use strict";

/**
 * Removes the flat grey background from rare-item textures (items/textures/*.png,
 * as listed in data/rare-items.json) by making it transparent.
 *
 * How it decides: the background colour is the grey (r ~ g ~ b) that fills most of an
 * image's border. Starting from every border pixel, the script flood-fills through
 * pixels of that colour and turns them transparent — so grey that is *inside* an item
 * (not connected to the border) is left alone, and images that already have a
 * transparent background are skipped.
 *
 * Usage:
 *   node scripts/remove-rare-backgrounds.js            dry run: only lists what would change
 *   node scripts/remove-rare-backgrounds.js --apply    rewrites the PNGs in place (keep git handy to undo)
 *   node scripts/remove-rare-backgrounds.js --apply --tolerance=6
 *   node scripts/remove-rare-backgrounds.js --apply --from=1494   only rare_image1494.png and up
 *                                                               (i.e. just a fresh import's textures)
 *
 * Handles 8-bit RGBA, RGB and palette PNGs (all output as RGBA); anything else is reported and skipped.
 */

const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const ROOT = path.resolve(__dirname, "..");
const APPLY = process.argv.includes("--apply");
const TOL = (() => { const a = process.argv.find((x) => x.startsWith("--tolerance=")); return a ? Number(a.split("=")[1]) : 4; })();

function readPng(file) {
	const b = fs.readFileSync(file);
	if (b.readUInt32BE(0) !== 0x89504e47) return null;
	let off = 8, w = 0, h = 0, depth = 0, ct = -1, il = 0, plte = null, trns = null;
	const idat = [];
	while (off < b.length) {
		const len = b.readUInt32BE(off), type = b.toString("ascii", off + 4, off + 8), data = b.subarray(off + 8, off + 8 + len);
		off += 12 + len;
		if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; ct = data[9]; il = data[12]; }
		else if (type === "PLTE") plte = data;
		else if (type === "tRNS") trns = data;
		else if (type === "IDAT") idat.push(data);
		else if (type === "IEND") break;
	}
	if (depth !== 8 || il !== 0 || ![2, 3, 6].includes(ct)) return null;
	const ch = { 2: 3, 3: 1, 6: 4 }[ct], stride = w * ch;
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const px = Buffer.alloc(h * stride);
	for (let y = 0; y < h; y++) {
		const ft = raw[y * (stride + 1)];
		for (let i = 0; i < stride; i++) {
			const x = raw[y * (stride + 1) + 1 + i];
			const a = i >= ch ? px[y * stride + i - ch] : 0, u = y > 0 ? px[(y - 1) * stride + i] : 0, c = i >= ch && y > 0 ? px[(y - 1) * stride + i - ch] : 0;
			let v;
			if (ft === 0) v = x; else if (ft === 1) v = x + a; else if (ft === 2) v = x + u; else if (ft === 3) v = x + ((a + u) >> 1);
			else { const p = a + u - c, pa = Math.abs(p - a), pb = Math.abs(p - u), pc = Math.abs(p - c); v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? u : c); }
			px[y * stride + i] = v & 255;
		}
	}
	const rgba = Buffer.alloc(w * h * 4);
	for (let i = 0; i < w * h; i++) {
		if (ct === 6) { px.copy(rgba, i * 4, i * 4, i * 4 + 4); }
		else if (ct === 2) { rgba[i * 4] = px[i * 3]; rgba[i * 4 + 1] = px[i * 3 + 1]; rgba[i * 4 + 2] = px[i * 3 + 2]; rgba[i * 4 + 3] = 255; }
		else { const pi = px[i] * 3; rgba[i * 4] = plte[pi]; rgba[i * 4 + 1] = plte[pi + 1]; rgba[i * 4 + 2] = plte[pi + 2]; rgba[i * 4 + 3] = trns && px[i] < trns.length ? trns[px[i]] : 255; }
	}
	return { w, h, rgba };
}

function writePng(file, im) {
	const stride = im.w * 4, raw = Buffer.alloc(im.h * (stride + 1));
	for (let y = 0; y < im.h; y++) { raw[y * (stride + 1)] = 0; im.rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride); }
	const chunk = (type, data) => {
		const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
		const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
		const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32 ? zlib.crc32(td) >>> 0 : crc32(td) >>> 0);
		return Buffer.concat([len, td, crc]);
	};
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(im.w, 0); ihdr.writeUInt32BE(im.h, 4); ihdr[8] = 8; ihdr[9] = 6;
	fs.writeFileSync(file, Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
}
let CRC_TABLE = null;
function crc32(buf) {
	if (!CRC_TABLE) { CRC_TABLE = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; CRC_TABLE[n] = c >>> 0; } }
	let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0;
}

const isGrey = (rgba, i, spread) => Math.abs(rgba[i] - rgba[i + 1]) <= spread && Math.abs(rgba[i + 1] - rgba[i + 2]) <= spread && Math.abs(rgba[i] - rgba[i + 2]) <= spread;
// The slot's dark outline (#373737) and its soft, semi-transparent edge pixels — part of the inventory background too.
const isFrame = (rgba, i) => rgba[i + 3] > 0 && ((rgba[i + 3] < 255 && isGrey(rgba, i, 6)) || (rgba[i + 3] > 200 && isGrey(rgba, i, 3) && Math.abs(rgba[i] - 55) <= 3));

// The grey that fills most of the border, or null (already transparent / not a flat grey background).
function findBackground(im) {
	const { w, h, rgba } = im, counts = new Map();
	let border = 0;
	const visit = (x, y) => {
		const i = (y * w + x) * 4;
		if (isFrame(rgba, i)) return; // frame pixels don't count either way
		border++;
		if (rgba[i + 3] < 200 || !isGrey(rgba, i, 6)) return;
		const k = rgba[i]; counts.set(k, (counts.get(k) || 0) + 1);
	};
	// the outer 1-3px can be all slot frame, so sample a few rings in
	for (let d = 0; d < 4 && d < (Math.min(w, h) >> 1); d++) {
		for (let x = d; x < w - d; x++) { visit(x, d); visit(x, h - 1 - d); }
		for (let y = d + 1; y < h - 1 - d; y++) { visit(d, y); visit(w - 1 - d, y); }
	}
	let best = null;
	for (const [k, n] of counts) if (!best || n > best[1]) best = [k, n];
	return best && best[1] >= border * 0.25 ? best[0] : null;
}

function removeBackground(im, grey) {
	const { w, h, rgba } = im, seen = new Uint8Array(w * h), stack = [];
	const isBg = (x, y) => {
		const i = (y * w + x) * 4;
		if (rgba[i + 3] === 0) return true;
		if (isFrame(rgba, i) && (x < 3 || y < 3 || x >= w - 3 || y >= h - 3)) return true;
		return rgba[i + 3] > 200 && Math.abs(rgba[i] - grey) <= TOL && Math.abs(rgba[i + 1] - grey) <= TOL && Math.abs(rgba[i + 2] - grey) <= TOL;
	};
	for (let x = 0; x < w; x++) stack.push([x, 0], [x, h - 1]);
	for (let y = 0; y < h; y++) stack.push([0, y], [w - 1, y]);
	let removed = 0;
	while (stack.length) {
		const [x, y] = stack.pop();
		if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x] || !isBg(x, y)) continue;
		seen[y * w + x] = 1;
		const i = (y * w + x) * 4;
		if (rgba[i + 3]) removed++;
		rgba[i] = rgba[i + 1] = rgba[i + 2] = rgba[i + 3] = 0;
		stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
	}
	// The item's edge was blended against the grey, leaving a 1-2px ring of slightly lighter grey — peel it off.
	for (let pass = 0; pass < 2 && removed; pass++) {
		const kill = [];
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const i = (y * w + x) * 4;
			if (!rgba[i + 3] || rgba[i + 3] < 200 || !isGrey(rgba, i, 3) || rgba[i] < grey + 1 || rgba[i] > grey + 20) continue;
			let adj = false;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const X = x + dx, Y = y + dy; if (X >= 0 && Y >= 0 && X < w && Y < h && !rgba[(Y * w + X) * 4 + 3]) adj = true; }
			if (adj) kill.push(i);
		}
		for (const i of kill) { rgba[i] = rgba[i + 1] = rgba[i + 2] = rgba[i + 3] = 0; removed++; }
	}
	return removed;
}

// Grey the slot showed through *holes* in an item (key rings, glass cases, map-frame centres) isn't connected
// to the border. Clear enclosed blobs of the exact slot grey when they're big enough not to be real art.
const HOLE_MIN = (() => { const a = process.argv.find((x) => x.startsWith("--hole-min=")); return a ? Number(a.split("=")[1]) : 8; })();
function removeHoles(im, grey) {
	const { w, h, rgba } = im, seen = new Uint8Array(w * h);
	const isG = (i) => rgba[i + 3] > 200 && Math.abs(rgba[i] - grey) <= 1 && Math.abs(rgba[i + 1] - grey) <= 1 && Math.abs(rgba[i + 2] - grey) <= 1;
	let removed = 0;
	for (let p = 0; p < w * h; p++) {
		if (seen[p] || !isG(p * 4)) continue;
		const comp = [p]; seen[p] = 1;
		for (let k = 0; k < comp.length; k++) {
			const x = comp[k] % w, y = (comp[k] / w) | 0;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				const X = x + dx, Y = y + dy;
				if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
				const r = Y * w + X;
				if (!seen[r] && isG(r * 4)) { seen[r] = 1; comp.push(r); }
			}
		}
		if (comp.length < HOLE_MIN) continue;
		for (const q of comp) { const i = q * 4; rgba[i] = rgba[i + 1] = rgba[i + 2] = rgba[i + 3] = 0; }
		removed += comp.length;
	}
	return removed;
}

const items = JSON.parse(fs.readFileSync(path.join(ROOT, "data", "rare-items.json"), "utf8"));
const FROM = (() => { const a = process.argv.find((x) => x.startsWith("--from=")); return a ? Number(a.split("=")[1]) : 0; })();
const files = [...new Set(items.map((i) => i.texture).filter(Boolean))].filter((t) => {
	if (!FROM) return true;
	const m = /rare_image(\d+)\.png$/.exec(t);
	return m && Number(m[1]) >= FROM;
});
let holeTextures = 0, changed = 0, skipped = 0, already = 0, missing = 0, unsupported = [];
const report = [];
for (const t of files) {
	const file = path.join(ROOT, t);
	if (!fs.existsSync(file)) { missing++; continue; }
	let im;
	try { im = readPng(file); } catch (e) { im = null; }
	if (!im) { unsupported.push(t); continue; }
	const grey = findBackground(im);
	const removed = grey !== null ? removeBackground(im, grey) : 0;
	const holes = removeHoles(im, grey !== null ? grey : 139);
	if (grey === null && !holes) { already++; continue; }
	if (!removed && !holes) { skipped++; continue; }
	if (holes) holeTextures++;
	report.push([t, grey, removed + holes, im.w * im.h]);
	if (APPLY) writePng(file, im);
	changed++;
}
report.sort((a, b) => b[2] - a[2]);
console.log((APPLY ? "APPLIED" : "DRY RUN") + " — textures checked: " + files.length);
console.log("  with a grey background " + (APPLY ? "cleaned" : "to clean") + ": " + changed);
console.log("  of those, with enclosed holes cleared: " + holeTextures);
console.log("  already transparent / no flat grey border: " + already);
console.log("  nothing removable: " + skipped + ", file missing: " + missing + ", unsupported format: " + unsupported.length);
if (unsupported.length) console.log("  unsupported: " + unsupported.slice(0, 10).join(", "));
report.slice(0, 5).forEach((r) => console.log("  e.g. " + r[0] + " (grey " + r[1] + ", " + r[2] + "/" + r[3] + " px removed)"));
if (!APPLY) console.log("\nRe-run with --apply to rewrite the files.");

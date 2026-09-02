#!/usr/bin/env node
"use strict";

/**
 * Imports new rare items from an updated "Snailcraft Items" xlsx export into
 * data/rare-items.json + items/textures/, the same way it was done for the
 * original "add rares" import (see git commit b1da0b0).
 *
 * The workbook uses modern Excel "image in cell" rich values, not classic
 * floating drawings, so extracting each row's icon requires walking a chain:
 *
 *   cell vm="N" (1-indexed)
 *     -> xl/metadata.xml <futureMetadata name="XLRICHVALUE"><bk> list, 0-indexed at N-1
 *        -> that <bk>'s <xlrd:rvb i="M"/> gives M
 *   -> xl/richData/rdrichvalue.xml <rv> list, 0-indexed at M
 *        -> that <rv>'s first <v> is K (the LocalImageIdentifier)
 *   -> xl/richData/richValueRel.xml <rel r:id="..."/> list, 0-indexed at K
 *        -> gives an r:id (e.g. "rId42")
 *   -> xl/richData/_rels/richValueRel.xml.rels maps r:id -> ../media/imageY.png
 *        (NOT stored in rId order -- must be parsed into a map, never assumed sequential)
 *
 * Column layout is auto-detected per sheet from its header row (matched by
 * keyword, not a hardcoded letter) since the 6 sheets don't all use the same
 * columns for the same fields.
 *
 * Usage:
 *   node scripts/import-rare-items.js                  dry run: validates the
 *                                                        pipeline against the
 *                                                        OLD file, diffs the
 *                                                        NEW file against the
 *                                                        current JSON, prints
 *                                                        a report. Writes nothing.
 *   node scripts/import-rare-items.js --report-diffs    also lists items present
 *                                                        in both files under the
 *                                                        same (name, category)
 *                                                        but with different
 *                                                        field values.
 *   node scripts/import-rare-items.js --apply           actually copies new
 *                                                        textures + appends new
 *                                                        entries to the JSON.
 */

const fs = require("fs");
const path = require("path");
const os = require("os");
const { execFileSync } = require("child_process");

const REPO_ROOT = path.resolve(__dirname, "..");
const OLD_XLSX = path.join(REPO_ROOT, "Snailcraft Items.xlsx");
// The original import compared a separate "...New.xlsx" against this one;
// since then the workflow has become "edit this same file in place, re-run
// the import" — so both point at the same file now. The old-file validation
// step below still runs (harmlessly trivial when it's the same file) as a
// pipeline sanity check before trusting the diff.
const NEW_XLSX = OLD_XLSX;
const RARE_ITEMS_JSON = path.join(REPO_ROOT, "data", "rare-items.json");
const TEXTURES_DIR = path.join(REPO_ROOT, "items", "textures");
const STATE_FILE = path.join(os.tmpdir(), "sctp-import-rare-items-state.json");

const APPLY = process.argv.includes("--apply");
const REPORT_DIFFS = process.argv.includes("--report-diffs");

// ---------- XML helpers ----------

function decodeXmlEntities(s) {
	return s
		.replace(/&lt;/g, "<")
		.replace(/&gt;/g, ">")
		.replace(/&quot;/g, '"')
		.replace(/&apos;/g, "'")
		.replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(parseInt(n, 10)))
		.replace(/&#x([0-9a-fA-F]+);/g, (_, n) => String.fromCodePoint(parseInt(n, 16)))
		.replace(/&amp;/g, "&");
}

function readFile(p) {
	return fs.readFileSync(p, "utf8");
}

// ---------- unzip ----------

function unzipXlsx(xlsxPath, destDir) {
	fs.rmSync(destDir, { recursive: true, force: true });
	fs.mkdirSync(destDir, { recursive: true });
	execFileSync("unzip", ["-o", "-q", xlsxPath, "-d", destDir]);
}

// ---------- shared strings ----------

function parseSharedStrings(dir) {
	const p = path.join(dir, "xl", "sharedStrings.xml");
	if (!fs.existsSync(p)) return [];
	const xml = readFile(p);
	return xml
		.split("<si>")
		.slice(1)
		.map((chunk) => {
			const part = chunk.split("</si>")[0];
			const texts = [...part.matchAll(/<t[^>]*>([^<]*)<\/t>/g)].map((m) => decodeXmlEntities(m[1]));
			return texts.join("");
		});
}

// ---------- workbook sheet-name -> sheetN.xml resolution ----------

function parseWorkbookSheets(dir) {
	const workbookXml = readFile(path.join(dir, "xl", "workbook.xml"));
	const relsXml = readFile(path.join(dir, "xl", "_rels", "workbook.xml.rels"));

	const relTargets = new Map();
	for (const m of relsXml.matchAll(/<Relationship\s+Id="([^"]+)"[^>]*Target="([^"]+)"/g)) {
		relTargets.set(m[1], m[2]);
	}

	const sheets = [];
	for (const m of workbookXml.matchAll(/<sheet\s+name="([^"]+)"[^>]*r:id="([^"]+)"/g)) {
		const name = decodeXmlEntities(m[1]);
		const rid = m[2];
		const target = relTargets.get(rid);
		if (!target) throw new Error(`Sheet "${name}" (${rid}) has no workbook.xml.rels target`);
		sheets.push({ name, file: path.join(dir, "xl", target.replace(/^\/?/, "")) });
	}
	return sheets;
}

// ---------- rich-value image chain ----------

function buildImageResolver(dir) {
	const metadataPath = path.join(dir, "xl", "metadata.xml");
	const rdrichvaluePath = path.join(dir, "xl", "richData", "rdrichvalue.xml");
	const richValueRelPath = path.join(dir, "xl", "richData", "richValueRel.xml");
	const richValueRelRelsPath = path.join(dir, "xl", "richData", "_rels", "richValueRel.xml.rels");

	if (![metadataPath, rdrichvaluePath, richValueRelPath, richValueRelRelsPath].every(fs.existsSync)) {
		return () => null; // sheet/workbook has no cell images at all
	}

	// bk index (0-based) -> rv index, via <xlrd:rvb i="M"/> inside each <bk>
	const metadataXml = readFile(metadataPath);
	const bkToRvIndex = [...metadataXml.matchAll(/<bk>.*?<xlrd:rvb i="(\d+)"\s*\/>.*?<\/bk>/gs)].map((m) =>
		parseInt(m[1], 10)
	);

	// rv index (0-based) -> local image identifier K, the FIRST <v> inside each <rv>
	const rdrichvalueXml = readFile(rdrichvaluePath);
	const rvToLocalImageId = [...rdrichvalueXml.matchAll(/<rv[^>]*>\s*<v>(\d+)<\/v>/g)].map((m) => parseInt(m[1], 10));

	// local image id K (0-based position in this list) -> r:id
	const richValueRelXml = readFile(richValueRelPath);
	const localImageIdToRid = [...richValueRelXml.matchAll(/<rel\s+r:id="([^"]+)"\s*\/>/g)].map((m) => m[1]);

	// r:id -> media path (NOT necessarily in rId order in the file -- map, don't assume)
	const relsXml = readFile(richValueRelRelsPath);
	const ridToTarget = new Map();
	for (const m of relsXml.matchAll(/<Relationship\s+Id="([^"]+)"[^>]*Target="([^"]+)"/g)) {
		ridToTarget.set(m[1], m[2]);
	}

	return function resolveVm(vmAttr) {
		const bkIdx = parseInt(vmAttr, 10) - 1;
		const rvIdx = bkToRvIndex[bkIdx];
		if (rvIdx === undefined) return null;
		const localId = rvToLocalImageId[rvIdx];
		if (localId === undefined) return null;
		const rid = localImageIdToRid[localId];
		if (rid === undefined) return null;
		const target = ridToTarget.get(rid);
		if (!target) return null;
		// target is like "../media/image42.png", relative to xl/richData/
		return path.normalize(path.join(dir, "xl", "richData", target));
	};
}

// ---------- worksheet parsing ----------

const FIELD_KEYWORDS = [
	{ field: "obtainedFrom", test: (h) => /obtained/i.test(h) },
	{ field: "releaseDate", test: (h) => /release/i.test(h) },
	{ field: "dyeable", test: (h) => /^dyeable$/i.test(h.trim()) },
	{ field: "glowDyeableCombined", test: (h) => /glow/i.test(h) && /dyeable/i.test(h) },
	{ field: "glowParticles", test: (h) => /glow|particle/i.test(h) },
	{ field: "typeSlot", test: (h) => /type|slot/i.test(h) },
	{ field: "effect", test: (h) => /effect|description|contents/i.test(h) },
	{ field: "name", test: (h) => /^name$/i.test(h.trim()) },
];

function classifyHeader(headerText) {
	for (const { field, test } of FIELD_KEYWORDS) {
		if (test(headerText)) return field;
	}
	return null;
}

/** True if v looks like a plain yes/no answer rather than a glow-particle description. */
function looksLikeYesNo(v) {
	return /^(yes|no)$/i.test(String(v || "").trim());
}

// Excel wraps long cell text with embedded \r\n for display — the original
// JSON was normalized to plain single spaces, so match that here rather than
// reporting every wrapped cell as a "mismatch".
function normalizeWhitespace(s) {
	return s == null ? s : s.replace(/\s+/g, " ").trim();
}

function parseCellValue(attrs, inner, sharedStrings) {
	const inlineMatch = /<is>.*?<t[^>]*>([^<]*)<\/t>/s.exec(inner);
	if (inlineMatch) return normalizeWhitespace(decodeXmlEntities(inlineMatch[1]));

	const isShared = /\st="s"/.test(attrs);
	const vMatch = /<v>([^<]*)<\/v>/.exec(inner);
	if (!vMatch) return null;
	if (isShared) {
		const idx = parseInt(vMatch[1], 10);
		const raw = sharedStrings[idx];
		return raw == null ? null : normalizeWhitespace(raw);
	}
	return normalizeWhitespace(decodeXmlEntities(vMatch[1]));
}

/**
 * Cells can be self-closing (<c r="A1" s="5"/>, no value) or open/close
 * (<c r="B1" s="6" t="s"><v>0</v></c>). A single greedy/lazy regex over the
 * whole row mishandles the self-closing case (it has no </c> of its own, so
 * a naive ".*?</c>" lazily swallows the NEXT cell's content instead). Scan
 * cell-by-cell instead: find each opening tag, and only look for a matching
 * </c> when the tag wasn't self-closed. Safe because cells never nest.
 */
function extractCells(rowInnerXml) {
	const cells = [];
	const openRe = /<c\s+r="([A-Z]+)\d+"([^>]*)>/g;
	let m;
	while ((m = openRe.exec(rowInnerXml)) !== null) {
		const [, col, rawAttrs] = m;
		const selfClosed = rawAttrs.trimEnd().endsWith("/");
		const attrs = selfClosed ? rawAttrs.trimEnd().slice(0, -1) : rawAttrs;
		let inner = "";
		if (!selfClosed) {
			const closeIdx = rowInnerXml.indexOf("</c>", openRe.lastIndex);
			if (closeIdx === -1) break;
			inner = rowInnerXml.slice(openRe.lastIndex, closeIdx);
			openRe.lastIndex = closeIdx + 4;
		}
		cells.push({ col, attrs, inner });
	}
	return cells;
}

function parseSheet(sheetPath, category, sharedStrings, resolveVm) {
	const xml = readFile(sheetPath);
	const rowMatches = [...xml.matchAll(/<row[^>]*r="(\d+)"[^>]*>(.*?)<\/row>/gs)];
	if (rowMatches.length === 0) return [];

	// --- header row (row 1): map column letter -> field name ---
	const headerCells = extractCells(rowMatches[0][2]);
	const colToField = new Map();
	for (const { col, attrs, inner } of headerCells) {
		const text = parseCellValue(attrs, inner, sharedStrings);
		if (!text) continue;
		const field = classifyHeader(text);
		if (field) colToField.set(col, field);
	}

	const entries = [];
	for (let i = 1; i < rowMatches.length; i++) {
		const cells = extractCells(rowMatches[i][2]);
		if (cells.length === 0) continue;

		const row = { category };
		let imagePath = null;

		for (const { col, attrs, inner } of cells) {
			if (col === "A") {
				const vmMatch = /\svm="(\d+)"/.exec(attrs);
				if (vmMatch) imagePath = resolveVm(vmMatch[1]);
				continue;
			}
			const field = colToField.get(col);
			if (!field) continue;
			const value = parseCellValue(attrs, inner, sharedStrings);
			if (value === null) continue;

			if (field === "glowDyeableCombined") {
				// Decor's header combines "Glow/Dyeable" into one column. Split by
				// content shape: a plain yes/no reads as the dyeable answer,
				// anything else (a particle name, "None", ...) reads as glow.
				if (looksLikeYesNo(value)) row.dyeable = value;
				else row.glowParticles = value;
			} else {
				row[field] = value;
			}
		}

		if (!row.name) continue; // header/blank row artifact
		row._imagePath = imagePath;
		entries.push(row);
	}
	return entries;
}

function parseWorkbook(xlsxPath, extractDir) {
	unzipXlsx(xlsxPath, extractDir);
	const sharedStrings = parseSharedStrings(extractDir);
	const resolveVm = buildImageResolver(extractDir);
	const sheets = parseWorkbookSheets(extractDir);

	const all = [];
	for (const sheet of sheets) {
		const entries = parseSheet(sheet.file, sheet.name, sharedStrings, resolveVm);
		all.push(...entries);
	}
	return all;
}

// ---------- id slug (matches existing convention, e.g. "rare-1-hour-fly-token") ----------

function slugify(name) {
	return (
		"rare-" +
		String(name)
			.toLowerCase()
			.replace(/[^a-z0-9]+/g, "-")
			.replace(/^-+|-+$/g, "")
	);
}

function normKey(entry) {
	return `${entry.name.trim().toLowerCase()}|${entry.category.trim().toLowerCase()}`;
}

// ---------- main ----------

function main() {
	console.log("== Parsing OLD file (pipeline self-check) ==");
	const oldExtract = path.join(os.tmpdir(), "sctp-xlsx-old");
	const oldEntries = parseWorkbook(OLD_XLSX, oldExtract);
	console.log(`Parsed ${oldEntries.length} rows from the old workbook.`);

	const currentJson = JSON.parse(fs.readFileSync(RARE_ITEMS_JSON, "utf8"));
	const currentByKey = new Map(currentJson.map((e) => [normKey(e), e]));

	let matched = 0,
		textMismatch = 0,
		missingFromOld = 0;
	const FIELDS_TO_COMPARE = ["effect", "obtainedFrom", "releaseDate", "glowParticles", "typeSlot", "dyeable"];
	for (const e of oldEntries) {
		const key = normKey(e);
		const current = currentByKey.get(key);
		if (!current) {
			missingFromOld++;
			continue;
		}
		matched++;
		const diffFields = FIELDS_TO_COMPARE.filter((f) => (current[f] || null) !== (e[f] || null));
		if (diffFields.length > 0) {
			textMismatch++;
			if (REPORT_DIFFS) {
				console.log(`  [old-file mismatch] ${e.name} (${e.category}): ${diffFields.join(", ")}`);
			}
		}
	}
	console.log(
		`Old-file validation: ${matched}/${currentJson.length} current entries matched by (name, category); ` +
			`${textMismatch} had different text fields; ${missingFromOld} old-file rows not found in current JSON.`
	);
	if (matched < currentJson.length * 0.8) {
		console.error("Old-file match rate looks too low to trust this pipeline on the new file — stopping.");
		process.exit(1);
	}

	console.log("\n== Parsing NEW file ==");
	const newExtract = path.join(os.tmpdir(), "sctp-xlsx-new");
	const newEntries = parseWorkbook(NEW_XLSX, newExtract);
	console.log(`Parsed ${newEntries.length} rows from the new workbook.`);

	const newOnly = [];
	let sameKeyDifferentData = 0;
	for (const e of newEntries) {
		const key = normKey(e);
		if (currentByKey.has(key)) {
			if (REPORT_DIFFS) {
				const current = currentByKey.get(key);
				const diffFields = FIELDS_TO_COMPARE.filter((f) => (current[f] || null) !== (e[f] || null));
				if (diffFields.length > 0) {
					sameKeyDifferentData++;
					console.log(`  [existing, new file differs] ${e.name} (${e.category}): ${diffFields.join(", ")}`);
				}
			}
			continue; // existing entries are left untouched, per the stated import policy
		}
		newOnly.push(e);
	}

	console.log(`\n${newOnly.length} new (name, category) pairs not present in data/rare-items.json.`);
	if (REPORT_DIFFS) {
		console.log(`${sameKeyDifferentData} existing pairs have different field values in the new file (left untouched).`);
	}
	if (newOnly.length > 0) {
		console.log("New items:");
		for (const e of newOnly) console.log(`  - ${e.name} (${e.category})${e._imagePath ? "" : "  [NO IMAGE FOUND]"}`);
	}

	if (!APPLY) {
		console.log("\nDry run only — pass --apply to copy textures and write data/rare-items.json.");
		return;
	}

	if (newOnly.length === 0) {
		console.log("\nNothing to apply.");
		return;
	}

	console.log("\n== Applying ==");

	let existingIds = new Set(currentJson.map((e) => e.id));
	let stateProcessed = new Set();
	if (fs.existsSync(STATE_FILE)) {
		try {
			stateProcessed = new Set(JSON.parse(fs.readFileSync(STATE_FILE, "utf8")).processedKeys || []);
		} catch {
			/* ignore corrupt state file */
		}
	}

	let maxTextureNum = 0;
	for (const f of fs.readdirSync(TEXTURES_DIR)) {
		const m = /^rare_image(\d+)\.png$/.exec(f);
		if (m) maxTextureNum = Math.max(maxTextureNum, parseInt(m[1], 10));
	}

	const appended = [];
	for (const e of newOnly) {
		const key = normKey(e);
		if (stateProcessed.has(key)) {
			console.log(`  skip (already processed in a prior run): ${e.name}`);
			continue;
		}
		let id = slugify(e.name);
		if (existingIds.has(id)) {
			console.log(`  WARNING: id collision for "${e.name}" -> ${id}, skipping`);
			continue;
		}

		let texture = null;
		if (e._imagePath && fs.existsSync(e._imagePath)) {
			maxTextureNum++;
			const destName = `rare_image${maxTextureNum}.png`;
			fs.copyFileSync(e._imagePath, path.join(TEXTURES_DIR, destName));
			texture = `/items/textures/${destName}`;
		} else {
			console.log(`  WARNING: no image found for "${e.name}" — leaving texture null`);
		}

		appended.push({
			id,
			name: e.name,
			category: e.category,
			texture,
			effect: e.effect || null,
			releaseDate: e.releaseDate || null,
			obtainedFrom: e.obtainedFrom || null,
			glowParticles: e.glowParticles || null,
			typeSlot: e.typeSlot || null,
			dyeable: e.dyeable || null,
		});
		existingIds.add(id);
		stateProcessed.add(key);
	}

	if (appended.length > 0) {
		const updated = currentJson.concat(appended);
		fs.writeFileSync(RARE_ITEMS_JSON, JSON.stringify(updated));
		console.log(`\nAppended ${appended.length} new entries to data/rare-items.json.`);
	}

	fs.writeFileSync(STATE_FILE, JSON.stringify({ processedKeys: [...stateProcessed] }));
	console.log("Done.");
}

main();

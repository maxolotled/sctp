#!/usr/bin/env node
"use strict";
/**
 * The Golden Shell: makes a personal puzzle set for every hunter. Everything that
 * can differ between hunters does: every chapter's answer, which rares, maparts,
 * days, items and junk each puzzle uses, where the impostor hides, the torn map
 * piece (place and how it's turned), the chain of things to hold, the riddles.
 * Shared by everyone (it's one vault in one world): the whorl squares, the key,
 * the Apex and the lectern word, plus the physical things in game (the ghost
 * barrels, the junk shop, the job, the shelf, the Butterfly Exchange note's step).
 *
 *   cd scripts/hunt && npm install            (once: sharp, for the torn map pieces)
 *   node scripts/hunt/export-data.js           (fresh rares/prices/maparts first)
 *   node scripts/hunt/generate.js --count=200 [--seed=anything] [--out=scripts/hunt/out/real]
 *   HUNT_ADMIN_TOKEN=... node scripts/hunt/seed.js --sets=scripts/hunt/out/real
 *
 * Writes to the out folder (gitignored: it holds every answer, never commit it):
 *   sets.json     what gets uploaded (answers are hashed by the server)
 *   answers.txt   the answer sheet for every set (seed.js adds the set numbers after upload)
 *   answers.json  the same, for scripts
 *   img/          the torn map pieces, uploaded by seed.js
 *   pieces.png    every torn piece on one sheet: check none of them shows off someone's base
 *
 * A region that must never be a torn piece (a player's base, say) goes in c9-skip.txt
 * as "x, z" (the region numbers the map shows), one per line. Then generate again.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const W = require("./words.js");

// ---------------- arguments ----------------
const arg = (name, def) => { const a = process.argv.find((x) => x.startsWith("--" + name + "=")); return a ? a.slice(name.length + 3) : def; };
const COUNT = Number(arg("count", "10"));
const SEED = arg("seed", crypto.randomBytes(6).toString("hex"));
const OUT = path.resolve(arg("out", path.join(__dirname, "out", "sets-" + SEED)));
const NO_IMAGES = process.argv.includes("--no-images"); // quick dry runs: pick regions, skip drawing them

// ---------------- data ----------------
const ROOT = path.join(__dirname, "../..");
const DATA = path.join(__dirname, "data");
const API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
const rares = JSON.parse(fs.readFileSync(path.join(ROOT, "data/rare-items.json"), "utf8"));
const shelf = JSON.parse(fs.readFileSync(path.join(__dirname, "shellkeeper.json"), "utf8"));
const baseItems = JSON.parse(fs.readFileSync(path.join(DATA, "base-items.json"), "utf8"));
const history = fs.readFileSync(path.join(DATA, "price-history.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l));
const maparts = fs.readFileSync(path.join(DATA, "maparts.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l));
const tileManifest = JSON.parse(fs.readFileSync(path.join(ROOT, "world/tiles.json"), "utf8"));
const skipRegions = new Set((fs.existsSync(path.join(__dirname, "c9-skip.txt")) ? fs.readFileSync(path.join(__dirname, "c9-skip.txt"), "utf8") : "")
	.split("\n").map((l) => (l.match(/-?\d+/g) || []).join(",")).filter((k) => k.includes(",")));

// ---------------- small helpers ----------------
const text = (v) => ({ t: "text", v });
const list = (items, ordered = true) => ({ t: "list", items, ordered });
const assert = (cond, msg) => { if (!cond) throw new Error("check failed: " + msg); };
const ok = (v) => v && v !== "null";
const letters = (s) => String(s).replace(/[^A-Za-z]/g, "");
const letterAt = (s, n) => letters(s)[n - 1];
const cap = (s) => s.replace(/\b\w/g, (c) => c.toUpperCase());
const fmtDate = (d) => new Date(d + "T12:00:00Z").toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "UTC" });
const ORDINALS = ["First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth"];

/** Seeded random numbers (sfc32), so a seed always gives the same sets. */
function rng(seedText) {
	const h = crypto.createHash("sha256").update(seedText).digest();
	let a = h.readUInt32LE(0), b = h.readUInt32LE(4), c = h.readUInt32LE(8), d = h.readUInt32LE(12);
	const next = () => {
		a >>>= 0; b >>>= 0; c >>>= 0; d >>>= 0;
		const t = (a + b) | 0; a = b ^ (b >>> 9); b = (c + (c << 3)) | 0; c = (c << 21) | (c >>> 11); d = (d + 1) | 0;
		const r = (t + d) | 0; c = (c + r) | 0;
		return (r >>> 0) / 4294967296;
	};
	for (let i = 0; i < 12; i++) next();
	const R = {
		next,
		int: (n) => Math.floor(next() * n),
		pick: (arr) => arr[Math.floor(next() * arr.length)],
		shuffle: (arr) => { const o = arr.slice(); for (let i = o.length - 1; i > 0; i--) { const j = Math.floor(next() * (i + 1)); [o[i], o[j]] = [o[j], o[i]]; } return o; },
	};
	return R;
}

/** Positions (1-based) where a letter sits in a name, counting letters only, within the first `within`. */
function positionsOf(name, ch, within) {
	const l = letters(name).toLowerCase(), out = [];
	for (let i = 0; i < Math.min(within, l.length); i++) if (l[i] === ch) out.push(i + 1);
	return out;
}

// ---------------- answers: each chapter's answers are unique across sets while the pool lasts ----------------
const usedAnswers = new Map(); // chapter -> Set
/**
 * Picks a word for a chapter: unused by other sets if possible, not used by this set's
 * other chapters, and accepted by `fits` (the puzzle can actually spell it).
 */
function pickWord(R, chapter, pool, fits, setWords) {
	if (!usedAnswers.has(chapter)) usedAnswers.set(chapter, new Set());
	const used = usedAnswers.get(chapter);
	const order = R.shuffle(pool);
	for (const pass of [0, 1]) {
		for (const w of order) {
			if (setWords.has(w) || (pass === 0 && used.has(w))) continue;
			const built = fits(w);
			if (built) { used.add(w); setWords.add(w); return { word: w, built }; }
		}
	}
	throw new Error(chapter + ": no word fits");
}
const pool = (...lengths) => lengths.flatMap((n) => W.wordsOfLength(n));

// ================ the puzzles ================

// ---- c1 The Impostor: eight honest rare descriptions and one that fits no rare ----
const descKey = (r) => [r.category, r.releaseDate, r.obtainedFrom, r.typeSlot].join("|").toLowerCase();
const descCounts = new Map();
for (const r of rares) descCounts.set(descKey(r), (descCounts.get(descKey(r)) || 0) + 1);
const describable = rares.filter((r) => ok(r.category) && ok(r.releaseDate) && ok(r.obtainedFrom) && ok(r.typeSlot) && descCounts.get(descKey(r)) === 1 && !/^\d/.test(r.name));
const describe = (n, cat, from, rel, slot) => `[${n}] ${cat}, from ${from}, released ${rel}, ${slot}.`;
function impostor(R, word) {
	const used = new Set(), lines = [];
	for (const ch of word.toLowerCase()) {
		const cands = describable.filter((r) => !used.has(r.id) && positionsOf(r.name, ch, 10).length);
		if (!cands.length) return null;
		const r = R.pick(cands);
		used.add(r.id);
		const n = R.pick(positionsOf(r.name, ch, 10));
		lines.push({ n, r, text: describe(n, r.category, r.obtainedFrom, r.releaseDate, r.typeSlot) });
	}
	// the liar: fields borrowed from two real rares, in a combination no rare has
	let fake = null;
	for (let tries = 0; tries < 500 && !fake; tries++) {
		const a = R.pick(describable), b = R.pick(describable);
		if (a.id === b.id || a.category === b.category) continue;
		const k = [a.category, b.releaseDate, b.obtainedFrom, a.typeSlot].join("|").toLowerCase();
		if (descCounts.has(k)) continue;
		fake = describe(1 + R.int(8), a.category, b.obtainedFrom, b.releaseDate, a.typeSlot);
	}
	if (!fake) return null;
	assert(lines.map((l) => letterAt(l.r.name, l.n)).join("").toLowerCase() === word.toLowerCase(), "c1 word");
	const at = R.int(lines.length + 1);
	const items = lines.map((l) => l.text);
	items.splice(at, 0, fake);
	return { items, sheet: lines.map((l) => `${l.r.name} [${l.n}] ${letterAt(l.r.name, l.n).toUpperCase()}`).join(" · ") + ` · the impostor is line ${at + 1}` };
}

// ---- c2 Same Bones: rares on one base item, positions in the A-Z list ----
const byId = new Map(rares.map((r) => [r.id, r]));
const BONES = (() => {
	const groups = new Map();
	for (const [id, base] of Object.entries(baseItems)) { const r = byId.get(id); if (!r) continue; if (!groups.has(base)) groups.set(base, []); groups.get(base).push(r); }
	const out = [];
	for (const [base, rs] of groups) {
		const names = rs.map((r) => r.name).sort((a, b) => a.localeCompare(b));
		if (names.length < 9 || names.length > 60 || new Set(names.map((n) => n.toLowerCase())).size !== names.length) continue;
		// (a rare added to the base later shifts positions: the server also checks the live list, see secret:bones)
		out.push({ base, names });
	}
	return out;
})();
function sameBones(R, base, word) {
	const positions = [];
	for (const ch of word.toLowerCase()) {
		const idx = base.names.map((n, i) => (n.toLowerCase().startsWith(ch) ? i + 1 : 0)).filter(Boolean);
		if (!idx.length) return null;
		positions.push(R.pick(idx));
	}
	const others = base.names.filter((_, i) => !positions.includes(i + 1));
	if (others.length < 4) return null;
	const shown = R.shuffle(others).slice(0, 4);
	assert(positions.map((p) => base.names[p - 1][0]).join("").toLowerCase() === word.toLowerCase(), "c2 word");
	return { base: base.base, shown, positions, sheet: `base ${base.base}, A-Z: ${base.names.join(" | ")}, positions ${positions.join(" ")}` };
}

// ---- c3 Cross Yourself: a chain of things to hold, starting with a clock ----
function crossYourself(R, word) {
	const chain = ["clock", ...R.shuffle(W.HOLDABLES.filter(([id]) => id !== "clock").map(([id]) => id)).slice(0, word.length - 1)];
	const riddle = new Map(W.HOLDABLES);
	const notes = chain.map((id, i) => ({
		context: "item:minecraft:" + id, chapterId: "c3",
		text: ORDINALS[i] + ": " + word[i] + ". " + (i + 1 < chain.length ? "Next, hold " + riddle.get(chain[i + 1]) + "." : "That was the last."),
	}));
	return { notes, sheet: chain.map((id, i) => id + " " + word[i]).join(", ") + " (X+I while holding each)" };
}

// ---- c4 Daily Bread: Rare-dle boards; first letters of their secret rares ----
// Same rule as Rare-dle itself (raredleDerivedIds in worker.js): never a rare made from another rare
const RAREDLE_EXCLUDED_SOURCES = ["Pocket Pals Claw Machine", "Gills' Fishing Rod", "May the 4th Plushie Chance Box", "Easter 2025 Event", "D6",
	"Menagerie Sword", "White Cat & Associates Chance Box", "May the 4th Chance Box", "Archangel Armor Set"];
const BOARD_RARES = (() => {
	const norm = (s) => String(s == null ? "" : s).toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
	const names = new Map();
	for (const i of rares) { const n = norm(i.name); if (n.length < 4) continue; if (!names.has(n)) names.set(n, []); names.get(n).push(i.id); }
	const derived = new Set();
	for (const i of rares) {
		const src = norm(i.obtainedFrom);
		if (!src || src === "null") continue;
		const words = src.split(" ");
		let found = false;
		for (let a = 0; a < words.length && !found; a++) for (let b = a; b < Math.min(words.length, a + 8) && !found; b++) {
			const ids = names.get(words.slice(a, b + 1).join(" "));
			if (ids && ids.some((id) => id !== i.id)) found = true;
		}
		if (found) derived.add(i.id);
	}
	const excluded = new Set(RAREDLE_EXCLUDED_SOURCES.map(norm));
	for (const i of rares) if (String(i.obtainedFrom == null ? "" : i.obtainedFrom).split(/\s*[/,]\s*/).some((p) => excluded.has(norm(p)))) derived.add(i.id);
	return rares.filter((x) => ok(x.texture) && ok(x.category) && ok(x.releaseDate) && ok(x.obtainedFrom) && ok(x.typeSlot) && !derived.has(x.id) && /^[a-z]/i.test(x.name) && !/plush|token|key/i.test(x.name));
})();
function boards(R, word) {
	const used = new Set(), picks = [];
	for (const ch of word.toLowerCase()) {
		const cands = BOARD_RARES.filter((x) => !used.has(x.id) && x.name.toLowerCase().startsWith(ch));
		if (!cands.length) return null;
		const r = R.pick(cands);
		used.add(r.id);
		picks.push(r);
	}
	return { picks, sheet: "boards " + picks.map((r) => r.name).join(", ") };
}

// ---- c5 Strange Prices: the junk shop, price in diamonds = letter (1 is A) ----
const JUNK = ["Dead Bush", "Poisonous Potato", "Rotten Flesh", "Spider Eye", "Bone", "String", "Stick", "Dirt", "Gravel", "Cobblestone", "Wheat Seeds", "Kelp", "Pufferfish",
	"Lily Pad", "Clay Ball", "Feather", "Leather", "Ink Sac", "Flint", "Snowball", "Egg", "Sugar Cane", "Cactus", "Brown Mushroom", "Red Mushroom", "Fern"];
function strangePrices(word) {
	const items = [...word.toUpperCase()].map((c) => JUNK[c.charCodeAt(0) - 65]);
	return { items, sheet: items.map((i) => i + " " + (JUNK.indexOf(i) + 1)).join(", ") };
}

// ---- c7 Days Gone By: four of a kind, one world, one day: which was cheapest? ----
// Prices as the item page shows them: lowest price per stack of 64, rounded to 2 decimals.
const stack = (p) => Math.round(p * 64 * 100) / 100;
const DAY_ROWS = (() => {
	const out = new Map(); // "date|world" -> [{name, p}]
	for (const h of history) {
		if (!(h.lowestPrice > 0)) continue;
		const k = h.date + "|" + h.world;
		if (!out.has(k)) out.set(k, []);
		out.get(k).push({ name: h.itemKey.split("|")[1], p: stack(h.lowestPrice) });
	}
	return out;
})();
const DAYS = [...new Set(history.map((h) => h.date))].sort();
const LATEST = DAYS[DAYS.length - 1];
const latestPrice = (world, name) => { const x = (DAY_ROWS.get(LATEST + "|" + world) || []).find((y) => y.name === name); return x ? x.p : null; };
// Four of a kind per question (four dyes, four tulips...), so the answer can't be guessed from the names
const KIND_PREFIXES = ["light blue", "light gray", "white", "orange", "magenta", "yellow", "lime", "pink", "gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
	"dark oak", "pale oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped"];
const kindOf = (name) => { for (const p of KIND_PREFIXES) if (name.startsWith(p + " ")) return name.slice(p.length + 1); return null; };
function daysGoneBy(R, word) {
	// older days only: the newest prices are what hunters see first
	const older = DAYS.slice(0, -3);
	const usedKinds = new Set(), usedDays = new Set(), qs = [];
	for (const chRaw of word) {
		const ch = chRaw.toLowerCase();
		let q = null;
		for (const date of R.shuffle(older.filter((d) => !usedDays.has(d)))) {
			const world = R.pick(["Firefly", "Honeybee"]);
			const kinds = new Map();
			for (const x of DAY_ROWS.get(date + "|" + world) || []) { const k = kindOf(x.name); if (!k || usedKinds.has(k)) continue; if (!kinds.has(k)) kinds.set(k, []); kinds.get(k).push(x); }
			for (const cheap of R.shuffle([...kinds.values()].filter((l) => l.length >= 4).flat().filter((x) => x.p >= 0.05 && positionsOf(x.name, ch, 12).length))) {
				// the cheapest of its four that day, but not by a mile (1.5x to 6x)
				const rivals = kinds.get(kindOf(cheap.name)).filter((x) => x !== cheap && x.p >= cheap.p * 1.5 && x.p <= cheap.p * 6);
				if (rivals.length < 3) continue;
				// and not the cheapest of the four any more, so that day has to be looked up
				const pick = R.shuffle(rivals).slice(0, 3);
				const now = latestPrice(world, cheap.name);
				if (now != null && !pick.some((x) => { const p = latestPrice(world, x.name); return p != null && p <= now; })) continue;
				q = { date, world, cheap, others: pick, n: R.pick(positionsOf(cheap.name, ch, 12)) };
				break;
			}
			if (q) break;
		}
		if (!q) return null;
		usedKinds.add(kindOf(q.cheap.name));
		usedDays.add(q.date);
		qs.push({ date: q.date, world: q.world, group: [q.cheap, ...q.others].map((x) => cap(x.name)).sort(), n: q.n, answer: cap(q.cheap.name),
			prices: [q.cheap, ...q.others].map((x) => cap(x.name) + " " + x.p).join(", ") });
	}
	assert(qs.map((q) => letterAt(q.answer, q.n)).join("").toLowerCase() === word.toLowerCase(), "c7 word");
	return { qs, sheet: qs.map((q) => `${q.world} ${q.date}: ${q.answer} [${q.n}] (per stack: ${q.prices})`).join(" · ") };
}

// ---- c8 Wanted: A Reader: the job's code, shifted back by a personal key ----
function readerKey(word) {
	const code = shelf.job.code;
	assert(code.length === word.length, "c8 length");
	const key = [...word.toUpperCase()].map((c, i) => (code.charCodeAt(i) - c.charCodeAt(0) + 26) % 26);
	const back = [...code].map((c, i) => String.fromCharCode(((c.charCodeAt(0) - 65 - key[i] + 26) % 26) + 65)).join("");
	assert(back === word.toUpperCase(), "c8 decode");
	return { key, sheet: `job code ${code}, key ${key.join(" ")}` };
}

// ---- c10 The Gallery: maparts by size, category, artist and world (one match in the whole catalog) ----
const GALLERY = (() => {
	const uniq = new Map();
	for (const m of maparts) {
		if (!m.title || /^\//.test(m.title) || !m.artist || !m.category) continue;
		const k = [m.artist, m.category, m.width + "x" + m.height, m.world].join("|").toLowerCase();
		uniq.set(k, uniq.has(k) ? null : m);
	}
	return [...uniq.values()].filter((m) => m && m.locked); // locked: no takedowns mid-hunt
})();
function gallery(R, word) {
	const used = new Set(), out = [];
	for (const ch of word.toLowerCase()) {
		const cands = GALLERY.filter((x) => !used.has(x.slug) && positionsOf(x.title, ch, 8).length);
		if (!cands.length) return null;
		const m = R.pick(cands);
		used.add(m.slug);
		out.push({ m, n: R.pick(positionsOf(m.title, ch, 8)) });
	}
	assert(out.map((o) => letterAt(o.m.title, o.n)).join("").toLowerCase() === word.toLowerCase(), "c10 word");
	return {
		items: out.map((o) => `[${o.n}] A ${o.m.width}×${o.m.height} ${o.m.category.toLowerCase()} piece by ${o.m.artist}, on ${o.m.world}.`),
		sheet: out.map((o) => `${o.m.title} (${o.m.artist}) [${o.n}]`).join(" · "),
	};
}

// ---- c11 The Shelf: H = what I have, W = what I want, each A to Z ----
const HAVE = shelf.collection.map((x) => x.name).sort((a, b) => a.localeCompare(b));
const WANT = shelf.wishlist.map((x) => x.name).sort((a, b) => a.localeCompare(b));
function theShelf(R, word) {
	const refs = [];
	for (const ch of word.toLowerCase()) {
		const opts = [["H", HAVE], ["W", WANT]].flatMap(([tag, l]) => l.map((n, i) => (n.toLowerCase().startsWith(ch) ? tag + (i + 1) : null)).filter(Boolean));
		if (!opts.length) return null;
		refs.push(R.pick(opts));
	}
	const read = refs.map((r) => (r[0] === "H" ? HAVE : WANT)[Number(r.slice(1)) - 1][0]).join("");
	assert(read.toLowerCase() === word.toLowerCase(), "c11 word");
	return { refs, sheet: refs.join(" ") };
}

// ---- c13 Wings for Coin: every 9th letter (nine kinds of butterfly for sale) ----
function wings(R, word, step = 9) {
	const abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	let out = "";
	for (let i = 0; i < word.length * step; i++) out += i % step === 0 ? word[i / step] : abc[R.int(26)];
	assert([...out].filter((_, i) => i % step === 0).join("") === word, "c13");
	return out.match(/.{1,5}/g).join(" ");
}

// ---- c14 / c15: a riddle, or a little-known word in a simple cipher ----
function littlePuzzle(R, chapter, setWords) {
	const kind = R.pick(["riddle", "shift", "shift", "mirror", "numbers"]);
	if (kind === "riddle") {
		const used = usedAnswers.get(chapter + ":riddle") || new Set();
		usedAnswers.set(chapter + ":riddle", used);
		const fresh = W.RIDDLES.filter(([a]) => !setWords.has(a) && !used.has(a));
		const [answer, riddle] = R.pick(fresh.length ? fresh : W.RIDDLES.filter(([a]) => !setWords.has(a)));
		used.add(answer);
		setWords.add(answer);
		return { answer, text: riddle + " " + answer.length + " letters.", how: "riddle" };
	}
	const { word } = pickWord(R, chapter, pool(5, 6, 7), () => true, setWords);
	const A = 65;
	if (kind === "shift") {
		const k = 1 + R.int(4);
		const enc = [...word].map((c) => String.fromCharCode(((c.charCodeAt(0) - A + k) % 26) + A)).join("");
		return { answer: word, text: `Every letter here walked ${k} step${k > 1 ? "s" : ""} forward through the alphabet. Walk them back: ${enc}`, how: `each letter ${k} back` };
	}
	if (kind === "mirror") {
		const enc = [...word].map((c) => String.fromCharCode(A + 25 - (c.charCodeAt(0) - A))).join("");
		return { answer: word, text: `A is Z, B is Y, and so on, all the way to the middle: ${enc}`, how: "A=Z mirror" };
	}
	return { answer: word, text: [...word].map((c) => c.charCodeAt(0) - A + 1).join(" · ") + " (A is 1)", how: "letter numbers" };
}

// ---- c9 The Torn Corner: 3x3 map regions, turned and/or flipped; answer = the centre region ----
const OFFSET = tileManifest.offset;
const HAVE_TILE = new Set(tileManifest.tiles["0"]);
const REGION_OK = (rx, rz) => {
	for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) if (!HAVE_TILE.has((rx + dx + OFFSET) + "_" + (rz + dz + OFFSET))) return false;
	return !skipRegions.has(rx + "," + rz);
};
const REGION_CANDS = tileManifest.tiles["0"].map((k) => k.split("_").map((n) => Number(n) - OFFSET)).filter(([rx, rz]) => REGION_OK(rx, rz));
const TURNS = [
	{ rotate: 90, flip: false, say: "turned a quarter clockwise" }, { rotate: 180, flip: false, say: "upside down" },
	{ rotate: 270, flip: false, say: "turned a quarter anticlockwise" }, { rotate: 0, flip: true, say: "flipped top to bottom" },
	{ rotate: 90, flip: true, say: "turned a quarter and flipped" }, { rotate: 180, flip: true, say: "upside down and flipped" },
	{ rotate: 270, flip: true, say: "turned three quarters and flipped" }, { rotate: 0, flop: true, say: "mirrored left to right" },
];
const TILE_CACHE = path.join(__dirname, ".tile-cache");
async function tile(tx, tz) {
	const f = path.join(TILE_CACHE, tx + "_" + tz + ".webp");
	if (!fs.existsSync(f)) {
		const res = await fetch(API + "/world-tiles/v1/0/" + tx + "_" + tz + ".webp");
		if (!res.ok) throw new Error("tile " + tx + "_" + tz + ": " + res.status);
		fs.mkdirSync(TILE_CACHE, { recursive: true });
		fs.writeFileSync(f, Buffer.from(await res.arrayBuffer()));
	}
	return f;
}
let sharp = null;
async function regionImage(rx, rz) {
	const parts = [];
	for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
		parts.push({ input: await sharp(await tile(rx + dx + OFFSET, rz + dz + OFFSET)).png().toBuffer(), left: (dx + 1) * 512, top: (dz + 1) * 512 });
	}
	return sharp({ create: { width: 1536, height: 1536, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } }).composite(parts).raw().toBuffer();
}
/** A piece worth finding: almost fully drawn, with some coast (all-forest or all-sea pieces are hopeless). */
function interesting(raw) {
	let blank = 0, water = 0, n = 0;
	for (let i = 0; i < raw.length; i += 4 * 7) {
		n++;
		const r = raw[i], g = raw[i + 1], b = raw[i + 2];
		// unmapped, or near-black (big dark builds and void platforms look like holes in the map)
		if (raw[i + 3] < 128 || r + g + b < 75) { blank++; continue; }
		if (b > r + 25 && b >= g) water++;
	}
	const drawn = n - blank;
	return blank / n < 0.01 && water / drawn > 0.12 && water / drawn < 0.75;
}
const usedRegions = [];
async function tornCorner(R) {
	for (let tries = 0; tries < 400; tries++) {
		const [rx, rz] = R.pick(REGION_CANDS);
		// far from every other hunter's piece, so no two pieces overlap
		if (usedRegions.some(([x, z]) => Math.abs(x - rx) < 4 && Math.abs(z - rz) < 4)) continue;
		if (NO_IMAGES) { usedRegions.push([rx, rz]); return { region: [rx, rz], turn: R.pick(TURNS), image: null }; }
		const raw = await regionImage(rx, rz);
		if (!interesting(raw)) continue;
		usedRegions.push([rx, rz]);
		const turn = R.pick(TURNS);
		let img = sharp(raw, { raw: { width: 1536, height: 1536, channels: 4 } }).flatten({ background: "#0A0F0C" });
		if (turn.rotate) img = img.rotate(turn.rotate);
		if (turn.flip) img = img.flip();
		if (turn.flop) img = img.flop();
		const key = "torn-" + crypto.randomBytes(9).toString("hex");
		const file = path.join(OUT, "img", key + ".webp");
		await sharp(await img.png().toBuffer()).resize(768, 768).webp({ quality: 88 }).toFile(file);
		return { region: [rx, rz], turn, image: "img/" + key + ".webp", file };
	}
	throw new Error("c9: no region left (loosen interesting() or clear c9-skip.txt)");
}

// ---- c16 Two Worlds: a word in two halves ----
const HALF_NOTES = [["First half: ", "Second half: "], ["It starts with ", "It ends with "], ["The beginning: ", "The end: "], ["Before: ", "After: "]];

// ================ one set ================
async function makeSet(index) {
	const R = rng(SEED + ":" + index);
	const setWords = new Set();
	const sheet = {};
	const ch = {};
	const notes = [];

	const c1 = pickWord(R, "c1", pool(8), (w) => impostor(R, w), setWords);
	ch.c1 = { kind: "word", answer: c1.word, content: [text("Nine treasures, in my own hand."), list(c1.built.items)] };
	sheet.c1 = c1.word + ": " + c1.built.sheet;

	const c2 = pickWord(R, "c2", pool(4, 5), (w) => sameBones(R, R.pick(BONES), w), setWords);
	ch.c2 = { kind: "word", answer: c2.word, content: [text("Four of mine: " + c2.built.shown.join(", ") + "."), text("Numbers: " + c2.built.positions.join(" · "))] };
	notes.push({ context: "secret:bones", chapterId: "c2", text: JSON.stringify({ base: c2.built.base, positions: c2.built.positions }) });
	sheet.c2 = c2.word + ": " + c2.built.sheet;

	const c3 = pickWord(R, "c3", pool(6, 7), (w) => crossYourself(R, w), setWords);
	ch.c3 = { kind: "word", answer: c3.word, content: [text(`${c3.word.length} letters, ${c3.word.length} things to hold. The first is the hour.`)] };
	notes.push(...c3.built.notes);
	sheet.c3 = c3.word + ": " + c3.built.sheet;

	const c4 = pickWord(R, "c4", pool(5), (w) => boards(R, w), setWords);
	ch.c4 = { kind: "word", answer: c4.word, content: [text("Five boards. Their secret rares' first letters, board by board."), { t: "link", href: "/rare-dle/?mode=hunt", v: "Play your boards" }] };
	c4.built.picks.forEach((r, i) => notes.push({ context: "secret:raredle:" + (i + 1), chapterId: "c4", text: r.id }));
	sheet.c4 = c4.word + ": " + c4.built.sheet;

	const c5 = pickWord(R, "c5", pool(5, 6, 7), (w) => strangePrices(w), setWords);
	ch.c5 = { kind: "word", answer: c5.word, content: [text("What nobody wants, in the order I want it:"), list(c5.built.items)] };
	sheet.c5 = c5.word + ": " + c5.built.sheet;

	const barrels = shelf.ghostBarrels.barrels.slice().sort((a, b) => a.stock - b.stock);
	const c6 = pickWord(R, "c6", pool(barrels.length), () => true, setWords);
	ch.c6 = { kind: "word", answer: c6.word, content: [text("Eight ghosts on Firefly. Eight letters, if you ask them in the right order.")] };
	barrels.forEach((b, i) => notes.push({ context: "chest:Firefly:" + b.pos.replace(/ /g, ""), chapterId: "c6", text: c6.word[i] }));
	sheet.c6 = c6.word + ": barrels by stock " + barrels.map((b, i) => `${b.stock}=${c6.word[i]}`).join(" ");

	const c7 = pickWord(R, "c7", pool(5), (w) => daysGoneBy(R, w), setWords);
	ch.c7 = { kind: "word", answer: c7.word, content: [text("Five questions, five days, five letters."), list(c7.built.qs.map((q) => `${q.world}, ${fmtDate(q.date)}: which was cheapest: ${q.group.join(", ")}? [${q.n}]`))] };
	sheet.c7 = c7.word + ": " + c7.built.sheet;

	const c8 = pickWord(R, "c8", pool(shelf.job.code.length), (w) => readerKey(w), setWords);
	ch.c8 = { kind: "word", answer: c8.word, content: [text("Somebody is hiring on the marketplace. Apply, and read what comes back.")] };
	notes.push({ context: "secret:job", chapterId: "c8", text: "Thank you for applying. Shift each letter of my description back by: " + c8.built.key.join(" ") });
	sheet.c8 = c8.word + ": " + c8.built.sheet;

	const c9 = await tornCorner(R);
	ch.c9 = { kind: "numbers", answer: c9.region.join(", "), content: [{ t: "image", key: c9.image || "img/missing.webp", alt: "A torn piece of the Firefly map" }, text("Where does this belong? The region at its centre, please: two numbers.")] };
	sheet.c9 = c9.region.join(", ") + ": the 3x3 regions around " + c9.region.join(", ") + ", " + c9.turn.say;

	const c10 = pickWord(R, "c10", pool(5), (w) => gallery(R, w), setWords);
	ch.c10 = { kind: "word", answer: c10.word, content: [text("Five pieces I remember, and one letter from each name."), list(c10.built.items)] };
	sheet.c10 = c10.word + ": " + c10.built.sheet;

	const c11 = pickWord(R, "c11", pool(4, 5, 6), (w) => theShelf(R, w), setWords);
	ch.c11 = { kind: "word", answer: c11.word, content: [text(c11.built.refs.join(" · "))] };
	sheet.c11 = c11.word + ": " + c11.built.sheet;

	const c12 = pickWord(R, "c12", W.ANAGRAM_WORDS.filter((w) => !W.BANNED.has(w)), () => true, setWords);
	const scattered = [...c12.word].sort().join(" ");
	ch.c12 = { kind: "word", answer: c12.word, content: [text("Not every page is signposted.")] };
	notes.push({ context: "door:fireflies", chapterId: "c12", text: "You found a door with no sign. Behind it, scattered letters: " + scattered });
	sheet.c12 = c12.word + ": sctp.nl/fireflies shows " + scattered;

	const c13 = pickWord(R, "c13", pool(5, 6), () => true, setWords);
	ch.c13 = { kind: "word", answer: c13.word, content: [text("Where wings are sold, I left you something to read.")] };
	notes.push({ context: "butterfly", chapterId: "c13", text: wings(R, c13.word) });
	sheet.c13 = c13.word + ": every 9th letter of the Butterfly Exchange note, starting with the first";

	const junk = R.pick(JUNK);
	const c14 = littlePuzzle(R, "c14", setWords);
	ch.c14 = {
		kind: "word", answer: c14.answer, content: [text(`I sell what nobody wants. Keep an eye on the one that costs ${JUNK.indexOf(junk) + 1}.`)],
		hints: [null, null, `Watch the ${junk}. My note sits in the corner of the mod's Watchlist screen.`],
	};
	notes.push({ context: "watch:" + junk.toLowerCase(), chapterId: "c14", text: c14.text });
	sheet.c14 = `${c14.answer}: watch the ${junk} (${JUNK.indexOf(junk) + 1} dia); ${c14.how}: ${c14.text}`;

	const c15 = littlePuzzle(R, "c15", setWords);
	ch.c15 = { kind: "word", answer: c15.answer, content: [text("Ask the ledger in your game for me.")] };
	notes.push({ context: "search:shellkeeper", chapterId: "c15", text: c15.text }, { context: "search:theshellkeeper", chapterId: "c15", text: c15.text });
	sheet.c15 = `${c15.answer}: search Shellkeeper in the mod; ${c15.how}: ${c15.text}`;

	const halves = new Map(W.COMPOUNDS.map(([a, b]) => [a + b, [a, b]]));
	const c16 = pickWord(R, "c16", [...halves.keys()], () => true, setWords);
	const [h1, h2] = halves.get(c16.word);
	const say = R.pick(HALF_NOTES);
	ch.c16 = { kind: "word", answer: h1 + h2, content: [text("Two worlds, one word.")] };
	notes.push({ context: "home:Firefly", chapterId: "c16", text: say[0] + h1 }, { context: "home:Honeybee", chapterId: "c16", text: say[1] + h2 });
	sheet.c16 = `${h1 + h2}: the mod's home screen: ${h1} on Firefly, ${h2} on Honeybee`;

	// every answer is different within the set
	const answers = Object.values(ch).map((c) => String(c.answer).toUpperCase());
	assert(new Set(answers).size === answers.length, "set " + index + ": repeated answer");
	return { id: "g" + String(index + 1).padStart(3, "0"), set: { chapters: ch, notes }, sheet, answers: Object.fromEntries(Object.entries(ch).map(([k, c]) => [k, c.answer])), c9 };
}

// ================ all sets ================
// (run directly; other scripts can require() this file for single puzzles)
module.exports = { rng, daysGoneBy, fmtDate };
if (require.main === module) (async () => {
	assert(COUNT > 0 && COUNT <= 1000, "--count between 1 and 1000");
	if (!NO_IMAGES) {
		try { sharp = require("sharp"); } catch (e) { console.error("sharp is missing: run `npm install` in scripts/hunt first (or use --no-images for a dry run)."); process.exit(1); }
	}
	fs.mkdirSync(path.join(OUT, "img"), { recursive: true });
	console.log(`seed ${SEED}: ${COUNT} sets -> ${path.relative(process.cwd(), OUT)}`);
	console.log(`pools: ${describable.length} describable rares, ${BONES.length} base items, ${BOARD_RARES.length} board rares, ${DAYS.length} days, ${GALLERY.length} maparts, ${REGION_CANDS.length} map regions`);

	const out = [];
	for (let i = 0; i < COUNT; i++) {
		out.push(await makeSet(i));
		if ((i + 1) % 10 === 0 || i + 1 === COUNT) process.stdout.write(`\r${i + 1}/${COUNT}`);
	}
	console.log("");

	fs.writeFileSync(path.join(OUT, "sets.json"), JSON.stringify({ seed: SEED, sets: out.map((s) => ({ id: s.id, ...s.set })) }));
	fs.writeFileSync(path.join(OUT, "answers.json"), JSON.stringify(out.map((s) => ({ id: s.id, setNo: null, answers: s.answers, sheet: s.sheet })), null, "\t"));
	writeAnswerSheet(out);

	// how often answers repeat between sets (lower is better: shared answers can be passed around)
	const dupes = [];
	for (const c of Object.keys(out[0].answers)) {
		const seen = new Map();
		for (const s of out) seen.set(s.answers[c], (seen.get(s.answers[c]) || 0) + 1);
		const repeated = [...seen.values()].filter((n) => n > 1).reduce((a, n) => a + n, 0);
		if (repeated) dupes.push(`${c}: ${repeated}`);
	}
	console.log("answers shared with another set: " + (dupes.length ? dupes.join(", ") : "none"));

	if (!NO_IMAGES) await contactSheet(out);
	console.log("done. Check pieces.png, then upload with seed.js --sets=" + path.relative(process.cwd(), OUT));
})().catch((e) => { console.error(e.stack || e.message); process.exit(1); });

function writeAnswerSheet(out) {
	const lines = [`The Golden Shell: answer sheets (seed ${SEED}). PRIVATE: never commit or share this file.`, ""];
	for (const s of out) {
		lines.push(`== ${s.id}${s.setNo ? " (set " + s.setNo + ")" : ""} ==`);
		for (const [k, v] of Object.entries(s.sheet)) lines.push(`${k.padEnd(4)} ${v}`);
		lines.push("");
	}
	fs.writeFileSync(path.join(OUT, "answers.txt"), lines.join("\n"));
}

/** Every torn piece on one picture, labelled with its set and region, to look over before uploading. */
async function contactSheet(out) {
	const S = 160, COLS = 10, rows = Math.ceil(out.length / COLS);
	const parts = [];
	for (let i = 0; i < out.length; i++) {
		const s = out[i];
		const label = `<svg width="${S}" height="${S}"><rect x="0" y="${S - 18}" width="${S}" height="18" fill="black" fill-opacity="0.7"/>` +
			`<text x="4" y="${S - 5}" font-family="Arial" font-size="12" fill="white">${s.id}  ${s.c9.region.join(", ")}</text></svg>`;
		// shown the right way round (as on the map), so a base is easy to recognise
		const raw = await regionImage(s.c9.region[0], s.c9.region[1]);
		const thumb = await sharp(raw, { raw: { width: 1536, height: 1536, channels: 4 } }).flatten({ background: "#0A0F0C" }).resize(S, S)
			.composite([{ input: Buffer.from(label) }]).png().toBuffer();
		parts.push({ input: thumb, left: (i % COLS) * S, top: Math.floor(i / COLS) * S });
	}
	await sharp({ create: { width: COLS * S, height: rows * S, channels: 3, background: "#000" } }).composite(parts).png().toFile(path.join(OUT, "pieces.png"));
}

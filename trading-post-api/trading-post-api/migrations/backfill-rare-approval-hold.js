// One-time script: retroactively applies the rare-item price-approval hold
// (see handleUploadListings' isCheapRare check in worker.js) against listings
// already live in D1, for items in the curated rare-items.json catalog
// (data/rare-items.json, generated from Snailcraft Items.xlsx — the site's
// actual "rares category") priced under 1 diamond block (9 diamonds).
//
// A decorative custom-named item built from an ordinary vanilla material
// (e.g. leather sold as a plushie) IS a legitimate rare on this server — an
// earlier version of this script wrongly excluded those by classifying
// "rare" via baseItem instead of matching the real catalog by name, and
// swept 5,747 listings into approval that were mostly normal build-kit
// shops. That run was reverted (see revert-rare-approval-hold-20260909.sql).
// This version matches the catalog's own name list instead, same primitive
// worker.js's getRareNameSet()/isCheapRare already use for new uploads.
//
// Two exclusions on top of the catalog+price match, per instruction:
//   - Plushies (catalog typeSlot === "Plushie") are skipped outright — they're
//     intentionally sold cheap as a matter of course on this server, not a
//     pricing mistake worth reviewing.
//   - Any item name with 2+ listings already priced under 9 diamonds is
//     skipped entirely (not just deduped to one) — multiple sellers
//     independently agreeing on a low price means that's just the going
//     rate for that item, not an isolated mispriced/scam listing. Only an
//     item that's cheap in exactly ONE listing site-wide gets held.
//
// Also excludes: currency "display" (not a real sale) and price exactly 0
// (not a real sale).
//
// Reads the live public /listings and /data/rare-items.json endpoints,
// computes matches, and writes a SQL file pairing a DELETE from `listings`
// with an INSERT into `pendingRareApprovals` for each — run once via
// `wrangler d1 execute --remote --file=`. Safe to re-run later (e.g.
// periodically, since which items are "isolated cheap" changes over time) —
// each run only reflects whatever's live at the time.
"use strict";

const fs = require("fs");
const path = require("path");

const LISTINGS_URL = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev/listings";
const RARE_ITEMS_URL = "https://sctp.nl/data/rare-items.json";
const OUT_FILE = path.resolve(__dirname, "backfill-rare-approval-hold.sql");

const CURRENCY_VALUE = {
	diamond: 1, diamondblock: 9,
	iron: 1 / 64, ironingot: 1 / 64, ironblock: 9 / 64,
	gold: 1 / 18, goldingot: 1 / 18, goldblock: 9 / 18,
	netherite: 18, netheriteingot: 18, netheriteblock: 162,
};
function priceInDiamonds(r) {
	const mult = CURRENCY_VALUE[String(r.currency || "").toLowerCase()];
	return r.price * (mult === undefined ? 1 : mult);
}

function sqlStr(v) {
	if (v === null || v === undefined) return "NULL";
	return "'" + String(v).replace(/'/g, "''") + "'";
}
function sqlBool(v) {
	return v ? "1" : "0";
}
function sqlNum(v) {
	return Number.isFinite(v) ? String(v) : "NULL";
}

async function main() {
	const [listingsRes, raresRes] = await Promise.all([fetch(LISTINGS_URL), fetch(RARE_ITEMS_URL)]);
	const listings = await listingsRes.json();
	const rares = await raresRes.json();

	const rareByName = new Map();
	for (const r of rares) rareByName.set(String(r.name || "").trim().toLowerCase(), r);

	// Step 1: catalog match + real sale + cheap + not a plushie.
	const candidates = listings.filter((r) => {
		if (r.marketplace) return false; // not a real shop row
		const currency = String(r.currency || "").toLowerCase();
		if (currency === "display") return false;
		if (r.price === 0) return false;
		const rareEntry = rareByName.get(String(r.itemName || "").trim().toLowerCase());
		if (!rareEntry) return false;
		if (rareEntry.typeSlot === "Plushie") return false;
		return priceInDiamonds(r) < 9;
	});

	// Step 2: drop any item name with 2+ cheap listings — only an isolated
	// single cheap listing for that name is suspicious enough to hold.
	const groups = new Map();
	for (const r of candidates) {
		const key = String(r.itemName || "").trim().toLowerCase();
		if (!groups.has(key)) groups.set(key, []);
		groups.get(key).push(r);
	}
	const matched = [...groups.values()].filter((rows) => rows.length === 1).map((rows) => rows[0]);

	console.log(`${candidates.length} cheap catalog matches, ${matched.length} after dropping items with 2+ cheap listings`);
	matched.forEach((r) => console.log(` - ${r.itemName} | ${r.price} ${r.currency} | ${r.seller} ${r.world}`));

	if (matched.length === 0) {
		console.log("Nothing to hold right now — no SQL file written.");
		return;
	}

	const submittedAt = new Date().toISOString();
	const out = [];
	for (const r of matched) {
		out.push(`DELETE FROM listings WHERE rowKey = ${sqlStr(r.rowKey)};`);
		out.push(
			`INSERT INTO pendingRareApprovals (id, rowKey, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen, submittedAt)\n` +
				`VALUES (${sqlStr(r.rowKey)}, ${sqlStr(r.rowKey)}, ${sqlStr(r.itemName)}, ${sqlStr(r.baseItem)}, ${sqlBool(r.bulk)}, ${sqlBool(r.bundled)}, ${sqlBool(r.mixedContents)}, ${sqlNum(r.price)}, ${sqlStr(r.priceLabel)}, ${sqlNum(r.stackSize)}, ${sqlNum(r.amount)}, ${sqlNum(r.stacksInStock)}, ${sqlStr(r.currency)}, ${sqlStr(r.seller)}, ${sqlStr(r.world)}, ${sqlStr(r.position)}, ${sqlStr(r.lastSeen)}, ${sqlStr(r.availableSince || r.lastSeen)})\n` +
				`ON CONFLICT(id) DO UPDATE SET\n` +
				`  itemName=excluded.itemName, baseItem=excluded.baseItem, bulk=excluded.bulk, bundled=excluded.bundled,\n` +
				`  mixedContents=excluded.mixedContents, price=excluded.price, priceLabel=excluded.priceLabel,\n` +
				`  stackSize=excluded.stackSize, amount=excluded.amount, stacksInStock=excluded.stacksInStock,\n` +
				`  currency=excluded.currency, seller=excluded.seller, world=excluded.world,\n` +
				`  position=excluded.position, lastSeen=excluded.lastSeen, submittedAt=excluded.submittedAt;`
		);
	}

	fs.writeFileSync(OUT_FILE, out.join("\n") + "\n");
	console.log("wrote", OUT_FILE);
}

main().catch((e) => {
	console.error(e);
	process.exit(1);
});

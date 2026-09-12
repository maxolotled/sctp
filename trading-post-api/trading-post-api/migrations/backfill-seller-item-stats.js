// One-time script: backfills sellerItemDailyStats for every day R2 already
// has a full listings snapshot for (see snapshotListingsToR2 in worker.js),
// not just going forward from whenever computeSellerItemStats first
// deployed. Mirrors that function's diff logic exactly (rowKey-level,
// yesterday's R2 snapshot vs today's) so the backfilled rows are
// indistinguishable from ones the daily cron would have produced itself, had
// it existed since the first snapshot.
//
// Usage: download every available snapshot first (wrangler has no R2 "list"
// command, so dates have to be named explicitly — see the DATES array
// below, adjust to whatever's actually in the bucket), e.g.:
//   wrangler r2 object get sctp-listings-snapshots/2026-08-31.json --remote --file=<dir>/2026-08-31.json
// ...then run this script pointing at that directory. It writes one .sql
// file per day (kept separate so no single `wrangler d1 execute` call gets
// too large) into the same directory, ready to run in date order.
"use strict";

const fs = require("fs");
const path = require("path");

const SNAPSHOT_DIR = process.argv[2];
if (!SNAPSHOT_DIR) {
	console.error("Usage: node backfill-seller-item-stats.js <dir-of-YYYY-MM-DD.json-files>");
	process.exit(1);
}
const ITEM_LANG_TABLE_URL = "https://sctp.nl/data/item-lang-table.json";

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
function alphaOnly(s) {
	return String(s || "").toLowerCase().replace(/[^a-z]/g, "");
}

function sqlStr(v) {
	if (v === null || v === undefined) return "NULL";
	return "'" + String(v).replace(/'/g, "''") + "'";
}
function sqlNum(v) {
	return Number.isFinite(v) ? String(v) : "0";
}

async function main() {
	const dates = fs.readdirSync(SNAPSHOT_DIR)
		.filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f))
		.map((f) => f.replace(/\.json$/, ""))
		.sort();
	if (dates.length === 0) {
		console.error("No YYYY-MM-DD.json files found in", SNAPSHOT_DIR);
		process.exit(1);
	}
	console.log(`Found ${dates.length} snapshots: ${dates[0]} .. ${dates[dates.length - 1]}`);

	let langTable = {};
	try {
		const res = await fetch(ITEM_LANG_TABLE_URL);
		if (res.ok) langTable = await res.json();
	} catch (e) {
		console.warn("Couldn't fetch item-lang-table.json, proceeding without localized-name normalization:", e.message);
	}
	const langSets = new Map();
	for (const baseItem in langTable) langSets.set(baseItem, new Set(langTable[baseItem].alt));
	function displayName(baseItem, itemName) {
		const set = langSets.get(baseItem);
		if (!set || !set.has(alphaOnly(itemName))) return itemName;
		return langTable[baseItem].en;
	}

	function loadDay(date) {
		return JSON.parse(fs.readFileSync(path.join(SNAPSHOT_DIR, `${date}.json`), "utf8"));
	}

	let priorRows = null; // previous iteration's full row array, or null for the very first day
	for (let i = 0; i < dates.length; i++) {
		const date = dates[i];
		const todayRows = loadDay(date);

		const buckets = new Map(); // sellerKey|itemKey|world -> aggregate
		function bucketFor(seller, baseItem, itemName, world) {
			const sellerKey = String(seller).toLowerCase();
			const name = displayName(baseItem, itemName);
			const itemKey = "v:" + String(baseItem).toLowerCase() + "|" + name.toLowerCase();
			const key = sellerKey + "|" + itemKey + "|" + world;
			let b = buckets.get(key);
			if (!b) {
				b = { seller, sellerKey, itemKey, itemName: name, world, totalStock: 0, listingCount: 0, prices: [], inferredSold: 0, inferredRevenue: 0 };
				buckets.set(key, b);
			}
			return b;
		}

		const todayByRowKey = new Map();
		for (const r of todayRows) {
			if (String(r.currency || "").toLowerCase() === "display") continue;
			todayByRowKey.set(r.rowKey, r);
			const b = bucketFor(r.seller, r.baseItem, r.itemName, r.world);
			b.totalStock += Number(r.amount) || 0;
			b.listingCount++;
			b.prices.push(priceInDiamonds(r) / (r.stackSize || 1));
		}

		if (priorRows) {
			const priorByRowKey = new Map();
			for (const r of priorRows) priorByRowKey.set(r.rowKey, r);

			for (const [rowKey, prior] of priorByRowKey) {
				if (String(prior.currency || "").toLowerCase() === "display") continue;
				const now = todayByRowKey.get(rowKey);
				const priorAmount = Number(prior.amount) || 0;
				const priceDia = priceInDiamonds(prior) / (prior.stackSize || 1);
				let sold = 0;
				if (!now) sold = priorAmount;
				else if (Number(now.amount) < priorAmount) sold = priorAmount - Number(now.amount);
				if (sold <= 0) continue;
				const b = bucketFor(prior.seller, prior.baseItem, prior.itemName, prior.world);
				b.inferredSold += sold;
				b.inferredRevenue += sold * priceDia;
			}
		}

		const out = [];
		const rows = [...buckets.values()];
		for (let j = 0; j < rows.length; j += 200) {
			const chunk = rows.slice(j, j + 200);
			out.push(
				`INSERT INTO sellerItemDailyStats (seller, sellerKey, itemKey, itemName, world, date, totalStock, listingCount, avgPriceDiamonds, inferredSold, inferredRevenueDiamonds) VALUES\n` +
					chunk.map((b) => {
						const avg = b.prices.length ? b.prices.reduce((a, c) => a + c, 0) / b.prices.length : 0;
						return `(${sqlStr(b.seller)}, ${sqlStr(b.sellerKey)}, ${sqlStr(b.itemKey)}, ${sqlStr(b.itemName)}, ${sqlStr(b.world)}, ${sqlStr(date)}, ${sqlNum(b.totalStock)}, ${sqlNum(b.listingCount)}, ${sqlNum(avg)}, ${sqlNum(b.inferredSold)}, ${sqlNum(b.inferredRevenue)})`;
					}).join(",\n") +
					`\nON CONFLICT(sellerKey, itemKey, world, date) DO UPDATE SET seller=excluded.seller, itemName=excluded.itemName, totalStock=excluded.totalStock, listingCount=excluded.listingCount, avgPriceDiamonds=excluded.avgPriceDiamonds, inferredSold=excluded.inferredSold, inferredRevenueDiamonds=excluded.inferredRevenueDiamonds;`
			);
		}

		const outFile = path.join(SNAPSHOT_DIR, `${date}.sql`);
		fs.writeFileSync(outFile, out.join("\n\n") + "\n");
		console.log(`${date}: ${rows.length} seller/item buckets -> ${outFile}`);

		priorRows = todayRows;
	}
}

main().catch((e) => {
	console.error(e);
	process.exit(1);
});

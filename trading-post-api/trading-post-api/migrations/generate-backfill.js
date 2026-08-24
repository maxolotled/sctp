// One-time script: reads the current GitHub-hosted JSON/txt data files and
// generates a SQL file that bulk-inserts them into the new D1 tables.
// Run once during the D1 cutover, then this script and its output are dead.
"use strict";

const fs = require("fs");
const path = require("path");

const DATA_DIR = path.resolve(__dirname, "../../../data");
const OUT_FILE = path.resolve(__dirname, "backfill.sql");

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

function rowKey(r) {
	return `${r.world}|${r.seller}|${r.baseItem}|${r.itemName}`.toLowerCase();
}

function readJson(name) {
	return JSON.parse(fs.readFileSync(path.join(DATA_DIR, name), "utf8"));
}
function readLines(name) {
	return fs
		.readFileSync(path.join(DATA_DIR, name), "utf8")
		.split(/\r?\n/)
		.map((s) => s.trim())
		.filter(Boolean);
}

// Batches `rows` into multi-row INSERT statements of up to `size` rows each,
// calling `rowSql(row)` -> "(...)" per row. Keeps individual statements a
// sane size for a single `wrangler d1 execute --file` run.
function batchInsert(out, table, columns, rows, rowSql, size) {
	for (let i = 0; i < rows.length; i += size) {
		const chunk = rows.slice(i, i + size);
		out.push(
			`INSERT INTO ${table} (${columns.join(", ")}) VALUES\n` +
				chunk.map(rowSql).join(",\n") +
				";"
		);
	}
}

const out = [];

// ---- listings ----
const listings = readJson("listings.json");
batchInsert(
	out,
	"listings",
	["rowKey", "itemName", "baseItem", "bulk", "mixedContents", "price", "priceLabel", "stackSize", "amount", "stacksInStock", "currency", "seller", "world", "position", "lastSeen"],
	listings,
	(r) =>
		`(${sqlStr(rowKey(r))}, ${sqlStr(r.itemName)}, ${sqlStr(r.baseItem)}, ${sqlBool(r.bulk)}, ${sqlBool(r.mixedContents)}, ${sqlNum(r.price)}, ${sqlStr(r.priceLabel)}, ${sqlNum(r.stackSize)}, ${sqlNum(r.amount)}, ${sqlNum(r.stacksInStock)}, ${sqlStr(r.currency)}, ${sqlStr(r.seller)}, ${sqlStr(r.world)}, ${sqlStr(r.position)}, ${sqlStr(r.lastSeen)})`,
	200
);

// ---- reports ----
const reports = readJson("reports.json");
if (reports.length) {
	batchInsert(
		out,
		"reports",
		["id", "listingKey", "listingJson", "reason", "details", "status", "createdAt", "resolvedAt"],
		reports,
		(r) =>
			`(${sqlStr(r.id)}, ${sqlStr(r.listingKey)}, ${sqlStr(r.listing ? JSON.stringify(r.listing) : null)}, ${sqlStr(r.reason)}, ${sqlStr(r.details || "")}, ${sqlStr(r.status)}, ${sqlStr(r.createdAt)}, ${sqlStr(r.resolvedAt)})`,
		200
	);
}

// ---- sharedShopRequests ----
const requests = readJson("shared-shops.json");
if (requests.length) {
	batchInsert(
		out,
		"sharedShopRequests",
		["id", "username", "shopsJson", "bio", "status", "createdAt", "resolvedAt"],
		requests,
		(r) =>
			`(${sqlStr(r.id)}, ${sqlStr(r.username)}, ${sqlStr(JSON.stringify(r.shops || []))}, ${sqlStr(r.bio || "")}, ${sqlStr(r.status)}, ${sqlStr(r.createdAt)}, ${sqlStr(r.resolvedAt)})`,
		200
	);
}

// ---- sharedShops (approved) ----
const approved = readJson("shared-shops-approved.json");
if (approved.length) {
	batchInsert(
		out,
		"sharedShops",
		["usernameKey", "username", "shopsJson", "bio"],
		approved,
		(r) =>
			`(${sqlStr(String(r.username).toLowerCase())}, ${sqlStr(r.username)}, ${sqlStr(JSON.stringify(r.shops || []))}, ${sqlStr(r.bio || "")})`,
		200
	);
}

// ---- faq ----
const faq = readJson("faq.json");
if (faq.length) {
	batchInsert(
		out,
		"faq",
		["id", "question", "answer", "createdAt"],
		faq,
		(r) => `(${sqlStr(r.id)}, ${sqlStr(r.question)}, ${sqlStr(r.answer)}, ${sqlStr(r.createdAt)})`,
		200
	);
}

// ---- worldMap ----
const worldMap = readJson("world-map.json");
const worldMapRows = Object.keys(worldMap).map((squareId) => ({ squareId, ...worldMap[squareId] }));
if (worldMapRows.length) {
	batchInsert(
		out,
		"worldMap",
		["squareId", "status", "username", "claimedAt", "completedAt"],
		worldMapRows,
		(r) => `(${sqlStr(r.squareId)}, ${sqlStr(r.status)}, ${sqlStr(r.username)}, ${sqlStr(r.claimedAt)}, ${sqlStr(r.completedAt)})`,
		200
	);
}

// ---- rareItems ----
// Both source lists have a few accidental duplicate lines — dedupe per world
// rather than touching the admin-maintained .txt files themselves.
function dedupeByWorld(world, names) {
	const seen = new Set();
	const out = [];
	for (const itemName of names) {
		if (seen.has(itemName)) continue;
		seen.add(itemName);
		out.push({ world, itemName });
	}
	return out;
}
const rareRows = [
	...dedupeByWorld("firefly", readLines("rar_firefly.txt")),
	...dedupeByWorld("honeybee", readLines("tl_honeybee.txt")),
];
batchInsert(
	out,
	"rareItems",
	["world", "itemName"],
	rareRows,
	(r) => `(${sqlStr(r.world)}, ${sqlStr(r.itemName)})`,
	200
);

fs.writeFileSync(OUT_FILE, out.join("\n\n") + "\n");
console.log("wrote", OUT_FILE);
console.log({
	listings: listings.length,
	reports: reports.length,
	sharedShopRequests: requests.length,
	sharedShops: approved.length,
	faq: faq.length,
	worldMap: worldMapRows.length,
	rareItems: rareRows.length,
});

// Usage: node recover-all.js snapshot1.json snapshot2.json ... snapshotN.json > merged.json
//
// Merges ANY NUMBER of historical listings.json snapshots together, keeping
// whichever version of each (world+seller+baseItem+itemName) listing has
// the newest lastSeen timestamp, wherever it happens to appear. Order of
// arguments doesn't matter — every file is treated equally, so this
// recovers data even if it only ever existed in some middle commit that
// got wiped again before the "current" state.
//
// See gather-snapshots.sh for how to produce the snapshot files from git history.

const fs = require("fs");

const paths = process.argv.slice(2);
if (paths.length === 0) {
	console.error("Usage: node recover-all.js <snapshot1.json> <snapshot2.json> ...");
	process.exit(1);
}

function rowKey(r) {
	return `${r.world}|${r.seller}|${r.baseItem}|${r.itemName}`.toLowerCase();
}
function rowTimestamp(r) {
	const t = Date.parse(r.lastSeen);
	return isNaN(t) ? 0 : t;
}

const map = new Map();
let totalSeen = 0;
let filesRead = 0;

for (const path of paths) {
	let rows;
	try {
		rows = JSON.parse(fs.readFileSync(path, "utf8"));
	} catch (e) {
		console.error(`Skipping ${path}: ${e.message}`);
		continue;
	}
	filesRead++;

	for (const r of rows) {
		if (!r.world || !r.seller || !r.baseItem || !r.itemName) continue; // skip malformed rows defensively
		totalSeen++;
		const key = rowKey(r);
		const existing = map.get(key);
		if (!existing || rowTimestamp(r) > rowTimestamp(existing)) {
			map.set(key, r);
		}
	}
}

const merged = Array.from(map.values());
console.error(`Read ${filesRead}/${paths.length} snapshot files.`);
console.error(`Saw ${totalSeen} total rows across all snapshots, merged down to ${merged.length} distinct listings.`);
console.log(JSON.stringify(merged, null, 2));

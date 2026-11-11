#!/usr/bin/env node
"use strict";
/**
 * Uploads the Golden Shell's shared content (content.json) and, for testing, a
 * test puzzle set plus the real settings (vault, key, lectern word, Apex, doors).
 *
 *   HUNT_ADMIN_TOKEN=<an sctp.nl session token with the hunt permission> node scripts/hunt/seed.js --content
 *   ... --test-set        one test puzzle set (test-set.json, made by make-test-set.js)
 *   ... --test-settings   the real vault, key, lectern word, Apex and the fireflies door (20:00-23:00 in each hunter's own time)
 *
 *   ... --sets=<folder>   the personal sets made by generate.js: their torn map pieces, then the sets
 *                         (writes the set numbers into the folder's answers.txt / answers.json)
 *   ... --clear-spare     first remove every set nobody has yet (old test copies), keeping hunters' sets
 */
const fs = require("fs");
const path = require("path");

const API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
const TOKEN = process.env.HUNT_ADMIN_TOKEN;
if (!TOKEN) { console.error("Set HUNT_ADMIN_TOKEN first."); process.exit(1); }

/** fetch, tried again (up to 4 times, waiting a bit longer each time) when the connection drops or the server has a hiccup (5xx). */
async function fetchRetry(url, opts) {
	for (let i = 1; ; i++) {
		try {
			const res = await fetch(url, opts);
			if (res.status < 500 || i >= 4) return res;
		} catch (e) {
			if (i >= 4) throw e;
		}
		await new Promise((r) => setTimeout(r, 1500 * i));
	}
}

async function post(p, body) {
	const res = await fetchRetry(API + p, { method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer " + TOKEN }, body: JSON.stringify(body) });
	const d = await res.json().catch(() => ({}));
	if (!res.ok) throw new Error(p + ": " + (d.error || res.status));
	return d;
}

// One test set with known answers: built (and checked) by make-test-set.js.
// --set=b uploads test-set-b.json instead
const SET_ARG = (process.argv.find((x) => x.startsWith("--set=")) || "--set=a").slice(6);
const TEST_SET = JSON.parse(fs.readFileSync(path.join(__dirname, SET_ARG === "a" ? "test-set.json" : "test-set-" + SET_ARG + ".json"), "utf8"));

// The real hunt's settings: the vault at 26380, 66, -23633 (see content.json for the squares).
const TEST_SETTINGS = {
	vault: { x: 26380, y: 66, z: -23633 }, vaultTolerance: 2, key: "2 5 7 3", vaultWord: "NACRE",
	apex: {
		title: "The Apex",
		riddle: "Five marks I left on your map, and three of them are liars. The honest two keep their feet wet.\n" +
			"Of the honest pair, the one nearer the sunrise gives you its column; the other gives you its row.\n" +
			"Where column meets row, three blocks above the sea, my door is waiting.",
		// 2 and 4 are in the water (2 is further east: X 26380; 4 gives Z -23633); 1, 3 and 5 stand on land
		marks: [{ n: "1", x: 25700, z: -24400 }, { n: "2", x: 26380, z: -24300 }, { n: "3", x: 26088, z: -24088 }, { n: "4", x: 25750, z: -23633 }, { n: "5", x: 26150, z: -23600 }],
	},
	doors: [{ slug: "fireflies", title: "A door with no sign", from: "20:00", to: "23:00", closed: "Closed. Come back when the fireflies are out." }],
};

(async () => {
	const args = new Set(process.argv.slice(2));
	if (args.has("--content")) {
		const c = JSON.parse(fs.readFileSync(path.join(__dirname, "content.json"), "utf8"));
		await post("/admin/hunt/content", { chapters: c.chapters, whorls: c.whorls });
		console.log("content: " + c.chapters.length + " chapters, " + c.whorls.length + " whorls");
	}
	if (args.has("--test-settings")) {
		await post("/admin/hunt/settings", TEST_SETTINGS);
		console.log("test settings saved");
	}
	if (args.has("--images")) {
		// every picture in scripts/hunt/data/ goes up as img/<file name> (the content refers to it by that key)
		for (const f of fs.readdirSync(path.join(__dirname, "data")).filter((n) => /\.(webp|png)$/.test(n))) {
			const res = await fetch(API + "/admin/hunt/image?key=" + encodeURIComponent("img/" + f), {
				method: "POST", headers: { Authorization: "Bearer " + TOKEN, "Content-Type": f.endsWith(".png") ? "image/png" : "image/webp" },
				body: fs.readFileSync(path.join(__dirname, "data", f)),
			});
			console.log("image img/" + f + ": " + (res.ok ? "uploaded" : "failed " + res.status));
		}
	}
	if (args.has("--clear-spare")) {
		const r = await post("/admin/hunt/sets/clear", {});
		console.log("spare sets removed (" + ((r.sets && r.sets.spare) ?? "?") + " spare left)");
	}
	const setsDir = (process.argv.find((x) => x.startsWith("--sets=")) || "").slice(7);
	if (setsDir) await uploadGenerated(path.resolve(setsDir));
	if (args.has("--test-set")) {
		const r = await post("/admin/hunt/sets", { sets: [TEST_SET] });
		console.log("test set added: " + r.added.join(", "));
	}
})().catch((e) => { console.error(e.message); process.exit(1); });

/** Uploads a generate.js folder: pictures first (a set must never point at a missing one), then the sets. */
async function uploadGenerated(dir) {
	const { seed, sets } = JSON.parse(fs.readFileSync(path.join(dir, "sets.json"), "utf8"));
	const answers = JSON.parse(fs.readFileSync(path.join(dir, "answers.json"), "utf8"));
	if (answers.some((a) => a.setNo)) throw new Error(dir + " was uploaded already (answers.json has set numbers).");
	const keys = sets.map((s) => s.chapters.c9.content[0].key);
	for (const key of keys) if (!fs.existsSync(path.join(dir, key))) throw new Error("missing picture " + key + " (a --no-images dry run can't be uploaded)");

	for (const key of keys) {
		const res = await fetchRetry(API + "/admin/hunt/image?key=" + encodeURIComponent(key), {
			method: "POST", headers: { Authorization: "Bearer " + TOKEN, "Content-Type": "image/webp" }, body: fs.readFileSync(path.join(dir, key)),
		});
		if (!res.ok) throw new Error("picture " + key + ": " + res.status);
	}
	console.log(keys.length + " pictures uploaded");

	for (let i = 0; i < sets.length; i += 25) {
		const batch = sets.slice(i, i + 25);
		const r = await post("/admin/hunt/sets", { sets: batch.map(({ chapters, notes }) => ({ chapters, notes })) });
		r.added.forEach((setNo, j) => { answers[i + j].setNo = setNo; });
		fs.writeFileSync(path.join(dir, "answers.json"), JSON.stringify(answers, null, "\t"));
		console.log("sets " + r.added[0] + "-" + r.added[r.added.length - 1] + " added");
	}

	// the answer sheet, now with set numbers (the admin page shows each hunter's set number)
	const lines = [`The Golden Shell: answer sheets (seed ${seed}). PRIVATE: never commit or share this file.`, ""];
	for (const a of answers) {
		lines.push(`== set ${a.setNo} (${a.id}) ==`);
		for (const [k, v] of Object.entries(a.sheet)) lines.push(`${k.padEnd(4)} ${v}`);
		lines.push("");
	}
	fs.writeFileSync(path.join(dir, "answers.txt"), lines.join("\n"));
	console.log("answer sheet updated: " + path.join(dir, "answers.txt"));
}

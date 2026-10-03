#!/usr/bin/env node
/**
 * Fills the D1 table itemNameTranslations (migration 0040): every Minecraft
 * language's item and enchantment names -> the US English names the site
 * uses everywhere.
 *
 * Why: mod versions before the English-names fix uploaded vanilla item
 * names in the player's game language ("Truhe" instead of "Chest", "Grey
 * Wool" instead of "Gray Wool"). The Worker looks incoming names up in this
 * table on upload (see englishItemNames() in worker.js), so players who
 * haven't updated don't split listings or miss catalog, search and
 * watchlist matches.
 *
 * Reads the language files from the local Minecraft assets Fabric Loom
 * downloads (any `./gradlew build` in a shoplogger tree fetches them), plus
 * en_us from the deobfuscated Minecraft jar. Writes SQL files to the OS temp
 * folder and, with --apply, runs them against the remote database. Re-run
 * after a Minecraft update:
 *
 *   node scripts/build-item-name-translations.js                  (26.2, dry run)
 *   node scripts/build-item-name-translations.js --apply
 *   node scripts/build-item-name-translations.js --mc=26.3 --apply
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const MC = (process.argv.find((a) => a.startsWith("--mc=")) || "--mc=26.2").slice(5);
const APPLY = process.argv.includes("--apply");
const LOOM = path.join(os.homedir(), ".gradle", "caches", "fabric-loom");
const ASSETS = path.join(LOOM, "assets");
const API_DIR = path.join(__dirname, "..", "trading-post-api", "trading-post-api");
const OUT_DIR = path.join(os.tmpdir(), "sctp-item-name-translations");
const ROWS_PER_INSERT = 400;   // keeps each statement well under D1's 100 KB limit
const INSERTS_PER_FILE = 100;  // 40,000 rows per file
const ENCHANTMENT = "*enchantment";

const indexFile = fs.readdirSync(path.join(ASSETS, "indexes")).filter((f) => f.startsWith(MC + "-")).sort().pop();
if (!indexFile) throw new Error(`No asset index for Minecraft ${MC} in ${ASSETS}/indexes — build a shoplogger tree for that version first.`);
const index = JSON.parse(fs.readFileSync(path.join(ASSETS, "indexes", indexFile), "utf8"));

function readAsset(name) {
	const obj = index.objects[name];
	if (!obj) return null;
	const file = path.join(ASSETS, "objects", obj.hash.slice(0, 2), obj.hash);
	return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : null;
}

// en_us ships inside the game jar, not as a downloaded asset
const jar = path.join(LOOM, "minecraftMaven", "net", "minecraft", "minecraft-merged-deobf", MC, `minecraft-merged-deobf-${MC}.jar`);
const en = JSON.parse(execFileSync("unzip", ["-p", jar, "assets/minecraft/lang/en_us.json"], { encoding: "utf8", maxBuffer: 1 << 26 }));

/** "item.minecraft.potion.effect.healing" -> "potion"; enchantments -> ENCHANTMENT; anything else -> null */
function groupOf(key) {
	const m = /^(item|block)\.minecraft\.([a-z0-9_]+)/.exec(key);
	if (m) return m[2];
	if (/^enchantment\.minecraft\.[a-z0-9_]+$/.test(key)) return ENCHANTMENT;
	return null;
}

// Every English name per group, so a foreign word that is ALSO a real English
// name in that group is never "translated" (that would rename correct listings).
const englishByGroup = new Map();
for (const [key, value] of Object.entries(en)) {
	const g = groupOf(key);
	if (!g) continue;
	if (!englishByGroup.has(g)) englishByGroup.set(g, new Set());
	englishByGroup.get(g).add(value);
}

const rows = new Map(); // "foreign\u0000group" -> [foreign, group, english]
let langCount = 0, skippedClash = 0;
for (const name of Object.keys(index.objects)) {
	const m = /^minecraft\/lang\/(.+)\.json$/.exec(name);
	if (!m || m[1] === "en_us") continue;
	const lang = readAsset(name);
	if (!lang) continue;
	langCount++;
	for (const [key, foreign] of Object.entries(lang)) {
		const english = en[key];
		const g = groupOf(key);
		if (!g || !english || !foreign || foreign === english) continue;
		if (englishByGroup.get(g).has(foreign)) { skippedClash++; continue; }
		const id = foreign + "\u0000" + g;
		if (!rows.has(id)) rows.set(id, [foreign, g, english]);
	}
}

// ---- write SQL ----
fs.rmSync(OUT_DIR, { recursive: true, force: true });
fs.mkdirSync(OUT_DIR, { recursive: true });
const q = (s) => "'" + String(s).replace(/'/g, "''") + "'";
const all = [...rows.values()];
const files = [];
let statements = ["DELETE FROM itemNameTranslations;"];
for (let i = 0; i < all.length; i += ROWS_PER_INSERT) {
	const values = all.slice(i, i + ROWS_PER_INSERT).map(([f, g, e]) => `(${q(f)},${q(g)},${q(e)})`).join(",");
	statements.push(`INSERT OR IGNORE INTO itemNameTranslations (foreignName, baseItem, englishName) VALUES ${values};`);
	if (statements.length >= INSERTS_PER_FILE) {
		const file = path.join(OUT_DIR, `part-${String(files.length + 1).padStart(3, "0")}.sql`);
		fs.writeFileSync(file, statements.join("\n") + "\n");
		files.push(file);
		statements = [];
	}
}
if (statements.length) {
	const file = path.join(OUT_DIR, `part-${String(files.length + 1).padStart(3, "0")}.sql`);
	fs.writeFileSync(file, statements.join("\n") + "\n");
	files.push(file);
}

console.log(`Minecraft ${MC}: ${langCount} languages -> ${all.length} translations (${skippedClash} skipped because they're also an English name) in ${files.length} SQL files at ${OUT_DIR}`);

if (!APPLY) {
	console.log("Dry run — pass --apply to load them into the remote D1 database.");
	process.exit(0);
}
for (const file of files) {
	console.log("Applying " + path.basename(file) + "…");
	execFileSync(process.platform === "win32" ? "npx.cmd" : "npx",
		["wrangler", "d1", "execute", "snailcraft-trading-post-db", "--remote", `--file=${file}`],
		{ cwd: API_DIR, stdio: "inherit", shell: process.platform === "win32" });
}
console.log("Done.");

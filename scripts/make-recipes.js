#!/usr/bin/env node
"use strict";
/**
 * Builds data/recipes.json for the build list (/list/): every crafting-table and
 * stonecutter recipe, so the list can suggest buying the ingredients when that's
 * much cheaper than buying the block (or the block isn't sold at all).
 *
 *   unzip <minecraft server jar> 'data/minecraft/recipe/*' 'data/minecraft/tags/item/*' -d /tmp/mc
 *   node scripts/make-recipes.js /tmp/mc 26.3
 *
 * (The jar is in the Fabric build cache, e.g. ~/.gradle/caches/fabric-loom/26.3/minecraft-extracted_server.jar.)
 *
 * Output: { version, recipes: { "minecraft:oak_stairs": [{ via: "craft"|"cut", makes: 4,
 *           needs: [{ any: ["minecraft:oak_planks"], n: 6 }] }] } }
 * "any" lists every item that fits that slot (a tag like "any oak log" is spelled out).
 */
const fs = require("fs");
const path = require("path");

const SRC = process.argv[2];
const VERSION = process.argv[3] || "";
if (!SRC) { console.error("usage: node scripts/make-recipes.js <extracted jar folder> [version]"); process.exit(1); }
const RECIPES = path.join(SRC, "data/minecraft/recipe");
const TAGS = path.join(SRC, "data/minecraft/tags/item");

const tagCache = new Map();
function tag(name) {
	if (tagCache.has(name)) return tagCache.get(name);
	tagCache.set(name, []); // guards against loops
	const file = path.join(TAGS, name.replace(/^minecraft:/, "") + ".json");
	const out = new Set();
	if (fs.existsSync(file)) {
		for (const v of JSON.parse(fs.readFileSync(file, "utf8")).values) {
			const id = typeof v === "string" ? v : v.id;
			if (id.startsWith("#")) tag(id.slice(1)).forEach((x) => out.add(x)); else out.add(id);
		}
	}
	const list = [...out].sort();
	tagCache.set(name, list);
	return list;
}
/** One ingredient (an id, "#tag", or a list of them) -> every item that fits. */
function options(ing) {
	const parts = Array.isArray(ing) ? ing : [ing];
	const out = new Set();
	for (const p of parts) {
		const id = typeof p === "string" ? p : (p.item || p.id || (p.tag ? "#" + p.tag : ""));
		if (!id) continue;
		if (id.startsWith("#")) tag(id.slice(1)).forEach((x) => out.add(x)); else out.add(id);
	}
	return [...out].sort();
}
function addNeed(needs, any, n) {
	if (!any.length) return false;
	const key = any.join(",");
	const hit = needs.find((x) => x.any.join(",") === key);
	if (hit) hit.n += n; else needs.push({ any, n });
	return true;
}

const recipes = {};
let count = 0;
for (const f of fs.readdirSync(RECIPES)) {
	const r = JSON.parse(fs.readFileSync(path.join(RECIPES, f), "utf8"));
	const needs = [];
	let via = null, ok = true;
	if (r.type === "minecraft:crafting_shaped") {
		via = "craft";
		const uses = {};
		for (const row of r.pattern) for (const ch of row) if (ch !== " ") uses[ch] = (uses[ch] || 0) + 1;
		for (const [ch, n] of Object.entries(uses)) ok = addNeed(needs, options(r.key[ch]), n) && ok;
	} else if (r.type === "minecraft:crafting_shapeless") {
		via = "craft";
		for (const ing of r.ingredients) ok = addNeed(needs, options(ing), 1) && ok;
	} else if (r.type === "minecraft:stonecutting") {
		via = "cut";
		ok = addNeed(needs, options(r.ingredient), 1);
	} else continue;
	if (!ok || !r.result || !r.result.id) continue;
	const id = r.result.id;
	// never "make X from X" (e.g. dyeing back, or bulk storage loops of one item)
	if (needs.some((x) => x.any.length === 1 && x.any[0] === id)) continue;
	(recipes[id] = recipes[id] || []).push({ via, makes: r.result.count || 1, needs });
	count++;
}
const out = { version: VERSION, recipes };
fs.writeFileSync(path.join(__dirname, "../data/recipes.json"), JSON.stringify(out));
console.log(count + " recipes for " + Object.keys(recipes).length + " items -> data/recipes.json");

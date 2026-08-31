// Snailcraft Trading Post API — backed by Cloudflare D1. Both writes AND
// public reads go through this Worker (reads are edge-cached for
// CACHE_TTL_SECONDS so most requests never touch D1 at all).
//
// Public (Authorization: Bearer <API_KEY>):
//   POST /listings                -> upserts into the listings table
//   POST /reports                 -> appends to reports
//   POST /shared-shop-requests    -> appends to sharedShopRequests
//   POST /world-map/claim         body: {squareId, username}  -> claims a map square
//   POST /world-map/unclaim       body: {squareId, username}  -> releases a claimed square
//   POST /world-map/complete      body: {squareId, username}  -> marks a claimed square done
//
// Public, unauthenticated, cached (CACHE_TTL_SECONDS at the edge):
//   GET /listings          -> full listings array
//   GET /shared-shops      -> approved shared-shop entries
//   GET /rare-items        -> {firefly: [...], honeybee: [...]}
//   GET /faq               -> faq entries
//   GET /world-map         -> {squareId: {status, username, claimedAt, completedAt}}
//
// Admin auth (see requireAdminAuth): Authorization: Bearer <session token
// from POST /admin/login>, limited to whichever permission bucket each route
// requires unless the logged-in admin is a head admin. The old shared
// ADMIN_KEY secret is no longer accepted anywhere — real accounts replaced
// it entirely once the first head admin existed (env.ADMIN_KEY itself is
// unused by this file now; the Cloudflare secret can be deleted).
//
//   POST /admin/login                        body: {username, password} -> {token, isHeadAdmin, permissions, expiresAt}
//
// Head-admin-only (or master key) — managing other admin accounts:
//   POST /admin/admins/create                body: {username, password, permissions: [...], isHeadAdmin?} -- isHeadAdmin true mints another head admin (also how the very first one gets created, via the master key)
//   GET  /admin/admins
//   POST /admin/admins/update-permissions    body: {id, permissions: [...]}
//   POST /admin/admins/delete                body: {id}
//   POST /admin/run-snapshot                 -> forces an item-history snapshot now (see the daily cron below)
//
// Any logged-in admin (own account only, unless caller is head admin):
//   POST /admin/admins/change-password       body: {id?, oldPassword?, newPassword} -- id omitted = self; oldPassword required unless a head admin is resetting someone else's
//
// Permission bucket "reports":
//   GET  /admin/reports
//   POST /admin/reports/resolve              body: {id, action: "approve"|"deny"|"edit", field?, value?}
// Permission bucket "sharedShopRequests":
//   GET  /admin/shared-shop-requests
//   POST /admin/shared-shop-requests/resolve body: {id, action: "approve"|"deny"}
// Permission bucket "faq":
//   GET  /admin/faq
//   POST /admin/faq/add                      body: {question, answer}
//   POST /admin/faq/update                   body: {id, question, answer}
//   POST /admin/faq/delete                   body: {id}
// Permission bucket "worldMap":
//   POST /admin/world-map/set                body: {squareId, status, username} -> force-set, bypasses username match
// Permission bucket "manualListings":
//   POST /admin/listings/manual-add          body: {seller, world, entries: [{itemName, price, currency, position, priceLabel?}]}
//   GET  /admin/listings/manual              -> lists manually-added listings (lastSeen "M001" etc instead of a timestamp)
//   POST /admin/listings/manual-delete       body: {id}  -> id is the "M001"-style identifier
//   POST /admin/listings/delete-shop         body: {seller, world?} -> deletes every listing for a seller (world omitted = both)
// Permission bucket "blockedSellers":
//   GET  /admin/blocked-sellers
//   POST /admin/blocked-sellers/add          body: {username, reason?} -> blocks a seller; strips their existing listings immediately
//   POST /admin/blocked-sellers/remove       body: {username}
// Permission bucket "rareApprovals":
//   GET  /admin/rare-approvals               -> rare items priced 1-2 diamond, held pending approval (see pendingRareApprovals below)
//   POST /admin/rare-approvals/resolve       body: {id, action: "approve"|"deny"}
//
// GET /items/history?itemKey=<key> (public, cached 1hr) -> daily price/stock/seller
//   history for one item. itemKey is "v:<baseItem>|<exact display name>" (lowercased)
//   for vanilla items. Populated by a daily cron trigger (see wrangler.toml), not
//   by any upload — see computeDailySnapshots() below.
//
// Rare-item price-approval hold: on upload, a listing whose item name is in
// the rare-items catalog (data/rare-items.json, fetched live — see
// getRareNameSet) AND priced at exactly 1 or 2 diamond is stored in
// pendingRareApprovals instead of `listings`, and doesn't appear anywhere
// public until an admin approves it. A rowKey that's already been approved
// once is exempt from future holds (see handleUploadListings) so re-scanning
// an unchanged, already-vetted listing doesn't need re-approval every time.

const BANNED_ITEMS = ["minecraft:diamond", "minecraft:diamond_block", "diamond", "diamondblock"];

const REPORT_REASONS = new Set(["scam", "wrong_info", "shop_gone", "inappropriate", "other"]);
const EDITABLE_LISTING_FIELDS = new Set([
	"itemName", "baseItem", "price", "priceLabel", "stackSize",
	"amount", "stacksInStock", "currency", "seller", "world", "position", "bundled",
]);
const WORLD_MAP_STATUSES = new Set(["unclaimed", "claimed", "done"]);
const WORLD_MAP_GRID_SIZE = 49; // leaf squares per axis, see /world for the full grid math

const CACHE_TTL_SECONDS = 30;
// Item history only changes once a day (see the scheduled handler at the
// bottom of this file), so there's no point re-querying D1 every 30s for it.
const HISTORY_CACHE_TTL_SECONDS = 3600;

// Same convention as index.html/404.html's price sort/filter logic —
// normalizes any currency to a "worth in diamonds" basis. Currencies outside
// this map (ironblock, goldingot, etc, all real ones seen in the wild) default
// to a 1:1 multiplier, same as the client-side version.
const CURRENCY_VALUE = { diamond: 1, diamondblock: 9 };
function priceInDiamonds(r) {
	const mult = CURRENCY_VALUE[String(r.currency || "").toLowerCase()];
	return r.price * (mult || 1);
}

function alphaOnly(s) {
	return String(s || "").toLowerCase().replace(/[^a-z]/g, "");
}

function isBannedItem(baseItem, itemName) {
	const b = String(baseItem || "").toLowerCase().replace(/[^a-z:_]/g, "");
	const n = String(itemName || "").toLowerCase().replace(/[^a-z]/g, "");
	return BANNED_ITEMS.includes(b) || BANNED_ITEMS.includes(n);
}

function rowKey(r) {
	// World is part of the key because the same seller can run independent
	// shops on both Firefly and Honeybee. bulk/bundled are part of it too so
	// a seller selling both a normal-priced stack AND a bulk/bundled batch of
	// the same item ends up as two distinct rows instead of one overwriting
	// the other — see migrate-rowkeys-bulk-bundled.sql for the one-time
	// migration this required when the key changed shape.
	return `${r.world}|${r.seller}|${r.baseItem}|${r.itemName}|${r.bulk ? 1 : 0}|${r.bundled ? 1 : 0}`.toLowerCase();
}

// Currency string (from a shop sign's line 3) -> the item id it actually
// pays with — mirrors ShopEntryFactory's CURRENCY_ITEM_IDS in the mod.
// Defense-in-depth here for older mod versions that upload without the
// client-side filter yet: a shop's own payment item should never also show
// up as something it's selling (e.g. an "ironingot" shop that also happens
// to stock loose iron ingots shouldn't list "1 Iron Ingot for 1 Iron Ingot").
const CURRENCY_ITEM_IDS = {
	diamond: "minecraft:diamond",
	diamondblock: "minecraft:diamond_block",
	iron: "minecraft:iron_ingot",
	ironingot: "minecraft:iron_ingot",
	ironblock: "minecraft:iron_block",
	gold: "minecraft:gold_ingot",
	goldingot: "minecraft:gold_ingot",
	goldblock: "minecraft:gold_block",
	emerald: "minecraft:emerald",
	emeraldblock: "minecraft:emerald_block",
	netherite: "minecraft:netherite_ingot",
	netheriteingot: "minecraft:netherite_ingot",
	netheriteblock: "minecraft:netherite_block",
	coal: "minecraft:coal",
};
function isPaymentItem(baseItem, currency) {
	const mapped = CURRENCY_ITEM_IDS[String(currency || "").toLowerCase()];
	return !!mapped && mapped === String(baseItem || "").toLowerCase();
}

function rowTimestamp(r) {
	const t = Date.parse(r.lastSeen);
	return isNaN(t) ? (r._uploadedAt || 0) : t;
}

function positionKey(world, position) {
	return `${world}|${position}`.toLowerCase();
}

function corsHeaders() {
	return {
		"Access-Control-Allow-Origin": "*",
		"Access-Control-Allow-Methods": "GET, POST, OPTIONS",
		"Access-Control-Allow-Headers": "Content-Type, Authorization",
	};
}

function json(data, status = 200) {
	return new Response(JSON.stringify(data), {
		status,
		headers: { "Content-Type": "application/json", ...corsHeaders() },
	});
}

function isAuthorized(request, key) {
	if (!key) return false; // secret not configured yet — refuse rather than compare against "undefined"
	const auth = request.headers.get("Authorization") || "";
	return auth === `Bearer ${key}`;
}

// Cloudflare's edge Cache API — a hit here never touches D1 at all. Cache key
// ignores the query string so old cache-busting `?t=` params (if any client
// still sends one) can't fragment the cache.
// ttlSeconds is part of the signature (not just the header) because the
// query string is now part of the cache key (see below) — endpoints like
// /items/history that take a distinguishing query param (itemKey) need
// every distinct value cached separately, not collapsed into one entry.
// Bump this to force every cachedGet() entry to miss once, on the next
// deploy — an escape hatch for a bad/stale cached response (e.g. one that
// got cached empty right before real data landed) without waiting out the TTL.
const CACHE_EPOCH = "3";

async function cachedGet(request, ctx, ttlSeconds, computeFn) {
	const cache = caches.default;
	const cacheUrl = new URL(request.url);
	cacheUrl.searchParams.set("__ce", CACHE_EPOCH);
	const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });

	const hit = await cache.match(cacheKey);
	if (hit) return hit;

	const data = await computeFn();
	const response = json(data);
	response.headers.set("Cache-Control", `public, max-age=${ttlSeconds}`);
	ctx.waitUntil(cache.put(cacheKey, response.clone()));
	return response;
}

// Mod versions older than this can have false-reported a still-stocked shop
// as gone. Two separate bugs, both in ShopAutoScanner#forgetGoneShops:
//   - <1.2.3: a not-yet-loaded client chunk was treated as "confirmed empty".
//   - <1.2.4: even with the chunk itself loaded, the shop's sign sits on an
//     adjacent block that can be in a *different*, still-loading chunk —
//     SignFinder.find() would transiently return null and the shop got wiped
//     on the very first failed check. 1.2.4 requires two consecutive failed
//     checks (5+ seconds apart) before actually removing a shop.
// Their scannedPositions-driven removals are not trustworthy below MIN_
// TRUSTED_PRUNE_VERSION, so they're ignored; regular add/update rows are
// unaffected and still processed normally either way.
const MIN_TRUSTED_PRUNE_VERSION = "1.2.4";

// Compares dot-separated numeric version strings, e.g. isVersionAtLeast("1.2.10", "1.2.3") -> true.
// Missing/unparseable segments count as 0, so an unknown or malformed version is never trusted.
function isVersionAtLeast(version, min) {
	const a = String(version || "").split(".").map((n) => parseInt(n, 10));
	const b = String(min || "").split(".").map((n) => parseInt(n, 10));
	for (let i = 0; i < Math.max(a.length, b.length); i++) {
		const av = Number.isFinite(a[i]) ? a[i] : 0;
		const bv = Number.isFinite(b[i]) ? b[i] : 0;
		if (av !== bv) return av > bv;
	}
	return true;
}

function isValidSquareId(id) {
	const m = /^(\d{1,2})_(\d{1,2})$/.exec(String(id || ""));
	if (!m) return false;
	const col = parseInt(m[1], 10), row = parseInt(m[2], 10);
	return col >= 0 && col < WORLD_MAP_GRID_SIZE && row >= 0 && row < WORLD_MAP_GRID_SIZE;
}

// Real Minecraft usernames: 1-16 chars, letters/digits/underscore only.
function isValidUsername(name) {
	return /^[A-Za-z0-9_]{1,16}$/.test(String(name || ""));
}

function chunkArray(arr, size) {
	const out = [];
	for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
	return out;
}
// SQLite/D1 cap how many bound `?` parameters a single statement can have —
// a big scan-session upload can easily produce more distinct rows/positions
// than that limit, so the IN(...)/OR-chain lookups below run in chunks of
// this size instead of one unbounded query (see handleUploadListings).
const MAX_QUERY_PARAMS_PER_CHUNK = 50;

// Shared by handleUploadListings and the rare-approval "approve" action so
// both write to `listings` through the exact same upsert logic.
function buildListingUpsertStmt(env, key, r) {
	// availableSince is intentionally NOT in the ON CONFLICT...DO UPDATE SET
	// list below — SQLite only applies the bound value on a genuine INSERT;
	// an existing row keeps whatever it already had regardless of what's
	// bound here. r.submittedAt (present on a pendingRareApprovals row being
	// approved) reflects when the item was actually first submitted, which is
	// more accurate than "now" (the approval moment) for a held rare item.
	const availableSince = r.submittedAt || new Date().toISOString();
	return env.DB.prepare(
		`INSERT INTO listings (rowKey, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen, availableSince)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON CONFLICT(rowKey) DO UPDATE SET
		   itemName=excluded.itemName, baseItem=excluded.baseItem, bulk=excluded.bulk, bundled=excluded.bundled,
		   mixedContents=excluded.mixedContents, price=excluded.price, priceLabel=excluded.priceLabel,
		   stackSize=excluded.stackSize, amount=excluded.amount, stacksInStock=excluded.stacksInStock,
		   currency=excluded.currency, seller=excluded.seller, world=excluded.world,
		   position=excluded.position, lastSeen=excluded.lastSeen`
	).bind(
		key, r.itemName, r.baseItem, r.bulk ? 1 : 0, r.bundled ? 1 : 0, r.mixedContents ? 1 : 0,
		r.price, r.priceLabel, r.stackSize, r.amount, r.stacksInStock,
		r.currency, r.seller, r.world, r.position, r.lastSeen, availableSince
	);
}

async function getBlockedSellerSet(env, sellers) {
	const keys = [...new Set(sellers.map((s) => String(s || "").toLowerCase()))].filter(Boolean);
	if (keys.length === 0) return new Set();
	const blocked = new Set();
	for (const chunk of chunkArray(keys, MAX_QUERY_PARAMS_PER_CHUNK)) {
		const placeholders = chunk.map(() => "?").join(",");
		const res = await env.DB.prepare(`SELECT usernameKey FROM blockedSellers WHERE usernameKey IN (${placeholders})`).bind(...chunk).all();
		for (const row of res.results) blocked.add(row.usernameKey);
	}
	return blocked;
}

// Rare-item catalog name set, fetched from the live site (the Worker has no
// local copy of data/rare-items.json) and cached in module scope for
// RARE_NAMES_CACHE_TTL_MS — same "fetch cross-origin, cache briefly"
// approach computeDailySnapshots() already uses for the item-lang table.
// NOTE: the existing `rareItems` D1 table is NOT this catalog — it's an
// unrelated admin-curated "spotted on this world" list (see
// handleGetRareItems / generate-backfill.js), not tied to the site's rare
// item pages at all.
const RARE_ITEMS_CATALOG_URL = "https://sctp.nl/data/rare-items.json";
const RARE_NAMES_CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour
let rareNameSetCache = { names: null, fetchedAt: 0 };

async function getRareNameSet() {
	const now = Date.now();
	if (rareNameSetCache.names && now - rareNameSetCache.fetchedAt < RARE_NAMES_CACHE_TTL_MS) {
		return rareNameSetCache.names;
	}
	try {
		const res = await fetch(RARE_ITEMS_CATALOG_URL);
		if (res.ok) {
			const catalog = await res.json();
			const names = new Set(catalog.map((e) => String(e.name || "").trim().toLowerCase()));
			rareNameSetCache = { names, fetchedAt: now };
			return names;
		}
	} catch (e) {
		// fall through to a stale cache below
	}
	// A failed fetch reuses whatever we last had rather than treating every
	// item as "not rare," which would silently bypass the approval hold.
	return rareNameSetCache.names || new Set();
}

// ---------------- admin auth: multi-account + granular permissions ----------------
//
// Admins are real accounts (username + password) with a session token from
// POST /admin/login. A head admin has full access to everything; everyone
// else is limited to whichever of these permission buckets they've been
// granted. The old shared ADMIN_KEY master-key bypass was removed once real
// accounts existed — see requireAnyAdmin.
const ADMIN_PERMISSION_BUCKETS = new Set([
	"reports", "sharedShopRequests", "faq", "worldMap", "manualListings", "blockedSellers", "rareApprovals",
]);
const ADMIN_SESSION_TTL_MS = 24 * 60 * 60 * 1000; // fixed 24h, not sliding — re-login after
const PBKDF2_ITERATIONS = 100000;

function bufToHex(buf) {
	return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function hexToBuf(hex) {
	const bytes = new Uint8Array(hex.length / 2);
	for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
	return bytes.buffer;
}
// Manual constant-time comparison — Web Crypto has no built-in for arbitrary
// buffers/hex strings, and a plain === would leak timing info about how many
// leading characters matched.
function timingSafeEqualHex(a, b) {
	if (a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	return diff === 0;
}
function newSaltHex() {
	return bufToHex(crypto.getRandomValues(new Uint8Array(16)).buffer);
}
function newToken() {
	return bufToHex(crypto.getRandomValues(new Uint8Array(32)).buffer);
}
async function hashPassword(password, saltHex) {
	const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
	const bits = await crypto.subtle.deriveBits(
		{ name: "PBKDF2", salt: hexToBuf(saltHex), iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
		key,
		256
	);
	return bufToHex(bits);
}
async function verifyPassword(password, saltHex, expectedHashHex) {
	const actual = await hashPassword(password, saltHex);
	return timingSafeEqualHex(actual, expectedHashHex);
}

/** Base auth: a valid unexpired session -> { ok, admin } / { ok:false, response }. */
async function requireAnyAdmin(request, env) {
	const auth = request.headers.get("Authorization") || "";
	const token = auth.startsWith("Bearer ") ? auth.slice(7) : null;
	if (!token) return { ok: false, response: json({ error: "Unauthorized" }, 401) };

	const session = await env.DB.prepare("SELECT * FROM adminSessions WHERE token = ?").bind(token).first();
	if (!session || Date.parse(session.expiresAt) < Date.now()) {
		return { ok: false, response: json({ error: "Unauthorized" }, 401) };
	}
	const admin = await env.DB.prepare("SELECT * FROM admins WHERE id = ?").bind(session.adminId).first();
	if (!admin) return { ok: false, response: json({ error: "Unauthorized" }, 401) };
	return { ok: true, admin };
}

/** permission === null means "head admin (or master key) only" — e.g. admin management, run-snapshot. */
async function requireAdminAuth(request, env, permission) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base;
	if (base.admin.isHeadAdmin) return base;
	if (permission === null) return { ok: false, response: json({ error: "Forbidden" }, 403) };
	let perms = [];
	try { perms = JSON.parse(base.admin.permissions || "[]"); } catch (e) { /* treat as no permissions */ }
	if (!perms.includes(permission)) return { ok: false, response: json({ error: "Forbidden" }, 403) };
	return base;
}

async function handleAdminLogin(request, env) {
	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const username = String(body.username || "").trim();
	const password = String(body.password || "");
	if (!username || !password) return json({ error: "username and password are required" }, 400);

	const admin = await env.DB.prepare("SELECT * FROM admins WHERE username = ?").bind(username).first();
	if (!admin || !(await verifyPassword(password, admin.passwordSalt, admin.passwordHash))) {
		return json({ error: "Invalid username or password" }, 401);
	}

	const token = newToken();
	const now = new Date();
	const expiresAt = new Date(now.getTime() + ADMIN_SESSION_TTL_MS).toISOString();
	await env.DB.prepare("INSERT INTO adminSessions (token, adminId, createdAt, expiresAt) VALUES (?, ?, ?, ?)")
		.bind(token, admin.id, now.toISOString(), expiresAt).run();

	let permissions = [];
	try { permissions = JSON.parse(admin.permissions || "[]"); } catch (e) { /* ignore */ }
	return json({ token, isHeadAdmin: !!admin.isHeadAdmin, permissions, expiresAt });
}

async function handleAdminCreateAdmin(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const username = String(body.username || "").trim();
	const password = String(body.password || "");
	const permissions = Array.isArray(body.permissions) ? body.permissions.filter((p) => ADMIN_PERMISSION_BUCKETS.has(p)) : [];
	// Only reachable by an existing head admin (or the master key) per the
	// requireAdminAuth(..., null) check above, so it's safe to let the caller
	// mint another head admin — most importantly, this is also how the very
	// first named head admin gets created (via the master key, since no
	// admins row exists yet to be a head admin the normal way).
	const isHeadAdmin = body.isHeadAdmin === true;
	if (!username) return json({ error: "username is required" }, 400);
	if (password.length < 8) return json({ error: "password must be at least 8 characters" }, 400);

	const existing = await env.DB.prepare("SELECT id FROM admins WHERE username = ?").bind(username).first();
	if (existing) return json({ error: "Username already exists" }, 409);

	const id = crypto.randomUUID();
	const salt = newSaltHex();
	const hash = await hashPassword(password, salt);
	const createdAt = new Date().toISOString();

	try {
		await env.DB.prepare(
			"INSERT INTO admins (id, username, passwordHash, passwordSalt, isHeadAdmin, permissions, createdAt, createdBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
		).bind(id, username, hash, salt, isHeadAdmin ? 1 : 0, JSON.stringify(permissions), createdAt, auth.admin.username).run();
		return json({ ok: true, id, isHeadAdmin });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleAdminListAdmins(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT id, username, isHeadAdmin, permissions, createdAt, createdBy FROM admins ORDER BY createdAt").all();
	return json(results.map((r) => ({ ...r, isHeadAdmin: !!r.isHeadAdmin, permissions: JSON.parse(r.permissions || "[]") })));
}

async function handleAdminUpdatePermissions(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const id = String(body.id || "");
	const permissions = Array.isArray(body.permissions) ? body.permissions.filter((p) => ADMIN_PERMISSION_BUCKETS.has(p)) : [];
	if (!id) return json({ error: "id is required" }, 400);

	const res = await env.DB.prepare("UPDATE admins SET permissions = ? WHERE id = ? AND isHeadAdmin = 0")
		.bind(JSON.stringify(permissions), id).run();
	if (res.meta.changes === 0) return json({ error: "Admin not found (or is a head admin, whose permissions can't be edited)" }, 404);
	return json({ ok: true });
}

async function handleAdminDeleteAdmin(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);

	const target = await env.DB.prepare("SELECT isHeadAdmin FROM admins WHERE id = ?").bind(id).first();
	if (!target) return json({ error: "Admin not found" }, 404);
	if (target.isHeadAdmin) return json({ error: "Can't delete a head admin" }, 400);

	await env.DB.prepare("DELETE FROM adminSessions WHERE adminId = ?").bind(id).run();
	await env.DB.prepare("DELETE FROM admins WHERE id = ?").bind(id).run();
	return json({ ok: true });
}

async function handleAdminChangePassword(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	if (!auth.admin.id) return json({ error: "The master key has no account to change a password for" }, 400);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const newPassword = String(body.newPassword || "");
	if (newPassword.length < 8) return json({ error: "newPassword must be at least 8 characters" }, 400);

	const targetId = body.id ? String(body.id) : auth.admin.id;
	const changingSelf = targetId === auth.admin.id;
	if (!changingSelf && !auth.admin.isHeadAdmin) {
		return json({ error: "Only a head admin can change another admin's password" }, 403);
	}

	if (changingSelf) {
		const oldPassword = String(body.oldPassword || "");
		if (!(await verifyPassword(oldPassword, auth.admin.passwordSalt, auth.admin.passwordHash))) {
			return json({ error: "Current password is incorrect" }, 401);
		}
	} else {
		const target = await env.DB.prepare("SELECT id FROM admins WHERE id = ?").bind(targetId).first();
		if (!target) return json({ error: "Admin not found" }, 404);
	}

	const salt = newSaltHex();
	const hash = await hashPassword(newPassword, salt);
	await env.DB.prepare("UPDATE admins SET passwordHash = ?, passwordSalt = ? WHERE id = ?").bind(hash, salt, targetId).run();
	await env.DB.prepare("DELETE FROM adminSessions WHERE adminId = ?").bind(targetId).run(); // force re-login everywhere
	return json({ ok: true });
}

// ---------------- blocked sellers ----------------

async function handleAdminListBlockedSellers(request, env) {
	const auth = await requireAdminAuth(request, env, "blockedSellers");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM blockedSellers ORDER BY blockedAt DESC").all();
	return json(results);
}

async function handleAdminBlockSeller(request, env) {
	const auth = await requireAdminAuth(request, env, "blockedSellers");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const username = String(body.username || "").trim();
	if (!isValidUsername(username)) return json({ error: "Invalid username" }, 400);
	const reason = String(body.reason || "").trim().slice(0, 300);
	const usernameKey = username.toLowerCase();

	try {
		await env.DB.prepare(
			`INSERT INTO blockedSellers (usernameKey, username, reason, blockedAt, blockedBy) VALUES (?, ?, ?, ?, ?)
			 ON CONFLICT(usernameKey) DO UPDATE SET username=excluded.username, reason=excluded.reason, blockedAt=excluded.blockedAt, blockedBy=excluded.blockedBy`
		).bind(usernameKey, username, reason, new Date().toISOString(), auth.admin.username).run();

		// A block takes effect immediately, not just for future uploads.
		await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = ?").bind(usernameKey).run();
		await env.DB.prepare("DELETE FROM pendingRareApprovals WHERE lower(seller) = ?").bind(usernameKey).run();
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleAdminUnblockSeller(request, env) {
	const auth = await requireAdminAuth(request, env, "blockedSellers");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const username = String(body.username || "").trim();
	if (!username) return json({ error: "username is required" }, 400);

	const res = await env.DB.prepare("DELETE FROM blockedSellers WHERE usernameKey = ?").bind(username.toLowerCase()).run();
	if (res.meta.changes === 0) return json({ error: "Not found" }, 404);
	return json({ ok: true });
}

// ---------------- rare-item price-approval queue ----------------

async function handleAdminListRareApprovals(request, env) {
	const auth = await requireAdminAuth(request, env, "rareApprovals");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM pendingRareApprovals ORDER BY submittedAt").all();
	return json(results.map((r) => ({ ...r, bulk: !!r.bulk, bundled: !!r.bundled, mixedContents: !!r.mixedContents })));
}

async function handleAdminResolveRareApproval(request, env) {
	const auth = await requireAdminAuth(request, env, "rareApprovals");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const id = String(body.id || "");
	const action = String(body.action || "");
	if (!id) return json({ error: "id is required" }, 400);
	if (!["approve", "deny"].includes(action)) return json({ error: "Invalid action" }, 400);

	try {
		const pending = await env.DB.prepare("SELECT * FROM pendingRareApprovals WHERE id = ?").bind(id).first();
		if (!pending) return json({ error: "Not found" }, 404);

		if (action === "approve") {
			await buildListingUpsertStmt(env, pending.rowKey, pending).run();
		}
		await env.DB.prepare("DELETE FROM pendingRareApprovals WHERE id = ?").bind(id).run();
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleUploadListings(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const incoming = Array.isArray(body.rows) ? body.rows : [];

	const modVersion = typeof body.modVersion === "string" ? body.modVersion : null;
	const trustedForPruning = modVersion !== null && isVersionAtLeast(modVersion, MIN_TRUSTED_PRUNE_VERSION);

	const scannedPositionsIn = Array.isArray(body.scannedPositions) ? body.scannedPositions : [];
	const validScannedPositions = trustedForPruning
		? scannedPositionsIn.filter((sp) => sp && sp.world && sp.position)
		: [];
	const scannedSet = new Set(validScannedPositions.map((sp) => positionKey(sp.world, sp.position)));
	const notice = (scannedPositionsIn.length > 0 && !trustedForPruning)
		? `Your Shop Logger version (${modVersion || "unknown"}) has a known bug that can misreport in-stock shops as removed, so listing cleanup has been disabled for this upload. Please update to the latest version to re-enable it.`
		: undefined;

	let added = 0, updated = 0, skipped = 0, removed = 0, heldForApproval = 0;
	try {
		const blockedSet = await getBlockedSellerSet(env, incoming.map((r) => r && r.seller));
		const rareNames = await getRareNameSet();

		const validRows = [];
		// Rare items (data/rare-items.json) priced at exactly 1-2 diamond go to
		// pendingRareApprovals instead of straight into `listings` — but only
		// the FIRST time: once a rowKey has already been approved once (i.e.
		// it's already sitting in `listings`), later re-scans of the same
		// unchanged listing just update it normally instead of re-holding it
		// forever. rareHoldCandidates collects the ones that MIGHT need
		// holding; which of them actually do gets decided just below, once we
		// know which of their keys already exist in `listings`.
		const rareHoldCandidates = [];
		for (const r of incoming) {
			if (!r.itemName || !r.seller || !r.world) { skipped++; continue; }
			if (isBannedItem(r.baseItem, r.itemName)) { skipped++; continue; }
			if (isPaymentItem(r.baseItem, r.currency)) { skipped++; continue; }
			if (blockedSet.has(String(r.seller).toLowerCase())) { skipped++; continue; }

			const key = rowKey(r);
			const isCheapRare = String(r.currency || "").toLowerCase() === "diamond"
				&& (r.price === 1 || r.price === 2)
				&& rareNames.has(String(r.itemName || "").trim().toLowerCase());

			if (isCheapRare) {
				rareHoldCandidates.push({ ...r, _key: key });
			} else {
				validRows.push({ ...r, _key: key });
			}
		}

		const stmts = [];

		if (rareHoldCandidates.length > 0) {
			const candidateKeys = [...new Set(rareHoldCandidates.map((r) => r._key))];
			const alreadyApproved = new Set();
			for (const chunk of chunkArray(candidateKeys, MAX_QUERY_PARAMS_PER_CHUNK)) {
				const placeholders = chunk.map(() => "?").join(",");
				const res = await env.DB.prepare(`SELECT rowKey FROM listings WHERE rowKey IN (${placeholders})`).bind(...chunk).all();
				for (const row of res.results) alreadyApproved.add(row.rowKey);
			}
			const submittedAt = new Date().toISOString();
			for (const r of rareHoldCandidates) {
				if (alreadyApproved.has(r._key)) {
					validRows.push(r); // already vetted once — flows through normally from here
					continue;
				}
				heldForApproval++;
				stmts.push(env.DB.prepare(
					`INSERT INTO pendingRareApprovals (id, rowKey, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen, submittedAt)
					 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					 ON CONFLICT(id) DO UPDATE SET
					   itemName=excluded.itemName, baseItem=excluded.baseItem, bulk=excluded.bulk, bundled=excluded.bundled,
					   mixedContents=excluded.mixedContents, price=excluded.price, priceLabel=excluded.priceLabel,
					   stackSize=excluded.stackSize, amount=excluded.amount, stacksInStock=excluded.stacksInStock,
					   currency=excluded.currency, seller=excluded.seller, world=excluded.world,
					   position=excluded.position, lastSeen=excluded.lastSeen, submittedAt=excluded.submittedAt`
				).bind(
					r._key, r._key, r.itemName, r.baseItem, r.bulk ? 1 : 0, r.bundled ? 1 : 0, r.mixedContents ? 1 : 0,
					r.price, r.priceLabel, r.stackSize, r.amount, r.stacksInStock,
					r.currency, r.seller, r.world, r.position, r.lastSeen, submittedAt
				));
			}
		}

		if (validRows.length > 0) {
			// Look up existing lastSeen for these keys so an older/duplicate
			// report of a listing never clobbers a fresher one already stored.
			// Chunked (see MAX_QUERY_PARAMS_PER_CHUNK) — a big scan-session
			// upload can easily have more distinct keys than one statement's
			// bound-parameter limit allows.
			const keys = [...new Set(validRows.map((r) => r._key))];
			const existingMap = new Map();
			for (const chunk of chunkArray(keys, MAX_QUERY_PARAMS_PER_CHUNK)) {
				const placeholders = chunk.map(() => "?").join(",");
				const existingRes = await env.DB.prepare(
					`SELECT rowKey, lastSeen FROM listings WHERE rowKey IN (${placeholders})`
				).bind(...chunk).all();
				for (const r of existingRes.results) existingMap.set(r.rowKey, r.lastSeen);
			}

			for (const r of validRows) {
				const prevLastSeen = existingMap.get(r._key);
				if (prevLastSeen === undefined) {
					added++;
				} else {
					if (rowTimestamp(r) < (Date.parse(prevLastSeen) || 0)) { skipped++; continue; }
					updated++;
				}
				stmts.push(buildListingUpsertStmt(env, r._key, r));
			}
		}

		// scannedPositions pruning: any listing already stored at a position
		// that was just actively re-scanned, but NOT re-reported in this exact
		// upload, is confirmed gone (sold out / chest emptied / shop removed)
		// and gets deleted. Deliberately NOT nested inside the validRows check
		// above — a chest scanned down to fully empty sends scannedPositions
		// with an empty `rows`, and that's exactly the case pruning exists for.
		if (validScannedPositions.length > 0) {
			const freshKeysAtScannedPos = new Set(
				validRows.filter((r) => scannedSet.has(positionKey(r.world, r.position))).map((r) => r._key)
			);
			// Chunked two positions' worth of params per slot (world+position),
			// same reasoning as the keys lookup above.
			for (const chunk of chunkArray(validScannedPositions, MAX_QUERY_PARAMS_PER_CHUNK)) {
				const orClauses = chunk.map(() => "(world = ? AND position = ?)").join(" OR ");
				const bindArgs = [];
				for (const sp of chunk) bindArgs.push(sp.world, sp.position);
				const atScanned = await env.DB.prepare(`SELECT rowKey FROM listings WHERE ${orClauses}`).bind(...bindArgs).all();
				for (const row of atScanned.results) {
					if (freshKeysAtScannedPos.has(row.rowKey)) continue;
					stmts.push(env.DB.prepare("DELETE FROM listings WHERE rowKey = ?").bind(row.rowKey));
					removed++;
				}
			}
		}

		if (stmts.length > 0) await env.DB.batch(stmts);

		if (added === 0 && updated === 0 && removed === 0 && heldForApproval === 0) {
			return json({ added: 0, updated: 0, skipped, removed: 0, heldForApproval: 0, committed: false, ...(notice ? { notice } : {}) });
		}
		const totalRow = await env.DB.prepare("SELECT COUNT(*) as c FROM listings").first();
		return json({ added, updated, skipped, removed, heldForApproval, total: totalRow.c, committed: true, ...(notice ? { notice } : {}) });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleGetListings(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		// Defense-in-depth: handleUploadListings already refuses new rows from
		// a blocked seller, but this excludes anything already stored from
		// before a block was added too, so a block takes effect immediately.
		const { results } = await env.DB.prepare(
			"SELECT * FROM listings WHERE lower(seller) NOT IN (SELECT usernameKey FROM blockedSellers)"
		).all();
		return results.map((r) => ({ ...r, bulk: !!r.bulk, bundled: !!r.bundled, mixedContents: !!r.mixedContents }));
	});
}

async function handleSubmitReport(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const listingKey = String(body.listingKey || "").trim();
	const reason = String(body.reason || "").trim();
	if (!listingKey) return json({ error: "listingKey is required" }, 400);
	if (!REPORT_REASONS.has(reason)) return json({ error: "Invalid reason" }, 400);

	const report = {
		id: crypto.randomUUID(),
		listingKey,
		listing: body.listing && typeof body.listing === "object" ? body.listing : null,
		reason,
		details: String(body.details || "").slice(0, 500),
		createdAt: new Date().toISOString(),
	};

	try {
		await env.DB.prepare(
			"INSERT INTO reports (id, listingKey, listingJson, reason, details, status, createdAt, resolvedAt) VALUES (?, ?, ?, ?, ?, 'pending', ?, NULL)"
		).bind(report.id, report.listingKey, report.listing ? JSON.stringify(report.listing) : null, report.reason, report.details, report.createdAt).run();
		return json({ ok: true, id: report.id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleSubmitSharedShopRequest(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const username = String(body.username || "").trim();
	const shops = Array.isArray(body.shops)
		? body.shops.map((s) => String(s).trim()).filter(Boolean).slice(0, 50)
		: [];
	const bio = String(body.bio || "").trim().slice(0, 500);
	if (!username) return json({ error: "username is required" }, 400);
	if (shops.length === 0 && !bio) return json({ error: "Add at least one shop or some profile text" }, 400);

	const id = crypto.randomUUID();
	const createdAt = new Date().toISOString();

	try {
		await env.DB.prepare(
			"INSERT INTO sharedShopRequests (id, username, shopsJson, bio, status, createdAt, resolvedAt) VALUES (?, ?, ?, ?, 'pending', ?, NULL)"
		).bind(id, username, JSON.stringify(shops), bio, createdAt).run();
		return json({ ok: true, id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleGetSharedShops(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare(
			"SELECT username, shopsJson, bio FROM sharedShops WHERE usernameKey NOT IN (SELECT usernameKey FROM blockedSellers)"
		).all();
		return results.map((r) => ({ username: r.username, shops: JSON.parse(r.shopsJson), bio: r.bio }));
	});
}

async function handleGetRareItems(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare("SELECT world, itemName FROM rareItems").all();
		const out = { firefly: [], honeybee: [] };
		for (const r of results) if (out[r.world]) out[r.world].push(r.itemName);
		return out;
	});
}

async function handleGetFaqPublic(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare("SELECT id, question, answer, createdAt FROM faq ORDER BY createdAt").all();
		return results;
	});
}

async function handleGetWorldMap(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare("SELECT * FROM worldMap").all();
		const out = {};
		for (const r of results) {
			out[r.squareId] = { status: r.status, username: r.username, claimedAt: r.claimedAt, completedAt: r.completedAt };
		}
		return out;
	});
}

async function handleListReports(request, env) {
	const auth = await requireAdminAuth(request, env, "reports");
	if (!auth.ok) return auth.response;
	try {
		const { results } = await env.DB.prepare("SELECT * FROM reports ORDER BY createdAt").all();
		const data = results.map((r) => ({
			id: r.id, listingKey: r.listingKey,
			listing: r.listingJson ? JSON.parse(r.listingJson) : null,
			reason: r.reason, details: r.details, status: r.status,
			createdAt: r.createdAt, resolvedAt: r.resolvedAt,
		}));
		return json(data);
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListSharedShopRequests(request, env) {
	const auth = await requireAdminAuth(request, env, "sharedShopRequests");
	if (!auth.ok) return auth.response;
	try {
		const { results } = await env.DB.prepare("SELECT * FROM sharedShopRequests ORDER BY createdAt").all();
		const data = results.map((r) => ({
			id: r.id, username: r.username, shops: JSON.parse(r.shopsJson), bio: r.bio,
			status: r.status, createdAt: r.createdAt, resolvedAt: r.resolvedAt,
		}));
		return json(data);
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleResolveReport(request, env) {
	const auth = await requireAdminAuth(request, env, "reports");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const id = String(body.id || "");
	const action = String(body.action || "");
	if (!id) return json({ error: "id is required" }, 400);
	if (!["approve", "deny", "edit"].includes(action)) return json({ error: "Invalid action" }, 400);
	if (action === "edit" && !EDITABLE_LISTING_FIELDS.has(String(body.field || ""))) {
		return json({ error: "Invalid or missing field for edit" }, 400);
	}

	let reportRow;
	try {
		reportRow = await env.DB.prepare("SELECT * FROM reports WHERE id = ?").bind(id).first();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
	if (!reportRow) return json({ error: "Report not found" }, 404);

	const newStatus = action === "edit" ? "edited" : action === "approve" ? "approved" : "denied";
	const resolvedAt = new Date().toISOString();

	let listingChanged = false;
	try {
		await env.DB.prepare("UPDATE reports SET status = ?, resolvedAt = ? WHERE id = ?").bind(newStatus, resolvedAt, id).run();

		if (action === "approve") {
			const res = await env.DB.prepare("DELETE FROM listings WHERE rowKey = ?").bind(reportRow.listingKey).run();
			listingChanged = res.meta.changes > 0;
		} else if (action === "edit") {
			// body.field is checked against the EDITABLE_LISTING_FIELDS whitelist
			// above, so interpolating it into the column list here is safe.
			const res = await env.DB.prepare(`UPDATE listings SET ${body.field} = ? WHERE rowKey = ?`).bind(body.value, reportRow.listingKey).run();
			listingChanged = res.meta.changes > 0;
		}
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true, listingChanged });
}

async function handleResolveSharedShopRequest(request, env) {
	const auth = await requireAdminAuth(request, env, "sharedShopRequests");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const id = String(body.id || "");
	const action = String(body.action || "");
	if (!id) return json({ error: "id is required" }, 400);
	if (!["approve", "deny"].includes(action)) return json({ error: "Invalid action" }, 400);

	let reqRow;
	try {
		reqRow = await env.DB.prepare("SELECT * FROM sharedShopRequests WHERE id = ?").bind(id).first();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
	if (!reqRow) return json({ error: "Request not found" }, 404);

	const newStatus = action === "approve" ? "approved" : "denied";
	const resolvedAt = new Date().toISOString();

	try {
		await env.DB.prepare("UPDATE sharedShopRequests SET status = ?, resolvedAt = ? WHERE id = ?").bind(newStatus, resolvedAt, id).run();

		if (action === "approve") {
			const usernameKey = reqRow.username.toLowerCase();
			await env.DB.prepare(
				`INSERT INTO sharedShops (usernameKey, username, shopsJson, bio) VALUES (?, ?, ?, ?)
				 ON CONFLICT(usernameKey) DO UPDATE SET username=excluded.username, shopsJson=excluded.shopsJson, bio=excluded.bio`
			).bind(usernameKey, reqRow.username, reqRow.shopsJson, reqRow.bio || "").run();
		}
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true });
}

async function handleListFaq(request, env) {
	const auth = await requireAdminAuth(request, env, "faq");
	if (!auth.ok) return auth.response;
	try {
		const { results } = await env.DB.prepare("SELECT * FROM faq ORDER BY createdAt").all();
		return json(results);
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleAddFaq(request, env) {
	const auth = await requireAdminAuth(request, env, "faq");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const question = String(body.question || "").trim();
	const answer = String(body.answer || "").trim();
	if (!question) return json({ error: "question is required" }, 400);
	if (!answer) return json({ error: "answer is required" }, 400);

	const id = crypto.randomUUID();
	const createdAt = new Date().toISOString();

	try {
		await env.DB.prepare("INSERT INTO faq (id, question, answer, createdAt) VALUES (?, ?, ?, ?)").bind(id, question, answer, createdAt).run();
		return json({ ok: true, id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleUpdateFaq(request, env) {
	const auth = await requireAdminAuth(request, env, "faq");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const id = String(body.id || "");
	const question = String(body.question || "").trim();
	const answer = String(body.answer || "").trim();
	if (!id) return json({ error: "id is required" }, 400);
	if (!question) return json({ error: "question is required" }, 400);
	if (!answer) return json({ error: "answer is required" }, 400);

	try {
		const res = await env.DB.prepare("UPDATE faq SET question = ?, answer = ? WHERE id = ?").bind(question, answer, id).run();
		if (res.meta.changes === 0) return json({ error: "FAQ entry not found" }, 404);
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleDeleteFaq(request, env) {
	const auth = await requireAdminAuth(request, env, "faq");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);

	try {
		const res = await env.DB.prepare("DELETE FROM faq WHERE id = ?").bind(id).run();
		if (res.meta.changes === 0) return json({ error: "FAQ entry not found" }, 404);
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleClaimSquare(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const squareId = String(body.squareId || "");
	const username = String(body.username || "").trim();
	if (!isValidSquareId(squareId)) return json({ error: "Invalid squareId" }, 400);
	if (!isValidUsername(username)) return json({ error: "Invalid username" }, 400);

	try {
		const existing = await env.DB.prepare("SELECT status FROM worldMap WHERE squareId = ?").bind(squareId).first();
		if (existing) return json({ error: "Square is already claimed" }, 409);
		await env.DB.prepare(
			"INSERT INTO worldMap (squareId, status, username, claimedAt, completedAt) VALUES (?, 'claimed', ?, ?, NULL)"
		).bind(squareId, username, new Date().toISOString()).run();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true, status: "claimed" });
}

async function handleUnclaimSquare(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const squareId = String(body.squareId || "");
	const username = String(body.username || "").trim();
	if (!isValidSquareId(squareId)) return json({ error: "Invalid squareId" }, 400);
	if (!username) return json({ error: "username is required" }, 400);

	try {
		const existing = await env.DB.prepare("SELECT status, username FROM worldMap WHERE squareId = ?").bind(squareId).first();
		if (!existing || existing.status !== "claimed") return json({ error: "Square is not claimed" }, 409);
		if (existing.username.toLowerCase() !== username.toLowerCase()) return json({ error: "Username doesn't match the claim on this square" }, 403);
		await env.DB.prepare("DELETE FROM worldMap WHERE squareId = ?").bind(squareId).run();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true, status: "unclaimed" });
}

async function handleCompleteSquare(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const squareId = String(body.squareId || "");
	const username = String(body.username || "").trim();
	if (!isValidSquareId(squareId)) return json({ error: "Invalid squareId" }, 400);
	if (!username) return json({ error: "username is required" }, 400);

	try {
		const existing = await env.DB.prepare("SELECT status, username FROM worldMap WHERE squareId = ?").bind(squareId).first();
		if (!existing || existing.status !== "claimed") return json({ error: "Square is not claimed" }, 409);
		if (existing.username.toLowerCase() !== username.toLowerCase()) return json({ error: "Username doesn't match the claim on this square" }, 403);
		await env.DB.prepare("UPDATE worldMap SET status = 'done', completedAt = ? WHERE squareId = ?").bind(new Date().toISOString(), squareId).run();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true, status: "done" });
}

// Admin override — unlike the public claim/unclaim/complete endpoints, this
// doesn't check that the submitted username matches the existing claim; it
// just sets whatever status/owner the admin typed in.
async function handleAdminSetSquare(request, env) {
	const auth = await requireAdminAuth(request, env, "worldMap");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const squareId = String(body.squareId || "");
	const status = String(body.status || "");
	const username = String(body.username || "").trim();
	if (!isValidSquareId(squareId)) return json({ error: "Invalid squareId" }, 400);
	if (!WORLD_MAP_STATUSES.has(status)) return json({ error: "Invalid status" }, 400);
	if (status !== "unclaimed" && !isValidUsername(username)) return json({ error: "Invalid username" }, 400);

	try {
		if (status === "unclaimed") {
			await env.DB.prepare("DELETE FROM worldMap WHERE squareId = ?").bind(squareId).run();
			return json({ ok: true });
		}

		const existing = await env.DB.prepare("SELECT claimedAt, completedAt FROM worldMap WHERE squareId = ?").bind(squareId).first();
		const now = new Date().toISOString();
		const claimedAt = (existing && existing.claimedAt) || now;
		const completedAt = status === "done" ? ((existing && existing.completedAt) || now) : null;

		await env.DB.prepare(
			`INSERT INTO worldMap (squareId, status, username, claimedAt, completedAt) VALUES (?, ?, ?, ?, ?)
			 ON CONFLICT(squareId) DO UPDATE SET status=excluded.status, username=excluded.username, claimedAt=excluded.claimedAt, completedAt=excluded.completedAt`
		).bind(squareId, status, username, claimedAt, completedAt).run();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	return json({ ok: true });
}

const MANUAL_ID_PATTERN = /^M(\d+)$/;
const MAX_MANUAL_ENTRIES_PER_BATCH = 100;

// Manually-added listings (admin.html) reuse the same `listings` table as
// mod-scanned ones so they show up in search/filters/the seller page for
// free, but are otherwise distinguishable: baseItem is always the literal
// "manual", position holds free-form display text instead of coordinates,
// and lastSeen holds a sequential "M001"-style id instead of a timestamp —
// that id is what handleAdminDeleteManualListing looks entries up by, since
// there's no real chest position to key off of.
async function nextManualIds(env, count) {
	const { results } = await env.DB.prepare("SELECT lastSeen FROM listings WHERE lastSeen LIKE 'M%'").all();
	let max = 0;
	for (const r of results) {
		const m = MANUAL_ID_PATTERN.exec(r.lastSeen);
		if (m) max = Math.max(max, parseInt(m[1], 10));
	}
	const ids = [];
	for (let i = 1; i <= count; i++) ids.push("M" + String(max + i).padStart(3, "0"));
	return ids;
}

async function handleAdminAddManualListings(request, env) {
	const auth = await requireAdminAuth(request, env, "manualListings");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const seller = String(body.seller || "").trim();
	const worldRaw = String(body.world || "").trim().toLowerCase();
	const world = worldRaw === "firefly" ? "Firefly" : worldRaw === "honeybee" ? "Honeybee" : null;
	const entries = Array.isArray(body.entries) ? body.entries : [];

	if (!isValidUsername(seller)) return json({ error: "Invalid seller username" }, 400);
	if (!world) return json({ error: "World must be Firefly or Honeybee" }, 400);
	if (entries.length === 0) return json({ error: "No entries given" }, 400);
	if (entries.length > MAX_MANUAL_ENTRIES_PER_BATCH) return json({ error: `Too many entries at once (max ${MAX_MANUAL_ENTRIES_PER_BATCH})` }, 400);

	const parsed = [];
	for (const e of entries) {
		const itemName = String((e && e.itemName) || "").trim().slice(0, 100);
		const price = Number(e && e.price);
		const currency = String((e && e.currency) || "").trim().toLowerCase().slice(0, 40);
		const position = String((e && e.position) || "").trim().slice(0, 200);
		if (!itemName || !currency || !position || !Number.isFinite(price) || price < 0) {
			return json({ error: `Invalid entry: ${JSON.stringify(e)}` }, 400);
		}
		if (isBannedItem("manual", itemName)) {
			return json({ error: `"${itemName}" isn't allowed` }, 400);
		}
		const priceLabel = String((e && e.priceLabel) || "").trim().slice(0, 60) || `${price} ${currency}`;
		parsed.push({ itemName, price, currency, position, priceLabel });
	}

	try {
		const ids = await nextManualIds(env, parsed.length);
		const stmts = parsed.map((e, i) => {
			const rowKeyVal = `${world}|${seller}|manual|${e.itemName}`.toLowerCase();
			return env.DB.prepare(
				`INSERT INTO listings (rowKey, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen)
				 VALUES (?, ?, 'manual', 0, 0, 0, ?, ?, 1, 1, 1, ?, ?, ?, ?, ?)
				 ON CONFLICT(rowKey) DO UPDATE SET
				   price=excluded.price, priceLabel=excluded.priceLabel, currency=excluded.currency,
				   position=excluded.position, lastSeen=excluded.lastSeen`
			).bind(rowKeyVal, e.itemName, e.price, e.priceLabel, e.currency, seller, world, e.position, ids[i]);
		});
		await env.DB.batch(stmts);
		return json({ ok: true, added: parsed.map((e, i) => ({ itemName: e.itemName, id: ids[i] })) });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleAdminListManualListings(request, env) {
	const auth = await requireAdminAuth(request, env, "manualListings");
	if (!auth.ok) return auth.response;
	try {
		const { results } = await env.DB.prepare("SELECT * FROM listings WHERE lastSeen LIKE 'M%' ORDER BY lastSeen").all();
		return json(results.map((r) => ({ ...r, bulk: !!r.bulk, mixedContents: !!r.mixedContents })));
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleAdminDeleteManualListing(request, env) {
	const auth = await requireAdminAuth(request, env, "manualListings");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const id = String(body.id || "").trim();
	if (!MANUAL_ID_PATTERN.test(id)) return json({ error: "Invalid id" }, 400);

	try {
		const res = await env.DB.prepare("DELETE FROM listings WHERE lastSeen = ?").bind(id).run();
		if (res.meta.changes === 0) return json({ error: "Not found" }, 404);
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

// Deletes every listing for a seller in one shot — for a shop that's gone
// entirely (player quit, moved, shop torn down) rather than one stale item,
// which is what /admin/reports/resolve and /admin/listings/manual-delete are
// each scoped to. world is optional — omitted, this clears the seller on
// both worlds at once (the same seller can run independent shops on each).
async function handleAdminDeleteShopListings(request, env) {
	const auth = await requireAdminAuth(request, env, "manualListings");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const seller = String(body.seller || "").trim();
	if (!isValidUsername(seller)) return json({ error: "Invalid or missing seller" }, 400);
	const world = String(body.world || "").trim();
	if (world && world !== "Firefly" && world !== "Honeybee") return json({ error: "Invalid world" }, 400);

	try {
		const res = world
			? await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = lower(?) AND world = ?").bind(seller, world).run()
			: await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = lower(?)").bind(seller).run();
		return json({ ok: true, deleted: res.meta.changes });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

// ---------------- item price history (daily snapshots) ----------------

// Same file the website already reads for non-English name normalization —
// fetched fresh each run rather than duplicated into D1, since it changes
// rarely and this only runs once a day.
const ITEM_LANG_TABLE_URL = "https://sctp.nl/data/item-lang-table.json";

// Reads the current listings table and writes one row per (item, world) into
// itemDailyStats for today. "Item" here means baseItem + exact normalized
// display name together — a custom-renamed sword sharing a vanilla sword's
// baseItem is a *different* item for this purpose, same rule the item pages
// use to decide which listings count toward their own stats. itemKey is
// "v:<baseItem>|<name>" for everything today (no rare-item source data
// exists yet — see README for the "r:<slug>" scheme once it does).
//
// Deliberately grouping (and storing min/max/count/stock, not just avg) for
// *every* distinct item+name combo seen, not just the ~750 catalog items —
// cheap to store, and means nothing has to be re-derived later if a future
// feature wants it (e.g. browsing custom-named items, not just vanilla ones).
async function computeDailySnapshots(env) {
	let langTable = {};
	try {
		const res = await fetch(ITEM_LANG_TABLE_URL);
		if (res.ok) langTable = await res.json();
	} catch (e) {
		// Fall through with an empty table — every name is then treated as
		// already-English (matches localizedNameToEnglish()'s own fallback).
	}
	const langSets = new Map();
	for (const baseItem in langTable) langSets.set(baseItem, new Set(langTable[baseItem].alt));
	function displayName(baseItem, itemName) {
		const set = langSets.get(baseItem);
		if (!set || !set.has(alphaOnly(itemName))) return itemName;
		return langTable[baseItem].en;
	}

	const { results: rows } = await env.DB.prepare(
		"SELECT baseItem, itemName, price, currency, stackSize, amount, seller, world FROM listings"
	).all();

	// groupKey -> { itemKey, world, prices: number[], sellers: Set, totalStock, listingCount }
	const groups = new Map();
	for (const r of rows) {
		if (String(r.currency || "").toLowerCase() === "display") continue; // no real price/stock — same exclusion as the site's own price summary
		const name = displayName(r.baseItem, r.itemName);
		const itemKey = "v:" + String(r.baseItem).toLowerCase() + "|" + name.toLowerCase();
		const groupKey = itemKey + " " + r.world;
		let g = groups.get(groupKey);
		if (!g) {
			g = { itemKey, world: r.world, prices: [], sellers: new Set(), totalStock: 0, listingCount: 0 };
			groups.set(groupKey, g);
		}
		g.prices.push(priceInDiamonds(r) / (r.stackSize || 1));
		g.sellers.add(String(r.seller).toLowerCase());
		g.totalStock += Number(r.amount) || 0;
		g.listingCount++;
	}

	const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
	const stmts = [];
	for (const g of groups.values()) {
		const avg = g.prices.reduce((a, b) => a + b, 0) / g.prices.length;
		stmts.push(env.DB.prepare(
			`INSERT INTO itemDailyStats (itemKey, world, date, avgPrice, lowestPrice, highestPrice, listingCount, sellerCount, totalStock)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(itemKey, world, date) DO UPDATE SET
			   avgPrice=excluded.avgPrice, lowestPrice=excluded.lowestPrice, highestPrice=excluded.highestPrice,
			   listingCount=excluded.listingCount, sellerCount=excluded.sellerCount, totalStock=excluded.totalStock`
		).bind(g.itemKey, g.world, today, avg, Math.min(...g.prices), Math.max(...g.prices), g.listingCount, g.sellers.size, g.totalStock));
	}

	for (const chunk of chunkArray(stmts, 100)) {
		if (chunk.length > 0) await env.DB.batch(chunk);
	}

	return { date: today, itemsSnapshotted: groups.size, listingsScanned: rows.length };
}

async function handleGetItemHistory(request, env, ctx) {
	const url = new URL(request.url);
	const itemKey = url.searchParams.get("itemKey");
	if (!itemKey) return json({ error: "itemKey is required" }, 400);
	return cachedGet(request, ctx, HISTORY_CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare(
			"SELECT world, date, avgPrice, lowestPrice, highestPrice, listingCount, sellerCount, totalStock FROM itemDailyStats WHERE itemKey = ? ORDER BY date"
		).bind(itemKey).all();
		return results;
	});
}

// Admin-only manual trigger — same logic the daily cron runs, exposed so a
// snapshot can be forced without waiting for the schedule (testing, or
// backfilling today's data after a deploy).
async function handleAdminRunSnapshot(request, env) {
	const auth = await requireAdminAuth(request, env, null); // ops trigger, not a delegable content-moderation bucket
	if (!auth.ok) return auth.response;
	try {
		const result = await computeDailySnapshots(env);
		return json({ ok: true, ...result });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

const ROUTES = [
	["POST", "/listings", handleUploadListings],
	["GET", "/listings", handleGetListings],
	["POST", "/reports", handleSubmitReport],
	["POST", "/shared-shop-requests", handleSubmitSharedShopRequest],
	["GET", "/shared-shops", handleGetSharedShops],
	["GET", "/rare-items", handleGetRareItems],
	["GET", "/faq", handleGetFaqPublic],
	["GET", "/world-map", handleGetWorldMap],
	["POST", "/world-map/claim", handleClaimSquare],
	["POST", "/world-map/unclaim", handleUnclaimSquare],
	["POST", "/world-map/complete", handleCompleteSquare],
	["POST", "/admin/world-map/set", handleAdminSetSquare],
	["POST", "/admin/listings/manual-add", handleAdminAddManualListings],
	["GET", "/admin/listings/manual", handleAdminListManualListings],
	["POST", "/admin/listings/manual-delete", handleAdminDeleteManualListing],
	["POST", "/admin/listings/delete-shop", handleAdminDeleteShopListings],
	["GET", "/items/history", handleGetItemHistory],
	["POST", "/admin/run-snapshot", handleAdminRunSnapshot],
	["GET", "/admin/reports", handleListReports],
	["GET", "/admin/shared-shop-requests", handleListSharedShopRequests],
	["POST", "/admin/reports/resolve", handleResolveReport],
	["POST", "/admin/shared-shop-requests/resolve", handleResolveSharedShopRequest],
	["GET", "/admin/faq", handleListFaq],
	["POST", "/admin/faq/add", handleAddFaq],
	["POST", "/admin/faq/update", handleUpdateFaq],
	["POST", "/admin/faq/delete", handleDeleteFaq],
	["POST", "/admin/login", handleAdminLogin],
	["POST", "/admin/admins/create", handleAdminCreateAdmin],
	["GET", "/admin/admins", handleAdminListAdmins],
	["POST", "/admin/admins/update-permissions", handleAdminUpdatePermissions],
	["POST", "/admin/admins/delete", handleAdminDeleteAdmin],
	["POST", "/admin/admins/change-password", handleAdminChangePassword],
	["GET", "/admin/blocked-sellers", handleAdminListBlockedSellers],
	["POST", "/admin/blocked-sellers/add", handleAdminBlockSeller],
	["POST", "/admin/blocked-sellers/remove", handleAdminUnblockSeller],
	["GET", "/admin/rare-approvals", handleAdminListRareApprovals],
	["POST", "/admin/rare-approvals/resolve", handleAdminResolveRareApproval],
];

export default {
	async fetch(request, env, ctx) {
		const url = new URL(request.url);

		if (request.method === "OPTIONS") {
			return new Response(null, { headers: corsHeaders() });
		}

		for (const [method, path, handler] of ROUTES) {
			if (request.method === method && url.pathname === path) {
				return handler(request, env, ctx);
			}
		}

		return json({ error: "Not found" }, 404);
	},

	// Daily cron trigger (see wrangler.toml) — takes today's snapshot of every
	// item's price/stock/seller stats. See computeDailySnapshots() for why.
	async scheduled(event, env, ctx) {
		ctx.waitUntil(computeDailySnapshots(env));
	},
};

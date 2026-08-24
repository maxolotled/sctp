// Snailcraft Trading Post API — commits shared data straight to your GitHub
// repo, so GitHub Pages can serve it as plain static files.
//
// Public (Authorization: Bearer <API_KEY>):
//   POST /listings                -> merges into data/listings.json
//   POST /reports                 -> appends to data/reports.json
//   POST /shared-shop-requests    -> appends to data/shared-shops.json
//
// Admin-only (Authorization: Bearer <ADMIN_KEY>, set separately from API_KEY):
//   GET  /admin/reports
//   GET  /admin/shared-shop-requests
//   POST /admin/reports/resolve              body: {id, action: "approve"|"deny"|"edit", field?, value?}
//   POST /admin/shared-shop-requests/resolve body: {id, action: "approve"|"deny"}
//
// NOTE: data/reports.json and data/shared-shops.json are never linked from
// the website, but GitHub Pages has no per-file access control — anyone who
// knows/guesses the URL can fetch them directly, same as listings.json.
// Don't put anything in a report you wouldn't want technically public.

const BANNED_ITEMS = ["minecraft:diamond", "minecraft:diamond_block", "diamond", "diamondblock"];

const LISTINGS_PATH = "data/listings.json";
const REPORTS_PATH = "data/reports.json";
const SHARED_SHOPS_PATH = "data/shared-shops.json";
const SHARED_SHOPS_APPROVED_PATH = "data/shared-shops-approved.json";

const REPORT_REASONS = new Set(["scam", "wrong_info", "shop_gone", "inappropriate", "other"]);
const EDITABLE_LISTING_FIELDS = new Set([
	"itemName", "baseItem", "price", "priceLabel", "stackSize",
	"amount", "stacksInStock", "currency", "seller", "world", "position",
]);

function isBannedItem(baseItem, itemName) {
	const b = String(baseItem || "").toLowerCase().replace(/[^a-z:_]/g, "");
	const n = String(itemName || "").toLowerCase().replace(/[^a-z]/g, "");
	return BANNED_ITEMS.includes(b) || BANNED_ITEMS.includes(n);
}

function rowKey(r) {
	// World is part of the key because the same seller can run independent
	// shops on both Firefly and Honeybee.
	return `${r.world}|${r.seller}|${r.baseItem}|${r.itemName}`.toLowerCase();
}

function rowTimestamp(r) {
	const t = Date.parse(r.lastSeen);
	return isNaN(t) ? (r._uploadedAt || 0) : t;
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

// UTF-8-safe base64 helpers (plain btoa/atob mangle non-ASCII names).
function utf8ToBase64(str) {
	return btoa(unescape(encodeURIComponent(str)));
}
function base64ToUtf8(b64) {
	return decodeURIComponent(escape(atob(b64.replace(/\n/g, ""))));
}

async function githubRequest(env, path, method, body) {
	const url = `https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/contents/${path}`;
	const res = await fetch(url + (method === "GET" ? `?ref=${env.GITHUB_BRANCH}` : ""), {
		method,
		headers: {
			"Authorization": `Bearer ${env.GITHUB_TOKEN}`,
			"Accept": "application/vnd.github+json",
			"User-Agent": "snailcraft-trading-post-worker", // GitHub API rejects requests without one
			"Content-Type": "application/json",
		},
		body: body ? JSON.stringify(body) : undefined,
	});
	return res;
}

async function fetchCurrentFile(env, path) {
	const res = await githubRequest(env, path, "GET");
	if (res.status === 404) return { data: [], sha: null };
	if (!res.ok) throw new Error(`GitHub GET ${path} failed: ${res.status} ${await res.text()}`);
	const body = await res.json();
	const content = base64ToUtf8(body.content);
	return { data: content.trim() ? JSON.parse(content) : [], sha: body.sha };
}

async function commitFile(env, path, data, sha, message) {
	const body = {
		message,
		content: utf8ToBase64(JSON.stringify(data, null, 2)),
		branch: env.GITHUB_BRANCH,
	};
	if (sha) body.sha = sha;

	return githubRequest(env, path, "PUT", body);
}

/** Reads, mutates via `mutate(data) -> data`, and commits a JSON file, retrying on sha conflicts. */
async function updateFile(env, path, message, mutate) {
	for (let attempt = 0; attempt < 3; attempt++) {
		const current = await fetchCurrentFile(env, path);
		const next = mutate(current.data);
		if (next === null) return { committed: false }; // mutate() returns null to signal "nothing to do"

		const commitRes = await commitFile(env, path, next, current.sha, message);
		if (commitRes.status === 409) continue; // sha conflict, retry
		if (!commitRes.ok) {
			throw new Error(`GitHub commit to ${path} failed: ${commitRes.status} ${await commitRes.text()}`);
		}
		return { committed: true, data: next };
	}
	throw new Error(`Too many conflicting writes to ${path}, try again`);
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

	let added = 0, updated = 0, skipped = 0;
	try {
		const result = await updateFile(env, LISTINGS_PATH, "", (rows) => {
			const map = new Map();
			for (const r of rows) map.set(rowKey(r), r);

			added = 0; updated = 0; skipped = 0;
			for (const r of incoming) {
				if (!r.itemName || !r.seller) { skipped++; continue; }
				if (isBannedItem(r.baseItem, r.itemName)) { skipped++; continue; }
				const key = rowKey(r);
				const prev = map.get(key);
				if (!prev) { map.set(key, r); added++; }
				else if (rowTimestamp(r) >= rowTimestamp(prev)) { map.set(key, r); updated++; }
			}
			if (added === 0 && updated === 0) return null;
			return Array.from(map.values());
		});

		if (!result.committed) {
			return json({ added: 0, updated: 0, skipped, committed: false });
		}
		return json({ added, updated, skipped, total: result.data.length, committed: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
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
		status: "pending",
		createdAt: new Date().toISOString(),
		resolvedAt: null,
	};

	try {
		await updateFile(env, REPORTS_PATH, `New report on ${listingKey}`, (reports) => [...reports, report]);
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
	if (!username) return json({ error: "username is required" }, 400);
	if (shops.length === 0) return json({ error: "At least one shop is required" }, 400);

	const req = {
		id: crypto.randomUUID(),
		username,
		shops,
		status: "pending",
		createdAt: new Date().toISOString(),
		resolvedAt: null,
	};

	try {
		await updateFile(env, SHARED_SHOPS_PATH, `New shared-shop request for ${username}`, (reqs) => [...reqs, req]);
		return json({ ok: true, id: req.id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListReports(request, env) {
	if (!isAuthorized(request, env.ADMIN_KEY)) return json({ error: "Unauthorized" }, 401);
	try {
		const { data } = await fetchCurrentFile(env, REPORTS_PATH);
		return json(data);
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListSharedShopRequests(request, env) {
	if (!isAuthorized(request, env.ADMIN_KEY)) return json({ error: "Unauthorized" }, 401);
	try {
		const { data } = await fetchCurrentFile(env, SHARED_SHOPS_PATH);
		return json(data);
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleResolveReport(request, env) {
	if (!isAuthorized(request, env.ADMIN_KEY)) return json({ error: "Unauthorized" }, 401);

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

	let targetReport = null;
	try {
		await updateFile(env, REPORTS_PATH, `Resolve report ${id} (${action})`, (reports) => {
			const idx = reports.findIndex((r) => r.id === id);
			if (idx === -1) return null;
			targetReport = reports[idx];
			const resolved = {
				...targetReport,
				status: action === "edit" ? "edited" : action === "approve" ? "approved" : "denied",
				resolvedAt: new Date().toISOString(),
			};
			const next = [...reports];
			next[idx] = resolved;
			return next;
		});
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	if (!targetReport) return json({ error: "Report not found" }, 404);

	let listingChanged = false;
	if (action === "approve") {
		try {
			const result = await updateFile(env, LISTINGS_PATH, `Remove reported listing (report ${id})`, (rows) => {
				const next = rows.filter((r) => rowKey(r) !== targetReport.listingKey);
				if (next.length === rows.length) return null; // nothing matched, listing already gone
				listingChanged = true;
				return next;
			});
			listingChanged = result.committed;
		} catch (e) {
			return json({ error: String(e) }, 502);
		}
	} else if (action === "edit") {
		try {
			const result = await updateFile(env, LISTINGS_PATH, `Edit reported listing (report ${id})`, (rows) => {
				const idx = rows.findIndex((r) => rowKey(r) === targetReport.listingKey);
				if (idx === -1) return null; // listing already gone
				const next = [...rows];
				next[idx] = { ...next[idx], [body.field]: body.value };
				listingChanged = true;
				return next;
			});
			listingChanged = result.committed;
		} catch (e) {
			return json({ error: String(e) }, 502);
		}
	}

	return json({ ok: true, listingChanged });
}

async function handleResolveSharedShopRequest(request, env) {
	if (!isAuthorized(request, env.ADMIN_KEY)) return json({ error: "Unauthorized" }, 401);

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

	let targetRequest = null;
	try {
		await updateFile(env, SHARED_SHOPS_PATH, `Resolve shared-shop request ${id} (${action})`, (reqs) => {
			const idx = reqs.findIndex((r) => r.id === id);
			if (idx === -1) return null;
			targetRequest = reqs[idx];
			const resolved = {
				...targetRequest,
				status: action === "approve" ? "approved" : "denied",
				resolvedAt: new Date().toISOString(),
			};
			const next = [...reqs];
			next[idx] = resolved;
			return next;
		});
	} catch (e) {
		return json({ error: String(e) }, 502);
	}

	if (!targetRequest) return json({ error: "Request not found" }, 404);

	if (action === "approve") {
		try {
			await updateFile(env, SHARED_SHOPS_APPROVED_PATH, `Publish approved shared shops for ${targetRequest.username}`, (list) => {
				const key = targetRequest.username.toLowerCase();
				const next = list.filter((e) => String(e.username || "").toLowerCase() !== key);
				next.push({ username: targetRequest.username, shops: targetRequest.shops });
				return next;
			});
		} catch (e) {
			return json({ error: String(e) }, 502);
		}
	}

	return json({ ok: true });
}

const ROUTES = [
	["POST", "/listings", handleUploadListings],
	["POST", "/reports", handleSubmitReport],
	["POST", "/shared-shop-requests", handleSubmitSharedShopRequest],
	["GET", "/admin/reports", handleListReports],
	["GET", "/admin/shared-shop-requests", handleListSharedShopRequests],
	["POST", "/admin/reports/resolve", handleResolveReport],
	["POST", "/admin/shared-shop-requests/resolve", handleResolveSharedShopRequest],
];

export default {
	async fetch(request, env) {
		const url = new URL(request.url);

		if (request.method === "OPTIONS") {
			return new Response(null, { headers: corsHeaders() });
		}

		for (const [method, path, handler] of ROUTES) {
			if (request.method === method && url.pathname === path) {
				return handler(request, env);
			}
		}

		return json({ error: "Not found" }, 404);
	},
};

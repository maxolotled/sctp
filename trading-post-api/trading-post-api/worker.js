// Snailcraft Trading Post API — backed by Cloudflare D1. Both writes AND
// public reads go through this Worker (reads are edge-cached for
// CACHE_TTL_SECONDS so most requests never touch D1 at all).
//
// Public (Authorization: Bearer <API_KEY>):
//   POST /listings                -> upserts into the listings table. Rejected (426) if the
//     uploading mod's modVersion is below MIN_UPLOAD_VERSION — see handleUploadListings.
//   POST /reports                 -> appends to reports
//   POST /suggestions             body: {title, details, submitterName?} -> appends to suggestions
//   POST /bug-reports             body: {title, details, area: "website"|"mod", world?, pageUrl?, submitterName?} -> appends to bugReports
//   POST /player-reports          body: {reportedUsername, world, reason, details, reporterName?} -> appends to playerReports
//   POST /shared-shop-requests    -> appends to sharedShopRequests
//   POST /world-map/claim         body: {squareId, username}  -> claims a map square
//   POST /world-map/unclaim       body: {squareId, username}  -> releases a claimed square
//   POST /world-map/complete      body: {squareId, username}  -> marks a claimed square done
//
// Public, unauthenticated, cached (CACHE_TTL_SECONDS at the edge):
//   GET /listings          -> full listings array
//     ?mapart=1 also appends one pseudo-listing per mapart gallery piece (mapartGallery: true,
//     currency "display") — used only by the website's listings table. Filled-map shop
//     scans are ignored on upload (maps live in the mapart gallery instead).
//   GET /shared-shops      -> approved shared-shop entries
//   GET /rare-items        -> {firefly: [...], honeybee: [...]}
//   GET /faq               -> faq entries
//   GET /world-map         -> {squareId: {status, username, claimedAt, completedAt}}
//   GET /update-notice     -> {enabled, minVersion, message} — the mod checks this on
//     join and prints `message` to chat if its own version is below minVersion (see
//     UpdateNoticeCheck in the mod and the "updateNotice" permission bucket below).
//   GET /roadmap           -> {fields, lists, cards} — server-side proxy of the public
//     Trello roadmap board + its Amazing Fields Power-Up data, see roadmap/index.html.
//
// Note: there's deliberately no server-rendered share/embed page — the site's Share
// buttons (index.html, marketplace/index.html, 404.html) link straight back to a real
// sctp.nl page (?listing=<id>, #listing=/#job=, /items/<slug>, /s/<world>/<seller>)
// instead of a page hosted on this Worker's own domain. That gives up a real Discord/
// etc. embed (static GitHub Pages can't generate per-listing og:tags), traded
// deliberately for every shared link being a real, same-site page.
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
//   GET  /admin/admins                       ?username=<exact> looks up one account; omitted -> every account (see handleAdminListAdmins)
//   POST /admin/admins/update-permissions    body: {id, permissions: [...]}
//   POST /admin/admins/delete                body: {id}
//   POST /admin/run-snapshot                 -> forces an item-history snapshot + full listings.json dump to R2 now (see the daily cron below)
//   GET  /admin/snapshots                    -> {dates: [...]}, every date with a stored R2 dump
//   GET  /admin/snapshots?date=YYYY-MM-DD    -> that day's full listings.json (see snapshotListingsToR2)
//
// Any logged-in admin (own account only, unless caller is head admin):
//   POST /admin/admins/change-password       body: {id?, oldPassword?, newPassword} -- id omitted = self; oldPassword required unless a head admin is resetting someone else's
//
// Any logged-in account, self-service (see account/index.html):
//   GET  /account/me                         -> {username, isHeadAdmin, mcUsername, mcVerified, contactDiscord, contactTimezone}
//   POST /account/contact-info               body: {contactDiscord?, contactTimezone?} -- free text, shown to the other party once a trade is confirmed (see contactInfoText)
//
// Self-service registration (see register/index.html and /verify at the repo root):
//   POST /account/register/direct            body: {mcUsername, password} -> {token, ...} (logs them in immediately)
//     Currently what the website actually uses — takes the typed username on trust
//     (mcVerified: 0), no real login check. See handleDirectRegistration.
//   POST /account/register/start             body: {mcUsername} -> {code, joinAddress, expiresAt}
//   GET  /account/register/status?code=      (public, polled by the website) -> {verified, mcUsername}
//   POST /account/register/complete          body: {code, password} -> {token, ...} (logs them in immediately)
//   POST /account/register/verify-callback   (VERIFY_SERVER_SECRET only, called by java_server.py) body: {code, mcUsername, mcUuid?}
//   GET  /account/register/find-pending      (VERIFY_SERVER_SECRET only, called by bedrock_bridge.py) ?mcUsername= -> {code}
//     The 5 routes above implement the real join-a-server verification flow — not
//     currently wired up to the frontend (see /account/register/direct), but left
//     intact so it's a frontend swap, not a rebuild, to turn verification back on.
//
// Mapart catalog (see 0017_mapart.sql). Scanned item-frame maparts come from the
// temporary mapart-scanner mod; owners (verified accounts that have claimed a piece)
// and head admins manage them.
//   GET  /mapart                             (public, cached) -> every mapart
//   GET  /mapart/by-slug?slug=               (public, resolves old slugs too) -> one mapart
//   GET  /mapart/image?id=                   (public) -> the stitched PNG from R2
//   POST /mapart/upload                      (API_KEY) body: {world, maps:[{leadMapId, rawName, width, height, partMapIds, png(base64)}]}
//   GET  /mapart/mine                        (verified account) -> its claimed maparts
//   POST /mapart/claim | /mapart/abandon     (verified account) body: {id}
//   POST /mapart/update                      (owner, head admin, or "manageMapart") body: {id, title?, artist?, whereToBuy?, notForSale?, category?, world?} — "manageMapart" holders may only touch artist/world/category
//   POST /mapart/report                      (API_KEY, like POST /reports) body: {id, reason: "wrong_artist"|"wrong_world"|"wrong_category"|"inappropriate_image", details?} -> lands in the `reports` queue as listingKey "mapart:<id>", handled by "manageMapart"
//   GET  /collection/mine, POST /collection/set {kind: "rare"|"mapart", world, ids[], owned}, POST /collection/privacy {private}   (any account) — personal collections, per world
//   GET  /collection/public?username=        (public) -> {username, private, items?}
//   GET  /raredle/state, POST /raredle/guess {itemId}, POST /raredle/practice/new + mode=practice on state/guess (any account), GET /raredle/leaderboard (public) — the daily Rare-dle game; the answer never leaves the Worker until a game is finished. POST /raredle/reset exists but is QA-only (RAREDLE_ALLOW_RESET is false live).
//   GET  /profile?username=                  (public, cached 2m) -> mapart (as artist/commissioner/owner), commission info, collection summary, marketplace history, jobRating (thumbs up/down summed across every job they've posted)
//   POST /account/commission                 (verified account) body: {open, info?, discord?} — shown on the profile's Mapart tab
//   GET  /mapart/of-the-day                  (public) one random piece per UTC day; POST /admin/mapart/otd/reroll ("manageMapart") body: {id?} picks another
//   POST /mapart/search-image                (public) body: {hash} 64-hex difference hash from mapart-search.js -> closest pieces; POST /admin/mapart/build-index ("manageMapart") indexes not-yet-hashed pieces in small batches
//   GET  /stats/mine now also returns hints: {restock[], reprice[], undercut[]}
//   POST /mapart/submit                      (verified account) body: {title, world, width, height, png(base64, exactly width*128 x height*128), artist?, category?, whereToBuy?, notForSale?}
//   POST /mapart/delete-own                  (verified account) body: {id} — only pieces the account uploaded itself
//   POST /mapart/takedown | /mapart/takedown/cancel   (verified owner) body: {id, reason?} — asks a head admin to delete the piece for good and block re-uploads
//   GET  /admin/mapart/takedowns, POST /admin/mapart/takedowns/resolve {id, action: approve|deny}   (head admin only)
//   POST /admin/mapart/split {id, ownedIndexes?, dryRun?} ("manageMapart") splits a wrongly-merged piece into its 1x1 maps — see handleAdminSplitMapart
//   GET  /store/listings                     (verified account) -> {seller, manual[], scanned[] (read-only), scannedTotal, manualCap}
//   POST /store/listings/add | /update | /delete   (verified account; manual listings of its own MC username only, max 100)
//   POST /admin/mapart/delete {id}           (head admin or "manageMapart"; also the "approve" of an inappropriate_image report)
//   GET  /admin/mapart, POST /admin/mapart/assign {id, username}   (head admin only)
//   POST /admin/mapart/rederive  (head admin only) re-runs artist/title detection on unclaimed, unedited pieces
// Verification links — single-use, expiring, head-admin generated:
//   POST /admin/verification-links/create {mcUsername, days?}, GET /admin/verification-links, POST /admin/verification-links/revoke {token}   (head admin only)
//   GET  /verify-link/info?token=            (public) -> {mcUsername, expiresAt}
//   POST /verify-link/redeem                 (public) body: {token, mode: "register"|"link", username?, password} -> session
// Password reset links — same single-use/expiring/head-admin-generated shape as
// verification links above, but for an existing account's password (no email
// sending here — the admin sends the link to the player some other way):
//   POST /admin/password-reset-links/create {username, days?}, GET /admin/password-reset-links, POST /admin/password-reset-links/revoke {token}   (head admin only)
//   GET  /password-reset-link/info?token=    (public) -> {username, expiresAt}
//   POST /password-reset-link/redeem         (public) body: {token, newPassword} -> session
//
// Forms — a small custom form builder, entirely separate from every other
// content type here. See ADMIN-ONLY below for the "manageForms" bucket
// (create/edit/list/delete forms + view responses); these are the PUBLIC
// routes anyone filling out a form actually hits:
//   GET  /forms/get?slug=<slug>              -> {id, title, description, questions, requireLogin,
//     responsePolicy, status, closesAt, responseLimit, responsesSoFar, confirmationMessage} for an
//     OPEN form; a draft/closed form 404s here unless the caller has "manageForms" (lets the
//     admin preview before publishing) — see handleGetPublicForm.
//   GET  /forms/my-response?slug=<slug>&token=<respondentToken>   -> {exists, answers?, submittedAt?,
//     updatedAt?} for the caller's own prior response, if any — token is only used when logged
//     out (Authorization header takes priority when present). Lets the public page pre-fill an
//     editable form, or show a locked form's own answers read-only.
//   POST /forms/submit    body: {slug, answers: {questionId: value}, respondentToken?} -> {ok,
//     confirmationMessage, updated} — updated is true when this overwrote an existing response
//     (see responsePolicy "oncePerRespondentEditable" in recordFormResponse).
//
// Permission bucket "reports":
//   GET  /admin/reports
//   POST /admin/reports/resolve              body: {id, action: "approve"|"deny"|"edit", field?, value?}
//   POST /admin/listings/remove              body: {rowKey} -> instant delete, no report record (website's Remove button)
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
// Permission bucket "updateNotice":
//   GET  /admin/update-notice                -> current config {enabled, minVersion, message, updatedAt, updatedBy}
//   POST /admin/update-notice/set            body: {enabled, minVersion, message}
// Permission bucket "suggestions":
//   GET  /admin/suggestions
//   POST /admin/suggestions/delete           body: {id}
// Permission bucket "bugReports":
//   GET  /admin/bug-reports
//   POST /admin/bug-reports/delete           body: {id}
// Permission bucket "playerReports":
//   GET  /admin/player-reports
//   POST /admin/player-reports/resolve       body: {id, action: "ban"|"remove"|"none"} -> ban blocks the seller
//     (both worlds, permanent until unblocked); remove wipes just their listings in the
//     reported world (not banned, can sell again); none dismisses with no side effect.
// Permission bucket "manageForms" (see "Forms" above for the public routes):
//   POST /admin/forms/create   body: {slug, title, description?, questions, requireLogin, responsePolicy,
//     status?, closesAt?, responseLimit?, confirmationMessage?} -> full form (slug must be unique,
//     [a-z0-9-]+, 2-64 chars)
//   GET  /admin/forms                        -> every form, metadata + responseCount only (no questions/responses)
//   GET  /admin/forms/get?id=<id>            -> one form's full definition, for editing
//   POST /admin/forms/update   body: {id, ...same fields as create}
//   POST /admin/forms/set-status              body: {id, status: "draft"|"open"|"closed"}
//   POST /admin/forms/delete                  body: {id} -> also deletes every response
//   GET  /admin/forms/responses?id=<id>&format=json|csv (default json)  -> every response, raw
//   POST /admin/forms/responses/delete        body: {id} -> delete one response (moderation)
//   GET  /admin/forms/summary?id=<id>         -> per-question aggregates for the Summary tab
//     (option counts for choice/rating types, min/avg/max for number, a raw value list for text/date)
//
// GET /items/history?itemKey=<key> (public, cached 1hr) -> daily price/stock/seller
//   history for one item. itemKey is "v:<baseItem>|<exact display name>" (lowercased)
//   for vanilla items. Populated by a daily cron trigger (see wrangler.toml), not
//   by any upload — see computeDailySnapshots() below.
// GET /stats/item?itemKey=<key> (public, cached 1hr) -> all-time totals (estimated units
//   sold, distinct sellers ever) per world, the latest day's snapshot, a daily sold-units
//   trend (soldTrend, per world) and a combined daily average (avgSoldPerDay).
// GET /stats/world?world=Firefly|Honeybee (public, cached 1hr) -> world-wide daily trend
//   (listings/stock/distinct items/sellers) + every item ever sold here, sorted by units
//   sold (estimated) — not just a top-N, so the /stats page can search the full list.
// GET /stats/mine (any logged-in account with a verified linked MC username) -> that
//   seller's own shop stats: active listings, best sellers, sales trend, all estimated
//   from listing-snapshot deltas — see computeSellerItemStats' doc comment for why.
//
// Marketplace — every site account (admin or plain marketplace user) is a
// row in `admins`; "admin" just means isHeadAdmin or a non-empty permissions
// array. POST /admin/login is the one shared login for everyone.
//   POST /admin/admins/set-mc (head-admin only)   body: {id, mcUsername, mcVerified} -> manually link + verify an account's MC username
//   GET  /marketplace/listings (public, cached)   -> active selling/lookingFor posts with bid summaries
//     + bidHistory (amount/currency/message/status/createdAt, never bidderAccountId — full
//     bidder identity is only ever visible to the listing's own owner, via /marketplace/mine)
//   POST /marketplace/listings/create             body: {type: "selling"|"lookingFor", itemName, baseItem?, world: "Firefly"|"Honeybee"|"Cross-world", quantity, notes?, askingPrice?, askingCurrency?, startingBid?, startingBidCurrency?, budget?, budgetCurrency?}
//     -> currency fields must be one of MARKETPLACE_CURRENCIES ("diamond"|"diamondblock"|"diamondstack" — dia/db/stx in the UI)
//   POST /marketplace/listings/cancel             body: {id} -> owner only
//   POST /marketplace/bids/place                  body: {listingId, amount, currency, message?} -> currency must be one of MARKETPLACE_CURRENCIES
//     works on both "selling" (a buyer's bid) and "lookingFor" (a seller offering to sell at that price) listings
//   POST /marketplace/bids/withdraw                body: {bidId} -> bidder only
//   POST /marketplace/bids/accept                 body: {bidId} -> listing owner only, rejects every other pending bid
//   POST /marketplace/bids/reject                 body: {bidId} -> listing owner only
//   GET  /marketplace/mine                        -> caller's own listings + bids placed + bids received
//   GET  /marketplace/notifications                -> caller's own notifications
//   POST /marketplace/notifications/mark-read      body: {ids: [...]} or {} for "mark all"
//   GET  /marketplace/notifications/for-mc?mcUsername=<name> (public, no session — the MOD calls this on join)
//     -> undelivered notifications for a VERIFIED account only, marks them delivered
//     also the basis for unique-mod-user tracking (see recordModUserPing/modUserPings) —
//     every mod install hits this on every join, account or no account, so it doubles
//     as a live per-player ping without needing any mod update.
//   GET  /admin/mod-user-stats                     (head admin only) -> {total, activeLast7d, activeLast30d, trackingSince}
//   GET  /admin/marketplace/listings?username=<exact> (empty/missing -> []), POST /admin/marketplace/listings/remove (permission "marketplaceListings")
//
// Jobs/tasks marketplace — deliberately simpler than the item listings above
// (see 0015_marketplace_jobs.sql): no bidding, "I'm interested" is a single
// action that reveals contact info to both sides immediately.
//   GET  /marketplace/jobs (public, cached)        -> active hiring/forHire posts
//   POST /marketplace/jobs/create                  body: {type: "hiring"|"forHire", title, description?, world, rewardAmount?, rewardCurrency?, deadline?}
//   POST /marketplace/jobs/interest                body: {jobId, message?} -> records interest, returns {contactInfo} for the poster, notifies the poster with the responder's contact info
//   POST /marketplace/jobs/close                   body: {id, status: "fulfilled"|"cancelled"} -> poster only
//   POST /marketplace/jobs/review                  body: {jobId, vote: 1|-1|0} -> thumbs up/down on someone else's job post, 0 removes your vote; GET /marketplace/jobs returns each job's {thumbsUp, thumbsDown}, GET /marketplace/mine returns your own votes as {myJobReviews: {jobId: vote}}
//   GET  /marketplace/mine also returns {jobs, myJobInterests, jobInterestsReceived} (the last one includes contactInfo directly — see handleGetMyMarketplace)
// Active selling/lookingFor listings are also merged straight into GET
// /listings (see handleGetListings) — tagged marketplace/marketplaceType/
// marketplaceListingId — so they show up in the site's normal listings
// table/search/item pages and the mod's /search + watchlist check for free.
// Listings auto-expire 14 days after creation (expireOldMarketplaceListings,
// piggybacking the daily cron) if never fulfilled/cancelled.

const BANNED_ITEMS = ["minecraft:diamond", "minecraft:diamond_block", "diamond", "diamondblock"];

// "wrong_world" is a pre-existing site bug fix bundled in here: index.html's
// report modal has always sent this value for its "Wrong World" radio option,
// but it was missing from this set — that reason 400'd on submit.
const REPORT_REASONS = new Set(["scam", "wrong_info", "wrong_world", "shop_gone", "inappropriate", "other"]);
const BUG_REPORT_AREAS = new Set(["website", "mod"]);
const PLAYER_REPORT_REASONS = new Set(["scamming", "inappropriate_content", "spam", "other"]);
const EDITABLE_LISTING_FIELDS = new Set([
	"itemName", "baseItem", "price", "priceLabel", "stackSize",
	"amount", "stacksInStock", "currency", "seller", "world", "position", "bundled",
]);
const WORLD_MAP_STATUSES = new Set(["unclaimed", "claimed", "done"]);
const WORLD_MAP_GRID_SIZE = 49; // leaf squares per axis, see /world for the full grid math

// ---- forms (see the "Forms" doc-comment block above) ----
const FORM_QUESTION_TYPES = new Set(["short_text", "paragraph", "multiple_choice", "checkboxes", "dropdown", "number", "date", "rating", "yes_no"]);
const FORM_CHOICE_TYPES = new Set(["multiple_choice", "checkboxes", "dropdown"]); // these carry an `options` array
const FORM_STATUSES = new Set(["draft", "open", "closed"]);
const FORM_RESPONSE_POLICIES = new Set(["unlimited", "oncePerRespondentEditable", "oncePerRespondentLocked"]);
const FORM_SLUG_RE = /^[a-z0-9-]{2,64}$/;
const FORM_MAX_QUESTIONS = 50;
const FORM_RATING_MAX_SPAN = 10; // e.g. 1-10 at most — keeps the summary chart sane

const CACHE_TTL_SECONDS = 120;
// Item history only changes once a day (see the scheduled handler at the
// bottom of this file), so there's no point re-querying D1 every 30s for it.
const HISTORY_CACHE_TTL_SECONDS = 3600;

// Same convention as index.html/404.html/list/index.html's price sort/filter
// logic and the mod's Listing.java — real player-market rates: 64 iron = 1
// diamond, 18 gold = 1 diamond, 1 netherite ingot = 18 diamonds. Block
// variants use the standard 9-per-block crafting ratio. Currencies outside
// this map (emerald, coal, ...) default to 1:1, same as everywhere else.
const CURRENCY_VALUE = {
	diamond: 1, diamondblock: 9, diamondstack: 576, // diamondstack = 64 diamond blocks — marketplace-only currency, see MARKETPLACE_CURRENCIES
	iron: 1 / 64, ironingot: 1 / 64, ironblock: 9 / 64,
	gold: 1 / 18, goldingot: 1 / 18, goldblock: 9 / 18,
	netherite: 18, netheriteingot: 18, netheriteblock: 162,
};

// Marketplace price/bid fields are a fixed 3-option dropdown (dia/db/stx in
// the UI) rather than free text — unlike real shop-sign currencies (which
// come from whatever a player actually wrote on a sign), a marketplace post
// is hand-entered so there's no reason to allow arbitrary strings here.
const MARKETPLACE_CURRENCIES = new Set(["diamond", "diamondblock", "diamondstack"]);
const MARKETPLACE_WORLDS = new Set(["Firefly", "Honeybee", "Cross-world"]);
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
const CACHE_EPOCH = "5"; // bumped: mapart split tool changes the mapart list

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

// Hard floor: an upload from below this version is rejected outright (see
// handleUploadListings) rather than partially trusted. Since this is above
// MIN_TRUSTED_PRUNE_VERSION, every upload that gets past this gate is
// automatically also trusted for scannedPositions pruning.
const MIN_UPLOAD_VERSION = "1.5.1";

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

// Shared upsert logic for writing a row into `listings` — used by handleUploadListings.
function buildListingUpsertStmt(env, key, r) {
	// availableSince and id are intentionally NOT in the ON CONFLICT...DO
	// UPDATE SET list below — SQLite only applies the bound value on a
	// genuine INSERT; an existing row keeps whatever it already had
	// regardless of what's bound here. For id specifically, this is what
	// makes it a stable per-listing identifier (see 0016_listing_ids.sql) —
	// a listing that gets re-uploaded/updated keeps the same shareable id.
	const availableSince = r.submittedAt || new Date().toISOString();
	return env.DB.prepare(
		`INSERT INTO listings (rowKey, id, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen, availableSince, missingStreak)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
		 ON CONFLICT(rowKey) DO UPDATE SET
		   itemName=excluded.itemName, baseItem=excluded.baseItem, bulk=excluded.bulk, bundled=excluded.bundled,
		   mixedContents=excluded.mixedContents, price=excluded.price, priceLabel=excluded.priceLabel,
		   stackSize=excluded.stackSize, amount=excluded.amount, stacksInStock=excluded.stacksInStock,
		   currency=excluded.currency, seller=excluded.seller, world=excluded.world,
		   position=excluded.position, lastSeen=excluded.lastSeen, missingStreak=0`
	).bind(
		key, newId(), r.itemName, r.baseItem, r.bulk ? 1 : 0, r.bundled ? 1 : 0, r.mixedContents ? 1 : 0,
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

// ---------------- admin auth: multi-account + granular permissions ----------------
//
// Admins are real accounts (username + password) with a session token from
// POST /admin/login. A head admin has full access to everything; everyone
// else is limited to whichever of these permission buckets they've been
// granted. The old shared ADMIN_KEY master-key bypass was removed once real
// accounts existed — see requireAnyAdmin.
const ADMIN_PERMISSION_BUCKETS = new Set([
	"reports", "sharedShopRequests", "faq", "worldMap", "manualListings", "blockedSellers", "marketplaceListings", "updateNotice",
	"suggestions", "bugReports", "playerReports", "manageMapart",
]);

// Ranks bids for a seller's convenience using the shared CURRENCY_VALUE
// table above (see priceInDiamonds) — amounts/currencies are always shown
// and stored as-is, never silently combined into one converted number.
function marketplaceValueInDiamonds(amount, currency) {
	const mult = CURRENCY_VALUE[String(currency || "").toLowerCase()];
	return (amount || 0) * (mult === undefined ? 1 : mult);
}
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

/** Passes if the account holds at least one of `permissions` (head admins always pass). Adds `perms` (the account's own list) so callers can tell which one(s) matched. */
async function requireAnyPermission(request, env, permissions) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base;
	let perms = [];
	try { perms = JSON.parse(base.admin.permissions || "[]"); } catch (e) { /* treat as no permissions */ }
	if (base.admin.isHeadAdmin) return { ...base, perms: [...permissions] };
	if (!permissions.some((p) => perms.includes(p))) return { ok: false, response: json({ error: "Forbidden" }, 403) };
	return { ...base, perms };
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

	// Case-insensitive: "Steve" and "steve" are the same login (uniqueness is
	// enforced the same way at every account-creation path — see the other
	// lower(username) lookups below).
	const admin = await env.DB.prepare("SELECT * FROM admins WHERE lower(username) = lower(?)").bind(username).first();
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
	// Every account logs in through here now, admin or not — permissions
	// being empty and isHeadAdmin false just means "a plain marketplace
	// account", not an error. mcUsername/mcVerified let the frontend show a
	// verified checkmark and decide whether in-game notification delivery
	// applies to this account.
	return json({
		token, username: admin.username, isHeadAdmin: !!admin.isHeadAdmin, permissions, expiresAt,
		mcUsername: admin.mcUsername || null, mcVerified: !!admin.mcVerified,
	});
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
	// Plain marketplace accounts go through this exact same endpoint now —
	// just with isHeadAdmin/permissions left at their defaults (false/[]).
	// mcUsername can be set here, but mcVerified always starts false — see
	// handleAdminSetMc, a deliberate separate action, never implied by creation.
	const mcUsername = body.mcUsername ? String(body.mcUsername).trim() : null;
	if (!username) return json({ error: "username is required" }, 400);
	if (password.length < 8) return json({ error: "password must be at least 8 characters" }, 400);

	const existing = await env.DB.prepare("SELECT id FROM admins WHERE lower(username) = lower(?)").bind(username).first();
	if (existing) return json({ error: "Username already exists" }, 409);

	const id = crypto.randomUUID();
	const salt = newSaltHex();
	const hash = await hashPassword(password, salt);
	const createdAt = new Date().toISOString();

	try {
		await env.DB.prepare(
			"INSERT INTO admins (id, username, passwordHash, passwordSalt, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
		).bind(id, username, hash, salt, isHeadAdmin ? 1 : 0, JSON.stringify(permissions), createdAt, auth.admin.username, mcUsername).run();
		return json({ ok: true, id, isHeadAdmin });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

// Head-admin-only: link/relink an account's Minecraft username and set
// whether it's actually been verified (manual process for now — see the
// project notes; there's no automated proof-of-ownership flow yet).
async function handleAdminSetMc(request, env) {
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
	const mcUsername = body.mcUsername ? String(body.mcUsername).trim() : null;
	const mcVerified = body.mcVerified === true;

	if (mcVerified && mcUsername) {
		const holder = await env.DB.prepare(
			"SELECT id FROM admins WHERE mcVerified = 1 AND id != ? AND lower(ltrim(mcUsername, '.')) = lower(ltrim(?, '.'))"
		).bind(id, mcUsername).first();
		if (holder) return json({ error: "Another account is already verified as that Minecraft username." }, 409);
	}

	const res = await env.DB.prepare("UPDATE admins SET mcUsername = ?, mcVerified = ? WHERE id = ?")
		.bind(mcUsername, mcVerified ? 1 : 0, id).run();
	if (res.meta.changes === 0) return json({ error: "Account not found" }, 404);
	if (mcVerified && mcUsername) await mapartAutoClaimSweep(env, id, mcUsername);
	return json({ ok: true });
}

// ?username=<exact> looks up just that one account (admin.html's account
// search bar) — with self-registration now live (see /account/register/*),
// this table is every player who's ever registered, not just a handful of
// staff, so "list everyone" isn't something the UI should default to
// anymore. The no-param behavior is kept for any other caller, but
// admin.html itself never calls it without a username now.
async function handleAdminListAdmins(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const url = new URL(request.url);
	const username = (url.searchParams.get("username") || "").trim();

	const { results } = username
		? (await env.DB.prepare(
			"SELECT id, username, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified FROM admins WHERE lower(username) = ?"
		).bind(username.toLowerCase()).all())
		: (await env.DB.prepare(
			"SELECT id, username, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified FROM admins ORDER BY createdAt"
		).all());
	return json(results.map((r) => ({ ...r, isHeadAdmin: !!r.isHeadAdmin, mcVerified: !!r.mcVerified, permissions: JSON.parse(r.permissions || "[]") })));
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

// ---------------- account settings (self-service, any logged-in account) ----------------

async function handleGetAccountMe(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	const a = auth.admin;
	return json({
		username: a.username, isHeadAdmin: !!a.isHeadAdmin,
		mcUsername: a.mcUsername || null, mcVerified: !!a.mcVerified,
		contactDiscord: a.contactDiscord || null, contactTimezone: a.contactTimezone || null,
		commission: { open: !!a.commissionOpen, info: a.commissionInfo || "", discord: a.commissionDiscord || "" },
	});
}

// contactDiscord/contactTimezone are free text the account owner sets
// themselves (unlike mcUsername, which only a head admin can set/verify) —
// shown to a bid's other party once a trade is actually confirmed, see
// contactInfoText. Deliberately no "personal info" validation here beyond
// length; the warning against it lives in the account settings UI copy.
async function handleSetAccountContactInfo(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	if (!auth.admin.id) return json({ error: "The master key has no account to set contact info for" }, 400);

	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const contactDiscord = body.contactDiscord ? String(body.contactDiscord).trim().slice(0, 100) : null;
	const contactTimezone = body.contactTimezone ? String(body.contactTimezone).trim().slice(0, 100) : null;

	await env.DB.prepare("UPDATE admins SET contactDiscord = ?, contactTimezone = ? WHERE id = ?")
		.bind(contactDiscord, contactTimezone, auth.admin.id).run();
	return json({ ok: true, contactDiscord, contactTimezone });
}

// ---------------- self-service registration (see /verify at the repo root) ----------------
//
// Registration flow: POST /account/register/start (website) -> a code, the
// player joins <code>.verify.sctp.nl in Minecraft -> the standalone verify
// server (NOT this Worker — a separate always-on Python process, since a
// Cloudflare Worker can't hold a raw listening TCP socket open) completes a
// real Mojang-authenticated login (or, for Bedrock, a Geyser-authenticated
// one) and calls back here -> website polls GET /account/register/status
// until verified -> POST /account/register/complete with a chosen password
// actually creates the account, logged in immediately (mcVerified from the
// very first second, no head-admin step needed).
//
// The two endpoints the verify server itself calls are gated behind
// VERIFY_SERVER_SECRET (a Cloudflare secret, distinct from every other
// auth boundary in this file) rather than a real player's session token —
// this whole flow's security rests on that secret staying private, same as
// API_KEY/ADMIN_KEY already do for their own boundaries.

const REGISTRATION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I — easy to misread when copying into a MC server address
const REGISTRATION_TTL_MS = 10 * 60 * 1000; // 10 minutes

function newRegistrationCode() {
	let out = "";
	const bytes = crypto.getRandomValues(new Uint8Array(6));
	for (const b of bytes) out += REGISTRATION_CODE_CHARS[b % REGISTRATION_CODE_CHARS.length];
	return out;
}

function isAuthorizedVerifyServer(request, env) {
	return isAuthorized(request, env.VERIFY_SERVER_SECRET);
}

// A bare MC username, or one Bedrock "." prefix — see WatchedItem-style
// convention notes elsewhere in this file for why Bedrock accounts always
// carry that prefix (matches Floodgate's own default on the real server).
function isValidClaimedMcUsername(name) {
	const bare = String(name || "").replace(/^\./, "");
	return isValidUsername(bare);
}

async function handleStartRegistration(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const claimedMcUsername = String(body.mcUsername || "").trim();
	if (!isValidClaimedMcUsername(claimedMcUsername)) {
		return json({ error: "That doesn't look like a valid Minecraft username (Bedrock accounts: include the leading '.')" }, 400);
	}

	let code;
	for (let attempt = 0; attempt < 5; attempt++) {
		code = newRegistrationCode();
		const existing = await env.DB.prepare("SELECT code FROM pendingRegistrations WHERE code = ?").bind(code).first();
		if (!existing) break;
		code = null;
	}
	if (!code) return json({ error: "Couldn't generate a registration code, please try again" }, 502);

	const now = new Date();
	await env.DB.prepare(
		"INSERT INTO pendingRegistrations (code, claimedMcUsername, verified, createdAt, expiresAt) VALUES (?, ?, 0, ?, ?)"
	).bind(code, claimedMcUsername, now.toISOString(), new Date(now.getTime() + REGISTRATION_TTL_MS).toISOString()).run();

	return json({ code, joinAddress: `${code}.verify.sctp.nl`, expiresAt: new Date(now.getTime() + REGISTRATION_TTL_MS).toISOString() });
}

// Called by java_server.py once a real Mojang login succeeds, or by
// bedrock_bridge.py once it's matched an Xbox-authenticated connection to a
// pending code via handleFindPendingRegistration below.
async function handleRegistrationVerifyCallback(request, env) {
	if (!isAuthorizedVerifyServer(request, env)) return json({ error: "Unauthorized" }, 401);
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const code = String(body.code || "").toUpperCase();
	const mcUsername = String(body.mcUsername || "").trim();
	const mcUuid = body.mcUuid ? String(body.mcUuid) : null;
	if (!code || !mcUsername) return json({ error: "code and mcUsername are required" }, 400);

	const pending = await env.DB.prepare("SELECT * FROM pendingRegistrations WHERE code = ?").bind(code).first();
	if (!pending) return json({ error: "Unknown or expired code" }, 404);
	if (Date.parse(pending.expiresAt) < Date.now()) return json({ error: "Code expired" }, 410);

	await env.DB.prepare("UPDATE pendingRegistrations SET mcUsername = ?, mcUuid = ?, verified = 1 WHERE code = ?")
		.bind(mcUsername, mcUuid, code).run();
	return json({ ok: true });
}

// Bedrock-only — see bedrock_bridge.py's docstring for why there's no
// per-connection code to read the way the Java path has one. Matches by
// whatever the player typed on the website in step 1 (claimedMcUsername),
// since the real mcUsername column is still NULL at this point.
async function handleFindPendingRegistration(request, env) {
	if (!isAuthorizedVerifyServer(request, env)) return json({ error: "Unauthorized" }, 401);
	const url = new URL(request.url);
	const mcUsername = (url.searchParams.get("mcUsername") || "").trim();
	if (!mcUsername) return json({ error: "mcUsername is required" }, 400);

	const pending = await env.DB.prepare(
		"SELECT code FROM pendingRegistrations WHERE lower(claimedMcUsername) = ? AND verified = 0 AND expiresAt > ? ORDER BY createdAt DESC LIMIT 1"
	).bind(mcUsername.toLowerCase(), new Date().toISOString()).first();
	if (!pending) return json({ error: "No pending registration for that username" }, 404);
	return json({ code: pending.code });
}

// Public — the website polls this while the player goes and joins the verify server.
async function handleGetRegistrationStatus(request, env) {
	const url = new URL(request.url);
	const code = (url.searchParams.get("code") || "").trim().toUpperCase();
	if (!code) return json({ error: "code is required" }, 400);

	const pending = await env.DB.prepare("SELECT verified, mcUsername, expiresAt FROM pendingRegistrations WHERE code = ?").bind(code).first();
	if (!pending) return json({ error: "Unknown code" }, 404);
	if (Date.parse(pending.expiresAt) < Date.now()) return json({ error: "Code expired" }, 410);
	return json({ verified: !!pending.verified, mcUsername: pending.mcUsername || null });
}

// The actual account-creation step, once verified — the chosen password is
// the only new input; the login username IS the verified Minecraft
// username (no separate username to pick, matching the site's own
// 3-step "enter mc username, join server, pick password" flow exactly).
async function handleCompleteRegistration(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const code = String(body.code || "").trim().toUpperCase();
	const password = String(body.password || "");
	if (!code) return json({ error: "code is required" }, 400);
	if (password.length < 8) return json({ error: "password must be at least 8 characters" }, 400);

	const pending = await env.DB.prepare("SELECT * FROM pendingRegistrations WHERE code = ?").bind(code).first();
	if (!pending) return json({ error: "Unknown code" }, 404);
	if (Date.parse(pending.expiresAt) < Date.now()) return json({ error: "Code expired, please start again" }, 410);
	if (!pending.verified || !pending.mcUsername) return json({ error: "Not verified yet — join the server first" }, 400);

	const existing = await env.DB.prepare("SELECT id FROM admins WHERE lower(username) = lower(?)").bind(pending.mcUsername).first();
	if (existing) return json({ error: "An account for this Minecraft username already exists — log in instead, or ask a head admin for help." }, 409);

	const id = crypto.randomUUID();
	const salt = newSaltHex();
	const hash = await hashPassword(password, salt);
	const now = new Date().toISOString();
	await env.DB.prepare(
		"INSERT INTO admins (id, username, passwordHash, passwordSalt, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified) VALUES (?, ?, ?, ?, 0, '[]', ?, 'self-registration', ?, 1)"
	).bind(id, pending.mcUsername, hash, salt, now, pending.mcUsername).run();
	await env.DB.prepare("DELETE FROM pendingRegistrations WHERE code = ?").bind(code).run();

	// Log them in immediately — "done" should mean done, not "now go log in separately".
	const token = newToken();
	const expiresAt = new Date(Date.now() + ADMIN_SESSION_TTL_MS).toISOString();
	await env.DB.prepare("INSERT INTO adminSessions (token, adminId, createdAt, expiresAt) VALUES (?, ?, ?, ?)")
		.bind(token, id, now, expiresAt).run();

	return json({ token, username: pending.mcUsername, isHeadAdmin: false, permissions: [], expiresAt, mcUsername: pending.mcUsername, mcVerified: true });
}

// The real join-a-verify-server flow (handleStartRegistration/
// handleRegistrationVerifyCallback/handleCompleteRegistration above) is
// temporarily bypassed on the website — register/index.html calls this
// instead, taking the typed Minecraft username on trust (mcVerified: 0)
// rather than confirming it via a real login. The old verified flow's
// routes/tables are untouched so re-enabling it later is just a frontend
// swap back, not a backend rebuild.
async function handleDirectRegistration(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const mcUsername = String(body.mcUsername || "").trim();
	const password = String(body.password || "");
	if (!isValidClaimedMcUsername(mcUsername)) {
		return json({ error: "That doesn't look like a valid Minecraft username (Bedrock accounts: include the leading '.')" }, 400);
	}
	if (password.length < 8) return json({ error: "password must be at least 8 characters" }, 400);

	const existing = await env.DB.prepare("SELECT id FROM admins WHERE lower(username) = lower(?)").bind(mcUsername).first();
	if (existing) return json({ error: "An account for this Minecraft username already exists — log in instead, or ask a head admin for help." }, 409);

	const id = crypto.randomUUID();
	const salt = newSaltHex();
	const hash = await hashPassword(password, salt);
	const now = new Date().toISOString();
	await env.DB.prepare(
		"INSERT INTO admins (id, username, passwordHash, passwordSalt, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified) VALUES (?, ?, ?, ?, 0, '[]', ?, 'self-registration-unverified', ?, 0)"
	).bind(id, mcUsername, hash, salt, now, mcUsername).run();

	// Log them in immediately — "done" should mean done, not "now go log in separately".
	const token = newToken();
	const expiresAt = new Date(Date.now() + ADMIN_SESSION_TTL_MS).toISOString();
	await env.DB.prepare("INSERT INTO adminSessions (token, adminId, createdAt, expiresAt) VALUES (?, ?, ?, ?)")
		.bind(token, id, now, expiresAt).run();

	return json({ token, username: mcUsername, isHeadAdmin: false, permissions: [], expiresAt, mcUsername: mcUsername, mcVerified: false });
}

// ---------------- blocked sellers ----------------

async function handleAdminListBlockedSellers(request, env) {
	const auth = await requireAdminAuth(request, env, "blockedSellers");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM blockedSellers ORDER BY blockedAt DESC").all();
	return json(results);
}

// Shared by handleAdminBlockSeller and handleAdminResolvePlayerReport's "ban"
// action so both write through the exact same block-and-wipe logic.
async function blockSellerAndWipe(env, username, reason, blockedBy) {
	const usernameKey = username.toLowerCase();
	await env.DB.prepare(
		`INSERT INTO blockedSellers (usernameKey, username, reason, blockedAt, blockedBy) VALUES (?, ?, ?, ?, ?)
		 ON CONFLICT(usernameKey) DO UPDATE SET username=excluded.username, reason=excluded.reason, blockedAt=excluded.blockedAt, blockedBy=excluded.blockedBy`
	).bind(usernameKey, username, reason, new Date().toISOString(), blockedBy).run();

	// A block takes effect immediately, not just for future uploads.
	await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = ?").bind(usernameKey).run();
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

	try {
		await blockSellerAndWipe(env, username, reason, auth.admin.username);
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
	if (!isVersionAtLeast(modVersion, MIN_UPLOAD_VERSION)) {
		return json({ error: `Shop Logger ${modVersion || "(unknown version)"} is no longer supported — please update to ${MIN_UPLOAD_VERSION} or later.` }, 426);
	}

	// modVersion is now guaranteed >= MIN_UPLOAD_VERSION (1.5.1), which is
	// itself above MIN_TRUSTED_PRUNE_VERSION (1.2.4) — so every upload that
	// reaches this point is always trusted for scannedPositions pruning.
	const scannedPositionsIn = Array.isArray(body.scannedPositions) ? body.scannedPositions : [];
	const validScannedPositions = scannedPositionsIn.filter((sp) => sp && sp.world && sp.position);
	const scannedSet = new Set(validScannedPositions.map((sp) => positionKey(sp.world, sp.position)));

	let added = 0, updated = 0, skipped = 0, removed = 0;
	try {
		const blockedSet = await getBlockedSellerSet(env, incoming.map((r) => r && r.seller));

		const validRows = [];
		for (const r of incoming) {
			if (!r.itemName || !r.seller || !r.world) { skipped++; continue; }
			if (isBannedItem(r.baseItem, r.itemName)) { skipped++; continue; }
			if (isPaymentItem(r.baseItem, r.currency)) { skipped++; continue; }
			// Maps are catalogued in the mapart gallery instead (see handleGetListings).
			if (String(r.baseItem || "").toLowerCase() === "minecraft:filled_map") { skipped++; continue; }
			if (blockedSet.has(String(r.seller).toLowerCase())) { skipped++; continue; }
			validRows.push({ ...r, _key: rowKey(r) });
		}

		const stmts = [];

		if (validRows.length > 0) {
			// Look up existing lastSeen for these keys so an older/duplicate
			// report of a listing never clobbers a fresher one already stored.
			// Chunked (see MAX_QUERY_PARAMS_PER_CHUNK) — a big scan-session
			// upload can easily have more distinct keys than one statement's
			// bound-parameter limit allows.
			const keys = [...new Set(validRows.map((r) => r._key))];
			const keyChunks = await Promise.all(chunkArray(keys, MAX_QUERY_PARAMS_PER_CHUNK).map((chunk) => {
				const placeholders = chunk.map(() => "?").join(",");
				return env.DB.prepare(`SELECT rowKey, lastSeen FROM listings WHERE rowKey IN (${placeholders})`).bind(...chunk).all();
			}));
			const existingMap = new Map();
			for (const existingRes of keyChunks) for (const r of existingRes.results) existingMap.set(r.rowKey, r.lastSeen);

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
		// upload, is *probably* gone (sold out / chest emptied / shop removed)
		// — but a single scan occasionally missing one item out of a large
		// container (for whatever reason — timing, lag, an edge case we haven't
		// tracked down) shouldn't be enough on its own to delete a listing
		// that's still actually there. Same principle ShopAutoScanner's
		// whole-shop removal already uses client-side (see
		// MIN_TRUSTED_PRUNE_VERSION's comment): require it missing across 2
		// separate scans of that position before actually deleting; a single
		// miss just increments missingStreak, reset back to 0 the moment the
		// item is seen again (see buildListingUpsertStmt).
		// Deliberately NOT nested inside the validRows check above — a chest
		// scanned down to fully empty sends scannedPositions with an empty
		// `rows`, and that's exactly the case pruning exists for.
		const MISSING_STREAK_THRESHOLD = 2;
		if (validScannedPositions.length > 0) {
			const freshKeysAtScannedPos = new Set(
				validRows.filter((r) => scannedSet.has(positionKey(r.world, r.position))).map((r) => r._key)
			);
			// Chunked two positions' worth of params per slot (world+position),
			// same reasoning as the keys lookup above — and, same as those,
			// fired concurrently rather than one chunk at a time.
			const scannedChunks = await Promise.all(chunkArray(validScannedPositions, MAX_QUERY_PARAMS_PER_CHUNK).map((chunk) => {
				const orClauses = chunk.map(() => "(world = ? AND position = ?)").join(" OR ");
				const bindArgs = [];
				for (const sp of chunk) bindArgs.push(sp.world, sp.position);
				return env.DB.prepare(`SELECT rowKey, missingStreak FROM listings WHERE ${orClauses}`).bind(...bindArgs).all();
			}));
			for (const atScanned of scannedChunks) {
				for (const row of atScanned.results) {
					if (freshKeysAtScannedPos.has(row.rowKey)) continue;
					const streak = (row.missingStreak || 0) + 1;
					if (streak >= MISSING_STREAK_THRESHOLD) {
						stmts.push(env.DB.prepare("DELETE FROM listings WHERE rowKey = ?").bind(row.rowKey));
						removed++;
					} else {
						stmts.push(env.DB.prepare("UPDATE listings SET missingStreak = ? WHERE rowKey = ?").bind(streak, row.rowKey));
					}
				}
			}
		}

		if (stmts.length > 0) await env.DB.batch(stmts);

		if (added === 0 && updated === 0 && removed === 0) {
			return json({ added: 0, updated: 0, skipped, removed: 0, committed: false });
		}
		const totalRow = await env.DB.prepare("SELECT COUNT(*) as c FROM listings").first();
		return json({ added, updated, skipped, removed, total: totalRow.c, committed: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

// GET /seller/primary-world?name=<username> (public, cached) -> {world, counts}
// world is whichever world the player has the most shop listings in (Firefly on
// a tie or when they have none) — the site's generic "go to this player's
// profile" links redirect through /s/<name> (see 404.html) and use this to
// pick the world. Bedrock's leading '.' is ignored, same as everywhere else.
async function handleGetSellerPrimaryWorld(request, env, ctx) {
	const name = (new URL(request.url).searchParams.get("name") || "").trim();
	if (!name) return json({ error: "name is required" }, 400);
	return cachedGet(request, ctx, 300, async () => {
		const { results } = await env.DB.prepare(
			"SELECT world, COUNT(*) AS c FROM listings WHERE lower(ltrim(seller, '.')) = lower(ltrim(?, '.')) GROUP BY world"
		).bind(name).all();
		const counts = {};
		for (const r of results) counts[r.world] = r.c;
		const hb = counts.Honeybee || 0, ff = counts.Firefly || 0;
		return { world: hb > ff ? "Honeybee" : "Firefly", counts };
	});
}

async function handleGetListings(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		// Defense-in-depth: handleUploadListings already refuses new rows from
		// a blocked seller, but this excludes anything already stored from
		// before a block was added too, so a block takes effect immediately.
		const { results } = await env.DB.prepare(
			"SELECT * FROM listings WHERE lower(seller) NOT IN (SELECT usernameKey FROM blockedSellers)"
		).all();
		const shopRows = results.map((r) => ({ ...r, bulk: !!r.bulk, bundled: !!r.bundled, mixedContents: !!r.mixedContents }));
		// Active marketplace posts (selling AND lookingFor) are merged straight
		// into the same array everything already reads — the site's listings
		// table/search, item pages, and the mod's /search + watchlist check all
		// just consume GET /listings already, so this is the only change needed
		// for marketplace posts to show up everywhere shop listings do. Each row
		// is tagged marketplace/marketplaceType/marketplaceListingId so a
		// consumer that specifically shouldn't treat these as real purchasable
		// shop stock (e.g. the /list build-planner) can filter them back out.
		const marketplaceRows = await getActiveMarketplaceRows(env);
		// ?mapart=1 (the website's listings table) also adds one row per
		// catalogued mapart. The mod and every other consumer call plain
		// /listings and never see them, so nothing mistakes a gallery piece
		// for real shop stock.
		const wantGallery = new URL(request.url).searchParams.get("mapart") === "1";
		const galleryRows = wantGallery ? await getMapartGalleryRows(env) : [];
		return shopRows.concat(marketplaceRows, galleryRows);
	});
}

// ---------------- marketplace ----------------

const MARKETPLACE_LISTING_LIFETIME_MS = 14 * 24 * 60 * 60 * 1000; // 14 days

function newId() {
	return crypto.randomUUID();
}

async function notifyAccount(env, accountId, type, message, listingId) {
	await env.DB.prepare(
		"INSERT INTO marketplaceNotifications (id, accountId, type, message, listingId, createdAt) VALUES (?, ?, ?, ?, ?, ?)"
	).bind(newId(), accountId, type, message, listingId || null, new Date().toISOString()).run();
}

// Everything an admins row knows about how to reach that person in-game —
// used only once a trade is actually confirmed (a bid accepted), never
// exposed anywhere else. mcUsername is always shown when set, verified or
// not, since it's still the most useful single piece of info for finding
// someone in-game; contactDiscord/contactTimezone are self-reported free
// text (see /account/contact-info) and only shown if the account set them.
function contactInfoText(admin) {
	const parts = [`account username: ${admin.username}`];
	if (admin.mcUsername) parts.push(`Minecraft: ${admin.mcUsername}`);
	if (admin.contactDiscord) parts.push(`Discord: ${admin.contactDiscord}`);
	if (admin.contactTimezone) parts.push(`Timezone: ${admin.contactTimezone}`);
	return parts.join("\n");
}

// "diamondstack" is marketplace-only currency (real shop signs never use
// it) — shown as "STX" everywhere else (the marketplace page's own
// CURRENCY_LABELS, in-game WatchlistJoinCheck, etc.), so this baked-in
// priceLabel (rendered as-is by index.html/list/404.html's listings tables)
// needs the same abbreviation instead of the raw currency string.
function marketplacePriceLabel(amount, currency, suffix) {
	if (amount == null) return null;
	const currencyText = currency === "diamondstack" ? "STX" : currency || "?";
	return `${amount} ${currencyText} (${suffix})`;
}

// Maps one marketplaceListings row into the same shape GET /listings
// returns — see handleGetListings for why.
function marketplaceRowAsListing(m, posterName) {
	const base = {
		// Shared with real shop rows (see 0016_listing_ids.sql) so the share
		// button/link can treat every row the same regardless of origin.
		id: m.id,
		itemName: m.itemName, baseItem: m.baseItem || "", bulk: false, bundled: false, mixedContents: false,
		stackSize: m.quantity, amount: m.quantity, stacksInStock: 1,
		seller: posterName, world: m.world, position: "Marketplace listing",
		lastSeen: m.createdAt, availableSince: m.createdAt,
		marketplace: true, marketplaceType: m.type, marketplaceListingId: m.id,
	};
	if (m.type === "selling") {
		const price = m.askingPrice != null ? m.askingPrice : m.startingBid;
		const currency = m.askingPrice != null ? m.askingCurrency : m.startingBidCurrency;
		return { ...base, price, currency, priceLabel: marketplacePriceLabel(price, currency, m.askingPrice != null ? "asking" : "starting bid") };
	}
	return { ...base, price: m.budget, currency: m.budgetCurrency, priceLabel: marketplacePriceLabel(m.budget, m.budgetCurrency, "budget") };
}

async function getActiveMarketplaceRows(env) {
	const { results } = await env.DB.prepare(
		`SELECT ml.*, a.username AS accountUsername, a.mcUsername AS accountMcUsername
		 FROM marketplaceListings ml JOIN admins a ON a.id = ml.accountId
		 WHERE ml.status = 'active' AND ml.expiresAt > ?`
	).bind(new Date().toISOString()).all();
	return results.map((m) => marketplaceRowAsListing(m, m.accountMcUsername || m.accountUsername));
}

// GET /marketplace/listings — public. Same active-listing set as the rows
// merged into GET /listings, but in the marketplace's own richer shape
// (bid count/highest bid) — used by the /marketplace page and by the mod's
// local watchlist check (see WatchlistJoinCheck), which needs nothing more
// than this to work — no server-side "what is this player watching" ever exists.
async function handleGetMarketplaceListings(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare(
			`SELECT ml.*, a.username AS accountUsername, a.mcUsername AS accountMcUsername
			 FROM marketplaceListings ml JOIN admins a ON a.id = ml.accountId
			 WHERE ml.status = 'active' AND ml.expiresAt > ?`
		).bind(new Date().toISOString()).all();

		const listingIds = results.map((r) => r.id);
		const bidsByListing = new Map();
		if (listingIds.length > 0) {
			for (const chunk of chunkArray(listingIds, MAX_QUERY_PARAMS_PER_CHUNK)) {
				const placeholders = chunk.map(() => "?").join(",");
				// Every bid regardless of status — "previous bids" in the listing
				// popup shows the full history (amount/currency/message/status),
				// never who placed it (see bidHistory below); bidCount/highestBid
				// still only consider currently-open (pending) offers.
				const { results: bids } = await env.DB.prepare(
					`SELECT * FROM marketplaceBids WHERE listingId IN (${placeholders}) ORDER BY createdAt DESC`
				).bind(...chunk).all();
				for (const b of bids) {
					if (!bidsByListing.has(b.listingId)) bidsByListing.set(b.listingId, []);
					bidsByListing.get(b.listingId).push(b);
				}
			}
		}

		return results.map((m) => {
			const allBids = bidsByListing.get(m.id) || [];
			const pendingBids = allBids.filter((b) => b.status === "pending");
			let highestBid = null;
			for (const b of pendingBids) {
				if (!highestBid || marketplaceValueInDiamonds(b.amount, b.currency) > marketplaceValueInDiamonds(highestBid.amount, highestBid.currency)) highestBid = b;
			}
			return {
				id: m.id, type: m.type, itemName: m.itemName, baseItem: m.baseItem, world: m.world,
				quantity: m.quantity, notes: m.notes,
				askingPrice: m.askingPrice, askingCurrency: m.askingCurrency,
				startingBid: m.startingBid, startingBidCurrency: m.startingBidCurrency,
				budget: m.budget, budgetCurrency: m.budgetCurrency,
				createdAt: m.createdAt, expiresAt: m.expiresAt,
				seller: m.accountMcUsername || m.accountUsername,
				bidCount: pendingBids.length,
				highestBid: highestBid ? { amount: highestBid.amount, currency: highestBid.currency } : null,
				// Anonymized bid history for the public popup — amount/currency/
				// message/status/time only, never bidderAccountId, so a public
				// visitor can see how bidding has gone without learning who's bidding.
				bidHistory: allBids.map((b) => ({ amount: b.amount, currency: b.currency, message: b.message, status: b.status, createdAt: b.createdAt })),
			};
		});
	});
}

async function handleCreateMarketplaceListing(request, env) {
	const auth = await requireAnyAdmin(request, env); // any logged-in account, admin or not
	if (!auth.ok) return auth.response;

	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }

	const type = body.type === "lookingFor" ? "lookingFor" : body.type === "selling" ? "selling" : null;
	if (!type) return json({ error: "type must be 'selling' or 'lookingFor'" }, 400);
	const itemName = String(body.itemName || "").trim();
	if (!itemName) return json({ error: "itemName is required" }, 400);
	const world = MARKETPLACE_WORLDS.has(body.world) ? body.world : null;
	if (!world) return json({ error: "world must be 'Firefly', 'Honeybee', or 'Cross-world'" }, 400);
	const quantity = Math.max(1, parseInt(body.quantity, 10) || 1);
	const baseItem = body.baseItem ? String(body.baseItem).trim() : null;
	const notes = body.notes ? String(body.notes).trim().slice(0, 500) : null;

	const id = newId();
	const now = new Date();
	const createdAt = now.toISOString();
	const expiresAt = new Date(now.getTime() + MARKETPLACE_LISTING_LIFETIME_MS).toISOString();

	if (type === "selling") {
		const askingPrice = body.askingPrice != null && body.askingPrice !== "" ? Number(body.askingPrice) : null;
		const askingCurrency = askingPrice != null ? String(body.askingCurrency || "").trim() : null;
		const startingBid = body.startingBid != null && body.startingBid !== "" ? Number(body.startingBid) : null;
		const startingBidCurrency = startingBid != null ? String(body.startingBidCurrency || "").trim() : null;
		if (askingPrice == null && startingBid == null) return json({ error: "Provide an asking price, a starting bid, or both" }, 400);
		if (askingPrice != null && !MARKETPLACE_CURRENCIES.has(askingCurrency)) return json({ error: "askingCurrency must be diamond, diamondblock, or diamondstack" }, 400);
		if (startingBid != null && !MARKETPLACE_CURRENCIES.has(startingBidCurrency)) return json({ error: "startingBidCurrency must be diamond, diamondblock, or diamondstack" }, 400);
		await env.DB.prepare(
			`INSERT INTO marketplaceListings (id, accountId, type, itemName, baseItem, world, quantity, notes, askingPrice, askingCurrency, startingBid, startingBidCurrency, status, createdAt, expiresAt)
			 VALUES (?, ?, 'selling', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)`
		).bind(id, auth.admin.id, itemName, baseItem, world, quantity, notes, askingPrice, askingCurrency, startingBid, startingBidCurrency, createdAt, expiresAt).run();
	} else {
		const budget = body.budget != null && body.budget !== "" ? Number(body.budget) : null;
		const budgetCurrency = budget != null ? String(body.budgetCurrency || "").trim() : null;
		if (budget != null && !MARKETPLACE_CURRENCIES.has(budgetCurrency)) return json({ error: "budgetCurrency must be diamond, diamondblock, or diamondstack" }, 400);
		await env.DB.prepare(
			`INSERT INTO marketplaceListings (id, accountId, type, itemName, baseItem, world, quantity, notes, budget, budgetCurrency, status, createdAt, expiresAt)
			 VALUES (?, ?, 'lookingFor', ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)`
		).bind(id, auth.admin.id, itemName, baseItem, world, quantity, notes, budget, budgetCurrency, createdAt, expiresAt).run();
	}

	return json({ ok: true, id });
}

// ---------------- marketplace: jobs/tasks ----------------
// Deliberately simpler than item listings — see 0015_marketplace_jobs.sql's
// header comment. No bidding: "I'm interested" is a single action that
// reveals contact info to both sides right away, and the job stays active
// (so more than one person can express interest) until the poster closes it.

const MARKETPLACE_JOB_TYPES = new Set(["hiring", "forHire"]);

async function handleCreateMarketplaceJob(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }

	const type = MARKETPLACE_JOB_TYPES.has(body.type) ? body.type : null;
	if (!type) return json({ error: "type must be 'hiring' or 'forHire'" }, 400);
	const title = String(body.title || "").trim().slice(0, 100);
	if (!title) return json({ error: "title is required" }, 400);
	const description = body.description ? String(body.description).trim().slice(0, 1000) : null;
	const world = MARKETPLACE_WORLDS.has(body.world) ? body.world : null;
	if (!world) return json({ error: "world must be 'Firefly', 'Honeybee', or 'Cross-world'" }, 400);
	const rewardAmount = body.rewardAmount != null && body.rewardAmount !== "" ? Number(body.rewardAmount) : null;
	const rewardCurrency = rewardAmount != null ? String(body.rewardCurrency || "").trim() : null;
	if (rewardAmount != null && (!(rewardAmount > 0) || !MARKETPLACE_CURRENCIES.has(rewardCurrency))) {
		return json({ error: "rewardCurrency must be diamond, diamondblock, or diamondstack, with a positive rewardAmount" }, 400);
	}
	let deadline = null;
	if (body.deadline) {
		const d = new Date(body.deadline);
		if (!isNaN(d.getTime())) deadline = d.toISOString();
	}

	const id = newId();
	const now = new Date();
	const createdAt = now.toISOString();
	const expiresAt = new Date(now.getTime() + MARKETPLACE_LISTING_LIFETIME_MS).toISOString();

	await env.DB.prepare(
		`INSERT INTO marketplaceJobs (id, accountId, type, title, description, rewardAmount, rewardCurrency, world, deadline, status, createdAt, expiresAt)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)`
	).bind(id, auth.admin.id, type, title, description, rewardAmount, rewardCurrency, world, deadline, createdAt, expiresAt).run();

	return json({ ok: true, id });
}

// GET /marketplace/jobs — public, cached. Same shape philosophy as GET
// /marketplace/listings: everything needed to render the list without a
// second round trip, but never bidder/interest identity (see
// handleGetMyMarketplace for the private "who's interested" view).
async function handleGetMarketplaceJobs(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare(
			`SELECT mj.*, a.username AS accountUsername, a.mcUsername AS accountMcUsername
			 FROM marketplaceJobs mj JOIN admins a ON a.id = mj.accountId
			 WHERE mj.status = 'active' AND mj.expiresAt > ?`
		).bind(new Date().toISOString()).all();

		const jobIds = results.map((r) => r.id);
		const interestCountByJob = new Map();
		const reviewsByJob = new Map();
		if (jobIds.length > 0) {
			for (const chunk of chunkArray(jobIds, MAX_QUERY_PARAMS_PER_CHUNK)) {
				const placeholders = chunk.map(() => "?").join(",");
				const { results: counts } = await env.DB.prepare(
					`SELECT jobId, COUNT(*) as c FROM marketplaceJobInterests WHERE jobId IN (${placeholders}) GROUP BY jobId`
				).bind(...chunk).all();
				for (const row of counts) interestCountByJob.set(row.jobId, row.c);
				const { results: reviews } = await env.DB.prepare(
					`SELECT jobId, SUM(CASE WHEN vote = 1 THEN 1 ELSE 0 END) AS up, SUM(CASE WHEN vote = -1 THEN 1 ELSE 0 END) AS down
					 FROM marketplaceJobReviews WHERE jobId IN (${placeholders}) GROUP BY jobId`
				).bind(...chunk).all();
				for (const row of reviews) reviewsByJob.set(row.jobId, { up: row.up, down: row.down });
			}
		}

		return results.map((j) => ({
			id: j.id, type: j.type, title: j.title, description: j.description,
			rewardAmount: j.rewardAmount, rewardCurrency: j.rewardCurrency,
			world: j.world, deadline: j.deadline,
			createdAt: j.createdAt, expiresAt: j.expiresAt,
			poster: j.accountMcUsername || j.accountUsername,
			interestCount: interestCountByJob.get(j.id) || 0,
			thumbsUp: (reviewsByJob.get(j.id) || { up: 0 }).up,
			thumbsDown: (reviewsByJob.get(j.id) || { down: 0 }).down,
		}));
	});
}

// A single action, not a bid — records interest, then immediately hands the
// poster's contact info back in the response (for the responder) and
// notifies the poster with the responder's contact info (so neither side
// has to check back). The job stays active either way.
async function handleExpressJobInterest(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const jobId = String(body.jobId || "");
	const message = body.message ? String(body.message).trim().slice(0, 300) : null;
	if (!jobId) return json({ error: "jobId is required" }, 400);

	const job = await env.DB.prepare("SELECT * FROM marketplaceJobs WHERE id = ?").bind(jobId).first();
	if (!job || job.status !== "active") return json({ error: "That job isn't open" }, 400);
	if (job.accountId === auth.admin.id) return json({ error: "You can't express interest in your own job" }, 400);

	const existing = await env.DB.prepare(
		"SELECT id FROM marketplaceJobInterests WHERE jobId = ? AND interestedAccountId = ?"
	).bind(jobId, auth.admin.id).first();
	if (existing) return json({ error: "You've already expressed interest in this job" }, 400);

	await env.DB.prepare(
		"INSERT INTO marketplaceJobInterests (id, jobId, interestedAccountId, message, createdAt) VALUES (?, ?, ?, ?, ?)"
	).bind(newId(), jobId, auth.admin.id, message, new Date().toISOString()).run();

	const poster = await env.DB.prepare("SELECT * FROM admins WHERE id = ?").bind(job.accountId).first();
	const verb = job.type === "hiring" ? "wants the job" : "wants to hire you";
	await notifyAccount(env, job.accountId, "jobInterest",
		`${auth.admin.username} ${verb} for your "${job.title}" post! Contact them via:\n${contactInfoText(auth.admin)}`, jobId);

	return json({ ok: true, contactInfo: poster ? contactInfoText(poster) : null });
}

// POST /marketplace/jobs/review — thumbs up/down on a job post. One vote per
// (job, reviewer); posting again just changes it, vote: 0 removes it.
// Anyone with an account can review any job except their own.
async function handleReviewMarketplaceJob(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const jobId = String(body.jobId || "");
	const vote = Number(body.vote);
	if (!jobId) return json({ error: "jobId is required" }, 400);
	if (![1, -1, 0].includes(vote)) return json({ error: "vote must be 1, -1, or 0" }, 400);

	const job = await env.DB.prepare("SELECT id, accountId FROM marketplaceJobs WHERE id = ?").bind(jobId).first();
	if (!job) return json({ error: "Job not found" }, 404);
	if (job.accountId === auth.admin.id) return json({ error: "You can't review your own job post" }, 400);

	if (vote === 0) {
		await env.DB.prepare("DELETE FROM marketplaceJobReviews WHERE jobId = ? AND reviewerAccountId = ?").bind(jobId, auth.admin.id).run();
	} else {
		await env.DB.prepare(
			`INSERT INTO marketplaceJobReviews (id, jobId, reviewerAccountId, vote, createdAt) VALUES (?, ?, ?, ?, ?)
			 ON CONFLICT(jobId, reviewerAccountId) DO UPDATE SET vote = excluded.vote, createdAt = excluded.createdAt`
		).bind(newId(), jobId, auth.admin.id, vote, new Date().toISOString()).run();
	}
	return json({ ok: true });
}

async function handleCloseMarketplaceJob(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "");
	const status = body.status === "fulfilled" || body.status === "cancelled" ? body.status : null;
	if (!id || !status) return json({ error: "id and a valid status ('fulfilled' or 'cancelled') are required" }, 400);

	const job = await env.DB.prepare("SELECT * FROM marketplaceJobs WHERE id = ?").bind(id).first();
	if (!job) return json({ error: "Job not found" }, 404);
	if (job.accountId !== auth.admin.id) return json({ error: "Not your job listing" }, 403);
	if (job.status !== "active") return json({ error: "Job isn't active" }, 400);

	await env.DB.prepare("UPDATE marketplaceJobs SET status = ?, closedAt = ?, closedReason = ? WHERE id = ?")
		.bind(status, new Date().toISOString(), status === "fulfilled" ? "marked fulfilled by poster" : "cancelled by poster", id).run();
	return json({ ok: true });
}

async function handleCancelMarketplaceListing(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);

	const listing = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE id = ?").bind(id).first();
	if (!listing) return json({ error: "Listing not found" }, 404);
	if (listing.accountId !== auth.admin.id) return json({ error: "Not your listing" }, 403);
	if (listing.status !== "active") return json({ error: "Listing isn't active" }, 400);

	await env.DB.prepare("UPDATE marketplaceListings SET status = 'cancelled', closedAt = ?, closedReason = 'cancelled by owner' WHERE id = ?")
		.bind(new Date().toISOString(), id).run();
	return json({ ok: true });
}

async function handlePlaceBid(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const listingId = String(body.listingId || "");
	const amount = Number(body.amount);
	const currency = String(body.currency || "").trim();
	const message = body.message ? String(body.message).trim().slice(0, 300) : null;
	if (!listingId || !amount || amount <= 0 || !currency) return json({ error: "listingId, a positive amount, and currency are required" }, 400);
	if (!MARKETPLACE_CURRENCIES.has(currency)) return json({ error: "currency must be diamond, diamondblock, or diamondstack" }, 400);

	const listing = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE id = ?").bind(listingId).first();
	if (!listing || listing.status !== "active") return json({ error: "That listing isn't open for offers" }, 400);
	if (listing.accountId === auth.admin.id) return json({ error: "You can't respond to your own listing" }, 400);

	const id = newId();
	const createdAt = new Date().toISOString();
	await env.DB.prepare(
		"INSERT INTO marketplaceBids (id, listingId, bidderAccountId, amount, currency, message, createdAt, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')"
	).bind(id, listingId, auth.admin.id, amount, currency, message, createdAt).run();

	// Same "bid" row/mechanism works in both directions: on a "selling" post
	// it's a buyer's bid; on a "lookingFor" post it's someone offering to
	// sell at that price — only the notification wording differs.
	const notifyMsg = listing.type === "lookingFor"
		? `${auth.admin.username} offered to sell you ${listing.itemName} for ${amount} ${currency}.`
		: `${auth.admin.username} bid ${amount} ${currency} on your ${listing.itemName} listing.`;
	await notifyAccount(env, listing.accountId, "newBid", notifyMsg, listingId);
	return json({ ok: true, id });
}

async function handleWithdrawBid(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const bidId = String(body.bidId || "");
	if (!bidId) return json({ error: "bidId is required" }, 400);

	const bid = await env.DB.prepare("SELECT * FROM marketplaceBids WHERE id = ?").bind(bidId).first();
	if (!bid) return json({ error: "Bid not found" }, 404);
	if (bid.bidderAccountId !== auth.admin.id) return json({ error: "Not your bid" }, 403);
	if (bid.status !== "pending") return json({ error: "Bid isn't pending" }, 400);

	await env.DB.prepare("UPDATE marketplaceBids SET status = 'withdrawn' WHERE id = ?").bind(bidId).run();
	return json({ ok: true });
}

async function handleAcceptBid(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const bidId = String(body.bidId || "");
	if (!bidId) return json({ error: "bidId is required" }, 400);

	const bid = await env.DB.prepare("SELECT * FROM marketplaceBids WHERE id = ?").bind(bidId).first();
	if (!bid || bid.status !== "pending") return json({ error: "Bid not found or no longer pending" }, 404);
	const listing = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE id = ?").bind(bid.listingId).first();
	if (!listing || listing.accountId !== auth.admin.id) return json({ error: "Not your listing" }, 403);
	if (listing.status !== "active") return json({ error: "Listing isn't active" }, 400);

	const now = new Date().toISOString();
	const stmts = [
		env.DB.prepare("UPDATE marketplaceBids SET status = 'accepted' WHERE id = ?").bind(bidId),
		env.DB.prepare("UPDATE marketplaceListings SET status = 'fulfilled', closedAt = ?, closedReason = 'bid accepted' WHERE id = ?").bind(now, listing.id),
	];
	// Every other still-pending bid on this listing loses automatically — the item's spoken for now.
	const { results: otherBids } = await env.DB.prepare(
		"SELECT * FROM marketplaceBids WHERE listingId = ? AND id != ? AND status = 'pending'"
	).bind(listing.id, bidId).all();
	for (const ob of otherBids) stmts.push(env.DB.prepare("UPDATE marketplaceBids SET status = 'rejected' WHERE id = ?").bind(ob.id));
	await env.DB.batch(stmts);

	const bidder = await env.DB.prepare("SELECT * FROM admins WHERE id = ?").bind(bid.bidderAccountId).first();
	// On a "selling" post the poster is the seller and the responder is the
	// buyer; on a "lookingFor" post those roles are swapped (the poster is
	// the one looking to buy, the responder offered to sell) — same accept
	// flow either way, just the "who's who" in the notification text.
	const posterIsBuyer = listing.type === "lookingFor";
	const posterRole = posterIsBuyer ? "buyer" : "seller";
	const responderRole = posterIsBuyer ? "seller" : "buyer";
	const offerWord = posterIsBuyer ? "offer" : "bid";
	const offerArticle = posterIsBuyer ? "an" : "a";
	await notifyAccount(env, bid.bidderAccountId, "bidAccepted",
		`Your ${offerWord} of ${bid.amount} ${bid.currency} on ${listing.itemName} was accepted! Contact the ${posterRole} via:\n${contactInfoText(auth.admin)}`, listing.id);
	if (bidder) {
		await notifyAccount(env, auth.admin.id, "bidAcceptedConfirmation",
			`You accepted ${offerArticle} ${offerWord} on ${listing.itemName}! Contact the ${responderRole} via:\n${contactInfoText(bidder)}`, listing.id);
	}
	for (const ob of otherBids) {
		await notifyAccount(env, ob.bidderAccountId, "bidRejected", `Your ${offerWord} on ${listing.itemName} wasn't selected — the ${posterRole} accepted another offer.`, listing.id);
	}
	return json({ ok: true });
}

async function handleRejectBid(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const bidId = String(body.bidId || "");
	if (!bidId) return json({ error: "bidId is required" }, 400);

	const bid = await env.DB.prepare("SELECT * FROM marketplaceBids WHERE id = ?").bind(bidId).first();
	if (!bid || bid.status !== "pending") return json({ error: "Bid not found or no longer pending" }, 404);
	const listing = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE id = ?").bind(bid.listingId).first();
	if (!listing || listing.accountId !== auth.admin.id) return json({ error: "Not your listing" }, 403);

	const offerWord = listing.type === "lookingFor" ? "offer" : "bid";
	await env.DB.prepare("UPDATE marketplaceBids SET status = 'rejected' WHERE id = ?").bind(bidId).run();
	await notifyAccount(env, bid.bidderAccountId, "bidRejected", `Your ${offerWord} of ${bid.amount} ${bid.currency} on ${listing.itemName} was declined.`, listing.id);
	return json({ ok: true });
}

async function handleGetMyMarketplace(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;

	const { results: listings } = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE accountId = ? ORDER BY createdAt DESC").bind(auth.admin.id).all();
	const { results: myBids } = await env.DB.prepare(
		`SELECT b.*, l.itemName, l.world FROM marketplaceBids b JOIN marketplaceListings l ON l.id = b.listingId WHERE b.bidderAccountId = ? ORDER BY b.createdAt DESC`
	).bind(auth.admin.id).all();

	// Bids received on the caller's own listings, so the UI can show "3 offers" per listing without a separate round trip per listing.
	const listingIds = listings.map((l) => l.id);
	const bidsReceived = [];
	if (listingIds.length > 0) {
		for (const chunk of chunkArray(listingIds, MAX_QUERY_PARAMS_PER_CHUNK)) {
			const placeholders = chunk.map(() => "?").join(",");
			const { results } = await env.DB.prepare(`SELECT * FROM marketplaceBids WHERE listingId IN (${placeholders})`).bind(...chunk).all();
			bidsReceived.push(...results);
		}
	}

	const { results: jobs } = await env.DB.prepare("SELECT * FROM marketplaceJobs WHERE accountId = ? ORDER BY createdAt DESC").bind(auth.admin.id).all();
	const { results: myJobInterests } = await env.DB.prepare(
		`SELECT ji.*, j.title, j.world FROM marketplaceJobInterests ji JOIN marketplaceJobs j ON j.id = ji.jobId WHERE ji.interestedAccountId = ? ORDER BY ji.createdAt DESC`
	).bind(auth.admin.id).all();

	// Interest received on the caller's own jobs — unlike bidsReceived above,
	// this includes the interested person's contact info directly: expressing
	// interest IS the reveal moment for jobs (no accept step), so by the time
	// the poster is looking at this list they're already meant to have it.
	const jobIds = jobs.map((j) => j.id);
	const jobInterestsReceived = [];
	if (jobIds.length > 0) {
		for (const chunk of chunkArray(jobIds, MAX_QUERY_PARAMS_PER_CHUNK)) {
			const placeholders = chunk.map(() => "?").join(",");
			const { results } = await env.DB.prepare(
				`SELECT ji.*, a.username AS accountUsername, a.mcUsername AS accountMcUsername, a.contactDiscord, a.contactTimezone
				 FROM marketplaceJobInterests ji JOIN admins a ON a.id = ji.interestedAccountId
				 WHERE ji.jobId IN (${placeholders})`
			).bind(...chunk).all();
			jobInterestsReceived.push(...results.map((r) => ({
				id: r.id, jobId: r.jobId, message: r.message, createdAt: r.createdAt,
				interestedUsername: r.accountMcUsername || r.accountUsername,
				contactInfo: contactInfoText(r),
			})));
		}
	}

	// The caller's own thumbs up/down votes, keyed by jobId, so the marketplace
	// page can show their existing vote as already-selected on any job (not
	// just their own) without a round trip per card.
	const { results: myReviewRows } = await env.DB.prepare("SELECT jobId, vote FROM marketplaceJobReviews WHERE reviewerAccountId = ?").bind(auth.admin.id).all();
	const myJobReviews = {};
	for (const r of myReviewRows) myJobReviews[r.jobId] = r.vote;

	return json({ listings, myBids, bidsReceived, jobs, myJobInterests, jobInterestsReceived, myJobReviews });
}

async function handleGetMarketplaceNotifications(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM marketplaceNotifications WHERE accountId = ? ORDER BY createdAt DESC LIMIT 100").bind(auth.admin.id).all();
	return json(results);
}

async function handleMarkNotificationsRead(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const now = new Date().toISOString();
	if (Array.isArray(body.ids) && body.ids.length > 0) {
		for (const chunk of chunkArray(body.ids.map(String), MAX_QUERY_PARAMS_PER_CHUNK)) {
			const placeholders = chunk.map(() => "?").join(",");
			await env.DB.prepare(`UPDATE marketplaceNotifications SET readAt = ? WHERE accountId = ? AND id IN (${placeholders})`).bind(now, auth.admin.id, ...chunk).run();
		}
	} else {
		await env.DB.prepare("UPDATE marketplaceNotifications SET readAt = ? WHERE accountId = ? AND readAt IS NULL").bind(now, auth.admin.id).run();
	}
	return json({ ok: true });
}

// ---- unique mod-user tracking ----
// Piggybacks on GET /marketplace/notifications/for-mc: WatchlistJoinCheck (in
// the mod) already calls this, with the player's real MC username, on every
// single join — unconditionally, no watchlist or account needed — so this is
// a live per-player signal that already exists in every currently-deployed
// mod version, no mod update required. The raw username is never stored:
// only an HMAC-SHA256 of it (keyed by the dedicated MOD_USER_HASH_SECRET,
// never reused elsewhere), so this can only ever answer "how many distinct
// players", never "which ones". Best-effort — must never break notification
// delivery itself if it fails for any reason.
async function modUserPingHash(env, mcUsername) {
	const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.MOD_USER_HASH_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
	const normalized = mcUsername.trim().toLowerCase().replace(/^\.+/, ""); // Bedrock names carry a leading '.'
	const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(normalized));
	return bufToHex(sig);
}
async function recordModUserPing(env, mcUsername) {
	try {
		const hash = await modUserPingHash(env, mcUsername);
		const now = new Date().toISOString();
		await env.DB.prepare(
			"INSERT INTO modUserPings (usernameHash, firstSeenAt, lastSeenAt, pingCount) VALUES (?, ?, ?, 1) " +
			"ON CONFLICT(usernameHash) DO UPDATE SET lastSeenAt = excluded.lastSeenAt, pingCount = pingCount + 1"
		).bind(hash, now, now).run();
	} catch (e) { /* telemetry only — never worth failing the real request over */ }
}

// Head admin: unique-mod-user counts derived from modUserPings above.
async function handleAdminModUserStats(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const now = Date.now(), day = 24 * 60 * 60 * 1000;
	const cutoff7 = new Date(now - 7 * day).toISOString();
	const cutoff30 = new Date(now - 30 * day).toISOString();
	const [total, last7, last30, since] = await Promise.all([
		env.DB.prepare("SELECT COUNT(*) AS c FROM modUserPings").first(),
		env.DB.prepare("SELECT COUNT(*) AS c FROM modUserPings WHERE lastSeenAt >= ?").bind(cutoff7).first(),
		env.DB.prepare("SELECT COUNT(*) AS c FROM modUserPings WHERE lastSeenAt >= ?").bind(cutoff30).first(),
		env.DB.prepare("SELECT MIN(firstSeenAt) AS m FROM modUserPings").first(),
	]);
	return json({ total: total.c, activeLast7d: last7.c, activeLast30d: last30.c, trackingSince: since.m || null });
}

// Used by the MOD on join — no session token (the mod isn't a logged-in
// website session), just the player's own MC username, same trust model the
// rest of the mod's uploads already use. Verification is now mapart-only, so
// this delivers for EVERY account whose linked MC username matches (typed
// usernames are taken on trust — same as registration). Marks whatever it
// returns as delivered so it isn't repeated on the next join.
async function handleGetNotificationsForMc(request, env, ctx) {
	const url = new URL(request.url);
	const mcUsername = (url.searchParams.get("mcUsername") || "").trim();
	if (!mcUsername) return json({ error: "mcUsername is required" }, 400);
	ctx.waitUntil(recordModUserPing(env, mcUsername));

	const { results: accounts } = await env.DB.prepare("SELECT id FROM admins WHERE lower(ltrim(mcUsername, '.')) = lower(ltrim(?, '.'))").bind(mcUsername).all();
	if (accounts.length === 0) return json([]);

	const results = [];
	for (const account of accounts) {
		const { results: rows } = await env.DB.prepare(
			"SELECT * FROM marketplaceNotifications WHERE accountId = ? AND deliveredInGameAt IS NULL ORDER BY createdAt"
		).bind(account.id).all();
		results.push(...rows);
	}
	results.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)));
	if (results.length > 0) {
		const now = new Date().toISOString();
		for (const chunk of chunkArray(results.map((r) => r.id), MAX_QUERY_PARAMS_PER_CHUNK)) {
			const placeholders = chunk.map(() => "?").join(",");
			await env.DB.prepare(`UPDATE marketplaceNotifications SET deliveredInGameAt = ? WHERE id IN (${placeholders})`).bind(now, ...chunk).run();
		}
	}
	return json(results.map((r) => ({ type: r.type, message: r.message })));
}

// Permission bucket "marketplaceListings" — moderation.
// Search-based, not a full dump — an active-and-growing marketplace makes
// "list everything" both slow and useless to scroll through. Same idea as
// GET /admin/admins?username=.
async function handleAdminListMarketplaceListings(request, env) {
	const auth = await requireAdminAuth(request, env, "marketplaceListings");
	if (!auth.ok) return auth.response;
	const url = new URL(request.url);
	const username = (url.searchParams.get("username") || "").trim();
	if (!username) return json([]);
	const { results } = await env.DB.prepare(
		"SELECT ml.*, a.username AS accountUsername FROM marketplaceListings ml JOIN admins a ON a.id = ml.accountId WHERE lower(a.username) = lower(?) ORDER BY ml.createdAt DESC"
	).bind(username).all();
	return json(results);
}

async function handleAdminRemoveMarketplaceListing(request, env) {
	const auth = await requireAdminAuth(request, env, "marketplaceListings");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);
	const reason = body.reason ? String(body.reason).trim().slice(0, 300) : "no reason given";

	const listing = await env.DB.prepare("SELECT * FROM marketplaceListings WHERE id = ?").bind(id).first();
	if (!listing) return json({ error: "Listing not found" }, 404);
	if (listing.status !== "active") return json({ error: "Listing isn't active" }, 400);

	await env.DB.prepare("UPDATE marketplaceListings SET status = 'cancelled', closedAt = ?, closedReason = ? WHERE id = ?")
		.bind(new Date().toISOString(), `removed by admin: ${reason}`, id).run();
	await notifyAccount(env, listing.accountId, "listingRemovedByAdmin", `Your ${listing.itemName} listing was removed by an admin: ${reason}`, id);
	return json({ ok: true });
}

// Daily sweep (piggybacks the existing cron — see scheduled()): a shop
// listing not re-scanned in NOT_SEEN_REMOVAL_DAYS is deleted outright. This
// is separate from (and catches more than) the missingStreak mechanism in
// handleUploadListings, which only fires when the SAME position gets
// actively re-scanned and comes up empty — a shop nobody scans anymore at
// all (seller stopped playing, uninstalled the mod, that whole area just
// isn't visited) would otherwise sit in `listings` forever. No notification
// here — unlike marketplace listings, a real shop listing isn't tied to any
// logged-in account to notify.
const NOT_SEEN_REMOVAL_DAYS = 14;
async function removeStaleListings(env) {
	const cutoff = new Date(Date.now() - NOT_SEEN_REMOVAL_DAYS * 24 * 60 * 60 * 1000).toISOString();
	const res = await env.DB.prepare("DELETE FROM listings WHERE lastSeen < ?").bind(cutoff).run();
	return { removedStale: (res.meta && res.meta.changes) || 0 };
}

// Daily sweep (piggybacks the existing cron — see scheduled()): flips any
// listing past its expiresAt to status='expired' and notifies the owner.
// The active-listing queries above already filter on expiresAt too, so this
// is about correctness of the STATUS field and the notification, not about
// hiding expired listings sooner — those are already excluded immediately.
async function expireOldMarketplaceListings(env) {
	const now = new Date().toISOString();
	const { results } = await env.DB.prepare("SELECT id, accountId, itemName FROM marketplaceListings WHERE status = 'active' AND expiresAt <= ?").bind(now).all();
	if (results.length === 0) return { expired: 0 };
	const stmts = results.map((r) => env.DB.prepare("UPDATE marketplaceListings SET status = 'expired', closedAt = ? WHERE id = ?").bind(now, r.id));
	await env.DB.batch(stmts);
	for (const r of results) await notifyAccount(env, r.accountId, "listingExpired", `Your ${r.itemName} listing expired after 14 days with no accepted offer.`, r.id);
	return { expired: results.length };
}

// Same 14-day lifetime/piggybacked-cron idea as expireOldMarketplaceListings above.
async function expireOldMarketplaceJobs(env) {
	const now = new Date().toISOString();
	const { results } = await env.DB.prepare("SELECT id, accountId, title FROM marketplaceJobs WHERE status = 'active' AND expiresAt <= ?").bind(now).all();
	if (results.length === 0) return { expired: 0 };
	const stmts = results.map((r) => env.DB.prepare("UPDATE marketplaceJobs SET status = 'expired', closedAt = ? WHERE id = ?").bind(now, r.id));
	await env.DB.batch(stmts);
	for (const r of results) await notifyAccount(env, r.accountId, "jobExpired", `Your "${r.title}" job post expired after 14 days.`, r.id);
	return { expired: results.length };
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
	const auth = await requireAnyPermission(request, env, ["reports", "manageMapart"]);
	if (!auth.ok) return auth.response;
	// Mapart reports share this queue but only "manageMapart" holders can act on
	// them, listing reports only "reports" holders — each sees just their own.
	const seeListing = auth.perms.includes("reports"), seeMapart = auth.perms.includes("manageMapart");
	try {
		const { results: all } = await env.DB.prepare("SELECT * FROM reports ORDER BY createdAt").all();
		const results = all.filter((r) => (isMapartReportKey(r.listingKey) ? seeMapart : seeListing));
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
	const auth = await requireAnyPermission(request, env, ["reports", "manageMapart"]);
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

	let reportRow;
	try {
		reportRow = await env.DB.prepare("SELECT * FROM reports WHERE id = ?").bind(id).first();
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
	if (!reportRow) return json({ error: "Report not found" }, 404);

	const isMapart = isMapartReportKey(reportRow.listingKey);
	if (!auth.perms.includes(isMapart ? "manageMapart" : "reports")) return json({ error: "Forbidden" }, 403);
	if (action === "edit") {
		const okField = isMapart ? MAPART_REPORT_EDIT_FIELDS.has(String(body.field || "")) : EDITABLE_LISTING_FIELDS.has(String(body.field || ""));
		if (!okField) return json({ error: "Invalid or missing field for edit" }, 400);
	}
	if (reportRow.status !== "pending") return json({ error: "That report was already resolved." }, 409);
	if (isMapart) return resolveMapartReport(env, reportRow, action, body, auth.admin);

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

// Instant delete, no report record created — the website swaps the per-row
// "Report" button for a "Remove" button when the logged-in account already
// has the "reports" permission (see index.html's renderTable), skipping the
// modal/reason/confirmation entirely for someone already trusted to resolve
// reports the normal way.
async function handleAdminRemoveListingDirect(request, env) {
	const auth = await requireAdminAuth(request, env, "reports");
	if (!auth.ok) return auth.response;

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const rowKey = String(body.rowKey || "");
	if (!rowKey) return json({ error: "rowKey is required" }, 400);

	const res = await env.DB.prepare("DELETE FROM listings WHERE rowKey = ?").bind(rowKey).run();
	return json({ ok: true, deleted: res.meta.changes > 0 });
}

// ---------------- suggestions / bug reports / player reports ----------------
//
// Public submission (same trust model as POST /reports above — no login,
// gated only by the shared API_KEY that already ships in the page source;
// everything lands in a pending admin queue and nothing is auto-actioned).
// Suggestions/bug reports are dismissed with a plain delete (see
// handleAdminDeleteSuggestion/handleAdminDeleteBugReport) — the admin adds
// anything worth keeping to the Trello roadmap by hand. Player reports get a
// real resolution flow instead, since "ban"/"remove" have side effects.

async function handleSubmitSuggestion(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const title = String(body.title || "").trim().slice(0, 150);
	const details = String(body.details || "").trim().slice(0, 1000);
	if (!title) return json({ error: "title is required" }, 400);
	if (!details) return json({ error: "details is required" }, 400);
	const submitterName = String(body.submitterName || "").trim().slice(0, 50);

	const id = crypto.randomUUID();
	try {
		await env.DB.prepare(
			"INSERT INTO suggestions (id, title, details, submitterName, status, createdAt, resolvedAt) VALUES (?, ?, ?, ?, 'pending', ?, NULL)"
		).bind(id, title, details, submitterName, new Date().toISOString()).run();
		return json({ ok: true, id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListSuggestions(request, env) {
	const auth = await requireAdminAuth(request, env, "suggestions");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM suggestions ORDER BY createdAt DESC").all();
	return json(results);
}

async function handleAdminDeleteSuggestion(request, env) {
	const auth = await requireAdminAuth(request, env, "suggestions");
	if (!auth.ok) return auth.response;
	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);
	await env.DB.prepare("DELETE FROM suggestions WHERE id = ?").bind(id).run();
	return json({ ok: true });
}

async function handleSubmitBugReport(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const title = String(body.title || "").trim().slice(0, 150);
	const details = String(body.details || "").trim().slice(0, 1000);
	if (!title) return json({ error: "title is required" }, 400);
	if (!details) return json({ error: "details is required" }, 400);
	const area = BUG_REPORT_AREAS.has(body.area) ? body.area : "website";
	const world = (area === "mod" && (body.world === "Firefly" || body.world === "Honeybee")) ? body.world : null;
	const pageUrl = String(body.pageUrl || "").trim().slice(0, 300) || null;
	const submitterName = String(body.submitterName || "").trim().slice(0, 50);

	const id = crypto.randomUUID();
	try {
		await env.DB.prepare(
			"INSERT INTO bugReports (id, title, details, area, world, pageUrl, submitterName, status, createdAt, resolvedAt) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, NULL)"
		).bind(id, title, details, area, world, pageUrl, submitterName, new Date().toISOString()).run();
		return json({ ok: true, id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListBugReports(request, env) {
	const auth = await requireAdminAuth(request, env, "bugReports");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM bugReports ORDER BY createdAt DESC").all();
	return json(results);
}

async function handleAdminDeleteBugReport(request, env) {
	const auth = await requireAdminAuth(request, env, "bugReports");
	if (!auth.ok) return auth.response;
	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const id = String(body.id || "");
	if (!id) return json({ error: "id is required" }, 400);
	await env.DB.prepare("DELETE FROM bugReports WHERE id = ?").bind(id).run();
	return json({ ok: true });
}

async function handleSubmitPlayerReport(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);

	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}

	const reportedUsername = String(body.reportedUsername || "").trim();
	if (!isValidUsername(reportedUsername)) return json({ error: "Invalid reportedUsername" }, 400);
	const world = body.world === "Firefly" || body.world === "Honeybee" ? body.world : null;
	if (!world) return json({ error: "world must be 'Firefly' or 'Honeybee'" }, 400);
	const reason = String(body.reason || "").trim();
	if (!PLAYER_REPORT_REASONS.has(reason)) return json({ error: "Invalid reason" }, 400);
	const details = String(body.details || "").trim().slice(0, 500);
	if (!details) return json({ error: "details is required" }, 400);
	const reporterName = String(body.reporterName || "").trim().slice(0, 50);

	const id = crypto.randomUUID();
	try {
		await env.DB.prepare(
			"INSERT INTO playerReports (id, reportedUsername, world, reason, details, reporterName, status, actionTaken, createdAt, resolvedAt) VALUES (?, ?, ?, ?, ?, ?, 'pending', NULL, ?, NULL)"
		).bind(id, reportedUsername, world, reason, details, reporterName, new Date().toISOString()).run();
		return json({ ok: true, id });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

async function handleListPlayerReports(request, env) {
	const auth = await requireAdminAuth(request, env, "playerReports");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM playerReports ORDER BY createdAt DESC").all();
	return json(results);
}

// action "ban": blocks the seller (see blockSellerAndWipe) — permanent until
// manually unblocked in the Blocked Sellers section, both worlds at once,
// same as a normal block. action "remove": clears just this report's world
// (the shop they were actually reported for) without blocking — they can
// still sell again. action "none": dismiss with no side effect.
async function handleAdminResolvePlayerReport(request, env) {
	const auth = await requireAdminAuth(request, env, "playerReports");
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
	if (!["ban", "remove", "none"].includes(action)) return json({ error: "Invalid action" }, 400);

	const reportRow = await env.DB.prepare("SELECT * FROM playerReports WHERE id = ?").bind(id).first();
	if (!reportRow) return json({ error: "Report not found" }, 404);

	try {
		if (action === "ban") {
			await blockSellerAndWipe(env, reportRow.reportedUsername, `Player report: ${reportRow.reason}`, auth.admin.username);
		} else if (action === "remove") {
			await deleteShopListingsBySeller(env, reportRow.reportedUsername, reportRow.world);
		}
		await env.DB.prepare(
			"UPDATE playerReports SET status = 'resolved', actionTaken = ?, resolvedAt = ? WHERE id = ?"
		).bind(action, new Date().toISOString(), id).run();
		return json({ ok: true });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
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

// GET /roadmap — public, cached. Proxies the project's public Trello board
// (SCTP roadmap) enriched with its "Amazing Fields" Power-Up data (Where/
// What/When tags, Progress, Details) — see roadmap/index.html. The Amazing
// Fields API needs a paid-plan token (env.AMAZING_FIELDS_TOKEN, a Worker
// secret), which must never reach the browser, so this fetches server-side
// and returns only the public-safe shape the page actually renders.
const ROADMAP_BOARD_ID = "6aa066d1ae691933af775a19";
const ROADMAP_CACHE_TTL_SECONDS = 300; // 5 min — a personal roadmap board doesn't need to be live-live, and this keeps well under Amazing Fields' API quota

async function handleGetRoadmap(request, env, ctx) {
	return cachedGet(request, ctx, ROADMAP_CACHE_TTL_SECONDS, async () => {
		const url = `https://api.amazingpowerups.com/api/data/v1/boards/${ROADMAP_BOARD_ID}/cards?token=${env.AMAZING_FIELDS_TOKEN}`;
		const res = await fetch(url);
		if (!res.ok) throw new Error(`Amazing Fields API ${res.status}`);
		const data = await res.json();

		const fields = ((data.amazingFieldsConfig && data.amazingFieldsConfig.fields) || []).map((f) => ({
			id: f.id,
			name: f.name,
			type: f.type_str,
			options: (f.options || []).map((o) => ({ id: o.id, text: o.text, color: o.color })),
		}));

		const lists = (data.lists || [])
			.filter((l) => !l.closed)
			.sort((a, b) => a.pos - b.pos)
			.map((l) => ({ id: l.id, name: l.name }));

		// isTemplate excludes the board's own "New Item" card template (used to
		// seed new roadmap entries in Trello, not a real roadmap item itself).
		const cards = (data.cards || [])
			.filter((c) => !c.closed && !c.isTemplate)
			.sort((a, b) => a.pos - b.pos)
			.map((c) => ({
				id: c.id,
				name: c.name,
				idList: c.idList,
				shortUrl: c.shortUrl,
				due: c.due,
				dueComplete: c.dueComplete,
				checkItems: (c.badges && c.badges.checkItems) || 0,
				checkItemsChecked: (c.badges && c.badges.checkItemsChecked) || 0,
				amazingFields: ((c.amazingFields && c.amazingFields.fields) || []).map((f) => ({ id: f.id, value: f.value })),
			}));

		return { fields, lists, cards };
	});
}

// GET /update-notice — public, cached. The mod fetches this once per join
// (see UpdateNoticeCheck) and compares its own version against minVersion
// using the same isVersionAtLeast logic as MIN_TRUSTED_PRUNE_VERSION above;
// below it, `message` gets printed to chat — mod versions newer than 2.0 render any
// [text](url) inside it as a clickable link, older ones show the raw brackets/text.
// Nothing else on the site reads this.
async function handleGetUpdateNotice(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const row = await env.DB.prepare("SELECT enabled, minVersion, message FROM updateNotice WHERE id = 1").first();
		return row ? { enabled: !!row.enabled, minVersion: row.minVersion, message: row.message } : { enabled: false, minVersion: "", message: "" };
	});
}

// Permission bucket "updateNotice" — admin-panel-only read, bypasses the
// public endpoint's edge cache so a save is reflected immediately.
async function handleAdminGetUpdateNotice(request, env) {
	const auth = await requireAdminAuth(request, env, "updateNotice");
	if (!auth.ok) return auth.response;
	const row = await env.DB.prepare("SELECT * FROM updateNotice WHERE id = 1").first();
	return json(row ? { ...row, enabled: !!row.enabled } : { enabled: false, minVersion: "", message: "", updatedAt: null, updatedBy: "" });
}

async function handleAdminSetUpdateNotice(request, env) {
	const auth = await requireAdminAuth(request, env, "updateNotice");
	if (!auth.ok) return auth.response;
	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({ error: "Invalid JSON body" }, 400);
	}
	const enabled = !!body.enabled;
	const minVersion = String(body.minVersion || "").trim().slice(0, 20);
	const message = String(body.message || "").trim().slice(0, 500);

	await env.DB.prepare(
		"UPDATE updateNotice SET enabled = ?, minVersion = ?, message = ?, updatedAt = ?, updatedBy = ? WHERE id = 1"
	).bind(enabled ? 1 : 0, minVersion, message, new Date().toISOString(), auth.admin.username).run();

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

// Validates one manual-listing entry — shared by the admin endpoint and the
// seller's own "Manage my store". Returns { entry } or { error }.
function parseManualListingEntry(e) {
	const itemName = String((e && e.itemName) || "").trim().slice(0, 100);
	const price = Number(e && e.price);
	const currency = String((e && e.currency) || "").trim().toLowerCase().slice(0, 40);
	const position = String((e && e.position) || "").trim().slice(0, 200);
	if (!itemName || !currency || !position || !Number.isFinite(price) || price < 0) {
		return { error: `Invalid entry: ${JSON.stringify(e)}` };
	}
	if (isBannedItem("manual", itemName)) return { error: `"${itemName}" isn't allowed` };
	const priceLabel = String((e && e.priceLabel) || "").trim().slice(0, 60) || `${price} ${currency}`;
	return { entry: { itemName, price, currency, position, priceLabel } };
}

function manualListingRowKey(world, seller, itemName) {
	return `${world}|${seller}|manual|${itemName}`.toLowerCase();
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
		const r = parseManualListingEntry(e);
		if (r.error) return json({ error: r.error }, 400);
		parsed.push(r.entry);
	}

	try {
		const ids = await nextManualIds(env, parsed.length);
		const stmts = parsed.map((e, i) => {
			const rowKeyVal = manualListingRowKey(world, seller, e.itemName);
			return env.DB.prepare(
				`INSERT INTO listings (rowKey, id, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen)
				 VALUES (?, ?, ?, 'manual', 0, 0, 0, ?, ?, 1, 1, 1, ?, ?, ?, ?, ?)
				 ON CONFLICT(rowKey) DO UPDATE SET
				   price=excluded.price, priceLabel=excluded.priceLabel, currency=excluded.currency,
				   position=excluded.position, lastSeen=excluded.lastSeen`
			).bind(rowKeyVal, newId(), e.itemName, e.price, e.priceLabel, e.currency, seller, world, e.position, ids[i]);
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

// Shared by handleAdminDeleteShopListings and handleAdminResolvePlayerReport's
// "remove" action. world is optional — omitted, this clears the seller on
// both worlds at once (the same seller can run independent shops on each).
async function deleteShopListingsBySeller(env, seller, world) {
	const res = world
		? await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = lower(?) AND world = ?").bind(seller, world).run()
		: await env.DB.prepare("DELETE FROM listings WHERE lower(seller) = lower(?)").bind(seller).run();
	return res.meta.changes;
}

// Deletes every listing for a seller in one shot — for a shop that's gone
// entirely (player quit, moved, shop torn down) rather than one stale item,
// which is what /admin/reports/resolve and /admin/listings/manual-delete are
// each scoped to.
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
		const deleted = await deleteShopListingsBySeller(env, seller, world || null);
		return json({ ok: true, deleted });
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
		"SELECT rowKey, baseItem, itemName, price, currency, stackSize, amount, seller, world FROM listings"
	).all();

	// groupKey -> { itemKey, world, prices: number[], sellers: Set, totalStock, listingCount }
	const groups = new Map();
	for (const r of rows) {
		if (String(r.currency || "").toLowerCase() === "display") continue; // no real price/stock — same exclusion as the site's own price summary
		const name = displayName(r.baseItem, r.itemName);
		const itemKey = "v:" + String(r.baseItem).toLowerCase() + "|" + name.toLowerCase();
		const groupKey = itemKey + " " + r.world;
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

	const sellerStatsResult = await computeSellerItemStats(env, rows, today);

	return { date: today, itemsSnapshotted: groups.size, listingsScanned: rows.length, ...sellerStatsResult };
}

// Per-seller-per-item daily stats, INCLUDING an estimated sales figure — the
// only sales signal anywhere in this project, since no real transaction log
// exists. Diffs today's live D1 state (rows, already fetched by the caller)
// against YESTERDAY's full per-listing snapshot in R2 (see snapshotListingsToR2)
// at the exact rowKey level — this is real per-listing precision (bulk vs
// normal stacks of the same item from the same seller are distinct rowKeys),
// not just a same-day aggregate. A rowKey whose amount dropped, or that's
// gone entirely today, reads as an inferred sale of the difference (or the
// full remaining amount) — this can't distinguish an actual sale from the
// seller just pulling/changing the listing, so every consumer of this data
// must present it as an estimate, never a verified count.
async function computeSellerItemStats(env, todayRows, today) {
	let langTable = {};
	try {
		const res = await fetch(ITEM_LANG_TABLE_URL);
		if (res.ok) langTable = await res.json();
	} catch (e) {
		// same fallback as computeDailySnapshots — every name treated as English
	}
	const langSets = new Map();
	for (const baseItem in langTable) langSets.set(baseItem, new Set(langTable[baseItem].alt));
	function displayName(baseItem, itemName) {
		const set = langSets.get(baseItem);
		if (!set || !set.has(alphaOnly(itemName))) return itemName;
		return langTable[baseItem].en;
	}

	const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
	let yesterdayRows = [];
	try {
		const obj = await env.SNAPSHOTS.get(`${yesterday}.json`);
		if (obj) yesterdayRows = JSON.parse(await obj.text());
	} catch (e) {
		// no snapshot yet for yesterday (e.g. the very first day this ran) — an
		// empty baseline just means nothing reads as sold today, which is correct.
	}
	const yesterdayByRowKey = new Map();
	for (const r of yesterdayRows) yesterdayByRowKey.set(r.rowKey, r);

	// sellerKey|itemKey|world -> aggregate across every rowKey (bulk/bundled/etc) for that item
	const buckets = new Map();
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

	for (const [rowKey, prior] of yesterdayByRowKey) {
		if (String(prior.currency || "").toLowerCase() === "display") continue;
		const now = todayByRowKey.get(rowKey);
		const priorAmount = Number(prior.amount) || 0;
		const priceDia = priceInDiamonds(prior) / (prior.stackSize || 1);
		let sold = 0;
		if (!now) {
			sold = priorAmount; // gone entirely — sold out, or delisted; can't tell which (see doc comment above)
		} else if (Number(now.amount) < priorAmount) {
			sold = priorAmount - Number(now.amount);
		}
		if (sold <= 0) continue;
		const b = bucketFor(prior.seller, prior.baseItem, prior.itemName, prior.world);
		b.inferredSold += sold;
		b.inferredRevenue += sold * priceDia;
	}

	const stmts = [];
	for (const b of buckets.values()) {
		const avg = b.prices.length ? b.prices.reduce((a, c) => a + c, 0) / b.prices.length : 0;
		stmts.push(env.DB.prepare(
			`INSERT INTO sellerItemDailyStats (seller, sellerKey, itemKey, itemName, world, date, totalStock, listingCount, avgPriceDiamonds, inferredSold, inferredRevenueDiamonds)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(sellerKey, itemKey, world, date) DO UPDATE SET
			   seller=excluded.seller, itemName=excluded.itemName, totalStock=excluded.totalStock, listingCount=excluded.listingCount,
			   avgPriceDiamonds=excluded.avgPriceDiamonds, inferredSold=excluded.inferredSold, inferredRevenueDiamonds=excluded.inferredRevenueDiamonds`
		).bind(b.seller, b.sellerKey, b.itemKey, b.itemName, b.world, today, b.totalStock, b.listingCount, avg, b.inferredSold, b.inferredRevenue));
	}
	for (const chunk of chunkArray(stmts, 100)) {
		if (chunk.length > 0) await env.DB.batch(chunk);
	}

	return { sellerItemBucketsSnapshotted: buckets.size };
}

// Full daily dump of the live listings table — same query/shape as GET
// /listings itself, just archived to R2 instead of served live. Unlike
// itemDailyStats (aggregated across all sellers of an item), this preserves
// every individual row, so per-seller/per-item history can be reconstructed
// later by diffing consecutive days. Admin-only to read (see
// handleAdminSnapshots) — this is a much more detailed, permanently-retained
// record than the live public endpoint, so it isn't exposed publicly.
async function snapshotListingsToR2(env) {
	const { results } = await env.DB.prepare(
		"SELECT * FROM listings WHERE lower(seller) NOT IN (SELECT usernameKey FROM blockedSellers)"
	).all();
	const rows = results.map((r) => ({ ...r, bulk: !!r.bulk, bundled: !!r.bundled, mixedContents: !!r.mixedContents }));

	const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
	await env.SNAPSHOTS.put(`${today}.json`, JSON.stringify(rows), {
		httpMetadata: { contentType: "application/json" },
	});

	return { date: today, rows: rows.length };
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

// Public — extends the per-day series above with all-time aggregates: total
// ESTIMATED units sold and distinct sellers who've ever carried it, per
// world (from sellerItemDailyStats — see computeSellerItemStats' doc comment
// on why this is an estimate, never a verified sales count), plus the most
// recent day's snapshot for a quick "right now" summary.
async function handleGetItemStats(request, env, ctx) {
	const url = new URL(request.url);
	const itemKey = url.searchParams.get("itemKey");
	if (!itemKey) return json({ error: "itemKey is required" }, 400);

	return cachedGet(request, ctx, HISTORY_CACHE_TTL_SECONDS, async () => {
		const { results: totalsByWorld } = await env.DB.prepare(
			`SELECT world, SUM(inferredSold) as totalInferredSold, SUM(inferredRevenueDiamonds) as totalInferredRevenue,
			 COUNT(DISTINCT sellerKey) as distinctSellersEver
			 FROM sellerItemDailyStats WHERE itemKey = ? GROUP BY world`
		).bind(itemKey).all();

		const latestDateRow = await env.DB.prepare("SELECT MAX(date) as d FROM itemDailyStats WHERE itemKey = ?").bind(itemKey).first();
		const latestDate = latestDateRow ? latestDateRow.d : null;
		const current = latestDate
			? (await env.DB.prepare(
				"SELECT world, sellerCount, totalStock, avgPrice, lowestPrice, highestPrice FROM itemDailyStats WHERE itemKey = ? AND date = ?"
			).bind(itemKey, latestDate).all()).results
			: [];

		// Daily "units sold" trend (estimated, per world) for the item page's
		// sold-per-day chart, plus a combined (both worlds) daily average.
		const { results: soldTrendRows } = await env.DB.prepare(
			`SELECT date, world, SUM(inferredSold) as sold FROM sellerItemDailyStats WHERE itemKey = ? GROUP BY date, world ORDER BY date`
		).bind(itemKey).all();
		const soldByDate = new Map();
		for (const r of soldTrendRows) soldByDate.set(r.date, (soldByDate.get(r.date) || 0) + r.sold);
		const trackingDays = soldByDate.size;
		const totalSoldAllWorlds = [...soldByDate.values()].reduce((a, v) => a + v, 0);
		const avgSoldPerDay = trackingDays > 0 ? totalSoldAllWorlds / trackingDays : 0;

		return { itemKey, asOfDate: latestDate, current, totalsByWorld, soldTrend: soldTrendRows, trackingDays, avgSoldPerDay };
	});
}

// Public — world-wide economy trend (from itemDailyStats/sellerItemDailyStats,
// both already seller-anonymous at this aggregation level) plus a top-selling
// items list. "Top selling" is an ESTIMATE — see computeSellerItemStats.
async function handleGetWorldStats(request, env, ctx) {
	const url = new URL(request.url);
	const world = url.searchParams.get("world");
	if (world !== "Firefly" && world !== "Honeybee") return json({ error: "world must be 'Firefly' or 'Honeybee'" }, 400);

	return cachedGet(request, ctx, HISTORY_CACHE_TTL_SECONDS, async () => {
		const { results: dailyRows } = await env.DB.prepare(
			`SELECT date, SUM(listingCount) as listings, SUM(totalStock) as stock, COUNT(DISTINCT itemKey) as distinctItems
			 FROM itemDailyStats WHERE world = ? GROUP BY date ORDER BY date`
		).bind(world).all();

		const { results: sellerCountRows } = await env.DB.prepare(
			"SELECT date, COUNT(DISTINCT sellerKey) as sellers FROM sellerItemDailyStats WHERE world = ? GROUP BY date"
		).bind(world).all();
		const sellersByDate = new Map(sellerCountRows.map((r) => [r.date, r.sellers]));

		const trend = dailyRows.map((r) => ({
			date: r.date, listings: r.listings, stock: r.stock,
			distinctItems: r.distinctItems, sellers: sellersByDate.get(r.date) || 0,
		}));

		// Unlimited (not just a top-N) so the /stats page's item search can find
		// and show the % share of ANY item that's ever sold here, not only
		// whichever ones happen to be in the top of the list.
		const { results: topSellingItems } = await env.DB.prepare(
			`SELECT itemKey, itemName, SUM(inferredSold) as totalInferredSold
			 FROM sellerItemDailyStats WHERE world = ? GROUP BY itemKey HAVING totalInferredSold > 0
			 ORDER BY totalInferredSold DESC`
		).bind(world).all();

		return { world, latest: trend.length ? trend[trend.length - 1] : null, trend, topSellingItems };
	});
}

// Any logged-in account (see requireAnyAdmin) with a linked Minecraft
// username can see their own shop's stats — nobody else's. Verification is
// currently disabled account-wide (see handleDirectRegistration), so this
// only requires mcUsername to be set, not mcVerified — the same trust level
// as everything else self-service right now. Everything derived from
// inferredSold/inferredRevenueDiamonds is an ESTIMATE (see
// computeSellerItemStats' doc comment) and must be presented as such.
async function handleGetMyStats(request, env) {
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	if (!auth.admin.mcUsername) {
		return json({ error: "Link your Minecraft username first." }, 403);
	}
	const sellerKey = auth.admin.mcUsername.toLowerCase();

	const { results: rows } = await env.DB.prepare(
		`SELECT itemKey, itemName, world, date, totalStock, listingCount, avgPriceDiamonds, inferredSold, inferredRevenueDiamonds
		 FROM sellerItemDailyStats WHERE sellerKey = ? ORDER BY date`
	).bind(sellerKey).all();

	if (rows.length === 0) return json({ hasData: false });

	const dates = [...new Set(rows.map((r) => r.date))].sort();
	const latestDate = dates[dates.length - 1];

	const current = rows.filter((r) => r.date === latestDate);
	const activeListings = current.reduce((a, r) => a + r.listingCount, 0);
	const distinctItemsActive = current.length;
	const currentStockValueDiamonds = current.reduce((a, r) => a + r.totalStock * r.avgPriceDiamonds, 0);

	const totalInferredSold = rows.reduce((a, r) => a + r.inferredSold, 0);
	const totalInferredRevenue = rows.reduce((a, r) => a + r.inferredRevenueDiamonds, 0);

	const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
	const recent = rows.filter((r) => r.date >= cutoff);
	const recentInferredSold = recent.reduce((a, r) => a + r.inferredSold, 0);
	const recentInferredRevenue = recent.reduce((a, r) => a + r.inferredRevenueDiamonds, 0);

	const byItem = new Map();
	const byDate = new Map();
	for (const r of rows) {
		const itemKey2 = r.itemKey + "|" + r.world;
		let ie = byItem.get(itemKey2);
		if (!ie) { ie = { itemName: r.itemName, world: r.world, inferredSold: 0, inferredRevenue: 0 }; byItem.set(itemKey2, ie); }
		ie.inferredSold += r.inferredSold;
		ie.inferredRevenue += r.inferredRevenueDiamonds;

		let de = byDate.get(r.date);
		if (!de) { de = { date: r.date, inferredSold: 0, inferredRevenue: 0 }; byDate.set(r.date, de); }
		de.inferredSold += r.inferredSold;
		de.inferredRevenue += r.inferredRevenueDiamonds;
	}
	const bestSellers = [...byItem.values()].filter((e) => e.inferredSold > 0).sort((a, b) => b.inferredSold - a.inferredSold).slice(0, 10);
	const hints = await computeShopHints(env, sellerKey, rows, latestDate, dates.length).catch(() => ({ restock: [], reprice: [], undercut: [] }));
	const trend = [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date)).slice(-60);
	const busiestDay = [...byDate.values()].sort((a, b) => b.inferredSold - a.inferredSold)[0] || null;

	return json({
		hasData: true,
		seller: auth.admin.mcUsername,
		activeListings, distinctItemsActive, currentStockValueDiamonds,
		totalInferredSold, totalInferredRevenue,
		recentInferredSold, recentInferredRevenue,
		bestSellers, trend, busiestDay, hints,
		trackingStartDate: dates[0], trackingDays: dates.length,
	});
}

// Admin-only manual trigger — same logic the daily cron runs, exposed so a
// snapshot can be forced without waiting for the schedule (testing, or
// backfilling today's data after a deploy).
async function handleAdminRunSnapshot(request, env) {
	const auth = await requireAdminAuth(request, env, null); // ops trigger, not a delegable content-moderation bucket
	if (!auth.ok) return auth.response;
	try {
		const statsResult = await computeDailySnapshots(env);
		const r2Result = await snapshotListingsToR2(env);
		return json({ ok: true, ...statsResult, r2Snapshot: r2Result });
	} catch (e) {
		return json({ error: String(e) }, 502);
	}
}

// GET /admin/snapshots            -> { dates: [...] }, every date with a stored dump
// GET /admin/snapshots?date=YYYY-MM-DD -> that day's full listings.json, streamed straight
// from R2 (not re-parsed) since a single day can be several MB.
async function handleAdminSnapshots(request, env) {
	const auth = await requireAdminAuth(request, env, null); // ops/internal tooling, not a delegable content-moderation bucket
	if (!auth.ok) return auth.response;

	const url = new URL(request.url);
	const date = url.searchParams.get("date");

	if (date) {
		const obj = await env.SNAPSHOTS.get(`${date}.json`);
		if (!obj) return json({ error: "No snapshot for that date" }, 404);
		return new Response(obj.body, { headers: { "Content-Type": "application/json", ...corsHeaders() } });
	}

	const listed = await env.SNAPSHOTS.list();
	const dates = listed.objects.map((o) => o.key.replace(/\.json$/, "")).sort();
	return json({ dates });
}

// ---------------- mapart ----------------

const MAPART_CATEGORIES = ["Pets", "Anime", "TV/Animation", "Art", "Memes", "Nature", "Photography", "Letters", "Seasonal", "Advertisement", "Misc", "Flags"];
const MAPART_WORLDS = ["Firefly", "Honeybee"];
const MAPART_MAX_PNG_BYTES = 4 * 1024 * 1024;
const MAPART_MAX_GROUPS_PER_UPLOAD = 40;
const MAPART_MAX_GRID = 20;
// /mapart/<slug> is served by 404.html's router unless a real folder exists
// there — these slugs would collide with real static paths.
const MAPART_RESERVED_SLUGS = new Set(["manage", "index", "image"]);
const VERIFICATION_LINK_BASE_URL = "https://sctp.nl/verify-link/?t=";
const PASSWORD_RESET_LINK_BASE_URL = "https://sctp.nl/reset-password/?t=";

function mapartSlugify(title) {
	let s = String(title || "").toLowerCase().normalize("NFKD").replace(/[̀-ͯ]/g, "")
		.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 60).replace(/-+$/, "");
	if (!s) s = "mapart";
	if (MAPART_RESERVED_SLUGS.has(s)) s += "-art";
	return s;
}

// Picks a slug nobody else owns (own old slugs count as free) and records it
// in mapartSlugs — old slugs are never removed so old links keep resolving.
async function assignMapartSlug(env, mapartId, title) {
	const base = mapartSlugify(title);
	let slug = null;
	for (let i = 1; i < 200 && !slug; i++) {
		const cand = i === 1 ? base : `${base}-${i}`;
		const row = await env.DB.prepare("SELECT mapartId FROM mapartSlugs WHERE slug = ?").bind(cand).first();
		if (!row || row.mapartId === mapartId) slug = cand;
	}
	if (!slug) slug = `${base}-${mapartId.slice(0, 8)}`;
	await env.DB.prepare("INSERT OR IGNORE INTO mapartSlugs (slug, mapartId) VALUES (?, ?)").bind(slug, mapartId).run();
	return slug;
}

function mapartNormKey(s) {
	return String(s || "").toLowerCase().replace(/[^a-z0-9]/g, "");
}

// Nicknames used in map names that stand for one or more real usernames.
// `phrases` are matched as whole words ignoring case/punctuation ("Earth &
// Charm", "earth-charm" and "EARTH CHARM" all match "earth charm") — but ONLY
// in credit position (see aliasCreditIndex), since many are ordinary words.
const MAPART_ALIASES = [
	{ phrases: ["azi"], artists: ["AziPazi"] },
	{ phrases: ["toto"], artists: ["totoric"] },
	{ phrases: ["earth charm", "earthcharm"], artists: ["EarthHQ", "Charmolyqi"] },
	{ phrases: ["mehoy"], artists: ["MehoyMinoy"] },
	{ phrases: ["wis", "wisteria"], artists: ["Magical_Wisteria"] },
	{ phrases: ["moldy"], artists: ["MoldyNug"] },
	{ phrases: ["grace"], artists: ["GraceLuna"] },
	{ phrases: ["peaches", "peachy", "peach"], artists: ["dr_peaches"] },
	{ phrases: ["video"], artists: ["videoghost9"] },
	{ phrases: ["mora"], artists: ["Moratenzis"] },
	{ phrases: ["frigid"], artists: ["FrigidAmbiance"] },
	{ phrases: ["zepp"], artists: ["TheZepptum"] },
	{ phrases: ["kat"], artists: ["NamelessKat"] },
	{ phrases: ["siren"], artists: ["DetectiveSiren"] },
	{ phrases: ["trev"], artists: ["trevorcd"] },
	{ phrases: ["lucky"], artists: ["Lucky_MoonXx"] },
	{ phrases: ["god"], artists: ["God404"] },
	{ phrases: ["cel"], artists: ["CelRxn"] },
	{ phrases: ["deny"], artists: ["DenyIndex"] },
	{ phrases: ["killz"], artists: ["KillzBob"] },
	{ phrases: ["rvban"], artists: ["Rvban97"] },
	{ phrases: ["lap"], artists: ["LapJi852"] },
	{ phrases: ["carrot"], artists: ["Carrot__Cake"] },
	{ phrases: ["ants"], artists: ["antsandpants"] },
	{ phrases: ["ender"], artists: ["EnderThe16th"] },
	{ phrases: ["aslan"], artists: ["AslanDev"] },
	{ phrases: ["lumi"], artists: ["LuminousSheep"] },
	{ phrases: ["moon"], artists: ["MoonNettle"] },
	{ phrases: ["memelord"], artists: ["memelordmars"] },
];
// Real usernames that appear in map names but have no listings/account, so
// nothing else would ever recognise them (and names taken at face value —
// "if we only know the short name, treat it as the username").
const MAPART_EXTRA_KNOWN_NAMES = [
	"Saternine21", "Sm0ochie", "DigiverseDragon", "AKST4R", "Alex_Calibre", "bstar", "mars", "DrawingLivii", "YNMS",
	"sukittyD", "Chlozer", "Edan0618", "Saigesky", "CFA", "Sample", "Jolt242", "LovieeAngel", "CloudNine22", "khaotikrypt",
	"ErzaRose", "Horizon50k", "Keita", "Saige", "Hand_Samwitch", "DemonicStijn", "MermaidKatie", "GameYeti", "AtlasMage",
	"dihsorder", "ariesmike", "To0ncez", "Aceramey", "MakiAi", "Nemesiszilla", "DetectivePeaches", "Mylilyaya1",
	"SnailLaxing", "flapchick", "nottabbyy", "Gh0st", "Cityfanart", "xardx", "Mochi_King", "TheHive", "Omen", "DirkVogel",
	"MartienVogel", "Jolt242",
];
const MAPART_MAX_ARTISTS = 3;

// Lowercased, every run of non-alphanumerics (underscores included) becomes
// one space, padded — so "Ariel_Saternine21", "Carrot__Cake" and
// "Earth & Charm" all compare as plain space-separated words.
function mapartMatchNorm(s) {
	return " " + String(s || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim().replace(/\s+/g, " ") + " ";
}

// Usernames worth recognising inside a map's name: every seller that has
// ever had a listing, plus every registered account's login/MC username,
// plus the alias targets and the hard-coded extras above.
let mapartKnownNamesCache = { at: 0, names: [] };
async function getMapartKnownNames(env, force) {
	if (!force && Date.now() - mapartKnownNamesCache.at < 5 * 60 * 1000 && mapartKnownNamesCache.names.length) return mapartKnownNamesCache.names;
	const names = new Map();
	const add = (n) => {
		const bare = String(n || "").replace(/^\./, "");
		if (bare.length >= 3 && isValidUsername(bare)) names.set(bare.toLowerCase(), bare);
	};
	const sellers = await env.DB.prepare("SELECT DISTINCT seller FROM listings").all();
	for (const r of sellers.results) add(r.seller);
	const accounts = await env.DB.prepare("SELECT username, mcUsername FROM admins").all();
	for (const r of accounts.results) { add(r.username); add(r.mcUsername); }
	// Filled-map shop listings are gone, so sellers who only ever sold maps
	// are no longer in `listings` — every artist already recorded on a
	// mapart (however it got there) still counts as a known username.
	const artists = await env.DB.prepare("SELECT DISTINCT artist FROM maparts WHERE artist IS NOT NULL").all();
	for (const r of artists.results) for (const n of splitMapartArtists(r.artist)) add(n);
	for (const n of MAPART_EXTRA_KNOWN_NAMES) add(n);
	for (const a of MAPART_ALIASES) for (const n of a.artists) add(n);
	mapartKnownNamesCache = { at: Date.now(), names: [...names.values()] };
	return mapartKnownNamesCache.names;
}

// A nickname only counts as a credit when it sits in credit position of ONE
// name: after a separator (- ~ • | / & , + :) or "by"/"from"/"x", or as the
// whole name — never as a word in the middle of a title ("God of War").
// Returns the match offset in `rawName`, or -1.
function aliasCreditIndex(rawName, phrase) {
	const toks = phrase.split(" ").map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
	const re = new RegExp("(?<![A-Za-z0-9])" + toks.join("[^A-Za-z0-9]+") + "(?![A-Za-z0-9])", "i");
	const m = re.exec(rawName);
	if (!m) return -1;
	const pre = rawName.slice(0, m.index).replace(/\s+$/, "");
	const post = rawName.slice(m.index + m[0].length).replace(/^\s+/, "");
	const sepBefore = /[-~\u2022|\/&,+:]$/.test(pre) || /\b(by|from|x)$/i.test(pre);
	const endsHere = post === "" || /^[-~\u2022|\/&,+:(\[]/.test(post) || /^\d/.test(post) || /^(and|x)\b/i.test(post);
	if (pre === "") return endsHere ? m.index : -1;
	return sepBefore ? m.index : -1;
}

// Looks for credited artists across ALL of a piece's map names (title and
// artist are often on different maps of one merged piece). Returns
// { artists: [...], phrases: [...] } — phrases are the normalized word
// sequences that matched, so the title cleaner can remove them.
//  - Anything right after "by"/"from", any alias in credit position, and
//    "/shop <name>" is a strong signal: all such matches are credited (max
//    MAPART_MAX_ARTISTS).
//  - Otherwise only the single longest plain username match is used ("Sunset"
//    the word shouldn't credit a user called Sunset when a real "by Steve" exists).
//  - A word that's a slightly shortened known username (map names get cut off)
//    counts too, e.g. "Emmythegamer45" -> Emmythegamer453.
function findMapartArtists(names, knownNames) {
	const list = (Array.isArray(names) ? names : [names]).filter(Boolean);
	const raw = list.join(" \n ");
	const norm = mapartMatchNorm(raw);
	const matches = [];
	const add = (phrase, artists, kind, idxOverride) => {
		if (!phrase) return;
		const idx = idxOverride !== undefined ? idxOverride : norm.indexOf(" " + phrase + " ");
		if (idx === -1) return;
		const afterBy = idxOverride !== undefined ? true : /\b(by|from)\s$/.test(norm.slice(0, idx + 1));
		// A plain username written in credit position ("Title - A & B",
		// "Title | A") is as good a credit as an alias.
		const inCreditPosition = kind === "plain" && idxOverride === undefined && list.some((n) => aliasCreditIndex(n, phrase) !== -1);
		matches.push({ phrase, artists, idx, kind, strong: kind !== "plain" || afterBy || inCreditPosition });
	};

	let offset = 0;
	for (const name of list) {
		for (const a of MAPART_ALIASES) {
			for (const p of a.phrases) {
				const phrase = mapartMatchNorm(p).trim();
				const at = aliasCreditIndex(name, phrase);
				if (at !== -1 && !matches.some((m) => m.kind === "alias" && m.phrase === phrase)) add(phrase, a.artists, "alias", offset + at);
			}
		}
		offset += name.length + 3;
	}
	const shop = /\/shop\s+([A-Za-z0-9_.]{3,16})/i.exec(raw);
	if (shop) add(mapartMatchNorm(shop[1]).trim(), [shop[1].replace(/^\./, "")], "shop");

	const tokens = new Set(norm.trim().split(" "));
	const singleTokenNames = [];
	for (const name of knownNames) {
		const p = mapartMatchNorm(name).trim();
		if (p.length < 3) continue;
		add(p, [name], "plain");
		if (!p.includes(" ")) singleTokenNames.push({ p, name });
	}
	for (const t of tokens) {
		if (t.length < 7) continue;
		const owner = singleTokenNames.find((n) => n.p.startsWith(t) && n.p !== t && n.p.length - t.length <= 3);
		if (owner && !matches.some((m) => m.phrase === t)) add(t, [owner.name], "plain");
	}
	if (!matches.length) return { artists: [], phrases: [] };

	let chosen = matches.filter((m) => m.strong);
	if (chosen.length === 0) {
		chosen = [matches.reduce((best, m) => (m.phrase.length > best.phrase.length ? m : best))];
	}
	chosen.sort((a, b) => a.idx - b.idx);
	// Real usernames have a real casing in shop/account data — prefer it.
	const canonical = new Map(knownNames.map((n) => [n.toLowerCase(), n]));
	const artists = [];
	for (const m of chosen) {
		for (const a of m.artists) {
			const name = canonical.get(a.toLowerCase()) || a;
			if (!artists.some((x) => x.toLowerCase() === name.toLowerCase())) artists.push(name);
		}
	}
	return { artists: artists.slice(0, MAPART_MAX_ARTISTS), phrases: chosen.map((m) => m.phrase) };
}

// "A & B" joined, trimmed to the 40-character artist field.
function joinMapartArtists(artists) {
	const kept = [];
	for (const a of artists) {
		if ([...kept, a].join(" & ").length <= 40) kept.push(a);
	}
	return kept.length ? kept.join(" & ") : null;
}

function splitMapartArtists(artist) {
	return String(artist || "").split(" & ").map((s) => s.trim().replace(/^\./, "")).filter(Boolean);
}

// Collabs: a piece can list several artists, stored as "Head & Second & Third"
// (the first one is the head artist — the one shown on the catalog until the
// list is expanded). Accepts that string or an array of names; returns
// {value} (null when empty) or {error}.
const MAPART_MAX_COLLAB_ARTISTS = 8;
const MAPART_MAX_ARTIST_NAME = 40;
function cleanMapartArtist(input) {
	const raw = Array.isArray(input) ? input : String(input == null ? "" : input).split("&");
	const names = [], seen = new Set();
	for (const r of raw) {
		const n = String(r || "").replace(/&/g, " ").replace(/\s+/g, " ").trim();
		if (!n) continue;
		if (n.length > MAPART_MAX_ARTIST_NAME) return { error: `Each artist name must be at most ${MAPART_MAX_ARTIST_NAME} characters` };
		if (seen.has(n.toLowerCase())) continue;
		seen.add(n.toLowerCase());
		names.push(n);
	}
	if (names.length > MAPART_MAX_COLLAB_ARTISTS) return { error: `A piece can list at most ${MAPART_MAX_COLLAB_ARTISTS} artists` };
	return { value: names.length ? names.join(" & ") : null };
}

// "Was this a commission?": when on, artist = built by, commissionedBy = who it was built for.
function cleanCommission(on, by) {
	if (!on) return { commissioned: 0, commissionedBy: null };
	const name = String(by == null ? "" : by).replace(/&/g, " ").replace(/\s+/g, " ").trim().replace(/^\./, "");
	if (name.length > MAPART_MAX_ARTIST_NAME) return { error: `The commissioner's name must be at most ${MAPART_MAX_ARTIST_NAME} characters` };
	return { commissioned: 1, commissionedBy: name || null };
}

// Optional free-text price ("5 diamonds"); blank clears it.
function cleanMapartPrice(input) {
	const p = String(input == null ? "" : input).trim();
	if (p.length > 60) return { error: "price must be at most 60 characters" };
	return { value: p || null };
}

// One name's title: symbols dropped, credited names removed, and every
// number gone (part markers like 1x1 / 1/6 / 0_0, ordinals, and stray
// digits inside words).
function cleanMapartTitle(rawName, phrases) {
	let t = String(rawName || "").replace(/§./g, "");
	t = t.replace(/\/(shop|pw)\b/gi, " ");
	t = t.replace(/['‘’´`]/g, "");
	let tokens = t.split(/[^\p{L}\p{N}]+/u).filter(Boolean);

	for (const phrase of phrases) {
		const want = phrase.split(" ");
		for (let i = 0; i + want.length <= tokens.length; ) {
			if (want.every((w, k) => mapartMatchNorm(tokens[i + k]).trim() === w)) tokens.splice(i, want.length);
			else i++;
		}
	}
	tokens = tokens.filter((tok) => !/^\d+$/.test(tok) && !/^\d+[x×]\d+$/i.test(tok) && !/^\d+(st|nd|rd|th)$/i.test(tok));
	tokens = tokens.map((tok) => tok.replace(/\d+/g, "")).filter(Boolean);
	let out = tokens.join(" ");
	out = out.replace(/\s+(made\s+by|created\s+by|art\s+by|by|from)$/i, "").trim();
	return out.slice(0, 100);
}

// Title = the most informative cleaned name among the piece's maps (the
// longest one), so a map that only carries "Earth & Charm" doesn't blank it.
function deriveMapartTitle(names, phrases) {
	let best = "";
	for (const n of Array.isArray(names) ? names : [names]) {
		const c = cleanMapartTitle(n, phrases);
		if (c.length > best.length) best = c;
	}
	return best || "Untitled mapart";
}

// Artist + title for a piece from all of its map names.
function deriveMapartFields(names, knownNames) {
	const { artists, phrases } = findMapartArtists(names, knownNames);
	return { artist: joinMapartArtists(artists), title: deriveMapartTitle(names, phrases) };
}

function resolveMapartWhereToBuy(m) {
	if (m.notForSale) return null;
	if (m.whereToBuy) return m.whereToBuy;
	const names = splitMapartArtists(m.artist);
	return names.length ? names.map((n) => `/shop ${n}`).join(" or ") : null;
}


function mapartPublic(m) {
	return {
		id: m.id, slug: m.slug, title: m.title, artist: m.artist || null,
		whereToBuy: resolveMapartWhereToBuy(m), notForSale: !!m.notForSale, price: m.price || null, commissioned: !!m.commissioned, commissionedBy: m.commissionedBy || null,
		category: m.category || null, world: m.world, width: m.width, height: m.height,
		imageHash: m.imageHash || null, claimed: !!m.claimedByAccountId,
		// "Verified by artist" shows when the piece's owner is a currently
		// verified account (claimantVerified, joined in by the list queries), or
		// when the owner has really engaged with it: claimed it themselves (or an
		// admin assigned it) or set something on it. A bare name-match auto-claim
		// by an unverified account isn't enough.
		verified: !!m.claimedByAccountId && !!(m.claimantVerified || m.claimedManually || m.ownerEdited || m.category || m.whereToBuy || m.notForSale),
		updatedAt: m.updatedAt, lastSeen: m.lastSeen,
	};
}

function mapartForOwner(m) {
	return { ...mapartPublic(m), whereToBuyCustom: m.whereToBuy || "", claimedAt: m.claimedAt || null, uploaded: !!m.uploadedByAccountId };
}

async function deleteMapartRow(env, id) {
	await env.DB.batch([
		env.DB.prepare("DELETE FROM mapartParts WHERE mapartId = ?").bind(id),
		env.DB.prepare("DELETE FROM mapartSlugs WHERE mapartId = ?").bind(id),
		env.DB.prepare("DELETE FROM maparts WHERE id = ?").bind(id),
	]);
	try { await env.SNAPSHOTS.delete(`mapart/${id}.png`); } catch (e) { /* image already gone */ }
}

// A verified account owns every not-yet-claimed mapart whose decoded
// artist is its MC username (unless the previous owner explicitly abandoned
// it — autoClaimBlocked). Only ever runs for verified accounts.
async function mapartAutoClaimSweep(env, accountId, mcUsername) {
	if (!mcUsername) return 0;
	const res = await env.DB.prepare(
		"UPDATE maparts SET claimedByAccountId = ?, claimedAt = ? WHERE claimedByAccountId IS NULL AND autoClaimBlocked = 0 AND artist IS NOT NULL AND instr(' & ' || lower(artist) || ' & ', ' & ' || lower(ltrim(?, '.')) || ' & ') > 0"
	).bind(accountId, new Date().toISOString(), mcUsername).run();
	return res.meta.changes;
}

// Shown wherever an unverified account hits something verified-only.
const VERIFY_DISCLAIMER = "If you would like to verify your account, please dm maxolotled on discord! (@ectf)";

async function requireVerifiedAccount(request, env) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base;
	if (!base.admin.mcVerified || !base.admin.mcUsername) {
		return { ok: false, response: json({ error: VERIFY_DISCLAIMER }, 403) };
	}
	return base;
}

async function findMapartOverlaps(env, world, partIds) {
	const ids = new Set();
	for (const chunk of chunkArray(partIds, MAX_QUERY_PARAMS_PER_CHUNK - 1)) {
		const placeholders = chunk.map(() => "?").join(",");
		const { results } = await env.DB.prepare(
			`SELECT DISTINCT mapartId FROM mapartParts WHERE world = ? AND mapId IN (${placeholders})`
		).bind(world, ...chunk).all();
		for (const r of results) ids.add(r.mapartId);
	}
	return [...ids];
}

function readPngSize(bytes) {
	const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
	if (bytes.length < 24) return null;
	for (let i = 0; i < 8; i++) if (bytes[i] !== sig[i]) return null;
	const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
	return { width: dv.getUint32(16), height: dv.getUint32(20) };
}

async function sha256Hex16(bytes) {
	const digest = await crypto.subtle.digest("SHA-256", bytes);
	return bufToHex(digest).slice(0, 16);
}

// POST /mapart/upload — from the (temporary) mapart scanner mod; gated by
// the same shared API_KEY every other mod upload uses. Each entry is one
// complete rectangle of item frames, already stitched into one PNG.
// Kill switch: flip to false to let the mod's mapart scanner upload again.
// Turned off after it started uploading bad/unwanted data — see the mod's own
// mapart scanner code before re-enabling.
const MAPART_UPLOADS_ENABLED = false;

async function handleUploadMapart(request, env) {
	if (!MAPART_UPLOADS_ENABLED) return json({ error: "Mapart uploads from the mod are temporarily disabled." }, 503);
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const world = String(body.world || "");
	if (!MAPART_WORLDS.includes(world)) return json({ error: "world must be Firefly or Honeybee" }, 400);
	const maps = Array.isArray(body.maps) ? body.maps : [];
	if (maps.length === 0 || maps.length > MAPART_MAX_GROUPS_PER_UPLOAD) {
		return json({ error: `maps must contain 1-${MAPART_MAX_GROUPS_PER_UPLOAD} entries` }, 400);
	}

	const knownNames = await getMapartKnownNames(env);
	const results = [];
	for (const g of maps) {
		try {
			results.push(await processMapartGroup(env, world, g, knownNames));
		} catch (e) {
			results.push({ leadMapId: g && g.leadMapId, status: "error", error: String(e) });
		}
	}
	return json({ ok: true, results });
}

async function processMapartGroup(env, world, g, knownNames) {
	const leadMapId = g.leadMapId;
	const width = g.width, height = g.height;
	if (!Number.isInteger(leadMapId) || !Number.isInteger(width) || !Number.isInteger(height)
		|| width < 1 || height < 1 || width > MAPART_MAX_GRID || height > MAPART_MAX_GRID) {
		return { leadMapId, status: "skipped", reason: "bad dimensions" };
	}
	const partIds = [...new Set((Array.isArray(g.partMapIds) ? g.partMapIds : []).filter(Number.isInteger))];
	if (partIds.length !== width * height || !partIds.includes(leadMapId)) {
		return { leadMapId, status: "skipped", reason: "partMapIds must cover the full rectangle and include the lead map" };
	}
	// Legacy section-sign colour/format codes can survive in a custom name.
	const rawName = String(g.rawName || "").replace(/§./g, "").slice(0, 200);
	// Every named map in the piece (title and artist can be on different ones).
	const allNames = [];
	for (const n of [rawName, ...(Array.isArray(g.allNames) ? g.allNames : [])]) {
		const c = String(n || "").replace(/§./g, "").trim().slice(0, 200);
		if (c && !allNames.includes(c)) allNames.push(c);
		if (allNames.length >= 20) break;
	}
	const allNamesJson = JSON.stringify(allNames);

	let pngBytes;
	try { pngBytes = Uint8Array.from(atob(String(g.png || "")), (c) => c.charCodeAt(0)); } catch (e) {
		return { leadMapId, status: "skipped", reason: "png isn't valid base64" };
	}
	if (pngBytes.length > MAPART_MAX_PNG_BYTES) return { leadMapId, status: "skipped", reason: "png too large" };
	const size = readPngSize(pngBytes);
	if (!size || size.width !== width * 128 || size.height !== height * 128) {
		return { leadMapId, status: "skipped", reason: "png missing or wrong size for the grid" };
	}

	const blocked = await env.DB.prepare("SELECT 1 AS x FROM mapartBlocked WHERE world = ? AND leadMapId = ?").bind(world, leadMapId).first();
	if (blocked) return { leadMapId, status: "skipped", reason: "blocked by an admin" };
	if (await mapartAnyPartBlocked(env, world, partIds)) return { leadMapId, status: "skipped", reason: "removed at its owner's request" };
	// An admin split these apart (see handleAdminSplitMapart) — don't stitch them back together.
	if (await mapartSplitConflict(env, world, partIds)) return { leadMapId, status: "skipped", reason: "was split into separate pieces by an admin" };

	const now = new Date().toISOString();
	const partSet = new Set(partIds);
	const existingLead = await env.DB.prepare("SELECT * FROM maparts WHERE world = ? AND leadMapId = ?").bind(world, leadMapId).first();

	// Work out how this rectangle relates to whatever's already stored.
	const merges = [];
	for (const otherId of await findMapartOverlaps(env, world, partIds)) {
		if (existingLead && otherId === existingLead.id) continue;
		const other = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(otherId).first();
		if (!other) continue;
		const { results: otherParts } = await env.DB.prepare("SELECT mapId FROM mapartParts WHERE mapartId = ?").bind(otherId).all();
		if (otherParts.every((p) => partSet.has(p.mapId))) merges.push(other);
		else return { leadMapId, status: "skipped", reason: "overlaps a different, larger mapart" };
	}
	if (existingLead) {
		const { results: oldParts } = await env.DB.prepare("SELECT mapId FROM mapartParts WHERE mapartId = ?").bind(existingLead.id).all();
		if (!oldParts.every((p) => partSet.has(p.mapId))) {
			return { leadMapId, status: "skipped", reason: "already stored as a larger mapart" };
		}
	}

	const imageHash = await sha256Hex16(pngBytes);
	if (await env.DB.prepare("SELECT 1 AS x FROM mapartBlockedImages WHERE imageHash = ?").bind(imageHash).first()) {
		return { leadMapId, status: "skipped", reason: "removed at its owner's request" };
	}

	// Same picture, same names, same shape as what's stored (the scanner
	// re-sends everything nearby each time it's launched): just note it was
	// seen — no image rewrite, no parts rewrite, no re-derivation.
	if (existingLead && merges.length === 0 && existingLead.imageHash === imageHash
		&& existingLead.width === width && existingLead.height === height && existingLead.allNames === allNamesJson) {
		await env.DB.prepare("UPDATE maparts SET lastSeen = ? WHERE id = ?").bind(now, existingLead.id).run();
		return { leadMapId, status: "unchanged", id: existingLead.id, slug: existingLead.slug, mergedParts: 0 };
	}

	let id, status;
	if (existingLead) {
		id = existingLead.id;
		status = "updated";
		const freeToRederive = !existingLead.locked && !existingLead.claimedByAccountId;
		const derived = freeToRederive ? deriveMapartFields(allNames, knownNames) : null;
		const artist = derived ? derived.artist : existingLead.artist;
		const title = derived ? derived.title : existingLead.title;
		const slug = title !== existingLead.title ? await assignMapartSlug(env, id, title) : existingLead.slug;
		await env.DB.prepare(
			"UPDATE maparts SET rawName = ?, allNames = ?, title = ?, artist = ?, slug = ?, width = ?, height = ?, imageHash = ?, updatedAt = ?, lastSeen = ? WHERE id = ?"
		).bind(rawName, allNamesJson, title, artist, slug, width, height, imageHash, now, now, id).run();
	} else {
		id = crypto.randomUUID();
		status = "created";
		const { artist, title } = deriveMapartFields(allNames, knownNames);
		const slug = await assignMapartSlug(env, id, title);
		await env.DB.prepare(
			"INSERT INTO maparts (id, slug, world, leadMapId, rawName, allNames, title, artist, width, height, imageHash, createdAt, updatedAt, lastSeen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
		).bind(id, slug, world, leadMapId, rawName, allNamesJson, title, artist, width, height, imageHash, now, now, now).run();
	}

	// Earlier scans that caught only part of this piece get folded in — their
	// claim/edits carry over to the (still unclaimed / unedited) leading entry.
	for (const other of merges) {
		const cur = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(id).first();
		if (!cur.claimedByAccountId && other.claimedByAccountId) {
			await env.DB.prepare(
				"UPDATE maparts SET claimedByAccountId = ?, claimedAt = ?, category = COALESCE(category, ?), whereToBuy = COALESCE(whereToBuy, ?), notForSale = MAX(notForSale, ?), autoClaimBlocked = ? WHERE id = ?"
			).bind(other.claimedByAccountId, other.claimedAt, other.category, other.whereToBuy, other.notForSale, other.autoClaimBlocked, id).run();
			if (other.locked) {
				const slug = other.title !== cur.title ? await assignMapartSlug(env, id, other.title) : cur.slug;
				await env.DB.prepare("UPDATE maparts SET title = ?, artist = ?, slug = ?, locked = 1 WHERE id = ?").bind(other.title, other.artist, slug, id).run();
			}
		}
		await env.DB.prepare("UPDATE mapartSlugs SET mapartId = ? WHERE mapartId = ?").bind(id, other.id).run();
		await deleteMapartRow(env, other.id);
	}

	await env.DB.batch([
		env.DB.prepare("DELETE FROM mapartParts WHERE mapartId = ?").bind(id),
		...partIds.map((mapId) => env.DB.prepare("INSERT OR REPLACE INTO mapartParts (world, mapId, mapartId) VALUES (?, ?, ?)").bind(world, mapId, id)),
	]);
	await env.SNAPSHOTS.put(`mapart/${id}.png`, pngBytes, { httpMetadata: { contentType: "image/png" } });
	const scanHash = await phashOfPng(pngBytes, PHASH_MAX_INLINE_PIXELS);
	if (scanHash) await env.DB.prepare("UPDATE maparts SET phash = ? WHERE id = ?").bind(scanHash, id).run();

	// Auto-claim for a verified account whose MC username is the artist.
	const fresh = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(id).first();
	if (!fresh.claimedByAccountId && !fresh.autoClaimBlocked && fresh.artist) {
		for (const name of splitMapartArtists(fresh.artist)) {
			const acct = await env.DB.prepare(
				"SELECT id FROM admins WHERE mcVerified = 1 AND lower(ltrim(mcUsername, '.')) = lower(?)"
			).bind(name).first();
			if (acct) {
				await env.DB.prepare("UPDATE maparts SET claimedByAccountId = ?, claimedAt = ? WHERE id = ?").bind(acct.id, now, id).run();
				break;
			}
		}
	}
	return { leadMapId, status: merges.length ? "merged" : status, id, slug: fresh.slug, mergedParts: merges.length };
}

async function handleGetMapart(request, env, ctx) {
	return cachedGet(request, ctx, CACHE_TTL_SECONDS, async () => {
		const { results } = await env.DB.prepare(
			"SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId ORDER BY m.title COLLATE NOCASE"
		).all();
		return results.map(mapartPublic);
	});
}

// Resolves either a current or an old slug.
async function handleGetMapartBySlug(request, env, ctx) {
	return cachedGet(request, ctx, 60, async () => {
		const slug = (new URL(request.url).searchParams.get("slug") || "").toLowerCase();
		const link = await env.DB.prepare("SELECT mapartId FROM mapartSlugs WHERE slug = ?").bind(slug).first();
		if (!link) return { error: "Not found" };
		const m = await env.DB.prepare(
			"SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId WHERE m.id = ?"
		).bind(link.mapartId).first();
		return m ? mapartPublic(m) : { error: "Not found" };
	});
}

async function handleGetMapartImage(request, env) {
	const id = new URL(request.url).searchParams.get("id") || "";
	if (!/^[0-9a-f-]{36}$/.test(id)) return json({ error: "Bad id" }, 400);
	const obj = await env.SNAPSHOTS.get(`mapart/${id}.png`);
	if (!obj) return json({ error: "Not found" }, 404);
	return new Response(obj.body, {
		headers: { "Content-Type": "image/png", "Cache-Control": "public, max-age=86400", ...corsHeaders() },
	});
}

async function handleGetMyMapart(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM maparts WHERE claimedByAccountId = ? ORDER BY title COLLATE NOCASE").bind(auth.admin.id).all();
	const pending = await env.DB.prepare("SELECT mapartId FROM mapartTakedowns WHERE accountId = ? AND status = 'pending'").bind(auth.admin.id).all();
	const pendingIds = new Set(pending.results.map((r) => r.mapartId));
	return json(results.map((m) => ({ ...mapartForOwner(m), takedownPending: pendingIds.has(m.id) })));
}

async function handleClaimMapart(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	if (m.claimedByAccountId && m.claimedByAccountId !== auth.admin.id) {
		return json({ error: "This mapart is already claimed by someone else — ask a head admin if that's wrong." }, 403);
	}
	await env.DB.prepare("UPDATE maparts SET claimedByAccountId = ?, claimedAt = ?, autoClaimBlocked = 0, claimedManually = 1 WHERE id = ?")
		.bind(auth.admin.id, new Date().toISOString(), m.id).run();
	return json({ ok: true });
}

async function handleAbandonMapart(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const res = await env.DB.prepare(
		"UPDATE maparts SET claimedByAccountId = NULL, claimedAt = NULL, autoClaimBlocked = 1, claimedManually = 0, ownerEdited = 0 WHERE id = ? AND claimedByAccountId = ?"
	).bind(String(body.id || ""), auth.admin.id).run();
	if (res.meta.changes === 0) return json({ error: "You don't own that mapart" }, 404);
	return json({ ok: true });
}

// Owner (verified account that has claimed it) or any head admin.
async function handleUpdateMapart(request, env) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	const owns = base.admin.mcVerified && m.claimedByAccountId === base.admin.id;
	const isHead = !!base.admin.isHeadAdmin;
	// "manageMapart" holders fix what mapart reports are about (artist, world,
	// category) directly, without going through the report queue.
	const canManage = isHead || adminHasPermission(base.admin, "manageMapart");
	if (!isHead && !owns && !canManage) return json({ error: "Claim this mapart first to edit it." }, 403);
	if (!isHead && !owns && (body.title !== undefined || body.whereToBuy !== undefined || body.notForSale !== undefined || body.price !== undefined || body.commissioned !== undefined || body.commissionedBy !== undefined)) {
		return json({ error: "Mapart managers can only change the artist, world and category." }, 403);
	}
	if (body.world !== undefined && !canManage) return json({ error: "Only mapart managers can move a mapart to another world." }, 403);

	const sets = [], vals = [];
	let newTitle = null;
	if (body.world !== undefined && String(body.world) !== m.world) {
		const moved = await moveMapartToWorld(env, m, String(body.world));
		if (!moved.ok) return json({ error: moved.error }, moved.status);
		if (Object.keys(body).every((k) => k === "id" || k === "world")) {
			const fresh = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(m.id).first();
			return json({ ok: true, mapart: mapartForOwner(fresh) });
		}
	}
	if (body.title !== undefined) {
		const t = String(body.title).trim();
		if (!t || t.length > 100) return json({ error: "title must be 1-100 characters" }, 400);
		sets.push("title = ?"); vals.push(t); newTitle = t;
	}
	if (body.artist !== undefined) {
		const a = cleanMapartArtist(body.artist);
		if (a.error) return json({ error: a.error }, 400);
		sets.push("artist = ?"); vals.push(a.value);
	}
	if (body.price !== undefined) {
		const p = cleanMapartPrice(body.price);
		if (p.error) return json({ error: p.error }, 400);
		sets.push("price = ?"); vals.push(p.value);
	}
	if (body.commissioned !== undefined || body.commissionedBy !== undefined) {
		const cm = cleanCommission(body.commissioned === undefined ? !!m.commissioned : body.commissioned, body.commissionedBy === undefined ? m.commissionedBy : body.commissionedBy);
		if (cm.error) return json({ error: cm.error }, 400);
		sets.push("commissioned = ?"); vals.push(cm.commissioned);
		sets.push("commissionedBy = ?"); vals.push(cm.commissionedBy);
	}
	if (body.whereToBuy !== undefined) {
		const w = String(body.whereToBuy || "").trim();
		if (w.length > 200) return json({ error: "whereToBuy must be at most 200 characters" }, 400);
		sets.push("whereToBuy = ?"); vals.push(w || null);
	}
	if (body.notForSale !== undefined) { sets.push("notForSale = ?"); vals.push(body.notForSale ? 1 : 0); }
	if (body.category !== undefined) {
		const c = body.category ? String(body.category) : null;
		if (c !== null && !MAPART_CATEGORIES.includes(c)) return json({ error: "Unknown category" }, 400);
		sets.push("category = ?"); vals.push(c);
	}
	if (sets.length === 0) return json({ error: "Nothing to update" }, 400);
	if (body.title !== undefined || body.artist !== undefined) sets.push("locked = 1");
	if (newTitle !== null && newTitle !== m.title) {
		sets.push("slug = ?"); vals.push(await assignMapartSlug(env, m.id, newTitle));
	}
	if (owns) sets.push("ownerEdited = 1");
	sets.push("updatedAt = ?"); vals.push(new Date().toISOString());
	await env.DB.prepare(`UPDATE maparts SET ${sets.join(", ")} WHERE id = ?`).bind(...vals, m.id).run();
	const fresh = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(m.id).first();
	return json({ ok: true, mapart: mapartForOwner(fresh) });
}

// ---------------- mapart reports + direct edits ----------------
// Public reports about a catalogued mapart land in the same `reports` table
// as listing reports (listingKey "mapart:<id>", listingJson = a snapshot so
// the queue still reads fine after the piece is edited or deleted). Only
// "manageMapart" holders see and resolve them.
const MAPART_REPORT_REASONS = new Set(["wrong_artist", "wrong_world", "wrong_category", "inappropriate_image"]);
const MAPART_REPORT_EDIT_FIELDS = new Set(["artist", "world", "category"]);

function isMapartReportKey(key) {
	return String(key || "").startsWith("mapart:");
}

function adminHasPermission(admin, permission) {
	if (admin.isHeadAdmin) return true;
	try { return JSON.parse(admin.permissions || "[]").includes(permission); } catch (e) { return false; }
}

// Moves a piece to the other world. The (world, leadMapId) uniqueness and the
// per-map (world, mapId) parts table both have to stay clear of collisions.
async function moveMapartToWorld(env, m, world) {
	if (!MAPART_WORLDS.includes(world)) return { ok: false, status: 400, error: "world must be Firefly or Honeybee" };
	if (world === m.world) return { ok: true };
	const clash = await env.DB.prepare("SELECT slug FROM maparts WHERE world = ? AND leadMapId = ? AND id != ?").bind(world, m.leadMapId, m.id).first();
	const partClash = await env.DB.prepare(
		"SELECT p.mapId FROM mapartParts p WHERE p.world = ? AND p.mapId IN (SELECT mapId FROM mapartParts WHERE mapartId = ?) AND p.mapartId != ? LIMIT 1"
	).bind(world, m.id, m.id).first();
	if (clash || partClash) {
		return { ok: false, status: 409, error: `${world} already has a mapart with the same map${clash ? ` (${clash.slug})` : ""} — delete or merge that one first.` };
	}
	await env.DB.batch([
		env.DB.prepare("UPDATE maparts SET world = ?, updatedAt = ? WHERE id = ?").bind(world, new Date().toISOString(), m.id),
		env.DB.prepare("UPDATE mapartParts SET world = ? WHERE mapartId = ?").bind(world, m.id),
	]);
	return { ok: true };
}

// POST /mapart/report — anyone (same shared site key as POST /reports).
// body: {id (mapart id), reason, details?}. One open report per piece + reason.
async function handleSubmitMapartReport(request, env) {
	if (!isAuthorized(request, env.API_KEY)) return json({ error: "Unauthorized" }, 401);
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const reason = String(body.reason || "").trim();
	if (!MAPART_REPORT_REASONS.has(reason)) return json({ error: "Invalid reason" }, 400);
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	const listingKey = "mapart:" + m.id;
	const dup = await env.DB.prepare("SELECT id FROM reports WHERE listingKey = ? AND reason = ? AND status = 'pending'").bind(listingKey, reason).first();
	if (dup) return json({ ok: true, id: dup.id, alreadyReported: true });
	const snapshot = {
		mapart: true, id: m.id, slug: m.slug, itemName: m.title, seller: m.artist || "Unknown artist",
		world: m.world, category: m.category || null, width: m.width, height: m.height,
	};
	const id = crypto.randomUUID();
	await env.DB.prepare(
		"INSERT INTO reports (id, listingKey, listingJson, reason, details, status, createdAt, resolvedAt) VALUES (?, ?, ?, ?, ?, 'pending', ?, NULL)"
	).bind(id, listingKey, JSON.stringify(snapshot), reason, String(body.details || "").slice(0, 500), new Date().toISOString()).run();
	return json({ ok: true, id });
}

// Resolves a mapart report for handleResolveReport (permission already checked).
//   deny    -> dismissed
//   approve -> the piece is deleted (and blocked from re-scans, like the admin delete)
//   edit    -> body.field (artist|world|category) is set to body.value, no other change
async function resolveMapartReport(env, report, action, body, admin) {
	const mapartId = report.listingKey.slice("mapart:".length);
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(mapartId).first();
	const now = new Date().toISOString();
	let changed = false;
	if (action !== "deny") {
		if (!m) return json({ error: "That mapart no longer exists — dismiss the report." }, 404);
		if (action === "approve") {
			await env.DB.prepare("INSERT OR REPLACE INTO mapartBlocked (world, leadMapId, blockedAt) VALUES (?, ?, ?)").bind(m.world, m.leadMapId, now).run();
			await deleteMapartRow(env, m.id);
			changed = true;
		} else {
			const res = await editMapartField(env, m, String(body.field), body.value);
			if (!res.ok) return json({ error: res.error }, res.status);
			changed = true;
		}
	}
	const newStatus = action === "edit" ? "edited" : action === "approve" ? "approved" : "denied";
	await env.DB.prepare("UPDATE reports SET status = ?, resolvedAt = ? WHERE id = ?").bind(newStatus, now, report.id).run();
	return json({ ok: true, listingChanged: changed, resolvedBy: admin ? admin.username : null });
}

// Single-field edit shared by the report queue (same rules as /mapart/update).
async function editMapartField(env, m, field, value) {
	if (field === "world") return moveMapartToWorld(env, m, String(value || ""));
	const now = new Date().toISOString();
	if (field === "artist") {
		const a = cleanMapartArtist(value);
		if (a.error) return { ok: false, status: 400, error: a.error };
		await env.DB.prepare("UPDATE maparts SET artist = ?, locked = 1, updatedAt = ? WHERE id = ?").bind(a.value, now, m.id).run();
		return { ok: true };
	}
	if (field === "category") {
		const c = value ? String(value) : null;
		if (c !== null && !MAPART_CATEGORIES.includes(c)) return { ok: false, status: 400, error: "Unknown category (" + MAPART_CATEGORIES.join(", ") + ")" };
		await env.DB.prepare("UPDATE maparts SET category = ?, updatedAt = ? WHERE id = ?").bind(c, now, m.id).run();
		return { ok: true };
	}
	return { ok: false, status: 400, error: "Invalid field" };
}

async function handleAdminListMapart(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare(
		"SELECT m.*, a.username AS claimedByUsername FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId ORDER BY m.title COLLATE NOCASE"
	).all();
	return json(results.map((m) => ({ ...mapartForOwner(m), claimedByUsername: m.claimedByUsername || null, rawName: m.rawName || "", leadMapId: m.leadMapId })));
}

// Deleting also blocks the piece from being re-created by later scans.
async function handleAdminDeleteMapart(request, env) {
	const auth = await requireAdminAuth(request, env, "manageMapart");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	await env.DB.prepare("INSERT OR REPLACE INTO mapartBlocked (world, leadMapId, blockedAt) VALUES (?, ?, ?)")
		.bind(m.world, m.leadMapId, new Date().toISOString()).run();
	await deleteMapartRow(env, m.id);
	return json({ ok: true });
}

// body: {id, username} — username null/"" clears the claim.
async function handleAdminAssignMapart(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT id FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	const username = String(body.username || "").trim();
	if (!username) {
		await env.DB.prepare("UPDATE maparts SET claimedByAccountId = NULL, claimedAt = NULL, claimedManually = 0, ownerEdited = 0 WHERE id = ?").bind(m.id).run();
		return json({ ok: true });
	}
	const acct = await env.DB.prepare("SELECT id FROM admins WHERE lower(username) = lower(?)").bind(username).first();
	if (!acct) return json({ error: "No account with that username" }, 404);
	await env.DB.prepare("UPDATE maparts SET claimedByAccountId = ?, claimedAt = ?, autoClaimBlocked = 0, claimedManually = 1 WHERE id = ?")
		.bind(acct.id, new Date().toISOString(), m.id).run();
	return json({ ok: true });
}

// Re-runs artist/title detection over every mapart nobody has claimed or
// hand-edited — used after the alias list or known usernames change. Uses
// the stored per-piece names, so no rescan is needed. In-memory slug
// assignment + batched writes keep it well under the subrequest limit.
async function handleAdminRederiveMapart(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const knownNames = await getMapartKnownNames(env, true);
	const { results: rows } = await env.DB.prepare("SELECT * FROM maparts WHERE locked = 0 AND claimedByAccountId IS NULL").all();
	const { results: slugRows } = await env.DB.prepare("SELECT slug, mapartId FROM mapartSlugs").all();
	const slugOwner = new Map(slugRows.map((r) => [r.slug, r.mapartId]));
	const { results: verified } = await env.DB.prepare("SELECT id, mcUsername FROM admins WHERE mcVerified = 1 AND mcUsername IS NOT NULL").all();
	const accountByName = new Map(verified.map((a) => [String(a.mcUsername).replace(/^\./, "").toLowerCase(), a.id]));

	const now = new Date().toISOString();
	const stmts = [];
	let changed = 0, claimed = 0;
	for (const m of rows) {
		let names = [];
		try { names = JSON.parse(m.allNames || "[]"); } catch (e) { /* fall through */ }
		if (!names.length && m.rawName) names = [m.rawName];
		if (!names.length) continue;
		const { artist, title } = deriveMapartFields(names, knownNames);

		let slug = m.slug;
		if (title !== m.title) {
			const base = mapartSlugify(title);
			slug = null;
			for (let i = 1; i < 500 && !slug; i++) {
				const cand = i === 1 ? base : base + "-" + i;
				const owner = slugOwner.get(cand);
				if (!owner || owner === m.id) slug = cand;
			}
			if (!slug) slug = base + "-" + m.id.slice(0, 8);
			slugOwner.set(slug, m.id);
			stmts.push(env.DB.prepare("INSERT OR IGNORE INTO mapartSlugs (slug, mapartId) VALUES (?, ?)").bind(slug, m.id));
		}
		if (title !== m.title || artist !== m.artist) {
			changed++;
			stmts.push(env.DB.prepare("UPDATE maparts SET title = ?, artist = ?, slug = ?, updatedAt = ? WHERE id = ?").bind(title, artist, slug, now, m.id));
		}
		if (artist && !m.autoClaimBlocked) {
			for (const name of splitMapartArtists(artist)) {
				const accountId = accountByName.get(name.toLowerCase());
				if (accountId) {
					stmts.push(env.DB.prepare("UPDATE maparts SET claimedByAccountId = ?, claimedAt = ? WHERE id = ? AND claimedByAccountId IS NULL").bind(accountId, now, m.id));
					claimed++;
					break;
				}
			}
		}
	}
	for (const chunk of chunkArray(stmts, 40)) await env.DB.batch(chunk);
	return json({ ok: true, examined: rows.length, changed, autoClaimed: claimed });
}

// ---------------- seller store management (verified accounts) ----------------
// A verified account manages the listings of its own MC username: manual
// listings can be added, edited and deleted; scanned ones are read-only here
// (the mod owns them and rewrites them on every scan). storeManagers can also
// delegate ALL manual listings of another seller name to an account (e.g. a
// shared plot warp) — those listings keep showing the original seller.
const MAX_STORE_MANUAL_LISTINGS = 100;
const MAX_STORE_ENTRIES_PER_BATCH = 25;

async function requireStoreOwner(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth;
	const seller = String(auth.admin.mcUsername || "").replace(/^\./, "");
	if (!isValidUsername(seller)) {
		return { ok: false, response: json({ error: "Your linked Minecraft username can't be used for shop listings." }, 400) };
	}
	const blocked = await env.DB.prepare("SELECT 1 AS x FROM blockedSellers WHERE usernameKey = ?").bind(seller.toLowerCase()).first();
	if (blocked) return { ok: false, response: json({ error: "This seller can't manage listings." }, 403) };
	const managed = (await env.DB.prepare("SELECT sellerKey, sellerName FROM storeManagers WHERE accountId = ?").bind(auth.admin.id).all()).results;
	const key = seller.toLowerCase();
	return { ok: true, admin: auth.admin, seller, key, managed, keys: [key, ...managed.map((m) => m.sellerKey)] };
}

const STORE_OWNER_SQL = "lower(ltrim(seller, '.')) = ?";
function storeOwnersSql(keys) { return `lower(ltrim(seller, '.')) IN (${keys.map(() => "?").join(",")})`; }

async function handleStoreListings(request, env) {
	const auth = await requireStoreOwner(request, env);
	if (!auth.ok) return auth.response;
	const key = auth.seller.toLowerCase();
	const manual = await env.DB.prepare(`SELECT * FROM listings WHERE lastSeen LIKE 'M%' AND ${storeOwnersSql(auth.keys)} ORDER BY seller COLLATE NOCASE, world, itemName COLLATE NOCASE`).bind(...auth.keys).all();
	const scanned = await env.DB.prepare(
		`SELECT itemName, baseItem, bulk, bundled, price, priceLabel, stackSize, amount, stacksInStock, currency, world, position, lastSeen FROM listings WHERE lastSeen NOT LIKE 'M%' AND ${STORE_OWNER_SQL} ORDER BY world, itemName COLLATE NOCASE LIMIT 500`
	).bind(key).all();
	const total = await env.DB.prepare(`SELECT COUNT(*) AS c FROM listings WHERE lastSeen NOT LIKE 'M%' AND ${STORE_OWNER_SQL}`).bind(key).first();
	return json({
		seller: auth.seller,
		managedSellers: auth.managed.map((m) => ({ sellerName: m.sellerName })),
		manualCap: MAX_STORE_MANUAL_LISTINGS,
		manual: manual.results.map((r) => ({ id: r.lastSeen, itemName: r.itemName, price: r.price, priceLabel: r.priceLabel, currency: r.currency, world: r.world, position: r.position, seller: r.seller })),
		scanned: scanned.results.map((r) => ({ ...r, bulk: !!r.bulk, bundled: !!r.bundled })),
		scannedTotal: total.c,
	});
}

// body: {world, seller? (own name by default, or one delegated to this account), entries: [{itemName, price, currency, position, priceLabel?}]}
async function handleStoreAddListings(request, env) {
	const auth = await requireStoreOwner(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const worldRaw = String(body.world || "").trim().toLowerCase();
	const world = worldRaw === "firefly" ? "Firefly" : worldRaw === "honeybee" ? "Honeybee" : null;
	const entries = Array.isArray(body.entries) ? body.entries : [];
	if (!world) return json({ error: "World must be Firefly or Honeybee" }, 400);
	if (entries.length === 0) return json({ error: "No entries given" }, 400);
	if (entries.length > MAX_STORE_ENTRIES_PER_BATCH) return json({ error: `Add at most ${MAX_STORE_ENTRIES_PER_BATCH} listings at a time` }, 400);

	let target = auth.seller;
	const wanted = String(body.seller || "").trim();
	if (wanted && wanted.toLowerCase() !== auth.key) {
		const m = auth.managed.find((x) => x.sellerKey === wanted.toLowerCase());
		if (!m) return json({ error: "You can't manage listings for that seller." }, 403);
		target = m.sellerName;
	}

	const parsed = [];
	const seen = new Set();
	for (const e of entries) {
		const r = parseManualListingEntry(e);
		if (r.error) return json({ error: r.error }, 400);
		const k = manualListingRowKey(world, target, r.entry.itemName);
		if (seen.has(k)) return json({ error: `"${r.entry.itemName}" is in the list twice` }, 400);
		seen.add(k);
		parsed.push({ ...r.entry, rowKey: k });
	}

	const count = await env.DB.prepare(`SELECT COUNT(*) AS c FROM listings WHERE lastSeen LIKE 'M%' AND ${STORE_OWNER_SQL}`).bind(target.toLowerCase()).first();
	if (count.c + parsed.length > MAX_STORE_MANUAL_LISTINGS) {
		return json({ error: `${target} can have at most ${MAX_STORE_MANUAL_LISTINGS} manual listings (it has ${count.c}).` }, 400);
	}
	for (const e of parsed) {
		const exists = await env.DB.prepare("SELECT 1 AS x FROM listings WHERE rowKey = ?").bind(e.rowKey).first();
		if (exists) return json({ error: `You already have a manual listing for "${e.itemName}" in ${world} — edit that one instead.` }, 409);
	}

	const ids = await nextManualIds(env, parsed.length);
	await env.DB.batch(parsed.map((e, i) => env.DB.prepare(
		`INSERT INTO listings (rowKey, id, itemName, baseItem, bulk, bundled, mixedContents, price, priceLabel, stackSize, amount, stacksInStock, currency, seller, world, position, lastSeen)
		 VALUES (?, ?, ?, 'manual', 0, 0, 0, ?, ?, 1, 1, 1, ?, ?, ?, ?, ?)`
	).bind(e.rowKey, newId(), e.itemName, e.price, e.priceLabel, e.currency, target, world, e.position, ids[i])));
	return json({ ok: true, added: parsed.map((e, i) => ({ itemName: e.itemName, id: ids[i] })) });
}

// body: {id, itemName?, price?, priceLabel?, currency?, position?}
async function handleStoreUpdateListing(request, env) {
	const auth = await requireStoreOwner(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "").trim();
	if (!MANUAL_ID_PATTERN.test(id)) return json({ error: "Invalid id" }, 400);
	const row = await env.DB.prepare(`SELECT * FROM listings WHERE lastSeen = ? AND ${storeOwnersSql(auth.keys)}`).bind(id, ...auth.keys).first();
	if (!row) return json({ error: "Listing not found" }, 404);

	const merged = {
		itemName: body.itemName !== undefined ? body.itemName : row.itemName,
		price: body.price !== undefined ? body.price : row.price,
		currency: body.currency !== undefined ? body.currency : row.currency,
		position: body.position !== undefined ? body.position : row.position,
		// Keep a hand-written label; refresh an auto-generated one when price/currency change.
		priceLabel: body.priceLabel !== undefined ? body.priceLabel
			: (row.priceLabel === `${row.price} ${row.currency}` ? "" : row.priceLabel),
	};
	const r = parseManualListingEntry(merged);
	if (r.error) return json({ error: r.error }, 400);
	const e = r.entry;
	const newKey = manualListingRowKey(row.world, row.seller, e.itemName);
	if (newKey !== row.rowKey) {
		const clash = await env.DB.prepare("SELECT 1 AS x FROM listings WHERE rowKey = ?").bind(newKey).first();
		if (clash) return json({ error: `You already have a manual listing for "${e.itemName}" in ${row.world}.` }, 409);
	}
	await env.DB.prepare("UPDATE listings SET rowKey = ?, itemName = ?, price = ?, priceLabel = ?, currency = ?, position = ? WHERE lastSeen = ?")
		.bind(newKey, e.itemName, e.price, e.priceLabel, e.currency, e.position, id).run();
	return json({ ok: true, listing: { id, itemName: e.itemName, price: e.price, priceLabel: e.priceLabel, currency: e.currency, world: row.world, position: e.position, seller: row.seller } });
}

async function handleStoreDeleteListing(request, env) {
	const auth = await requireStoreOwner(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "").trim();
	if (!MANUAL_ID_PATTERN.test(id)) return json({ error: "Invalid id" }, 400);
	const res = await env.DB.prepare(`DELETE FROM listings WHERE lastSeen = ? AND ${storeOwnersSql(auth.keys)}`).bind(id, ...auth.keys).run();
	if (res.meta.changes === 0) return json({ error: "Listing not found" }, 404);
	return json({ ok: true });
}

// ---------------- takedown requests ----------------
// A verified owner can ask for a piece they claimed to be removed from SCTP for
// good. A head admin has to approve it; on approval the piece is deleted and
// every way it could come back is blocked: its lead map, all of its map ids,
// and its exact picture (so a re-scan under other ids or a portal upload of
// the same image is refused too).
async function mapartAnyPartBlocked(env, world, partIds) {
	for (const chunk of chunkArray(partIds, MAX_QUERY_PARAMS_PER_CHUNK - 1)) {
		const placeholders = chunk.map(() => "?").join(",");
		const row = await env.DB.prepare(`SELECT 1 AS x FROM mapartBlockedParts WHERE world = ? AND mapId IN (${placeholders}) LIMIT 1`).bind(world, ...chunk).first();
		if (row) return true;
	}
	return false;
}

// body: {id (mapart id), reason?}
async function handleRequestMapartTakedown(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m || m.claimedByAccountId !== auth.admin.id) return json({ error: "You can only request a takedown of mapart you've claimed." }, 403);
	const existing = await env.DB.prepare("SELECT 1 AS x FROM mapartTakedowns WHERE mapartId = ? AND status = 'pending'").bind(m.id).first();
	if (existing) return json({ error: "A takedown request for this mapart is already waiting for approval." }, 409);
	const reason = String(body.reason || "").trim().slice(0, 500);
	await env.DB.prepare(
		"INSERT INTO mapartTakedowns (id, mapartId, accountId, title, artist, world, leadMapId, imageHash, reason, status, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)"
	).bind(newId(), m.id, auth.admin.id, m.title, m.artist || null, m.world, m.leadMapId, m.imageHash || null, reason || null, new Date().toISOString()).run();
	return json({ ok: true });
}

// body: {id (mapart id)}
async function handleCancelMapartTakedown(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const res = await env.DB.prepare(
		"UPDATE mapartTakedowns SET status = 'cancelled', resolvedAt = ? WHERE mapartId = ? AND accountId = ? AND status = 'pending'"
	).bind(new Date().toISOString(), String(body.id || ""), auth.admin.id).run();
	if (res.meta.changes === 0) return json({ error: "No pending request to cancel." }, 404);
	return json({ ok: true });
}

async function handleAdminListTakedowns(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare(
		`SELECT t.*, a.username AS requestedBy, a.mcUsername AS requestedByMc FROM mapartTakedowns t LEFT JOIN admins a ON a.id = t.accountId
		 ORDER BY (t.status = 'pending') DESC, COALESCE(t.resolvedAt, t.createdAt) DESC LIMIT 150`
	).all();
	return json(results);
}

// body: {id (takedown id), action: "approve" | "deny"}
async function handleAdminResolveTakedown(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const action = String(body.action || "");
	if (action !== "approve" && action !== "deny") return json({ error: "action must be approve or deny" }, 400);
	const t = await env.DB.prepare("SELECT * FROM mapartTakedowns WHERE id = ?").bind(String(body.id || "")).first();
	if (!t) return json({ error: "Request not found" }, 404);
	if (t.status !== "pending") return json({ error: "That request was already resolved." }, 409);
	const now = new Date().toISOString();
	const who = auth.admin ? auth.admin.username : "master";

	if (action === "approve") {
		const stmts = [];
		if (t.leadMapId > 0) stmts.push(env.DB.prepare("INSERT OR REPLACE INTO mapartBlocked (world, leadMapId, blockedAt) VALUES (?, ?, ?)").bind(t.world, t.leadMapId, now));
		const { results: parts } = await env.DB.prepare("SELECT mapId FROM mapartParts WHERE mapartId = ?").bind(t.mapartId).all();
		for (const r of parts) stmts.push(env.DB.prepare("INSERT OR IGNORE INTO mapartBlockedParts (world, mapId) VALUES (?, ?)").bind(t.world, r.mapId));
		if (t.imageHash) stmts.push(env.DB.prepare("INSERT OR IGNORE INTO mapartBlockedImages (imageHash, blockedAt) VALUES (?, ?)").bind(t.imageHash, now));
		for (const chunk of chunkArray(stmts, 90)) await env.DB.batch(chunk);
		const m = await env.DB.prepare("SELECT id FROM maparts WHERE id = ?").bind(t.mapartId).first();
		if (m) await deleteMapartRow(env, m.id);
	}
	await env.DB.prepare("UPDATE mapartTakedowns SET status = ?, resolvedAt = ?, resolvedBy = ? WHERE id = ?")
		.bind(action === "approve" ? "approved" : "denied", now, who, t.id).run();
	await notifyAccount(env, t.accountId, "mapartTakedown", action === "approve"
		? `Your takedown request for "${t.title}" was approved — it has been removed and can't be uploaded to SCTP again.`
		: `Your takedown request for "${t.title}" was denied — the mapart stays up.`, null);
	return json({ ok: true, status: action === "approve" ? "approved" : "denied" });
}

// ---------------- splitting a wrongly merged mapart ----------------
// The scanner stitches any rectangle of adjacent item frames into ONE piece,
// so separate 1x1 maps hung next to each other end up as a single "5x1". An
// admin can split such a piece back into its individual maps:
//   POST /admin/mapart/split  ("manageMapart") body: {id, ownedIndexes?, dryRun?}
// Tiles are numbered 0.. row-major (left-to-right, top-to-bottom) — exactly the
// order partMapIds/mapartParts were stored in, and allNames holds the lead
// map's name first, then every other tile's name in that same grid order (both
// checked against real pieces' images). ownedIndexes are the tiles that belong
// to the piece's claimant (default: all of them) — those inherit the claim and
// the owner's edits (category / where-to-buy / price...); the rest come out
// unclaimed with their own name-derived artist. dryRun just lists the tiles.
// Any pending takedown request for the piece is resolved as "split".

const CRC32_TABLE = (() => {
	const t = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
		t[n] = c >>> 0;
	}
	return t;
})();
function crc32(bytes) {
	let c = 0xFFFFFFFF;
	for (let i = 0; i < bytes.length; i++) c = CRC32_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
	return (c ^ 0xFFFFFFFF) >>> 0;
}
function pngChunk(type, data) {
	const out = new Uint8Array(12 + data.length);
	const dv = new DataView(out.buffer);
	dv.setUint32(0, data.length);
	for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
	out.set(data, 8);
	dv.setUint32(8 + data.length, crc32(out.subarray(4, 8 + data.length)));
	return out;
}
// 8-bit RGBA, no filtering — the pieces are small (128x128 per tile).
async function encodePngRgba(w, h, rgba) {
	const stride = w * 4;
	const raw = new Uint8Array(h * (stride + 1));
	for (let y = 0; y < h; y++) raw.set(rgba.subarray(y * stride, (y + 1) * stride), y * (stride + 1) + 1);
	const idat = new Uint8Array(await new Response(new Blob([raw]).stream().pipeThrough(new CompressionStream("deflate"))).arrayBuffer());
	const ihdr = new Uint8Array(13);
	const dv = new DataView(ihdr.buffer);
	dv.setUint32(0, w); dv.setUint32(4, h);
	ihdr[8] = 8; ihdr[9] = 6;
	const chunks = [Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10]), pngChunk("IHDR", ihdr), pngChunk("IDAT", idat), pngChunk("IEND", new Uint8Array(0))];
	const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0));
	let off = 0;
	for (const c of chunks) { out.set(c, off); off += c.length; }
	return out;
}

// True if the rectangle contains two or more map ids that an admin split apart.
async function mapartSplitConflict(env, world, partIds) {
	const perSplit = new Map();
	for (const chunk of chunkArray(partIds, MAX_QUERY_PARAMS_PER_CHUNK - 1)) {
		const placeholders = chunk.map(() => "?").join(",");
		const { results } = await env.DB.prepare(`SELECT splitId FROM mapartSplitParts WHERE world = ? AND mapId IN (${placeholders})`).bind(world, ...chunk).all();
		for (const r of results) {
			const n = (perSplit.get(r.splitId) || 0) + 1;
			if (n > 1) return true;
			perSplit.set(r.splitId, n);
		}
	}
	return false;
}

async function handleAdminSplitMapart(request, env) {
	const auth = await requireAdminAuth(request, env, "manageMapart");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(String(body.id || "")).first();
	if (!m) return json({ error: "Mapart not found" }, 404);
	const tileCount = m.width * m.height;
	if (tileCount < 2) return json({ error: "That piece is already a single map." }, 400);
	const { results: parts } = await env.DB.prepare("SELECT mapId FROM mapartParts WHERE mapartId = ? ORDER BY rowid").bind(m.id).all();
	if (parts.length !== tileCount) return json({ error: "This piece's stored map ids don't cover its grid, so it can't be split safely." }, 409);

	let allNames = [];
	try { allNames = JSON.parse(m.allNames || "[]"); } catch (e) { /* fall back to generic names */ }
	const leadIdx = parts.findIndex((p) => p.mapId === m.leadMapId);
	const names = new Array(tileCount).fill(null);
	if (leadIdx >= 0 && allNames.length === tileCount) {
		let next = 1;
		for (let i = 0; i < tileCount; i++) names[i] = i === leadIdx ? allNames[0] : allNames[next++];
	}
	const tiles = parts.map((p, i) => ({ index: i, mapId: p.mapId, x: i % m.width, y: Math.floor(i / m.width), name: names[i] }));
	if (body.dryRun) return json({ ok: true, title: m.title, width: m.width, height: m.height, claimed: !!m.claimedByAccountId, tiles });

	let owned = tiles.map((t) => t.index);
	if (Array.isArray(body.ownedIndexes) && body.ownedIndexes.length) {
		owned = [...new Set(body.ownedIndexes.map(Number))];
		if (owned.some((i) => !Number.isInteger(i) || i < 0 || i >= tileCount)) return json({ error: "ownedIndexes out of range" }, 400);
	}
	const ownedSet = new Set(owned);

	// Cut + encode every tile BEFORE touching the database, so a bad image can't leave a half-split piece.
	const obj = await env.SNAPSHOTS.get(`mapart/${m.id}.png`);
	if (!obj) return json({ error: "The piece's image is missing from storage." }, 409);
	const img = await decodePngRgba(new Uint8Array(await obj.arrayBuffer()));
	if (!img || img.w !== m.width * 128 || img.h !== m.height * 128) return json({ error: "The piece's image doesn't match its grid size." }, 409);
	const knownNames = await getMapartKnownNames(env);
	const now = new Date().toISOString();
	const kids = [];
	for (const t of tiles) {
		const rgba = new Uint8Array(128 * 128 * 4);
		for (let row = 0; row < 128; row++) {
			const src = ((t.y * 128 + row) * img.w + t.x * 128) * 4;
			rgba.set(img.rgba.subarray(src, src + 128 * 4), row * 128 * 4);
		}
		const png = await encodePngRgba(128, 128, rgba);
		const isOwned = ownedSet.has(t.index);
		let title, artist, rawName;
		if (t.name) {
			const f = deriveMapartFields([t.name], knownNames);
			artist = f.artist || (isOwned ? m.artist : null) || null;
			rawName = t.name;
			// A name that's only a shop credit ("/shop Someone") has no title of its
			// own — call it after the piece it came from instead of "Untitled mapart".
			title = f.title && f.title !== "Untitled mapart" ? f.title : `${m.title} (${artist || t.index + 1})`;
		} else {
			title = `${m.title} (${t.index + 1})`;
			artist = isOwned ? m.artist || null : null;
			rawName = title;
		}
		let phash = null;
		try { phash = await phashOfPng(png, PHASH_MAX_INLINE_PIXELS); } catch (e) { /* optional */ }
		kids.push({ t, id: crypto.randomUUID(), png, isOwned, title, artist, rawName, phash, imageHash: await sha256Hex16(png) });
	}
	for (const k of kids) k.slug = await assignMapartSlug(env, k.id, k.title);
	for (const k of kids) await env.SNAPSHOTS.put(`mapart/${k.id}.png`, k.png, { httpMetadata: { contentType: "image/png" } });

	const { results: oldSlugs } = await env.DB.prepare("SELECT slug FROM mapartSlugs WHERE mapartId = ?").bind(m.id).all();
	const redirectTo = (kids.find((k) => k.isOwned && k.t.index === leadIdx) || kids.find((k) => k.isOwned) || kids[0]).id;
	const splitId = crypto.randomUUID();
	const stmts = [
		env.DB.prepare("DELETE FROM mapartParts WHERE mapartId = ?").bind(m.id),
		env.DB.prepare("DELETE FROM mapartSlugs WHERE mapartId = ?").bind(m.id),
		env.DB.prepare("DELETE FROM maparts WHERE id = ?").bind(m.id),
	];
	for (const k of kids) {
		const claim = k.isOwned && m.claimedByAccountId;
		stmts.push(env.DB.prepare(
			`INSERT INTO maparts (id, slug, world, leadMapId, rawName, allNames, title, artist, whereToBuy, notForSale, price, commissioned, commissionedBy, category, width, height, imageHash, phash,
			   claimedByAccountId, claimedAt, autoClaimBlocked, claimedManually, ownerEdited, createdAt, updatedAt, lastSeen)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
		).bind(k.id, k.slug, m.world, k.t.mapId, k.rawName, JSON.stringify([k.rawName]), k.title, k.artist,
			k.isOwned ? m.whereToBuy : null, k.isOwned ? m.notForSale : 0, k.isOwned ? m.price : null,
			k.isOwned ? m.commissioned : 0, k.isOwned ? m.commissionedBy : null, k.isOwned ? m.category : null, k.imageHash, k.phash,
			claim ? m.claimedByAccountId : null, claim ? m.claimedAt : null, claim ? m.autoClaimBlocked : 0,
			claim ? m.claimedManually : 0, claim ? m.ownerEdited : 0, now, now, now));
		stmts.push(env.DB.prepare("INSERT INTO mapartParts (world, mapId, mapartId) VALUES (?, ?, ?)").bind(m.world, k.t.mapId, k.id));
		stmts.push(env.DB.prepare("INSERT OR REPLACE INTO mapartSplitParts (world, mapId, splitId) VALUES (?, ?, ?)").bind(m.world, k.t.mapId, splitId));
	}
	for (const s of oldSlugs) stmts.push(env.DB.prepare("INSERT OR REPLACE INTO mapartSlugs (slug, mapartId) VALUES (?, ?)").bind(s.slug, redirectTo));
	// Collections that held the merged piece keep pointing at something real.
	stmts.push(env.DB.prepare("UPDATE OR IGNORE collectionItems SET itemId = ? WHERE kind = 'mapart' AND itemId = ?").bind(redirectTo, m.id));
	stmts.push(env.DB.prepare("DELETE FROM collectionItems WHERE kind = 'mapart' AND itemId = ?").bind(m.id));
	for (const chunk of chunkArray(stmts, 90)) await env.DB.batch(chunk);
	try { await env.SNAPSHOTS.delete(`mapart/${m.id}.png`); } catch (e) { /* already gone */ }

	// Same auto-claim a normal scan gets: a verified account whose MC name is a tile's artist.
	for (const k of kids) {
		if (k.isOwned && m.claimedByAccountId) continue;
		if (!k.artist) continue;
		for (const name of splitMapartArtists(k.artist)) {
			const acct = await env.DB.prepare("SELECT id FROM admins WHERE mcVerified = 1 AND lower(ltrim(mcUsername, '.')) = lower(?)").bind(name).first();
			if (acct) { await env.DB.prepare("UPDATE maparts SET claimedByAccountId = ?, claimedAt = ? WHERE id = ?").bind(acct.id, now, k.id).run(); break; }
		}
	}

	// Any takedown request still waiting on the merged piece is settled by this.
	const who = auth.admin ? auth.admin.username : "master";
	const { results: waiting } = await env.DB.prepare("SELECT id, accountId, title FROM mapartTakedowns WHERE mapartId = ? AND status = 'pending'").bind(m.id).all();
	for (const t of waiting) {
		await env.DB.prepare("UPDATE mapartTakedowns SET status = 'split', resolvedAt = ?, resolvedBy = ? WHERE id = ?").bind(now, who, t.id).run();
		await notifyAccount(env, t.accountId, "mapartTakedown",
			`Your report about "${t.title}" was resolved — it was split into ${kids.length} separate pieces, so each map now has its own page.`, null);
	}
	return json({ ok: true, split: kids.length, resolvedRequests: waiting.length, pieces: kids.map((k) => ({ id: k.id, slug: k.slug, title: k.title, artist: k.artist, index: k.t.index, claimed: !!(k.isOwned && m.claimedByAccountId) })) });
}

// ---------------- hand-uploaded mapart (verified accounts) ----------------
const MAPART_UPLOAD_MAX_GRID = 10;
const MAPART_MAX_UPLOADS_PER_ACCOUNT = 100;

// POST /mapart/submit — body: {title, world, width, height, png (base64),
// artist?, category?, whereToBuy?, notForSale?}. The page crops/scales the
// picture to exactly width*128 x height*128 before sending. The piece goes
// live at once, claimed by the uploader; it has no in-game map ids, so scans
// never touch it (synthetic negative leadMapId, no mapartParts rows).
async function handleSubmitMapart(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }

	const world = String(body.world || "");
	if (!MAPART_WORLDS.includes(world)) return json({ error: "world must be Firefly or Honeybee" }, 400);
	const width = body.width, height = body.height;
	if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1
		|| width > MAPART_UPLOAD_MAX_GRID || height > MAPART_UPLOAD_MAX_GRID) {
		return json({ error: `Size must be 1-${MAPART_UPLOAD_MAX_GRID} maps in each direction` }, 400);
	}
	const title = String(body.title || "").trim();
	if (!title || title.length > 100) return json({ error: "title must be 1-100 characters" }, 400);
	const mc = String(auth.admin.mcUsername || "").replace(/^\./, "");
	const cleanedArtist = cleanMapartArtist(body.artist === undefined ? mc : body.artist);
	if (cleanedArtist.error) return json({ error: cleanedArtist.error }, 400);
	const artist = cleanedArtist.value || mc;
	const cleanedPrice = cleanMapartPrice(body.price);
	if (cleanedPrice.error) return json({ error: cleanedPrice.error }, 400);
	const cleanedCommission = cleanCommission(body.commissioned, body.commissionedBy);
	if (cleanedCommission.error) return json({ error: cleanedCommission.error }, 400);
	const whereToBuy = String(body.whereToBuy || "").trim();
	if (whereToBuy.length > 200) return json({ error: "whereToBuy must be at most 200 characters" }, 400);
	const category = body.category ? String(body.category) : null;
	if (category !== null && !MAPART_CATEGORIES.includes(category)) return json({ error: "Unknown category" }, 400);

	let pngBytes;
	try { pngBytes = Uint8Array.from(atob(String(body.png || "")), (c) => c.charCodeAt(0)); } catch (e) {
		return json({ error: "png isn't valid base64" }, 400);
	}
	if (pngBytes.length > MAPART_MAX_PNG_BYTES) return json({ error: "That image is too large (over 4 MB) — try a smaller size." }, 400);
	const size = readPngSize(pngBytes);
	if (!size || size.width !== width * 128 || size.height !== height * 128) {
		return json({ error: "The image must be a PNG exactly " + width * 128 + "x" + height * 128 + " pixels." }, 400);
	}

	const mine = await env.DB.prepare("SELECT COUNT(*) AS c FROM maparts WHERE uploadedByAccountId = ?").bind(auth.admin.id).first();
	if (mine.c >= MAPART_MAX_UPLOADS_PER_ACCOUNT) {
		return json({ error: `You've reached the limit of ${MAPART_MAX_UPLOADS_PER_ACCOUNT} uploaded mapart — delete one first.` }, 400);
	}
	const imageHash = await sha256Hex16(pngBytes);
	if (await env.DB.prepare("SELECT 1 AS x FROM mapartBlockedImages WHERE imageHash = ?").bind(imageHash).first()) {
		return json({ error: "That picture was removed from SCTP at its owner's request and can't be uploaded again." }, 403);
	}
	const dup = await env.DB.prepare("SELECT title, slug FROM maparts WHERE imageHash = ? LIMIT 1").bind(imageHash).first();
	if (dup) return json({ error: `That exact picture is already in the gallery as "${dup.title}".`, slug: dup.slug }, 409);
	// Near-duplicates (a re-export, a screenshot of the same art, ...) count too.
	const uploadHash = await phashOfPng(pngBytes, 0);
	if (uploadHash && !body.allowSimilar) {
		const [near] = await findSimilarMapart(env, uploadHash, 10, 1);
		if (near) return json({ error: `That looks almost identical to "${near.title}" that's already in the gallery.`, slug: near.slug, similar: true }, 409);
	}

	const id = crypto.randomUUID();
	let leadMapId = null;
	for (let i = 0; i < 8 && leadMapId === null; i++) {
		const cand = -(1 + Math.floor(Math.random() * 2000000000));
		const taken = await env.DB.prepare("SELECT 1 AS x FROM maparts WHERE world = ? AND leadMapId = ?").bind(world, cand).first();
		if (!taken) leadMapId = cand;
	}
	if (leadMapId === null) return json({ error: "Couldn't allocate an id — please try again." }, 502);

	const slug = await assignMapartSlug(env, id, title);
	const now = new Date().toISOString();
	await env.DB.prepare(
		`INSERT INTO maparts (id, slug, world, leadMapId, rawName, allNames, title, artist, whereToBuy, notForSale, price, commissioned, commissionedBy, phash, category, width, height, imageHash,
			claimedByAccountId, claimedAt, autoClaimBlocked, locked, claimedManually, ownerEdited, uploadedByAccountId, createdAt, updatedAt, lastSeen)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, 1, 1, ?, ?, ?, ?)`
	).bind(id, slug, world, leadMapId, title, JSON.stringify([title]), title, artist, whereToBuy || null, body.notForSale ? 1 : 0, cleanedPrice.value, cleanedCommission.commissioned, cleanedCommission.commissionedBy, uploadHash || null, category, width, height, imageHash,
		auth.admin.id, now, auth.admin.id, now, now, now).run();
	try {
		await env.SNAPSHOTS.put(`mapart/${id}.png`, pngBytes, { httpMetadata: { contentType: "image/png" } });
	} catch (e) {
		await deleteMapartRow(env, id);
		return json({ error: "Couldn't store the image — please try again." }, 502);
	}
	const fresh = await env.DB.prepare("SELECT * FROM maparts WHERE id = ?").bind(id).first();
	return json({ ok: true, mapart: mapartForOwner(fresh) });
}

// Uploaders can delete what they uploaded (mistakes happen); scanned pieces
// can only be abandoned, and any piece can still be removed by a head admin.
async function handleDeleteOwnMapart(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const m = await env.DB.prepare("SELECT id FROM maparts WHERE id = ? AND uploadedByAccountId = ?").bind(String(body.id || ""), auth.admin.id).first();
	if (!m) return json({ error: "You can only delete mapart you uploaded yourself." }, 404);
	await deleteMapartRow(env, m.id);
	return json({ ok: true });
}

// One pseudo-listing per catalogued mapart, for the website's listings
// table. currency "display" = no real price (the site already sorts those
// last and keeps them out of averages); mapartGallery tells the site to show
// the "not a live listing" info icon and hide Report/Remove.
async function getMapartGalleryRows(env) {
	const { results } = await env.DB.prepare("SELECT * FROM maparts").all();
	return results.map((m) => {
		const artists = splitMapartArtists(m.artist);
		const buy = resolveMapartWhereToBuy(m);
		return {
			id: m.id, itemName: m.title, baseItem: "minecraft:filled_map",
			bulk: false, bundled: false, mixedContents: false,
			price: 0, priceLabel: m.price || "Gallery", stackSize: 1, amount: 1, stacksInStock: 1, currency: "display",
			seller: artists[0] || "Unknown artist", world: m.world,
			position: m.notForSale ? "Not for sale" : (buy || "Mapart gallery"),
			lastSeen: m.lastSeen, availableSince: m.createdAt,
			mapartGallery: true, mapartArtist: m.artist || null,
			mapart: { id: m.id, slug: m.slug, title: m.title, width: m.width, height: m.height, imageHash: m.imageHash },
		};
	});
}

// ---------------- collections ----------------
// An account ticks off the rare items and mapart it owns, per world. Public by
// default (anyone can open /collection/<username>); admins.collectionPrivate hides it.
//   GET  /collection/mine              (any account) -> {private, items:[{kind, itemId, world}]}
//   POST /collection/set               (any account) body: {kind: "rare"|"mapart", world, ids: [...], owned: bool}
//   POST /collection/privacy           (any account) body: {private: bool}
//   GET  /collection/public?username=  (public) -> {username, private, items?} (items left out when private)
const COLLECTION_KINDS = new Set(["rare", "mapart"]);
const COLLECTION_MAX_ITEMS = 10000;
const COLLECTION_MAX_IDS_PER_CALL = 2000;

async function handleGetMyCollection(request, env) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base.response;
	const { results } = await env.DB.prepare("SELECT kind, itemId, world, addedAt FROM collectionItems WHERE accountId = ?").bind(base.admin.id).all();
	return json({ username: base.admin.username, private: !!base.admin.collectionPrivate, items: results });
}

async function handleSetCollectionItems(request, env) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const kind = String(body.kind || "");
	if (!COLLECTION_KINDS.has(kind)) return json({ error: "kind must be rare or mapart" }, 400);
	const world = String(body.world || "");
	if (!MAPART_WORLDS.includes(world)) return json({ error: "world must be Firefly or Honeybee" }, 400);
	if (!Array.isArray(body.ids) || body.ids.length === 0 || body.ids.length > COLLECTION_MAX_IDS_PER_CALL) {
		return json({ error: `ids must be a list of 1-${COLLECTION_MAX_IDS_PER_CALL} items` }, 400);
	}
	let ids = [...new Set(body.ids.map((x) => String(x || "")))];
	const idOk = kind === "rare" ? (x) => /^rare-[A-Za-z0-9_.-]{1,120}$/.test(x) : (x) => /^[0-9a-f-]{36}$/.test(x);
	if (!ids.every(idOk)) return json({ error: "Invalid item id" }, 400);
	const accountId = base.admin.id;

	if (!body.owned) {
		const stmts = [];
		for (const chunk of chunkArray(ids, 80)) {
			stmts.push(env.DB.prepare(
				`DELETE FROM collectionItems WHERE accountId = ? AND kind = ? AND world = ? AND itemId IN (${chunk.map(() => "?").join(",")})`
			).bind(accountId, kind, world, ...chunk));
		}
		for (const c of chunkArray(stmts, 90)) await env.DB.batch(c);
		return json({ ok: true });
	}

	if (kind === "mapart") {
		// Only real pieces in the requested world count.
		const known = new Set();
		for (const chunk of chunkArray(ids, 80)) {
			const { results } = await env.DB.prepare(
				`SELECT id FROM maparts WHERE world = ? AND id IN (${chunk.map(() => "?").join(",")})`
			).bind(world, ...chunk).all();
			for (const r of results) known.add(r.id);
		}
		ids = ids.filter((x) => known.has(x));
		if (!ids.length) return json({ error: "None of those mapart exist in " + world }, 404);
	}
	const have = await env.DB.prepare("SELECT COUNT(*) AS c FROM collectionItems WHERE accountId = ?").bind(accountId).first();
	if (have.c + ids.length > COLLECTION_MAX_ITEMS) return json({ error: "Your collection is full." }, 400);
	const now = new Date().toISOString();
	const stmts = ids.map((id) => env.DB.prepare(
		"INSERT OR IGNORE INTO collectionItems (accountId, kind, itemId, world, addedAt) VALUES (?, ?, ?, ?, ?)"
	).bind(accountId, kind, id, world, now));
	for (const c of chunkArray(stmts, 90)) await env.DB.batch(c);
	return json({ ok: true });
}

async function handleSetCollectionPrivacy(request, env) {
	const base = await requireAnyAdmin(request, env);
	if (!base.ok) return base.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	await env.DB.prepare("UPDATE admins SET collectionPrivate = ? WHERE id = ?").bind(body.private ? 1 : 0, base.admin.id).run();
	return json({ ok: true, private: !!body.private });
}

async function handleGetPublicCollection(request, env) {
	const username = String(new URL(request.url).searchParams.get("username") || "").trim().toLowerCase();
	if (!username) return json({ error: "username is required" }, 400);
	const acct = await env.DB.prepare("SELECT id, username, mcUsername, collectionPrivate FROM admins WHERE lower(username) = ?").bind(username).first();
	if (!acct) return json({ error: "No account with that name" }, 404);
	if (acct.collectionPrivate) return json({ username: acct.username, private: true });
	const { results } = await env.DB.prepare("SELECT kind, itemId, world, addedAt FROM collectionItems WHERE accountId = ?").bind(acct.id).all();
	return json({ username: acct.username, mcUsername: acct.mcUsername || null, private: false, items: results });
}

// ---------------- shop hints (restock / reprice / undercut) ----------------
// Extra section of GET /stats/mine. Uses only the caller's own listings, the
// current listings of the *same items* from other sellers, and the daily
// per-item stats already tracked — nothing is written.
function hintKeyFor(r) {
	return String(r.baseItem || "").toLowerCase() + "|" + String(r.itemName || "").toLowerCase() + "|" + r.world;
}

async function computeShopHints(env, sellerKey, statRows, latestDate, trackingDays) {
	const hints = { restock: [], reprice: [], undercut: [] };

	// ---- market comparison: reprice + undercut
	const { results: mine } = await env.DB.prepare(
		"SELECT baseItem, itemName, price, currency, stackSize, world, position FROM listings WHERE lower(seller) = ?"
	).bind(sellerKey).all();
	const ownBest = new Map(); // key -> { r, each }
	for (const r of mine) {
		if (String(r.currency || "").toLowerCase() === "display") continue;
		const each = priceInDiamonds(r) / (r.stackSize || 1);
		const k = hintKeyFor(r);
		const cur = ownBest.get(k);
		if (!cur || each < cur.each) ownBest.set(k, { r, each });
	}
	if (ownBest.size) {
		const bases = [...new Set([...ownBest.values()].map((o) => String(o.r.baseItem).toLowerCase()))];
		const others = new Map(); // key -> [{each, seller}]
		for (const chunk of chunkArray(bases, 80)) {
			const { results } = await env.DB.prepare(
				`SELECT baseItem, itemName, price, currency, stackSize, seller, world FROM listings WHERE lower(baseItem) IN (${chunk.map(() => "?").join(",")}) AND lower(seller) != ?`
			).bind(...chunk, sellerKey).all();
			for (const r of results) {
				if (String(r.currency || "").toLowerCase() === "display") continue;
				const k = hintKeyFor(r);
				if (!ownBest.has(k)) continue;
				if (!others.has(k)) others.set(k, []);
				others.get(k).push({ each: priceInDiamonds(r) / (r.stackSize || 1), seller: r.seller });
			}
		}
		for (const [k, own] of ownBest) {
			const list = others.get(k);
			if (!list || !list.length) continue;
			const sorted = list.map((o) => o.each).sort((a, b) => a - b);
			const median = sorted[Math.floor(sorted.length / 2)];
			const cheapest = list.reduce((a, b) => (b.each < a.each ? b : a));
			const base = { itemName: own.r.itemName, world: own.r.world, yourPrice: own.each, marketMedian: median, others: list.length };
			if (cheapest.each < own.each * 0.98) {
				hints.undercut.push({ ...base, cheapestPrice: cheapest.each, cheapestSeller: cheapest.seller, pctCheaper: Math.round((1 - cheapest.each / own.each) * 100) });
			}
			if (list.length >= 2 && median > 0) {
				const ratio = own.each / median;
				if (ratio > 1.3) hints.reprice.push({ ...base, direction: "high", pct: Math.round((ratio - 1) * 100) });
				else if (ratio < 0.7) hints.reprice.push({ ...base, direction: "low", pct: Math.round((1 - ratio) * 100) });
			}
		}
		hints.undercut.sort((a, b) => b.pctCheaper - a.pctCheaper);
		hints.reprice.sort((a, b) => b.pct - a.pct);
	}

	// ---- restock: things that sell but are (nearly) gone
	const windowDays = Math.max(1, Math.min(14, trackingDays));
	const cutoff = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
	const byItem = new Map();
	for (const r of statRows) {
		const k = r.itemKey + "|" + r.world;
		let e = byItem.get(k);
		if (!e) { e = { itemName: r.itemName, world: r.world, sold14: 0, latestStock: null }; byItem.set(k, e); }
		if (r.date >= cutoff) e.sold14 += r.inferredSold;
		if (r.date === latestDate) e.latestStock = r.totalStock;
	}
	for (const e of byItem.values()) {
		if (e.sold14 <= 0) continue;
		const perDay = e.sold14 / windowDays;
		if (e.latestStock === null || e.latestStock === 0) {
			hints.restock.push({ itemName: e.itemName, world: e.world, sold14: e.sold14, stock: 0, daysLeft: 0 });
		} else {
			const daysLeft = e.latestStock / perDay;
			if (daysLeft < 4) hints.restock.push({ itemName: e.itemName, world: e.world, sold14: e.sold14, stock: e.latestStock, daysLeft: Math.round(daysLeft * 10) / 10 });
		}
	}
	hints.restock.sort((a, b) => a.daysLeft - b.daysLeft || b.sold14 - a.sold14);
	for (const k of Object.keys(hints)) hints[k] = hints[k].slice(0, 25);
	return hints;
}

// ---------------- commission info + public profiles ----------------
// Saved from the mapart management page; shown on the artist's profile.
//   POST /account/commission  (verified account) body: {open, info?, discord?}
async function handleSetCommissionInfo(request, env) {
	const auth = await requireVerifiedAccount(request, env);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const info = String(body.info || "").trim();
	const discord = String(body.discord || "").trim();
	if (info.length > 1000) return json({ error: "Commission details must be at most 1000 characters" }, 400);
	if (discord.length > 60) return json({ error: "Discord must be at most 60 characters" }, 400);
	await env.DB.prepare("UPDATE admins SET commissionOpen = ?, commissionInfo = ?, commissionDiscord = ? WHERE id = ?")
		.bind(body.open ? 1 : 0, info || null, discord || null, auth.admin.id).run();
	return json({ ok: true });
}

// GET /profile?username=  (public) — one page's worth of everything about a
// player: their mapart (as artist, commissioner or owner), commission info,
// public collection summary and marketplace history. The shop listings
// themselves come from the regular /listings feed on the client.
async function handleGetProfile(request, env, ctx) {
	return cachedGet(request, ctx, 120, async () => {
		const raw = String(new URL(request.url).searchParams.get("username") || "").trim();
		const name = raw.replace(/^\./, "");
		if (!name) return { error: "username is required" };
		const low = name.toLowerCase();
		const acct = await env.DB.prepare(
			"SELECT id, username, mcUsername, mcVerified, collectionPrivate, commissionOpen, commissionInfo, commissionDiscord FROM admins WHERE lower(username) = ? OR lower(replace(mcUsername, '.', '')) = ? ORDER BY (lower(username) = ?) DESC LIMIT 1"
		).bind(low, low, low).first();

		const artistName = acct && acct.mcUsername ? String(acct.mcUsername).replace(/^\./, "").toLowerCase() : low;
		const { results: mapartRows } = await env.DB.prepare(
			`SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId
			 WHERE instr(' & ' || lower(m.artist) || ' & ', ' & ' || ? || ' & ') > 0 OR lower(m.commissionedBy) = ? OR (? != '' AND m.claimedByAccountId = ?)
			 ORDER BY m.title COLLATE NOCASE`
		).bind(artistName, artistName, acct ? acct.id : "", acct ? acct.id : "").all();
		const mapart = mapartRows.map((m) => ({
			...mapartPublic(m),
			role: m.commissionedBy && m.commissionedBy.toLowerCase() === artistName ? "commissioner" : "artist",
		}));

		const out = { username: acct ? acct.username : name, hasAccount: !!acct, verified: !!(acct && acct.mcVerified), mapart };
		if (acct) {
			out.commission = { open: !!acct.commissionOpen, info: acct.commissionInfo || "", discord: acct.commissionDiscord || "" };
			out.collection = { public: !acct.collectionPrivate };
			if (!acct.collectionPrivate) {
				const { results } = await env.DB.prepare("SELECT kind, world, COUNT(*) AS n FROM collectionItems WHERE accountId = ? GROUP BY kind, world").bind(acct.id).all();
				out.collection.counts = results;
			}
			const { results: mk } = await env.DB.prepare(
				`SELECT type, itemName, world, quantity, askingPrice, askingCurrency, budget, budgetCurrency, status, createdAt
				 FROM marketplaceListings WHERE accountId = ? AND status IN ('active', 'fulfilled') ORDER BY createdAt DESC LIMIT 30`
			).bind(acct.id).all();
			out.marketplace = mk;

			// General seller rating — every thumbs up/down cast across every job
			// this person has ever posted (hiring or forHire alike), summed.
			const jobRatingRow = await env.DB.prepare(
				`SELECT SUM(CASE WHEN r.vote = 1 THEN 1 ELSE 0 END) AS up, SUM(CASE WHEN r.vote = -1 THEN 1 ELSE 0 END) AS down
				 FROM marketplaceJobReviews r JOIN marketplaceJobs j ON j.id = r.jobId WHERE j.accountId = ?`
			).bind(acct.id).first();
			const jobUp = (jobRatingRow && jobRatingRow.up) || 0, jobDown = (jobRatingRow && jobRatingRow.down) || 0;
			if (jobUp + jobDown > 0) out.jobRating = { up: jobUp, down: jobDown };
		}
		return out;
	});
}

// ---------------- mapart of the day ----------------
// One random piece per UTC day, picked lazily by the first visitor. Head
// admins / mapart managers can re-roll it from the admin panel. Only ever
// picked from VERIFIED pieces (see mapartPublic's `verified` — claimed by a
// real, engaged owner, not just an anonymous/unclaimed scan) — reuses that
// exact function rather than re-deriving the same condition in SQL, so this
// can never quietly drift out of sync with what "verified" means everywhere
// else on the site.
//   GET  /mapart/of-the-day                (public)
//   POST /admin/mapart/otd/reroll          ("manageMapart") body: {id?} -> picks a different random verified piece (or the given one, even if unverified — an explicit admin override)
const MOTD_KEY = "mapartOfTheDay";

async function pickRandomMapart(env, excludeId) {
	const { results } = await env.DB.prepare(
		"SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId WHERE m.id != ?"
	).bind(excludeId || "").all();
	const verified = results.filter((m) => mapartPublic(m).verified);
	if (!verified.length) return null;
	return verified[Math.floor(Math.random() * verified.length)].id;
}

async function getOrPickMapartOfTheDay(env) {
	const today = new Date().toISOString().slice(0, 10);
	const row = await env.DB.prepare("SELECT value FROM siteSettings WHERE key = ?").bind(MOTD_KEY).first();
	let cur = null;
	try { cur = row ? JSON.parse(row.value) : null; } catch (e) { cur = null; }
	if (cur && cur.date === today) {
		const existing = await env.DB.prepare(
			"SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId WHERE m.id = ?"
		).bind(cur.id).first();
		// Re-checked (not just "does it still exist") so a piece picked before
		// this restriction existed, or one that's lost its verified status since,
		// gets swapped out for a verified one rather than staying up all day.
		if (existing && mapartPublic(existing).verified) return cur;
	}
	const id = await pickRandomMapart(env, cur ? cur.id : "");
	if (!id) return null;
	cur = { date: today, id };
	await env.DB.prepare("INSERT OR REPLACE INTO siteSettings (key, value) VALUES (?, ?)").bind(MOTD_KEY, JSON.stringify(cur)).run();
	return cur;
}

async function loadMotdPayload(env, cur) {
	const m = await env.DB.prepare(
		"SELECT m.*, a.mcVerified AS claimantVerified FROM maparts m LEFT JOIN admins a ON a.id = m.claimedByAccountId WHERE m.id = ?"
	).bind(cur.id).first();
	return m ? { date: cur.date, mapart: mapartPublic(m) } : { error: "none" };
}

async function handleGetMapartOfTheDay(request, env, ctx) {
	return cachedGet(request, ctx, 300, async () => {
		const cur = await getOrPickMapartOfTheDay(env);
		return cur ? loadMotdPayload(env, cur) : { error: "none" };
	});
}

async function handleAdminRerollMapartOfTheDay(request, env) {
	const auth = await requireAdminAuth(request, env, "manageMapart");
	if (!auth.ok) return auth.response;
	let body = {};
	try { body = await request.json(); } catch (e) { /* no body is fine */ }
	const today = new Date().toISOString().slice(0, 10);
	const row = await env.DB.prepare("SELECT value FROM siteSettings WHERE key = ?").bind(MOTD_KEY).first();
	let prev = null;
	try { prev = row ? JSON.parse(row.value) : null; } catch (e) { /* ignore */ }
	let id = null;
	if (body.id) {
		if (!(await env.DB.prepare("SELECT 1 AS x FROM maparts WHERE id = ?").bind(String(body.id)).first())) return json({ error: "Mapart not found" }, 404);
		id = String(body.id);
	} else {
		id = await pickRandomMapart(env, prev ? prev.id : "");
	}
	if (!id) return json({ error: "No mapart to pick from" }, 404);
	const cur = { date: today, id };
	await env.DB.prepare("INSERT OR REPLACE INTO siteSettings (key, value) VALUES (?, ?)").bind(MOTD_KEY, JSON.stringify(cur)).run();
	// The public endpoint is edge-cached; the site refetches within minutes.
	return json({ ok: true, ...(await loadMotdPayload(env, cur)) });
}

// ---------------- reverse image search ----------------
// Every piece gets a 256-bit difference hash of its picture (16 rows x 16
// left-vs-right brightness comparisons over a 17x16 grid of averaged cells).
// The browser computes the same hash for an uploaded picture
// (mapart-search.js); the Hamming distance between two hashes says how
// alike the pictures look, independent of size and small colour shifts.
const PHASH_COLS = 17, PHASH_ROWS = 16;
const PHASH_MAX_INLINE_PIXELS = 300000; // bigger pieces are indexed by the admin backfill, not inline

async function inflateZlib(bytes) {
	const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate"));
	return new Uint8Array(await new Response(stream).arrayBuffer());
}

// 8-bit, non-interlaced PNG -> {w, h, gray: Uint8Array(w*h)}; null for anything else.
async function decodePngGray(bytes) {
	if (bytes.length < 33 || bytes[0] !== 0x89 || bytes[1] !== 0x50) return null;
	const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
	let off = 8, w = 0, h = 0, depth = 0, ctype = -1, interlace = 0, plte = null;
	const idat = [];
	while (off + 12 <= bytes.length) {
		const len = dv.getUint32(off);
		const type = String.fromCharCode(bytes[off + 4], bytes[off + 5], bytes[off + 6], bytes[off + 7]);
		const data = bytes.subarray(off + 8, off + 8 + len);
		off += 12 + len;
		if (type === "IHDR") { w = dv.getUint32(off - 12 - len + 8); h = dv.getUint32(off - 12 - len + 12); depth = data[8]; ctype = data[9]; interlace = data[12]; }
		else if (type === "PLTE") plte = data;
		else if (type === "IDAT") idat.push(data);
		else if (type === "IEND") break;
	}
	const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype];
	if (!w || !h || depth !== 8 || interlace !== 0 || !channels || !idat.length) return null;
	let total = 0;
	for (const c of idat) total += c.length;
	const joined = new Uint8Array(total);
	let p = 0;
	for (const c of idat) { joined.set(c, p); p += c.length; }
	const raw = await inflateZlib(joined);
	const stride = w * channels;
	if (raw.length < h * (stride + 1)) return null;
	const px = new Uint8Array(h * stride);
	for (let y = 0; y < h; y++) {
		const ft = raw[y * (stride + 1)];
		const src = y * (stride + 1) + 1, dst = y * stride, up = dst - stride;
		for (let i = 0; i < stride; i++) {
			const x = raw[src + i];
			const a = i >= channels ? px[dst + i - channels] : 0;
			const b = y > 0 ? px[up + i] : 0;
			const c = i >= channels && y > 0 ? px[up + i - channels] : 0;
			let v;
			if (ft === 0) v = x;
			else if (ft === 1) v = x + a;
			else if (ft === 2) v = x + b;
			else if (ft === 3) v = x + ((a + b) >> 1);
			else {
				const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
				v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
			}
			px[dst + i] = v & 255;
		}
	}
	const gray = new Uint8Array(w * h);
	for (let i = 0, n = w * h; i < n; i++) {
		const o = i * channels;
		let g;
		if (ctype === 0) g = px[o];
		else if (ctype === 2) g = 0.299 * px[o] + 0.587 * px[o + 1] + 0.114 * px[o + 2];
		else if (ctype === 3) { const pi = px[o] * 3; g = plte ? 0.299 * plte[pi] + 0.587 * plte[pi + 1] + 0.114 * plte[pi + 2] : px[o]; }
		else if (ctype === 4) g = px[o] * px[o + 1] / 255;
		else g = (0.299 * px[o] + 0.587 * px[o + 1] + 0.114 * px[o + 2]) * px[o + 3] / 255;
		gray[i] = g;
	}
	return { w, h, gray };
}

// Same maths as mapart-search.js: average brightness into a 17x16 grid, then
// one bit per left/right neighbour pair.
// 8-bit non-interlaced PNG -> {w, h, rgba: Uint8Array(w*h*4)}; null for anything else.
async function decodePngRgba(bytes) {
	if (bytes.length < 33 || bytes[0] !== 0x89 || bytes[1] !== 0x50) return null;
	const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
	let off = 8, w = 0, h = 0, depth = 0, ctype = -1, interlace = 0, plte = null, trns = null;
	const idat = [];
	while (off + 12 <= bytes.length) {
		const len = dv.getUint32(off);
		const type = String.fromCharCode(bytes[off + 4], bytes[off + 5], bytes[off + 6], bytes[off + 7]);
		const data = bytes.subarray(off + 8, off + 8 + len);
		off += 12 + len;
		if (type === "IHDR") { w = dv.getUint32(off - 12 - len + 8); h = dv.getUint32(off - 12 - len + 12); depth = data[8]; ctype = data[9]; interlace = data[12]; }
		else if (type === "PLTE") plte = data;
		else if (type === "tRNS") trns = data;
		else if (type === "IDAT") idat.push(data);
		else if (type === "IEND") break;
	}
	const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype];
	if (!w || !h || depth !== 8 || interlace !== 0 || !channels || !idat.length) return null;
	let total = 0;
	for (const c of idat) total += c.length;
	const joined = new Uint8Array(total);
	let p = 0;
	for (const c of idat) { joined.set(c, p); p += c.length; }
	const raw = await inflateZlib(joined);
	const stride = w * channels;
	if (raw.length < h * (stride + 1)) return null;
	const px = new Uint8Array(h * stride);
	for (let y = 0; y < h; y++) {
		const ft = raw[y * (stride + 1)];
		const src = y * (stride + 1) + 1, dst = y * stride, up = dst - stride;
		for (let i = 0; i < stride; i++) {
			const x = raw[src + i];
			const a = i >= channels ? px[dst + i - channels] : 0;
			const b = y > 0 ? px[up + i] : 0;
			const c = i >= channels && y > 0 ? px[up + i - channels] : 0;
			let v;
			if (ft === 0) v = x;
			else if (ft === 1) v = x + a;
			else if (ft === 2) v = x + b;
			else if (ft === 3) v = x + ((a + b) >> 1);
			else {
				const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
				v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
			}
			px[dst + i] = v & 255;
		}
	}
	const rgba = new Uint8Array(w * h * 4);
	for (let i = 0, n = w * h; i < n; i++) {
		const o = i * channels, q = i * 4;
		if (ctype === 0) { rgba[q] = rgba[q + 1] = rgba[q + 2] = px[o]; rgba[q + 3] = 255; }
		else if (ctype === 2) { rgba[q] = px[o]; rgba[q + 1] = px[o + 1]; rgba[q + 2] = px[o + 2]; rgba[q + 3] = 255; }
		else if (ctype === 3) { const pi = px[o] * 3; rgba[q] = plte ? plte[pi] : 0; rgba[q + 1] = plte ? plte[pi + 1] : 0; rgba[q + 2] = plte ? plte[pi + 2] : 0; rgba[q + 3] = trns && px[o] < trns.length ? trns[px[o]] : 255; }
		else if (ctype === 4) { rgba[q] = rgba[q + 1] = rgba[q + 2] = px[o]; rgba[q + 3] = px[o + 1]; }
		else { rgba[q] = px[o]; rgba[q + 1] = px[o + 1]; rgba[q + 2] = px[o + 2]; rgba[q + 3] = px[o + 3]; }
	}
	return { w, h, rgba };
}

// Average an image down to an n x n grid of "#rrggbb" (alpha-weighted; "" where mostly transparent).
function pixelGridFromRgba(img, n) {
	const cells = [];
	for (let cy = 0; cy < n; cy++) {
		for (let cx = 0; cx < n; cx++) {
			const x0 = Math.floor((cx * img.w) / n), x1 = Math.max(x0 + 1, Math.floor(((cx + 1) * img.w) / n));
			const y0 = Math.floor((cy * img.h) / n), y1 = Math.max(y0 + 1, Math.floor(((cy + 1) * img.h) / n));
			let r = 0, g = 0, b = 0, a = 0, count = 0;
			for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
				const i = (y * img.w + x) * 4, al = img.rgba[i + 3];
				r += img.rgba[i] * al; g += img.rgba[i + 1] * al; b += img.rgba[i + 2] * al; a += al; count++;
			}
			if (!a || a / (count * 255) < 0.2) { cells.push(""); continue; }
			const h2 = (v) => Math.round(v / a).toString(16).padStart(2, "0");
			cells.push("#" + h2(r) + h2(g) + h2(b));
		}
	}
	return cells;
}

const raredleTextureCache = new Map();
async function raredleTexture(item) {
	if (raredleTextureCache.has(item.id)) return raredleTextureCache.get(item.id);
	let img = null;
	try {
		const res = await fetch("https://sctp.nl" + item.texture, { cf: { cacheTtl: 86400, cacheEverything: true } });
		if (res.ok) img = await decodePngRgba(new Uint8Array(await res.arrayBuffer()));
	} catch (e) { img = null; }
	if (img) { if (raredleTextureCache.size > 400) raredleTextureCache.clear(); raredleTextureCache.set(item.id, img); }
	return img;
}

// The pixel hint for a game that has used `guessCount` guesses (null while still locked).
async function raredlePixelHint(answer, guessCount) {
	const step = RAREDLE_PIXEL_STEPS.find(([g]) => guessCount >= g);
	if (!step) return null;
	const img = await raredleTexture(answer);
	if (!img) return { size: step[1], cells: null };
	return { size: step[1], cells: pixelGridFromRgba(img, step[1]) };
}

function dHashFromGray(gray, w, h) {
	const sum = new Float64Array(PHASH_COLS * PHASH_ROWS), cnt = new Uint32Array(PHASH_COLS * PHASH_ROWS);
	for (let y = 0; y < h; y++) {
		const cy = Math.min(PHASH_ROWS - 1, Math.floor(y * PHASH_ROWS / h));
		for (let x = 0; x < w; x++) {
			const cx = Math.min(PHASH_COLS - 1, Math.floor(x * PHASH_COLS / w));
			const k = cy * PHASH_COLS + cx;
			sum[k] += gray[y * w + x]; cnt[k]++;
		}
	}
	let hex = "", nib = 0, nbits = 0;
	for (let cy = 0; cy < PHASH_ROWS; cy++) {
		for (let cx = 0; cx < PHASH_COLS - 1; cx++) {
			const a = sum[cy * PHASH_COLS + cx] / (cnt[cy * PHASH_COLS + cx] || 1);
			const b = sum[cy * PHASH_COLS + cx + 1] / (cnt[cy * PHASH_COLS + cx + 1] || 1);
			nib = (nib << 1) | (a > b ? 1 : 0);
			if (++nbits === 4) { hex += nib.toString(16); nib = 0; nbits = 0; }
		}
	}
	return hex;
}

async function phashOfPng(pngBytes, maxPixels) {
	try {
		const img = await decodePngGray(pngBytes);
		if (!img || (maxPixels && img.w * img.h > maxPixels)) return null;
		return dHashFromGray(img.gray, img.w, img.h);
	} catch (e) { return null; }
}

const POP4 = [0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4];
function hexDistance(a, b) {
	if (!a || !b || a.length !== b.length) return 256;
	let d = 0;
	for (let i = 0; i < a.length; i++) d += POP4[parseInt(a[i], 16) ^ parseInt(b[i], 16)];
	return d;
}

let phashIndexCache = { at: 0, rows: [] };
async function getPhashIndex(env) {
	if (Date.now() - phashIndexCache.at < 5 * 60 * 1000) return phashIndexCache.rows;
	const { results } = await env.DB.prepare(
		"SELECT id, slug, title, artist, world, width, height, phash FROM maparts WHERE phash IS NOT NULL AND phash != ''"
	).all();
	phashIndexCache = { at: Date.now(), rows: results };
	return results;
}

// Pieces whose picture is at most `maxDist` (of 256) bits away, closest first.
async function findSimilarMapart(env, hash, maxDist, limit) {
	const rows = await getPhashIndex(env);
	const out = [];
	for (const r of rows) {
		const d = hexDistance(hash, r.phash);
		if (d <= maxDist) out.push({ id: r.id, slug: r.slug, title: r.title, artist: r.artist, world: r.world, width: r.width, height: r.height, distance: d, similarity: Math.round((1 - d / 256) * 100) });
	}
	out.sort((a, b) => a.distance - b.distance);
	return out.slice(0, limit);
}

// POST /mapart/search-image (public) body: {hash (64 hex chars, from mapart-search.js)}
async function handleSearchMapartImage(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const hash = String(body.hash || "").toLowerCase();
	if (!/^[0-9a-f]{64}$/.test(hash)) return json({ error: "hash must be 64 hex characters" }, 400);
	return json({ matches: await findSimilarMapart(env, hash, 70, 12) });
}

// POST /admin/mapart/build-index ("manageMapart") body: {limit?} — indexes a few
// not-yet-hashed pieces per call (the admin page loops until remaining = 0).
async function handleAdminBuildMapartIndex(request, env) {
	const auth = await requireAdminAuth(request, env, "manageMapart");
	if (!auth.ok) return auth.response;
	let body = {};
	try { body = await request.json(); } catch (e) { /* defaults */ }
	const limit = Math.min(15, Math.max(1, Math.floor(Number(body.limit) || 8)));
	const { results } = await env.DB.prepare("SELECT id FROM maparts WHERE phash IS NULL LIMIT ?").bind(limit).all();
	let done = 0, failed = 0;
	for (const r of results) {
		let hash = "";
		try {
			const obj = await env.SNAPSHOTS.get(`mapart/${r.id}.png`);
			if (obj) hash = (await phashOfPng(new Uint8Array(await obj.arrayBuffer()), 0)) || "";
		} catch (e) { hash = ""; }
		await env.DB.prepare("UPDATE maparts SET phash = ? WHERE id = ?").bind(hash, r.id).run();
		if (hash) done++; else failed++;
	}
	phashIndexCache = { at: 0, rows: [] };
	const left = await env.DB.prepare("SELECT COUNT(*) AS c FROM maparts WHERE phash IS NULL").first();
	const total = await env.DB.prepare("SELECT COUNT(*) AS c FROM maparts").first();
	return json({ ok: true, indexed: done, failed, remaining: left.c, total: total.c });
}

// ---------------- Rare-dle ----------------
// Daily guess-the-rare game (see 0026_raredle.sql). Login required to play the
// daily. The answer lives only here: the client sends a guess and gets back
// per-attribute feedback; the answer is revealed only once the player's game is over.
//   GET  /raredle/state                (any account) -> today's game: guesses + feedback, status, hint, stats (answer only when finished)
//   POST /raredle/guess                (any account) body: {itemId}
//   GET  /raredle/leaderboard          (public, cached 1 min) -> {date, weekStart, daily: [...], week: [...], allTime: [...]}
// Resetting today's game (POST /raredle/reset) is a QA-only escape hatch, off now that
// Rare-dle is live — one official daily game per account per day.
// Practice mode (?mode=practice / body.mode:"practice") is playable logged OUT too —
// see the "anonymous practice" block below raredlePracticePool for how that state is
// carried in a signed client-held token instead of a DB row, since there's no accountId.
const RAREDLE_ALLOW_RESET = false;
// Unlimited random practice rounds are a real, permanent feature (not gated by launch state).
const RAREDLE_PRACTICE_ENABLED = true;
const RAREDLE_MAX_GUESSES = 8;
const RAREDLE_BASE_POINTS = [1000, 800, 650, 500, 400, 300, 200, 120]; // by number of guesses used when won
const RAREDLE_STREAK_BONUS = 25;        // per streak day, capped below
const RAREDLE_STREAK_BONUS_CAP = 10;
// Pixel hint: a blurred-down version of the rare's icon that sharpens as guesses are used —
// after guess 4 a 2x2 grid, 3x3 after 5, 4x4 after 6, 8x8 after 7.
const RAREDLE_PIXEL_STEPS = [[7, 8], [6, 4], [5, 3], [4, 2]]; // [guesses used, grid size], highest first
// Categories that can never be the secret rare (guessing them is still allowed). Empty = every category can be picked.
const RAREDLE_EXCLUDED_CATEGORIES = new Set([]);
const RAREDLE_NO_PEEK_BONUS = 125;      // extra points for not opening the Rare Items pages during the game
const RAREDLE_NO_REPEAT_DAYS = 90;
const RAREDLE_CATALOG_URL = "https://sctp.nl/data/rare-items.json";

// Sources whose rares are never picked as the secret rare (hand-picked list; guessing them is still fine).
// Matched against each part of an item's "obtained from" text (split on / and ,), ignoring case and punctuation.
const RAREDLE_EXCLUDED_SOURCES = [
	"Pocket Pals Claw Machine", "Gills' Fishing Rod", "May the 4th Plushie Chance Box", "Easter 2025 Event", "D6",
	"Menagerie Sword", "White Cat & Associates Chance Box", "May the 4th Chance Box", "Archangel Armor Set",
];

let raredleCatalogCache = { at: 0, items: null, byId: null, derived: new Set() };

// Rares whose "obtained from" is another rare (e.g. things dropped by "Demonic Wishing Eye" or "Infested Axe").
// They're never picked as the secret rare — guessing them is still fine. The origin text is matched
// against every rare's name, word for word.
function raredleDerivedIds(items) {
	const norm = (s) => String(s == null ? "" : s).toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
	const names = new Map(); // normalised name -> ids
	for (const i of items) {
		const n = norm(i.name);
		if (n.length < 4) continue;
		if (!names.has(n)) names.set(n, []);
		names.get(n).push(i.id);
	}
	const out = new Set();
	for (const i of items) {
		const src = norm(i.obtainedFrom);
		if (!src || src === "null") continue;
		const words = src.split(" ");
		let found = false;
		for (let a = 0; a < words.length && !found; a++) {
			for (let b = a; b < Math.min(words.length, a + 8) && !found; b++) {
				const ids = names.get(words.slice(a, b + 1).join(" "));
				if (ids && ids.some((id) => id !== i.id)) found = true;
			}
		}
		if (found) out.add(i.id);
	}
	// plus everything from the hand-picked excluded sources
	const excluded = new Set(RAREDLE_EXCLUDED_SOURCES.map(norm));
	for (const i of items) {
		const v = String(i.obtainedFrom == null ? "" : i.obtainedFrom);
		if (v.split(/\s*[\/,]\s*/).some((part) => excluded.has(norm(part)))) out.add(i.id);
	}
	return out;
}

async function getRareCatalog() {
	if (raredleCatalogCache.items && Date.now() - raredleCatalogCache.at < 30 * 60 * 1000) return raredleCatalogCache;
	const res = await fetch(RAREDLE_CATALOG_URL, { cf: { cacheTtl: 1800, cacheEverything: true } });
	if (!res.ok) throw new Error("catalog unavailable");
	const items = await res.json();
	const byId = new Map(items.map((i) => [i.id, i]));
	raredleCatalogCache = { at: Date.now(), items, byId, derived: raredleDerivedIds(items) };
	return raredleCatalogCache;
}

function raredleToday() { return new Date().toISOString().slice(0, 10); }

// "Feb 2026" -> [{y, m: 1}]; "2025" -> [{y, m: null}]; "Apr 2025 / Jan 2026" -> both; missing -> []
function raredleReleases(s) {
	const str = String(s == null ? "" : s).trim();
	if (!str || str === "null") return [];
	const out = [];
	const re = /([A-Za-z]{3})[a-z]*\s+(\d{4})|(\d{4})/g;
	let m;
	while ((m = re.exec(str))) {
		if (m[1]) {
			const idx = "jan feb mar apr may jun jul aug sep oct nov dec".indexOf(m[1].toLowerCase());
			out.push(idx >= 0 ? { y: +m[2], m: idx / 4 } : { y: +m[2], m: null });
		} else out.push({ y: +m[3], m: null });
	}
	return out;
}

function raredleWords(s) {
	return String(s == null ? "" : s).toLowerCase().replace(/wldcard/g, "wildcard")
		.split(/[^a-z0-9]+/).filter((w) => w.length >= 4);
}
function raredleParts(s) {
	const v = String(s == null ? "" : s).trim();
	if (!v || v.toLowerCase() === "null") return [];
	return v.split(/[\/,&]| and /i).map((p) => p.toLowerCase().replace(/wldcard/g, "wildcard").replace(/\s+/g, " ").trim()).filter(Boolean);
}

// A lot of rares share every compared field with some other rare (decor sets and recolors,
// mostly, that only really differ by name/icon) — so a wrong guess can legitimately show
// green on all six rows. This is the player-facing heads-up for exactly that: every row
// came back "correct" (green), but the guess still isn't the actual secret rare.
const RAREDLE_TWIN_NOTE = "Exact match on the categories, but not the same rare!";
function raredleTwinNote(feedback, guessId, answerId) {
	if (guessId === answerId) return null;
	return feedback.length > 0 && feedback.every((f) => f.s === "correct") ? RAREDLE_TWIN_NOTE : null;
}

// state: correct (green) | close (yellow) | wrong (red) | none (grey, unknown); dir: the answer is "up" (later) / "down" (earlier)
function raredleCompare(g, a) {
	const out = [];
	const eq = (x, y) => String(x || "").trim().toLowerCase() === String(y || "").trim().toLowerCase();

	out.push({ k: "category", v: g.category || "?", s: !g.category || !a.category ? "none" : eq(g.category, a.category) ? "correct" : "wrong" });

	const gr = raredleReleases(g.releaseDate), ar = raredleReleases(a.releaseDate);
	let rel = { k: "released", v: g.releaseDate && g.releaseDate !== "null" ? g.releaseDate : "?", s: "none" };
	if (gr.length && ar.length) {
		if (eq(g.releaseDate, a.releaseDate)) rel.s = "correct";
		else {
			// closest pair of dates wins (a release can span several dates)
			let best = null;
			for (const x of gr) for (const y of ar) {
				let d, exact;
				if (x.m !== null && y.m !== null) { d = (y.y * 12 + y.m) - (x.y * 12 + x.m); exact = d === 0; }
				else { d = (y.y - x.y) * 12; exact = d === 0 && x.m === null && y.m === null; }
				const near = x.m !== null && y.m !== null ? Math.abs(d) <= 3 : Math.abs(d) <= 12;
				const score = Math.abs(d);
				if (!best || score < best.score) best = { score, d, exact, near };
			}
			rel.s = best.exact ? "correct" : best.near ? "close" : "wrong";
			if (best.d !== 0) rel.d = best.d > 0 ? "up" : "down";
		}
	}
	out.push(rel);

	const gp = raredleParts(g.obtainedFrom), ap = raredleParts(a.obtainedFrom);
	let src = "none";
	if (gp.length && ap.length) {
		if (gp.length === ap.length && gp.every((p) => ap.includes(p))) src = "correct";
		else if (gp.some((p) => ap.includes(p))) src = "close";
		else {
			const gw = new Set(gp.flatMap(raredleWords));
			src = ap.flatMap(raredleWords).some((w) => gw.has(w)) ? "close" : "wrong";
		}
	}
	out.push({ k: "obtained", v: g.obtainedFrom && g.obtainedFrom !== "null" ? g.obtainedFrom : "?", s: src });

	const gs = String(g.typeSlot || "").trim(), as = String(a.typeSlot || "").trim();
	let slot = "none";
	if (gs && as && gs !== "null" && as !== "null") {
		if (eq(gs, as)) slot = "correct";
		else {
			const gw = new Set(raredleWords(gs));
			slot = raredleWords(as).some((w) => gw.has(w)) ? "close" : "wrong";
		}
	}
	out.push({ k: "slot", v: gs && gs !== "null" ? gs : "?", s: slot });

	const gd = String(g.dyeable || "").trim(), ad = String(a.dyeable || "").trim();
	out.push({ k: "dyeable", v: gd && gd !== "null" ? gd : "?", s: !gd || !ad || gd === "null" || ad === "null" ? "none" : eq(gd, ad) ? "correct" : "wrong" });

	const gg = String(g.glowParticles || "").trim(), ag = String(a.glowParticles || "").trim();
	let glow = "none";
	if (gg && ag && gg !== "null" && ag !== "null") {
		const gNone = eq(gg, "none"), aNone = eq(ag, "none");
		glow = eq(gg, ag) ? "correct" : (!gNone && !aNone) ? "close" : "wrong";
	}
	out.push({ k: "particles", v: gg && gg !== "null" ? gg : "?", s: glow });
	return out;
}

// True if every one of the six fields Rare-dle actually compares (category, released,
// obtained from, type/slot, dyeable, particles) is known — catalogued with a real value,
// not missing/"null", and not marked uncertain with a "?" (e.g. "Escargold?"). Anything
// less makes for an unfair puzzle (a category the player can never get feedback on, or
// worse, misleading feedback), so only items that pass this can be the secret rare of the
// day — guessing an item that fails it is still completely fine.
function raredleFullyKnown(i) {
	return [i.category, i.releaseDate, i.obtainedFrom, i.typeSlot, i.dyeable, i.glowParticles]
		.every((v) => v != null && String(v).trim() !== "" && String(v) !== "null" && !String(v).includes("?"));
}

async function raredleAnswerFor(env, date) {
	const row = await env.DB.prepare("SELECT itemId FROM raredleAnswers WHERE date = ?").bind(date).first();
	if (row) return row.itemId;
	const cat = await getRareCatalog();
	const { results: recent } = await env.DB.prepare("SELECT itemId FROM raredleAnswers ORDER BY date DESC LIMIT ?").bind(RAREDLE_NO_REPEAT_DAYS).all();
	const used = new Set(recent.map((r) => r.itemId));
	// Only items with every attribute known (see raredleFullyKnown) make for a fair puzzle.
	let pool = cat.items.filter((i) => i.texture && !cat.derived.has(i.id) && !RAREDLE_EXCLUDED_CATEGORIES.has(i.category) && raredleFullyKnown(i) && !used.has(i.id));
	if (!pool.length) pool = cat.items.filter((i) => !cat.derived.has(i.id) && !RAREDLE_EXCLUDED_CATEGORIES.has(i.category) && raredleFullyKnown(i));
	const pick = pool[crypto.getRandomValues(new Uint32Array(1))[0] % pool.length].id;
	await env.DB.prepare("INSERT OR IGNORE INTO raredleAnswers (date, itemId) VALUES (?, ?)").bind(date, pick).run();
	const stored = await env.DB.prepare("SELECT itemId FROM raredleAnswers WHERE date = ?").bind(date).first();
	return stored.itemId;
}

// Monday 00:00 UTC on/before the given YYYY-MM-DD — the "this week" leaderboard's
// reset point (Sunday night into Monday, same UTC-midnight convention the daily
// puzzle itself already resets on).
function raredleWeekStart(dateStr) {
	const d = new Date(dateStr + "T00:00:00Z");
	const sinceMonday = (d.getUTCDay() + 6) % 7; // Mon=0 ... Sun=6
	d.setUTCDate(d.getUTCDate() - sinceMonday);
	return d.toISOString().slice(0, 10);
}

function computeRaredleStreaks(games, today) {
	// games: [{date, status}] (finished only). A streak is consecutive days with a win.
	const won = new Set(games.filter((g) => g.status === "won").map((g) => g.date));
	const dayMs = 24 * 60 * 60 * 1000;
	const shift = (d, n) => new Date(Date.parse(d + "T00:00:00Z") + n * dayMs).toISOString().slice(0, 10);
	let best = 0, run = 0, prev = null;
	for (const d of [...won].sort()) {
		run = prev && shift(prev, 1) === d ? run + 1 : 1;
		if (run > best) best = run;
		prev = d;
	}
	// current: ends today, or yesterday if today isn't played/finished yet
	let cur = 0, d = won.has(today) ? today : shift(today, -1);
	while (won.has(d)) { cur++; d = shift(d, -1); }
	return { current: cur, best };
}

async function raredleStats(env, accountId, today) {
	const { results } = await env.DB.prepare("SELECT date, status, guessCount, score FROM raredleGames WHERE accountId = ? AND status IN ('won','lost')").bind(accountId).all();
	const wins = results.filter((r) => r.status === "won");
	const streaks = computeRaredleStreaks(results, today);
	return {
		played: results.length, wins: wins.length,
		winRate: results.length ? Math.round((wins.length / results.length) * 100) : 0,
		avgGuesses: wins.length ? Math.round((wins.reduce((a, r) => a + r.guessCount, 0) / wins.length) * 10) / 10 : null,
		points: results.reduce((a, r) => a + r.score, 0),
		streak: streaks.current, bestStreak: streaks.best,
	};
}

async function raredleStatePayload(env, admin) {
	const today = raredleToday();
	const cat = await getRareCatalog();
	const answerId = await raredleAnswerFor(env, today);
	const answer = cat.byId.get(answerId);
	let game = await env.DB.prepare("SELECT * FROM raredleGames WHERE accountId = ? AND date = ?").bind(admin.id, today).first();
	const ids = game ? JSON.parse(game.guesses) : [];
	const guesses = ids.map((id) => {
		const g = cat.byId.get(id);
		if (!g) return null;
		const feedback = raredleCompare(g, answer);
		return { itemId: id, name: g.name, texture: g.texture, feedback, note: raredleTwinNote(feedback, id, answerId) };
	}).filter(Boolean);
	const status = game ? game.status : "playing";
	const out = {
		date: today, maxGuesses: RAREDLE_MAX_GUESSES, status, guesses,
		pixelHint: status === "playing" ? await raredlePixelHint(answer, guesses.length) : null,
		pixelSteps: RAREDLE_PIXEL_STEPS.slice().reverse(),
		noPeek: { bonus: RAREDLE_NO_PEEK_BONUS, lost: !!(game && game.usedRares) },
		score: game ? game.score : 0,
		stats: await raredleStats(env, admin.id, today),
	};
	if (status !== "playing") out.answer = { id: answer.id, name: answer.name, texture: answer.texture, effect: answer.effect || "", category: answer.category, releaseDate: answer.releaseDate, obtainedFrom: answer.obtainedFrom };
	return out;
}

async function handleRaredleState(request, env) {
	const url = new URL(request.url);
	const isPractice = url.searchParams.get("mode") === "practice";
	if (!isPractice) {
		// Daily stays login-only.
		const auth = await requireAnyAdmin(request, env);
		if (!auth.ok) return auth.response;
		try { return json(await raredleStatePayload(env, auth.admin)); }
		catch (e) { return json({ error: "Rare-dle isn't available right now, try again in a minute." }, 502); }
	}
	if (!RAREDLE_PRACTICE_ENABLED) return json({ error: "Practice rounds aren't available right now." }, 403);
	try {
		const cat = await getRareCatalog();
		const auth = await requireAnyAdmin(request, env);
		if (auth.ok) return json(await raredlePracticePayload(env, auth.admin, cat, null));
		// Logged out: state rides in the token, not a DB row.
		const token = url.searchParams.get("token");
		let payload = token ? await raredleVerifyPracticeToken(env, token) : null;
		if (!payload) payload = raredleNewAnonPractice(cat);
		return json(await raredlePracticeAnonPayload(env, cat, payload));
	} catch (e) { return json({ error: "Rare-dle isn't available right now, try again in a minute." }, 502); }
}

// ---- practice rounds: unlimited random rares, one open round per account,
// never counted toward points, streaks or leaderboards.
function raredlePracticePool(cat) {
	const pool = cat.items.filter((i) => i.texture && i.category && !cat.derived.has(i.id) && !RAREDLE_EXCLUDED_CATEGORIES.has(i.category) && i.releaseDate && i.releaseDate !== "null" && i.obtainedFrom && i.obtainedFrom !== "null" && i.typeSlot && i.typeSlot !== "null");
	return pool.length ? pool : cat.items.filter((i) => !cat.derived.has(i.id) && !RAREDLE_EXCLUDED_CATEGORIES.has(i.category));
}

// ---- anonymous practice: logged-out players get practice too, but there's no
// accountId to key a DB row on, so the round's state (answer + guesses so far)
// rides along in a signed token the client holds and echoes back on every
// request instead. HMAC-SHA256 over a base64url JSON payload, keyed by the
// dedicated RAREDLE_PRACTICE_SECRET (never reused elsewhere) so a forged token
// can only ever pick its own answerId out of thin air, never anything sensitive.
function raredleB64urlEncode(str) {
	const bytes = new TextEncoder().encode(str);
	let bin = "";
	bytes.forEach((b) => { bin += String.fromCharCode(b); });
	return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function raredleB64urlDecode(str) {
	str = str.replace(/-/g, "+").replace(/_/g, "/");
	while (str.length % 4) str += "=";
	const bin = atob(str);
	const bytes = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
	return new TextDecoder().decode(bytes);
}
async function raredlePracticeHmac(env, data) {
	const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.RAREDLE_PRACTICE_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
	const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
	return bufToHex(sig);
}
async function raredleSignPracticeToken(env, payload) {
	const payloadB64 = raredleB64urlEncode(JSON.stringify(payload));
	return payloadB64 + "." + await raredlePracticeHmac(env, payloadB64);
}
async function raredleVerifyPracticeToken(env, token) {
	if (!token || typeof token !== "string") return null;
	const dot = token.lastIndexOf(".");
	if (dot < 0) return null;
	const payloadB64 = token.slice(0, dot), sig = token.slice(dot + 1);
	try {
		const expected = await raredlePracticeHmac(env, payloadB64);
		if (!timingSafeEqualHex(sig, expected)) return null;
		const parsed = JSON.parse(raredleB64urlDecode(payloadB64));
		if (!parsed || typeof parsed.answerId !== "string" || !Array.isArray(parsed.guesses)) return null;
		return parsed;
	} catch (e) { return null; }
}
function raredleNewAnonPractice(cat) {
	const pool = raredlePracticePool(cat);
	const answerId = pool[crypto.getRandomValues(new Uint32Array(1))[0] % pool.length].id;
	return { answerId, guesses: [], status: "playing" };
}
async function raredlePracticeAnonPayload(env, cat, payload) {
	const answer = cat.byId.get(payload.answerId);
	const guesses = payload.guesses.map((id) => {
		const g = cat.byId.get(id);
		if (!g) return null;
		const feedback = raredleCompare(g, answer);
		return { itemId: id, name: g.name, texture: g.texture, feedback, note: raredleTwinNote(feedback, id, payload.answerId) };
	}).filter(Boolean);
	const out = {
		date: raredleToday(), mode: "practice", maxGuesses: RAREDLE_MAX_GUESSES, status: payload.status, guesses,
		pixelHint: payload.status === "playing" ? await raredlePixelHint(answer, guesses.length) : null,
		pixelSteps: RAREDLE_PIXEL_STEPS.slice().reverse(),
		noPeek: null, score: 0,
		stats: { played: 0, wins: 0, winRate: 0, avgGuesses: null, streak: 0, bestStreak: 0, points: 0 },
		practiceToken: await raredleSignPracticeToken(env, payload),
	};
	if (payload.status !== "playing") out.answer = { id: answer.id, name: answer.name, texture: answer.texture, effect: answer.effect || "", category: answer.category, releaseDate: answer.releaseDate, obtainedFrom: answer.obtainedFrom };
	return out;
}
async function raredlePracticeAnonGuess(env, cat, itemId, token) {
	const guess = cat.byId.get(itemId);
	if (!guess) return json({ error: "Pick a rare from the list." }, 400);
	let payload = token ? await raredleVerifyPracticeToken(env, token) : null;
	if (!payload) payload = raredleNewAnonPractice(cat);
	if (payload.status !== "playing") return json({ error: "This practice round is over — start a new one." }, 409);
	if (payload.guesses.includes(itemId)) return json({ error: "You already guessed that one." }, 409);
	payload.guesses.push(itemId);
	payload.status = itemId === payload.answerId ? "won" : payload.guesses.length >= RAREDLE_MAX_GUESSES ? "lost" : "playing";
	return json(await raredlePracticeAnonPayload(env, cat, payload));
}

async function raredleNewPractice(env, accountId, cat) {
	const pool = raredlePracticePool(cat);
	const answerId = pool[crypto.getRandomValues(new Uint32Array(1))[0] % pool.length].id;
	await env.DB.prepare("INSERT OR REPLACE INTO raredlePractice (accountId, answerId, guesses, status, guessCount, updatedAt) VALUES (?, ?, '[]', 'playing', 0, ?)")
		.bind(accountId, answerId, new Date().toISOString()).run();
	return env.DB.prepare("SELECT * FROM raredlePractice WHERE accountId = ?").bind(accountId).first();
}

async function raredlePracticePayload(env, admin, cat, row) {
	if (!row) row = await env.DB.prepare("SELECT * FROM raredlePractice WHERE accountId = ?").bind(admin.id).first();
	if (!row) row = await raredleNewPractice(env, admin.id, cat);
	const answer = cat.byId.get(row.answerId);
	const ids = JSON.parse(row.guesses);
	const guesses = ids.map((id) => {
		const g = cat.byId.get(id);
		if (!g) return null;
		const feedback = raredleCompare(g, answer);
		return { itemId: id, name: g.name, texture: g.texture, feedback, note: raredleTwinNote(feedback, id, row.answerId) };
	}).filter(Boolean);
	const out = {
		date: raredleToday(), mode: "practice", maxGuesses: RAREDLE_MAX_GUESSES, status: row.status, guesses,
		pixelHint: row.status === "playing" ? await raredlePixelHint(answer, guesses.length) : null,
		pixelSteps: RAREDLE_PIXEL_STEPS.slice().reverse(),
		noPeek: null, score: 0,
		stats: await raredleStats(env, admin.id, raredleToday()),
	};
	if (row.status !== "playing") out.answer = { id: answer.id, name: answer.name, texture: answer.texture, effect: answer.effect || "", category: answer.category, releaseDate: answer.releaseDate, obtainedFrom: answer.obtainedFrom };
	return out;
}

async function handleRaredlePracticeNew(request, env) {
	if (!RAREDLE_PRACTICE_ENABLED) return json({ error: "Practice rounds aren't available right now." }, 403);
	try {
		const cat = await getRareCatalog();
		const auth = await requireAnyAdmin(request, env);
		if (auth.ok) {
			const row = await raredleNewPractice(env, auth.admin.id, cat);
			return json(await raredlePracticePayload(env, auth.admin, cat, row));
		}
		return json(await raredlePracticeAnonPayload(env, cat, raredleNewAnonPractice(cat)));
	} catch (e) { return json({ error: "Rare-dle isn't available right now, try again in a minute." }, 502); }
}

async function raredlePracticeGuess(env, admin, cat, itemId) {
	const guess = cat.byId.get(itemId);
	if (!guess) return json({ error: "Pick a rare from the list." }, 400);
	let row = await env.DB.prepare("SELECT * FROM raredlePractice WHERE accountId = ?").bind(admin.id).first();
	if (!row) row = await raredleNewPractice(env, admin.id, cat);
	if (row.status !== "playing") return json({ error: "This practice round is over — start a new one." }, 409);
	const ids = JSON.parse(row.guesses);
	if (ids.includes(itemId)) return json({ error: "You already guessed that one." }, 409);
	ids.push(itemId);
	const status = itemId === row.answerId ? "won" : ids.length >= RAREDLE_MAX_GUESSES ? "lost" : "playing";
	await env.DB.prepare("UPDATE raredlePractice SET guesses = ?, guessCount = ?, status = ?, updatedAt = ? WHERE accountId = ? AND guessCount = ? AND status = 'playing'")
		.bind(JSON.stringify(ids), ids.length, status, new Date().toISOString(), admin.id, row.guessCount).run();
	return json(await raredlePracticePayload(env, admin, cat, null));
}

// QA only: throw away the caller's game for today so it can be played again.
async function handleRaredleReset(request, env) {
	if (!RAREDLE_ALLOW_RESET) return json({ error: "Resetting isn't available." }, 403);
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	await env.DB.prepare("DELETE FROM raredleGames WHERE accountId = ? AND date = ?").bind(auth.admin.id, raredleToday()).run();
	try { return json(await raredleStatePayload(env, auth.admin)); } catch (e) { return json({ error: "Rare-dle isn't available right now, try again in a minute." }, 502); }
}

async function handleRaredleGuess(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const itemId = String(body.itemId || "");
	let cat;
	try { cat = await getRareCatalog(); } catch (e) { return json({ error: "Rare-dle isn't available right now, try again in a minute." }, 502); }

	if (body.mode === "practice") {
		if (!RAREDLE_PRACTICE_ENABLED) return json({ error: "Practice rounds aren't available right now." }, 403);
		const auth = await requireAnyAdmin(request, env);
		if (auth.ok) return raredlePracticeGuess(env, auth.admin, cat, itemId);
		return raredlePracticeAnonGuess(env, cat, itemId, body.practiceToken);
	}

	// Daily stays login-only.
	const auth = await requireAnyAdmin(request, env);
	if (!auth.ok) return auth.response;
	const guess = cat.byId.get(itemId);
	if (!guess) return json({ error: "Pick a rare from the list." }, 400);

	const today = raredleToday();
	const answerId = await raredleAnswerFor(env, today);
	const answer = cat.byId.get(answerId);
	let game = await env.DB.prepare("SELECT * FROM raredleGames WHERE accountId = ? AND date = ?").bind(auth.admin.id, today).first();
	const now = new Date().toISOString();
	if (!game) {
		await env.DB.prepare("INSERT OR IGNORE INTO raredleGames (accountId, date, startedAt) VALUES (?, ?, ?)").bind(auth.admin.id, today, now).run();
		game = await env.DB.prepare("SELECT * FROM raredleGames WHERE accountId = ? AND date = ?").bind(auth.admin.id, today).first();
	}
	if (game.status !== "playing") return json({ error: "You've already finished today's Rare-dle." }, 409);
	// The site tells us when the player opened a Rare Items page during this game. Once seen it sticks.
	const usedRares = game.usedRares || body.peeked === true ? 1 : 0;
	const ids = JSON.parse(game.guesses);
	if (ids.includes(itemId)) return json({ error: "You already guessed that one." }, 409);

	ids.push(itemId);
	let status = "playing", score = 0, finishedAt = null;
	if (itemId === answerId) status = "won";
	else if (ids.length >= RAREDLE_MAX_GUESSES) status = "lost";
	if (status !== "playing") finishedAt = now;
	if (status === "won") {
		const { results } = await env.DB.prepare("SELECT date, status FROM raredleGames WHERE accountId = ? AND status IN ('won','lost') AND date != ?").bind(auth.admin.id, today).all();
		const streak = computeRaredleStreaks([...results, { date: today, status: "won" }], today).current;
		score = RAREDLE_BASE_POINTS[Math.min(ids.length, RAREDLE_MAX_GUESSES) - 1] + Math.min(streak, RAREDLE_STREAK_BONUS_CAP) * RAREDLE_STREAK_BONUS + (usedRares ? 0 : RAREDLE_NO_PEEK_BONUS);
	}
	// The status = 'playing' guard makes a double-submit race harmless.
	const res = await env.DB.prepare("UPDATE raredleGames SET guesses = ?, guessCount = ?, status = ?, score = ?, finishedAt = ?, usedRares = ? WHERE accountId = ? AND date = ? AND status = 'playing' AND guessCount = ?")
		.bind(JSON.stringify(ids), ids.length, status, score, finishedAt, usedRares, auth.admin.id, today, game.guessCount).run();
	if (res.meta.changes === 0) return json({ error: "That guess didn't go through — try again." }, 409);
	return json(await raredleStatePayload(env, auth.admin));
}

async function handleRaredleLeaderboard(request, env, ctx) {
	return cachedGet(request, ctx, 60, async () => {
		const today = raredleToday();
		const weekStart = raredleWeekStart(today);
		const { results } = await env.DB.prepare(
			`SELECT g.accountId, a.username, g.date, g.status, g.guessCount, g.score, g.startedAt, g.finishedAt
			 FROM raredleGames g JOIN admins a ON a.id = g.accountId WHERE g.status IN ('won','lost')`
		).all();

		const daily = results.filter((r) => r.date === today && r.status === "won")
			.sort((x, y) => y.score - x.score || x.guessCount - y.guessCount || String(x.finishedAt).localeCompare(String(y.finishedAt)))
			.slice(0, 25).map((r, i) => ({ rank: i + 1, username: r.username, guesses: r.guessCount, score: r.score }));

		function groupByUser(rowsIn) {
			const byUser = new Map();
			for (const r of rowsIn) {
				let e = byUser.get(r.accountId);
				if (!e) { e = { username: r.username, games: [] }; byUser.set(r.accountId, e); }
				e.games.push(r);
			}
			return [...byUser.values()];
		}
		function pointsRow(e) {
			const wins = e.games.filter((g) => g.status === "won");
			return {
				username: e.username, points: e.games.reduce((a, g) => a + g.score, 0),
				played: e.games.length, wins: wins.length,
				avgGuesses: wins.length ? Math.round((wins.reduce((a, g) => a + g.guessCount, 0) / wins.length) * 10) / 10 : null,
			};
		}
		const rankByPoints = (rows) => rows.slice().sort((a, b) => b.points - a.points || b.wins - a.wins).slice(0, 25).map((r, i) => ({ rank: i + 1, ...r }));

		// All-time keeps its win-streak columns (an ongoing, not calendar-scoped
		// stat); "this week" is a fresh points race that resets Monday, so no streak there.
		const allTime = rankByPoints(groupByUser(results).map((e) => {
			const st = computeRaredleStreaks(e.games, today);
			return { ...pointsRow(e), streak: st.current, bestStreak: st.best };
		}));
		const week = rankByPoints(groupByUser(results.filter((r) => r.date >= weekStart)).map(pointsRow));

		return { date: today, weekStart, daily, week, allTime, playersToday: results.filter((r) => r.date === today).length };
	});
}

// ---------------- verification links ----------------

// Head admin: body {mcUsername, days?} -> {token, url, expiresAt}
async function handleAdminCreateVerificationLink(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const mcUsername = String(body.mcUsername || "").trim();
	if (!isValidClaimedMcUsername(mcUsername)) return json({ error: "That doesn't look like a valid Minecraft username" }, 400);
	const days = Math.min(365, Math.max(1, Math.floor(Number(body.days) || 7)));
	const token = newToken();
	const now = new Date();
	const expiresAt = new Date(now.getTime() + days * 24 * 60 * 60 * 1000).toISOString();
	await env.DB.prepare("INSERT INTO verificationLinks (token, mcUsername, createdBy, createdAt, expiresAt) VALUES (?, ?, ?, ?, ?)")
		.bind(token, mcUsername, auth.admin ? auth.admin.username : "master", now.toISOString(), expiresAt).run();
	return json({ token, url: VERIFICATION_LINK_BASE_URL + token, mcUsername, expiresAt });
}

async function handleAdminListVerificationLinks(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM verificationLinks ORDER BY createdAt DESC LIMIT 100").all();
	const now = Date.now();
	return json(results.map((l) => {
		const status = l.usedAt ? "used" : Date.parse(l.expiresAt) < now ? "expired" : "active";
		return {
			mcUsername: l.mcUsername, createdBy: l.createdBy, createdAt: l.createdAt, expiresAt: l.expiresAt, usedAt: l.usedAt || null,
			status, token: status === "active" ? l.token : null, url: status === "active" ? VERIFICATION_LINK_BASE_URL + l.token : null,
		};
	}));
}

async function handleAdminRevokeVerificationLink(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const res = await env.DB.prepare("DELETE FROM verificationLinks WHERE token = ? AND usedAt IS NULL").bind(String(body.token || "")).run();
	if (res.meta.changes === 0) return json({ error: "No such unused link" }, 404);
	return json({ ok: true });
}

async function loadUsableVerificationLink(env, token) {
	const link = await env.DB.prepare("SELECT * FROM verificationLinks WHERE token = ?").bind(String(token || "")).first();
	if (!link) return { error: json({ error: "This link doesn't exist." }, 404) };
	if (link.usedAt) return { error: json({ error: "This link has already been used." }, 410) };
	if (Date.parse(link.expiresAt) < Date.now()) return { error: json({ error: "This link has expired." }, 410) };
	return { link };
}

// Public — lets the landing page show which username it's about.
async function handleVerificationLinkInfo(request, env) {
	const token = new URL(request.url).searchParams.get("token");
	const res = await loadUsableVerificationLink(env, token);
	if (res.error) return res.error;
	return json({ mcUsername: res.link.mcUsername, expiresAt: res.link.expiresAt });
}

// body: {token, mode: "register"|"link", username?, password}
//  register: creates a brand-new, already-verified account (login username
//    defaults to the MC username; pick another if it's taken).
//  link: logs into an existing account with username+password and marks its
//    MC username verified. Either way the link is single-use.
async function handleRedeemVerificationLink(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const password = String(body.password || "");
	const mode = body.mode === "link" ? "link" : "register";
	const usable = await loadUsableVerificationLink(env, body.token);
	if (usable.error) return usable.error;
	const link = usable.link;

	const holder = await env.DB.prepare(
		"SELECT id, username FROM admins WHERE mcVerified = 1 AND lower(ltrim(mcUsername, '.')) = lower(ltrim(?, '.'))"
	).bind(link.mcUsername).first();

	let accountId, accountUsername;
	const now = new Date().toISOString();
	if (mode === "link") {
		const username = String(body.username || "").trim();
		const admin = await env.DB.prepare("SELECT * FROM admins WHERE lower(username) = lower(?)").bind(username).first();
		if (!admin || !(await verifyPassword(password, admin.passwordSalt, admin.passwordHash))) {
			return json({ error: "Invalid username or password" }, 401);
		}
		if (holder && holder.id !== admin.id) return json({ error: "That Minecraft username is already verified on another account." }, 409);
		accountId = admin.id; accountUsername = admin.username;
	} else {
		if (holder) return json({ error: "That Minecraft username is already verified on another account." }, 409);
		if (password.length < 8) return json({ error: "password must be at least 8 characters" }, 400);
		const username = String(body.username || "").trim() || link.mcUsername;
		if (!isValidClaimedMcUsername(username)) return json({ error: "Login username may only contain letters, digits and underscores (max 16)." }, 400);
		const taken = await env.DB.prepare("SELECT id FROM admins WHERE lower(username) = lower(?)").bind(username).first();
		if (taken) return json({ error: "That login username is already taken — pick a different one." }, 409);
		accountId = crypto.randomUUID(); accountUsername = username;
	}

	// Claim the single use before doing anything irreversible-ish.
	const claimed = await env.DB.prepare("UPDATE verificationLinks SET usedAt = ?, usedByAccountId = ? WHERE token = ? AND usedAt IS NULL")
		.bind(now, accountId, link.token).run();
	if (claimed.meta.changes === 0) return json({ error: "This link has already been used." }, 410);

	try {
		if (mode === "link") {
			await env.DB.prepare("UPDATE admins SET mcUsername = ?, mcVerified = 1 WHERE id = ?").bind(link.mcUsername, accountId).run();
		} else {
			const salt = newSaltHex();
			const hash = await hashPassword(password, salt);
			await env.DB.prepare(
				"INSERT INTO admins (id, username, passwordHash, passwordSalt, isHeadAdmin, permissions, createdAt, createdBy, mcUsername, mcVerified) VALUES (?, ?, ?, ?, 0, '[]', ?, 'verification-link', ?, 1)"
			).bind(accountId, accountUsername, hash, salt, now, link.mcUsername).run();
		}
	} catch (e) {
		await env.DB.prepare("UPDATE verificationLinks SET usedAt = NULL, usedByAccountId = NULL WHERE token = ?").bind(link.token).run();
		return json({ error: "Couldn't finish — please try again." }, 502);
	}

	await mapartAutoClaimSweep(env, accountId, link.mcUsername);

	const token = newToken();
	const expiresAt = new Date(Date.now() + ADMIN_SESSION_TTL_MS).toISOString();
	await env.DB.prepare("INSERT INTO adminSessions (token, adminId, createdAt, expiresAt) VALUES (?, ?, ?, ?)")
		.bind(token, accountId, now, expiresAt).run();
	const acct = await env.DB.prepare("SELECT isHeadAdmin, permissions FROM admins WHERE id = ?").bind(accountId).first();
	let permissions = [];
	try { permissions = JSON.parse(acct.permissions || "[]"); } catch (e) { /* ignore */ }
	return json({ token, username: accountUsername, isHeadAdmin: !!acct.isHeadAdmin, permissions, expiresAt, mcUsername: link.mcUsername, mcVerified: true });
}

// ---------------- password reset links ----------------
// Same mechanic as verification links just above: a head admin mints a
// single-use, expiring link for an existing account, sends it to the player
// privately (there's no outbound email here), and whoever opens it can set a
// brand-new password — no need to know the old one.

// Head admin: body {username, days?} -> {token, url, username, expiresAt}
async function handleAdminCreatePasswordResetLink(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const username = String(body.username || "").trim();
	if (!username) return json({ error: "username is required" }, 400);
	const account = await env.DB.prepare("SELECT id, username FROM admins WHERE lower(username) = lower(?)").bind(username).first();
	if (!account) return json({ error: "No account with that username" }, 404);
	const days = Math.min(365, Math.max(1, Math.floor(Number(body.days) || 7)));
	const token = newToken();
	const now = new Date();
	const expiresAt = new Date(now.getTime() + days * 24 * 60 * 60 * 1000).toISOString();
	await env.DB.prepare("INSERT INTO passwordResetLinks (token, accountId, username, createdBy, createdAt, expiresAt) VALUES (?, ?, ?, ?, ?, ?)")
		.bind(token, account.id, account.username, auth.admin ? auth.admin.username : "master", now.toISOString(), expiresAt).run();
	return json({ token, url: PASSWORD_RESET_LINK_BASE_URL + token, username: account.username, expiresAt });
}

async function handleAdminListPasswordResetLinks(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare("SELECT * FROM passwordResetLinks ORDER BY createdAt DESC LIMIT 100").all();
	const now = Date.now();
	return json(results.map((l) => {
		const status = l.usedAt ? "used" : Date.parse(l.expiresAt) < now ? "expired" : "active";
		return {
			username: l.username, createdBy: l.createdBy, createdAt: l.createdAt, expiresAt: l.expiresAt, usedAt: l.usedAt || null,
			status, token: status === "active" ? l.token : null, url: status === "active" ? PASSWORD_RESET_LINK_BASE_URL + l.token : null,
		};
	}));
}

async function handleAdminRevokePasswordResetLink(request, env) {
	const auth = await requireAdminAuth(request, env, null);
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const res = await env.DB.prepare("DELETE FROM passwordResetLinks WHERE token = ? AND usedAt IS NULL").bind(String(body.token || "")).run();
	if (res.meta.changes === 0) return json({ error: "No such unused link" }, 404);
	return json({ ok: true });
}

async function loadUsablePasswordResetLink(env, token) {
	const link = await env.DB.prepare("SELECT * FROM passwordResetLinks WHERE token = ?").bind(String(token || "")).first();
	if (!link) return { error: json({ error: "This link doesn't exist." }, 404) };
	if (link.usedAt) return { error: json({ error: "This link has already been used." }, 410) };
	if (Date.parse(link.expiresAt) < Date.now()) return { error: json({ error: "This link has expired." }, 410) };
	return { link };
}

// Public — lets the landing page show which account it's about.
async function handlePasswordResetLinkInfo(request, env) {
	const token = new URL(request.url).searchParams.get("token");
	const res = await loadUsablePasswordResetLink(env, token);
	if (res.error) return res.error;
	return json({ username: res.link.username, expiresAt: res.link.expiresAt });
}

// body: {token, newPassword} -> logs in with the new password, same shape as
// POST /admin/login. Single-use, and every other existing session on the
// account is torn down (same as a normal password change) — the link itself
// is the proof of identity here, so anything a stolen old session could still
// do gets cut off the moment it's redeemed.
async function handlePasswordResetLinkRedeem(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const newPassword = String(body.newPassword || "");
	if (newPassword.length < 8) return json({ error: "newPassword must be at least 8 characters" }, 400);
	const usable = await loadUsablePasswordResetLink(env, body.token);
	if (usable.error) return usable.error;
	const link = usable.link;

	// Claim the single use before doing anything irreversible-ish.
	const now = new Date().toISOString();
	const claimed = await env.DB.prepare("UPDATE passwordResetLinks SET usedAt = ? WHERE token = ? AND usedAt IS NULL").bind(now, link.token).run();
	if (claimed.meta.changes === 0) return json({ error: "This link has already been used." }, 410);

	const salt = newSaltHex();
	const hash = await hashPassword(newPassword, salt);
	await env.DB.prepare("UPDATE admins SET passwordHash = ?, passwordSalt = ? WHERE id = ?").bind(hash, salt, link.accountId).run();
	await env.DB.prepare("DELETE FROM adminSessions WHERE adminId = ?").bind(link.accountId).run();

	const admin = await env.DB.prepare("SELECT * FROM admins WHERE id = ?").bind(link.accountId).first();
	if (!admin) return json({ error: "That account no longer exists." }, 410);
	const token = newToken();
	const expiresAt = new Date(Date.now() + ADMIN_SESSION_TTL_MS).toISOString();
	await env.DB.prepare("INSERT INTO adminSessions (token, adminId, createdAt, expiresAt) VALUES (?, ?, ?, ?)")
		.bind(token, admin.id, now, expiresAt).run();
	let permissions = [];
	try { permissions = JSON.parse(admin.permissions || "[]"); } catch (e) { /* ignore */ }
	return json({
		token, username: admin.username, isHeadAdmin: !!admin.isHeadAdmin, permissions, expiresAt,
		mcUsername: admin.mcUsername || null, mcVerified: !!admin.mcVerified,
	});
}

// ---------------- forms ----------------
// See the "Forms" and "manageForms" doc-comment blocks near the top of this
// file for the full route list and shapes.

function formRowToAdminJson(row) {
	return {
		id: row.id, slug: row.slug, title: row.title, description: row.description || "",
		questions: JSON.parse(row.questionsJson), requireLogin: !!row.requireLogin,
		responsePolicy: row.responsePolicy, status: row.status, closesAt: row.closesAt || null,
		responseLimit: row.responseLimit, confirmationMessage: row.confirmationMessage || "",
		createdBy: row.createdBy, createdAt: row.createdAt, updatedAt: row.updatedAt,
	};
}

// "open" isn't quite the same as "currently accepting responses" — an admin
// can leave status="open" and let closesAt/responseLimit do the actual
// cutoff, so this is re-checked live rather than ever cached on the row.
function formIsAcceptingResponses(form, responseCount) {
	if (form.status !== "open") return false;
	if (form.closesAt && Date.parse(form.closesAt) < Date.now()) return false;
	if (form.responseLimit != null && responseCount >= form.responseLimit) return false;
	return true;
}

// Validates + normalizes a question list at create/update time. Doesn't touch
// D1 — pure validation, shared by create and update.
function validateFormQuestions(questionsIn) {
	if (!Array.isArray(questionsIn) || !questionsIn.length) return { ok: false, error: "At least one question is required." };
	if (questionsIn.length > FORM_MAX_QUESTIONS) return { ok: false, error: `At most ${FORM_MAX_QUESTIONS} questions.` };
	const seen = new Set();
	const cleaned = [];
	for (const qIn of questionsIn) {
		const type = String((qIn && qIn.type) || "");
		if (!FORM_QUESTION_TYPES.has(type)) return { ok: false, error: "Unknown question type: " + type };
		const label = String((qIn && qIn.label) || "").trim();
		if (!label) return { ok: false, error: "Every question needs a label." };
		let id = String((qIn && qIn.id) || "").trim();
		if (!id || seen.has(id)) id = "q_" + crypto.randomUUID().slice(0, 8);
		while (seen.has(id)) id = "q_" + crypto.randomUUID().slice(0, 8);
		seen.add(id);
		const q = { id, type, label: label.slice(0, 300), required: !!(qIn && qIn.required) };
		const help = String((qIn && qIn.help) || "").trim().slice(0, 500);
		if (help) q.help = help;
		if (FORM_CHOICE_TYPES.has(type)) {
			const options = Array.isArray(qIn.options) ? [...new Set(qIn.options.map((o) => String(o).trim()).filter(Boolean))].slice(0, 40) : [];
			if (options.length < 2) return { ok: false, error: `"${label}" needs at least 2 options.` };
			q.options = options;
		}
		if (type === "number") {
			if (qIn.min !== undefined && qIn.min !== null && qIn.min !== "") q.min = Number(qIn.min);
			if (qIn.max !== undefined && qIn.max !== null && qIn.max !== "") q.max = Number(qIn.max);
			if (q.min != null && q.max != null && q.max < q.min) return { ok: false, error: `"${label}"'s max must be at least its min.` };
		}
		if (type === "rating") {
			const min = qIn.min !== undefined && qIn.min !== null && qIn.min !== "" ? Math.floor(Number(qIn.min)) : 1;
			const max = qIn.max !== undefined && qIn.max !== null && qIn.max !== "" ? Math.floor(Number(qIn.max)) : 5;
			if (!Number.isFinite(min) || !Number.isFinite(max) || max <= min || (max - min) > FORM_RATING_MAX_SPAN) {
				return { ok: false, error: `"${label}" needs a valid scale (2-${FORM_RATING_MAX_SPAN + 1} points).` };
			}
			q.min = min; q.max = max;
			const minLabel = String(qIn.minLabel || "").trim().slice(0, 60), maxLabel = String(qIn.maxLabel || "").trim().slice(0, 60);
			if (minLabel) q.minLabel = minLabel;
			if (maxLabel) q.maxLabel = maxLabel;
		}
		cleaned.push(q);
	}
	return { ok: true, cleaned };
}

// Validates a respondent's answers against the form's question list at submit
// time — never trust the client's shape. Returns the same {questionId: value}
// shape back, but coerced/clamped to what each question type actually allows.
function validateFormAnswers(questions, answersIn) {
	const answers = answersIn && typeof answersIn === "object" ? answersIn : {};
	const cleaned = {};
	for (const q of questions) {
		const raw = answers[q.id];
		const empty = raw === undefined || raw === null || raw === "" || (Array.isArray(raw) && raw.length === 0);
		if (empty) {
			if (q.required) return { ok: false, error: `"${q.label}" is required.` };
			continue;
		}
		if (q.type === "short_text" || q.type === "date") {
			cleaned[q.id] = String(raw).slice(0, 500);
		} else if (q.type === "paragraph") {
			cleaned[q.id] = String(raw).slice(0, 5000);
		} else if (q.type === "number") {
			const n = Number(raw);
			if (!Number.isFinite(n)) return { ok: false, error: `"${q.label}" must be a number.` };
			if (q.min != null && n < q.min) return { ok: false, error: `"${q.label}" must be at least ${q.min}.` };
			if (q.max != null && n > q.max) return { ok: false, error: `"${q.label}" must be at most ${q.max}.` };
			cleaned[q.id] = n;
		} else if (q.type === "rating") {
			const n = Number(raw);
			if (!Number.isInteger(n) || n < q.min || n > q.max) return { ok: false, error: `"${q.label}" must be between ${q.min} and ${q.max}.` };
			cleaned[q.id] = n;
		} else if (q.type === "yes_no") {
			const v = String(raw);
			if (v !== "Yes" && v !== "No") return { ok: false, error: `"${q.label}" must be Yes or No.` };
			cleaned[q.id] = v;
		} else if (q.type === "multiple_choice" || q.type === "dropdown") {
			const v = String(raw);
			if (!(q.options || []).includes(v)) return { ok: false, error: `"${q.label}" has an invalid selection.` };
			cleaned[q.id] = v;
		} else if (q.type === "checkboxes") {
			if (!Array.isArray(raw)) return { ok: false, error: `"${q.label}" must be a list.` };
			const opts = new Set(q.options || []);
			const vals = [...new Set(raw.map(String).filter((v) => opts.has(v)))];
			if (!vals.length) return { ok: false, error: `"${q.label}" is required.` };
			cleaned[q.id] = vals;
		}
	}
	return { ok: true, cleaned };
}

function csvEscape(v) {
	const s = v === undefined || v === null ? "" : String(v);
	return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}
function formResponsesToCsv(questions, responses) {
	const header = ["Respondent", "Submitted at", "Updated at", ...questions.map((q) => q.label)];
	const lines = [header.map(csvEscape).join(",")];
	for (const r of responses) {
		const row = [r.respondent, r.submittedAt, r.updatedAt];
		for (const q of questions) {
			const v = r.answers[q.id];
			row.push(Array.isArray(v) ? v.join("; ") : v);
		}
		lines.push(row.map(csvEscape).join(","));
	}
	return lines.join("\r\n");
}

async function handleAdminCreateForm(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }

	const slug = String(body.slug || "").trim().toLowerCase();
	if (!FORM_SLUG_RE.test(slug)) return json({ error: "URL must be 2-64 characters, lowercase letters/digits/hyphens only." }, 400);
	const title = String(body.title || "").trim();
	if (!title) return json({ error: "Title is required." }, 400);
	const status = FORM_STATUSES.has(body.status) ? body.status : "draft";
	const responsePolicy = FORM_RESPONSE_POLICIES.has(body.responsePolicy) ? body.responsePolicy : "unlimited";
	const qv = validateFormQuestions(body.questions);
	if (!qv.ok) return json({ error: qv.error }, 400);
	const responseLimit = body.responseLimit !== undefined && body.responseLimit !== null && body.responseLimit !== "" ? Math.max(1, Math.floor(Number(body.responseLimit))) : null;
	let closesAt = null;
	if (body.closesAt) {
		const parsed = Date.parse(body.closesAt);
		if (Number.isNaN(parsed)) return json({ error: "Invalid close date." }, 400);
		closesAt = new Date(parsed).toISOString();
	}

	const existing = await env.DB.prepare("SELECT id FROM forms WHERE slug = ?").bind(slug).first();
	if (existing) return json({ error: "That URL is already taken by another form." }, 409);

	const id = crypto.randomUUID();
	const now = new Date().toISOString();
	await env.DB.prepare(
		`INSERT INTO forms (id, slug, title, description, questionsJson, requireLogin, responsePolicy, status, closesAt, responseLimit, confirmationMessage, createdBy, createdAt, updatedAt)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
	).bind(id, slug, title.slice(0, 200), String(body.description || "").slice(0, 2000) || null, JSON.stringify(qv.cleaned),
		body.requireLogin ? 1 : 0, responsePolicy, status, closesAt, responseLimit, String(body.confirmationMessage || "").slice(0, 500) || null,
		auth.admin ? auth.admin.username : "master", now, now).run();

	return json(formRowToAdminJson(await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first()));
}

async function handleAdminListForms(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	const { results } = await env.DB.prepare(
		`SELECT f.id, f.slug, f.title, f.status, f.requireLogin, f.responsePolicy, f.closesAt, f.responseLimit, f.createdBy, f.createdAt, f.updatedAt,
		 (SELECT COUNT(*) FROM formResponses r WHERE r.formId = f.id) AS responseCount
		 FROM forms f ORDER BY f.createdAt DESC`
	).all();
	return json(results.map((r) => ({ ...r, requireLogin: !!r.requireLogin })));
}

async function handleAdminGetForm(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	const id = new URL(request.url).searchParams.get("id") || "";
	const form = await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first();
	if (!form) return json({ error: "Form not found" }, 404);
	return json(formRowToAdminJson(form));
}

async function handleAdminUpdateForm(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "");
	const form = await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first();
	if (!form) return json({ error: "Form not found" }, 404);

	const slug = String(body.slug || "").trim().toLowerCase();
	if (!FORM_SLUG_RE.test(slug)) return json({ error: "URL must be 2-64 characters, lowercase letters/digits/hyphens only." }, 400);
	if (slug !== form.slug) {
		const clash = await env.DB.prepare("SELECT id FROM forms WHERE slug = ? AND id != ?").bind(slug, id).first();
		if (clash) return json({ error: "That URL is already taken by another form." }, 409);
	}
	const title = String(body.title || "").trim();
	if (!title) return json({ error: "Title is required." }, 400);
	const status = FORM_STATUSES.has(body.status) ? body.status : form.status;
	const responsePolicy = FORM_RESPONSE_POLICIES.has(body.responsePolicy) ? body.responsePolicy : form.responsePolicy;
	const qv = validateFormQuestions(body.questions);
	if (!qv.ok) return json({ error: qv.error }, 400);
	const responseLimit = body.responseLimit !== undefined && body.responseLimit !== null && body.responseLimit !== "" ? Math.max(1, Math.floor(Number(body.responseLimit))) : null;
	let closesAt = null;
	if (body.closesAt) {
		const parsed = Date.parse(body.closesAt);
		if (Number.isNaN(parsed)) return json({ error: "Invalid close date." }, 400);
		closesAt = new Date(parsed).toISOString();
	}
	const now = new Date().toISOString();

	await env.DB.prepare(
		`UPDATE forms SET slug=?, title=?, description=?, questionsJson=?, requireLogin=?, responsePolicy=?, status=?, closesAt=?, responseLimit=?, confirmationMessage=?, updatedAt=? WHERE id=?`
	).bind(slug, title.slice(0, 200), String(body.description || "").slice(0, 2000) || null, JSON.stringify(qv.cleaned),
		body.requireLogin ? 1 : 0, responsePolicy, status, closesAt, responseLimit, String(body.confirmationMessage || "").slice(0, 500) || null, now, id).run();

	return json(formRowToAdminJson(await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first()));
}

async function handleAdminSetFormStatus(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	if (!FORM_STATUSES.has(body.status)) return json({ error: "Invalid status" }, 400);
	const res = await env.DB.prepare("UPDATE forms SET status = ?, updatedAt = ? WHERE id = ?").bind(body.status, new Date().toISOString(), String(body.id || "")).run();
	if (res.meta.changes === 0) return json({ error: "Form not found" }, 404);
	return json({ ok: true });
}

async function handleAdminDeleteForm(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const id = String(body.id || "");
	await env.DB.prepare("DELETE FROM formResponses WHERE formId = ?").bind(id).run();
	const res = await env.DB.prepare("DELETE FROM forms WHERE id = ?").bind(id).run();
	if (res.meta.changes === 0) return json({ error: "Form not found" }, 404);
	return json({ ok: true });
}

async function handleAdminGetFormResponses(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	const url = new URL(request.url);
	const id = url.searchParams.get("id") || "";
	const format = url.searchParams.get("format") === "csv" ? "csv" : "json";
	const form = await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first();
	if (!form) return json({ error: "Form not found" }, 404);
	const { results } = await env.DB.prepare(
		"SELECT id, respondentUsername, answersJson, submittedAt, updatedAt FROM formResponses WHERE formId = ? ORDER BY submittedAt DESC"
	).bind(id).all();
	const responses = results.map((r) => ({
		id: r.id, respondent: r.respondentUsername || "Anonymous", answers: JSON.parse(r.answersJson),
		submittedAt: r.submittedAt, updatedAt: r.updatedAt,
	}));
	if (format === "json") return json(responses);

	const csv = formResponsesToCsv(JSON.parse(form.questionsJson), responses);
	return new Response(csv, { headers: { ...corsHeaders(), "Content-Type": "text/csv; charset=utf-8", "Content-Disposition": `attachment; filename="${form.slug}-responses.csv"` } });
}

async function handleAdminDeleteFormResponse(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const res = await env.DB.prepare("DELETE FROM formResponses WHERE id = ?").bind(String(body.id || "")).run();
	if (res.meta.changes === 0) return json({ error: "Response not found" }, 404);
	return json({ ok: true });
}

async function handleAdminGetFormSummary(request, env) {
	const auth = await requireAdminAuth(request, env, "manageForms");
	if (!auth.ok) return auth.response;
	const id = new URL(request.url).searchParams.get("id") || "";
	const form = await env.DB.prepare("SELECT * FROM forms WHERE id = ?").bind(id).first();
	if (!form) return json({ error: "Form not found" }, 404);
	const questions = JSON.parse(form.questionsJson);
	const { results } = await env.DB.prepare("SELECT answersJson FROM formResponses WHERE formId = ?").bind(id).all();
	const answersList = results.map((r) => JSON.parse(r.answersJson));

	const summary = questions.map((q) => {
		const values = answersList.map((a) => a[q.id]).filter((v) => v !== undefined && v !== null && v !== "");
		const base = { questionId: q.id, type: q.type, label: q.label, totalResponses: values.length };
		if (q.type === "multiple_choice" || q.type === "dropdown" || q.type === "yes_no") {
			const counts = {};
			for (const o of (q.type === "yes_no" ? ["Yes", "No"] : (q.options || []))) counts[o] = 0;
			for (const v of values) counts[v] = (counts[v] || 0) + 1;
			return { ...base, counts };
		}
		if (q.type === "checkboxes") {
			const counts = {};
			for (const o of (q.options || [])) counts[o] = 0;
			for (const v of values) for (const o of (Array.isArray(v) ? v : [])) counts[o] = (counts[o] || 0) + 1;
			return { ...base, counts };
		}
		if (q.type === "rating") {
			const counts = {};
			for (let n = q.min; n <= q.max; n++) counts[n] = 0;
			let sum = 0;
			for (const v of values) { counts[v] = (counts[v] || 0) + 1; sum += Number(v); }
			return { ...base, counts, avg: values.length ? Math.round((sum / values.length) * 100) / 100 : null, min: q.min, max: q.max };
		}
		if (q.type === "number") {
			const nums = values.map(Number).filter(Number.isFinite);
			const sum = nums.reduce((a, b) => a + b, 0);
			return { ...base, avg: nums.length ? Math.round((sum / nums.length) * 100) / 100 : null, min: nums.length ? Math.min(...nums) : null, max: nums.length ? Math.max(...nums) : null };
		}
		// short_text, paragraph, date — free text, no meaningful aggregate: just the raw values (capped)
		return { ...base, values: values.slice(0, 200).map(String) };
	});
	return json({ form: { id: form.id, title: form.title }, totalResponses: answersList.length, summary });
}

// ---- public ----

async function handleGetPublicForm(request, env) {
	const slug = (new URL(request.url).searchParams.get("slug") || "").trim().toLowerCase();
	if (!slug) return json({ error: "slug is required" }, 400);
	const form = await env.DB.prepare("SELECT * FROM forms WHERE slug = ?").bind(slug).first();
	if (!form) return json({ error: "Form not found" }, 404);
	if (form.status === "draft") {
		const auth = await requireAdminAuth(request, env, "manageForms");
		if (!auth.ok) return json({ error: "Form not found" }, 404); // don't leak that a draft exists
	}
	const countRow = await env.DB.prepare("SELECT COUNT(*) AS c FROM formResponses WHERE formId = ?").bind(form.id).first();
	const responsesSoFar = countRow.c;
	return json({
		id: form.id, slug: form.slug, title: form.title, description: form.description || "",
		questions: JSON.parse(form.questionsJson), requireLogin: !!form.requireLogin,
		responsePolicy: form.responsePolicy, status: form.status, closesAt: form.closesAt || null,
		responseLimit: form.responseLimit, responsesSoFar,
		acceptingResponses: formIsAcceptingResponses(form, responsesSoFar),
		confirmationMessage: form.confirmationMessage || "Thanks — your response has been recorded.",
	});
}

async function handleGetMyFormResponse(request, env) {
	const url = new URL(request.url);
	const slug = (url.searchParams.get("slug") || "").trim().toLowerCase();
	if (!slug) return json({ error: "slug is required" }, 400);
	const form = await env.DB.prepare("SELECT id FROM forms WHERE slug = ?").bind(slug).first();
	if (!form) return json({ error: "Form not found" }, 404);

	const auth = await requireAnyAdmin(request, env);
	let dedupKey;
	if (auth.ok) {
		dedupKey = auth.admin.id;
	} else {
		const token = (url.searchParams.get("token") || "").trim();
		if (!token) return json({ exists: false });
		dedupKey = "anon:" + token;
	}
	const row = await env.DB.prepare("SELECT answersJson, submittedAt, updatedAt FROM formResponses WHERE formId = ? AND dedupKey = ?").bind(form.id, dedupKey).first();
	if (!row) return json({ exists: false });
	return json({ exists: true, answers: JSON.parse(row.answersJson), submittedAt: row.submittedAt, updatedAt: row.updatedAt });
}

async function handleSubmitForm(request, env) {
	let body;
	try { body = await request.json(); } catch (e) { return json({ error: "Invalid JSON body" }, 400); }
	const slug = String(body.slug || "").trim().toLowerCase();
	if (!slug) return json({ error: "slug is required" }, 400);
	const form = await env.DB.prepare("SELECT * FROM forms WHERE slug = ?").bind(slug).first();
	if (!form) return json({ error: "Form not found" }, 404);

	const countRow = await env.DB.prepare("SELECT COUNT(*) AS c FROM formResponses WHERE formId = ?").bind(form.id).first();
	if (!formIsAcceptingResponses(form, countRow.c)) return json({ error: "This form isn't accepting responses right now." }, 403);

	const auth = await requireAnyAdmin(request, env);
	if (form.requireLogin && !auth.ok) return json({ error: "You need to be logged in to submit this form." }, 401);

	const questions = JSON.parse(form.questionsJson);
	const validated = validateFormAnswers(questions, body.answers);
	if (!validated.ok) return json({ error: validated.error }, 400);

	const accountId = auth.ok ? auth.admin.id : null;
	const respondentToken = accountId ? null : (String(body.respondentToken || "").trim() || null);
	const dedupKey = accountId ? accountId : "anon:" + (respondentToken || crypto.randomUUID());
	const respondentUsername = accountId ? auth.admin.username : null;
	const now = new Date().toISOString();
	const answersJson = JSON.stringify(validated.cleaned);

	if (form.responsePolicy !== "unlimited") {
		const existing = await env.DB.prepare("SELECT id FROM formResponses WHERE formId = ? AND dedupKey = ?").bind(form.id, dedupKey).first();
		if (existing) {
			if (form.responsePolicy === "oncePerRespondentLocked") return json({ error: "You've already submitted this form." }, 409);
			await env.DB.prepare("UPDATE formResponses SET answersJson = ?, updatedAt = ?, respondentUsername = ? WHERE id = ?")
				.bind(answersJson, now, respondentUsername, existing.id).run();
			return json({ ok: true, confirmationMessage: form.confirmationMessage || "Thanks — your response has been updated.", updated: true });
		}
	}
	await env.DB.prepare(
		"INSERT INTO formResponses (id, formId, accountId, respondentToken, dedupKey, respondentUsername, answersJson, submittedAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
	).bind(crypto.randomUUID(), form.id, accountId, respondentToken, dedupKey, respondentUsername, answersJson, now, now).run();
	return json({ ok: true, confirmationMessage: form.confirmationMessage || "Thanks — your response has been recorded.", updated: false });
}

const ROUTES = [
	["POST", "/listings", handleUploadListings],
	["GET", "/listings", handleGetListings],
	["POST", "/reports", handleSubmitReport],
	["POST", "/suggestions", handleSubmitSuggestion],
	["GET", "/admin/suggestions", handleListSuggestions],
	["POST", "/admin/suggestions/delete", handleAdminDeleteSuggestion],
	["POST", "/bug-reports", handleSubmitBugReport],
	["GET", "/admin/bug-reports", handleListBugReports],
	["POST", "/admin/bug-reports/delete", handleAdminDeleteBugReport],
	["POST", "/player-reports", handleSubmitPlayerReport],
	["GET", "/admin/player-reports", handleListPlayerReports],
	["POST", "/admin/player-reports/resolve", handleAdminResolvePlayerReport],
	["POST", "/shared-shop-requests", handleSubmitSharedShopRequest],
	["GET", "/shared-shops", handleGetSharedShops],
	["GET", "/rare-items", handleGetRareItems],
	["GET", "/faq", handleGetFaqPublic],
	["GET", "/world-map", handleGetWorldMap],
	["GET", "/roadmap", handleGetRoadmap],
	["GET", "/update-notice", handleGetUpdateNotice],
	["GET", "/admin/update-notice", handleAdminGetUpdateNotice],
	["POST", "/admin/update-notice/set", handleAdminSetUpdateNotice],
	["POST", "/world-map/claim", handleClaimSquare],
	["POST", "/world-map/unclaim", handleUnclaimSquare],
	["POST", "/world-map/complete", handleCompleteSquare],
	["POST", "/admin/world-map/set", handleAdminSetSquare],
	["POST", "/admin/listings/manual-add", handleAdminAddManualListings],
	["GET", "/admin/listings/manual", handleAdminListManualListings],
	["POST", "/admin/listings/manual-delete", handleAdminDeleteManualListing],
	["POST", "/admin/listings/delete-shop", handleAdminDeleteShopListings],
	["GET", "/items/history", handleGetItemHistory],
	["GET", "/stats/item", handleGetItemStats],
	["GET", "/stats/world", handleGetWorldStats],
	["GET", "/stats/mine", handleGetMyStats],
	["POST", "/admin/run-snapshot", handleAdminRunSnapshot],
	["GET", "/admin/snapshots", handleAdminSnapshots],
	["GET", "/admin/reports", handleListReports],
	["GET", "/admin/shared-shop-requests", handleListSharedShopRequests],
	["POST", "/admin/reports/resolve", handleResolveReport],
	["POST", "/admin/listings/remove", handleAdminRemoveListingDirect],
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
	["GET", "/account/me", handleGetAccountMe],
	["POST", "/account/contact-info", handleSetAccountContactInfo],
	["POST", "/account/register/start", handleStartRegistration],
	["POST", "/account/register/direct", handleDirectRegistration],
	["GET", "/mapart", handleGetMapart],
	["GET", "/mapart/by-slug", handleGetMapartBySlug],
	["GET", "/mapart/image", handleGetMapartImage],
	["POST", "/mapart/upload", handleUploadMapart],
	["GET", "/mapart/mine", handleGetMyMapart],
	["POST", "/mapart/claim", handleClaimMapart],
	["POST", "/mapart/abandon", handleAbandonMapart],
	["POST", "/mapart/submit", handleSubmitMapart],
	["POST", "/mapart/delete-own", handleDeleteOwnMapart],
	["POST", "/mapart/takedown", handleRequestMapartTakedown],
	["POST", "/mapart/takedown/cancel", handleCancelMapartTakedown],
	["GET", "/admin/mapart/takedowns", handleAdminListTakedowns],
	["POST", "/admin/mapart/takedowns/resolve", handleAdminResolveTakedown],
	["POST", "/admin/mapart/split", handleAdminSplitMapart],
	["GET", "/store/listings", handleStoreListings],
	["POST", "/store/listings/add", handleStoreAddListings],
	["POST", "/store/listings/update", handleStoreUpdateListing],
	["POST", "/store/listings/delete", handleStoreDeleteListing],
	["POST", "/mapart/update", handleUpdateMapart],
	["POST", "/mapart/report", handleSubmitMapartReport],
	["GET", "/mapart/of-the-day", handleGetMapartOfTheDay],
	["POST", "/admin/mapart/otd/reroll", handleAdminRerollMapartOfTheDay],
	["POST", "/mapart/search-image", handleSearchMapartImage],
	["POST", "/admin/mapart/build-index", handleAdminBuildMapartIndex],
	["POST", "/account/commission", handleSetCommissionInfo],
	["GET", "/profile", handleGetProfile],
	["GET", "/raredle/state", handleRaredleState],
	["POST", "/raredle/guess", handleRaredleGuess],
	["POST", "/raredle/reset", handleRaredleReset],
	["POST", "/raredle/practice/new", handleRaredlePracticeNew],
	["GET", "/raredle/leaderboard", handleRaredleLeaderboard],
	["GET", "/collection/mine", handleGetMyCollection],
	["POST", "/collection/set", handleSetCollectionItems],
	["POST", "/collection/privacy", handleSetCollectionPrivacy],
	["GET", "/collection/public", handleGetPublicCollection],
	["GET", "/admin/mapart", handleAdminListMapart],
	["POST", "/admin/mapart/delete", handleAdminDeleteMapart],
	["POST", "/admin/mapart/assign", handleAdminAssignMapart],
	["POST", "/admin/mapart/rederive", handleAdminRederiveMapart],
	["POST", "/admin/verification-links/create", handleAdminCreateVerificationLink],
	["GET", "/admin/verification-links", handleAdminListVerificationLinks],
	["POST", "/admin/verification-links/revoke", handleAdminRevokeVerificationLink],
	["GET", "/verify-link/info", handleVerificationLinkInfo],
	["POST", "/verify-link/redeem", handleRedeemVerificationLink],
	["POST", "/admin/password-reset-links/create", handleAdminCreatePasswordResetLink],
	["GET", "/admin/password-reset-links", handleAdminListPasswordResetLinks],
	["POST", "/admin/password-reset-links/revoke", handleAdminRevokePasswordResetLink],
	["GET", "/password-reset-link/info", handlePasswordResetLinkInfo],
	["POST", "/password-reset-link/redeem", handlePasswordResetLinkRedeem],
	["POST", "/admin/forms/create", handleAdminCreateForm],
	["GET", "/admin/forms", handleAdminListForms],
	["GET", "/admin/forms/get", handleAdminGetForm],
	["POST", "/admin/forms/update", handleAdminUpdateForm],
	["POST", "/admin/forms/set-status", handleAdminSetFormStatus],
	["POST", "/admin/forms/delete", handleAdminDeleteForm],
	["GET", "/admin/forms/responses", handleAdminGetFormResponses],
	["POST", "/admin/forms/responses/delete", handleAdminDeleteFormResponse],
	["GET", "/admin/forms/summary", handleAdminGetFormSummary],
	["GET", "/forms/get", handleGetPublicForm],
	["GET", "/forms/my-response", handleGetMyFormResponse],
	["POST", "/forms/submit", handleSubmitForm],
	["GET", "/account/register/status", handleGetRegistrationStatus],
	["POST", "/account/register/complete", handleCompleteRegistration],
	["POST", "/account/register/verify-callback", handleRegistrationVerifyCallback],
	["GET", "/account/register/find-pending", handleFindPendingRegistration],
	["GET", "/admin/blocked-sellers", handleAdminListBlockedSellers],
	["POST", "/admin/blocked-sellers/add", handleAdminBlockSeller],
	["POST", "/admin/blocked-sellers/remove", handleAdminUnblockSeller],
	["POST", "/admin/admins/set-mc", handleAdminSetMc],
	["GET", "/marketplace/listings", handleGetMarketplaceListings],
	["POST", "/marketplace/listings/create", handleCreateMarketplaceListing],
	["POST", "/marketplace/listings/cancel", handleCancelMarketplaceListing],
	["POST", "/marketplace/bids/place", handlePlaceBid],
	["POST", "/marketplace/bids/withdraw", handleWithdrawBid],
	["POST", "/marketplace/bids/accept", handleAcceptBid],
	["POST", "/marketplace/bids/reject", handleRejectBid],
	["GET", "/marketplace/jobs", handleGetMarketplaceJobs],
	["POST", "/marketplace/jobs/create", handleCreateMarketplaceJob],
	["POST", "/marketplace/jobs/interest", handleExpressJobInterest],
	["POST", "/marketplace/jobs/review", handleReviewMarketplaceJob],
	["POST", "/marketplace/jobs/close", handleCloseMarketplaceJob],
	["GET", "/marketplace/mine", handleGetMyMarketplace],
	["GET", "/marketplace/notifications", handleGetMarketplaceNotifications],
	["POST", "/marketplace/notifications/mark-read", handleMarkNotificationsRead],
	["GET", "/marketplace/notifications/for-mc", handleGetNotificationsForMc],
	["GET", "/admin/mod-user-stats", handleAdminModUserStats],
	["GET", "/seller/primary-world", handleGetSellerPrimaryWorld],
	["GET", "/admin/marketplace/listings", handleAdminListMarketplaceListings],
	["POST", "/admin/marketplace/listings/remove", handleAdminRemoveMarketplaceListing],
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
	// item's price/stock/seller stats (computeDailySnapshots), a full
	// listings.json dump to R2 (snapshotListingsToR2) for later per-seller
	// history/diffing, expires any marketplace listing past its 14-day
	// lifetime (expireOldMarketplaceListings), and removes any shop listing
	// not re-scanned in 14 days (removeStaleListings). These all run
	// concurrently (independent waitUntil calls so one failing doesn't stop
	// the others) — there's no ordering guarantee between them, so a listing
	// removed by removeStaleListings might or might not still be in that same
	// day's R2 dump depending on which finishes first; harmless either way.
	async scheduled(event, env, ctx) {
		ctx.waitUntil(computeDailySnapshots(env));
		ctx.waitUntil(snapshotListingsToR2(env));
		ctx.waitUntil(expireOldMarketplaceListings(env));
		ctx.waitUntil(expireOldMarketplaceJobs(env));
		ctx.waitUntil(removeStaleListings(env));
	},
};

-- Rare items (catalogued in data/rare-items.json) priced at exactly 1 or 2
-- diamond are held here for admin approval instead of going straight into
-- `listings`, since a rare that cheap is more likely a mistake or a scam
-- flip than a real price. Kept as its own table rather than a status column
-- on `listings` — that table is the hottest, edge-cached public read path in
-- the app, and this is an admin-only/rare-only concern that shouldn't add
-- cost or risk to every public request. Mirrors `listings`' columns so an
-- approval can reuse the exact same upsert path (see upsertListingRow).
CREATE TABLE pendingRareApprovals (
	id TEXT PRIMARY KEY,
	rowKey TEXT NOT NULL,
	itemName TEXT NOT NULL,
	baseItem TEXT NOT NULL,
	bulk INTEGER NOT NULL DEFAULT 0,
	bundled INTEGER NOT NULL DEFAULT 0,
	mixedContents INTEGER NOT NULL DEFAULT 0,
	price REAL NOT NULL,
	priceLabel TEXT,
	stackSize INTEGER,
	amount INTEGER,
	stacksInStock REAL,
	currency TEXT NOT NULL,
	seller TEXT NOT NULL,
	world TEXT NOT NULL,
	position TEXT,
	lastSeen TEXT NOT NULL,
	submittedAt TEXT NOT NULL
);
CREATE INDEX idx_pending_rare_seller ON pendingRareApprovals(seller);

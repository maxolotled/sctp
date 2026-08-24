-- Column names intentionally match the JS/JSON field names used across
-- worker.js and the site's HTML (itemName, baseItem, ...) rather than
-- snake_case, so D1 rows can be returned as JSON with zero remapping.

CREATE TABLE listings (
	rowKey TEXT PRIMARY KEY,
	itemName TEXT NOT NULL,
	baseItem TEXT NOT NULL,
	bulk INTEGER NOT NULL,
	mixedContents INTEGER NOT NULL,
	price REAL NOT NULL,
	priceLabel TEXT NOT NULL,
	stackSize INTEGER NOT NULL,
	amount INTEGER NOT NULL,
	stacksInStock INTEGER NOT NULL,
	currency TEXT NOT NULL,
	seller TEXT NOT NULL,
	world TEXT NOT NULL,
	position TEXT NOT NULL,
	lastSeen TEXT NOT NULL
);
CREATE INDEX idx_listings_world ON listings(world);
CREATE INDEX idx_listings_worldpos ON listings(world, position);

CREATE TABLE reports (
	id TEXT PRIMARY KEY,
	listingKey TEXT NOT NULL,
	listingJson TEXT,
	reason TEXT NOT NULL,
	details TEXT NOT NULL DEFAULT '',
	status TEXT NOT NULL DEFAULT 'pending',
	createdAt TEXT NOT NULL,
	resolvedAt TEXT
);
CREATE INDEX idx_reports_status ON reports(status);

CREATE TABLE sharedShopRequests (
	id TEXT PRIMARY KEY,
	username TEXT NOT NULL,
	shopsJson TEXT NOT NULL,
	bio TEXT NOT NULL DEFAULT '',
	status TEXT NOT NULL DEFAULT 'pending',
	createdAt TEXT NOT NULL,
	resolvedAt TEXT
);
CREATE INDEX idx_ssr_status ON sharedShopRequests(status);

CREATE TABLE sharedShops (
	usernameKey TEXT PRIMARY KEY,
	username TEXT NOT NULL,
	shopsJson TEXT NOT NULL,
	bio TEXT NOT NULL DEFAULT ''
);

CREATE TABLE faq (
	id TEXT PRIMARY KEY,
	question TEXT NOT NULL,
	answer TEXT NOT NULL,
	createdAt TEXT NOT NULL
);

CREATE TABLE worldMap (
	squareId TEXT PRIMARY KEY,
	status TEXT NOT NULL,
	username TEXT NOT NULL,
	claimedAt TEXT,
	completedAt TEXT
);

CREATE TABLE rareItems (
	world TEXT NOT NULL,
	itemName TEXT NOT NULL,
	PRIMARY KEY (world, itemName)
);

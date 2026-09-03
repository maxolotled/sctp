-- Marketplace: accounts (the existing admins table becomes every site
-- account, not just admins — an account is "an admin" purely by having
-- isHeadAdmin=1 or a non-empty permissions array, unchanged from today),
-- listings (selling / lookingFor), open-ended bids, and notifications.

ALTER TABLE admins ADD COLUMN mcUsername TEXT;
ALTER TABLE admins ADD COLUMN mcVerified INTEGER NOT NULL DEFAULT 0;

CREATE TABLE marketplaceListings (
	id TEXT PRIMARY KEY,
	accountId TEXT NOT NULL,
	type TEXT NOT NULL, -- 'selling' | 'lookingFor'
	itemName TEXT NOT NULL,
	baseItem TEXT,
	world TEXT NOT NULL,
	quantity INTEGER NOT NULL,
	notes TEXT,
	askingPrice REAL,
	askingCurrency TEXT,
	startingBid REAL,
	startingBidCurrency TEXT,
	budget REAL,
	budgetCurrency TEXT,
	status TEXT NOT NULL DEFAULT 'active', -- 'active' | 'fulfilled' | 'cancelled' | 'expired'
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL,
	closedAt TEXT,
	closedReason TEXT
);
CREATE INDEX idx_marketplaceListings_status ON marketplaceListings(status);
CREATE INDEX idx_marketplaceListings_account ON marketplaceListings(accountId);

CREATE TABLE marketplaceBids (
	id TEXT PRIMARY KEY,
	listingId TEXT NOT NULL,
	bidderAccountId TEXT NOT NULL,
	amount REAL NOT NULL,
	currency TEXT NOT NULL,
	message TEXT,
	createdAt TEXT NOT NULL,
	status TEXT NOT NULL DEFAULT 'pending' -- 'pending' | 'accepted' | 'rejected' | 'withdrawn'
);
CREATE INDEX idx_marketplaceBids_listing ON marketplaceBids(listingId);

CREATE TABLE marketplaceNotifications (
	id TEXT PRIMARY KEY,
	accountId TEXT NOT NULL,
	type TEXT NOT NULL,
	message TEXT NOT NULL,
	listingId TEXT,
	createdAt TEXT NOT NULL,
	readAt TEXT,
	deliveredInGameAt TEXT -- set once the mod has echoed this to chat on join, so it isn't repeated every join
);
CREATE INDEX idx_marketplaceNotifications_account ON marketplaceNotifications(accountId);

-- Rare-item auctions (/auction, /auction/admin). Players enter rares with a
-- starting bid and a lowest limit, then hand them in physically: a shulker
-- renamed "Registered <username>", dropped at /pw auction. Admins with the
-- "auctions" permission create auctions and record each item's result.
-- All prices are stored in diamonds (1 DB = 9).

CREATE TABLE auctions (
	id TEXT PRIMARY KEY,
	title TEXT NOT NULL,
	world TEXT NOT NULL,                -- Firefly | Honeybee (where it's held; drives price suggestions)
	auctionAt TEXT NOT NULL,            -- ISO date/time the auction takes place
	status TEXT NOT NULL,               -- open (taking entries) | closed (no new entries) | finished
	perPersonLimit INTEGER NOT NULL,    -- max items one Minecraft username may enter
	totalLimit INTEGER NOT NULL,        -- max items in the whole auction
	notes TEXT,
	createdAt TEXT NOT NULL,
	createdBy TEXT NOT NULL
);

CREATE TABLE auctionItems (
	id TEXT PRIMARY KEY,
	auctionId TEXT NOT NULL,
	batchId TEXT NOT NULL,              -- one "Continue" press = one shulker
	mcUsername TEXT NOT NULL,
	accountId TEXT,                     -- set when the player was signed in (results + notifications)
	rareId TEXT NOT NULL,
	itemName TEXT NOT NULL,
	texture TEXT,
	estimateDia REAL,                   -- SCTP's value estimate when entered (null if none)
	startDia INTEGER NOT NULL,
	minDia INTEGER NOT NULL,
	status TEXT NOT NULL,               -- entered | sold | unsold | removed
	soldDia INTEGER,
	cutDia INTEGER,
	payoutDia INTEGER,
	createdAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL
);
CREATE INDEX idx_auctionItems_auction ON auctionItems(auctionId);
CREATE INDEX idx_auctionItems_account ON auctionItems(accountId);
CREATE INDEX idx_auctionItems_user ON auctionItems(mcUsername);

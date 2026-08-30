-- Sellers permanently blocked from the Trading Post — none of their
-- listings show up publicly once blocked. usernameKey is lowercased for
-- case-insensitive matching, same convention as sharedShops.usernameKey.
CREATE TABLE blockedSellers (
	usernameKey TEXT PRIMARY KEY,
	username TEXT NOT NULL,
	reason TEXT NOT NULL DEFAULT '',
	blockedAt TEXT NOT NULL,
	blockedBy TEXT NOT NULL
);

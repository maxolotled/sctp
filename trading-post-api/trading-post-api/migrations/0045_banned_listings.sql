-- Banned listings: one item from one seller that must never show up again
-- (narrower than blockedSellers, which hides everything from a seller).
-- Matched on seller + item name, case-insensitive, on both worlds and every
-- bulk/bundled variant. Banning deletes the current matching rows; uploads and
-- manual listings that match are dropped from then on. "reports" permission.

CREATE TABLE bannedListings (
	id TEXT PRIMARY KEY,
	sellerKey TEXT NOT NULL,     -- lower(seller)
	itemNameKey TEXT NOT NULL,   -- lower(itemName)
	seller TEXT NOT NULL,        -- as it was shown, for the admin list
	itemName TEXT NOT NULL,
	world TEXT,                  -- where it was banned from (info only; the ban covers both worlds)
	reason TEXT,
	createdAt TEXT NOT NULL,
	createdBy TEXT NOT NULL
);
CREATE UNIQUE INDEX idx_bannedListings_pair ON bannedListings(sellerKey, itemNameKey);

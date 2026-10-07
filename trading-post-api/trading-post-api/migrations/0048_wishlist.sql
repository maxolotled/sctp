-- Wishlists: the items (rares/mapart) an account wants, starred on the same
-- /collection page, per world. Same shape as collectionItems; marking an item
-- as owned removes it from the wishlist. Shared and hidden together with the
-- collection (admins.collectionPrivate).
CREATE TABLE wishlistItems (
	accountId TEXT NOT NULL,
	kind TEXT NOT NULL,          -- 'rare' | 'mapart'
	itemId TEXT NOT NULL,
	world TEXT NOT NULL,         -- 'Firefly' | 'Honeybee'
	addedAt TEXT NOT NULL,
	PRIMARY KEY (accountId, kind, itemId, world)
);
CREATE INDEX idx_wishlistItems_item ON wishlistItems(kind, itemId);

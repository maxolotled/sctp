-- Daily snapshot per (item, world). itemKey is "v:<baseItem>" for vanilla
-- items (matched by baseItem + exact normalized name) or "r:<slug>" for rare
-- items once those exist (matched by exact normalized name only). Storing
-- broadly (min/max/count/stock, not just avg) so future graphs don't need a
-- schema change to backfill from — see worker.js's daily cron handler.
CREATE TABLE itemDailyStats (
	itemKey TEXT NOT NULL,
	world TEXT NOT NULL,
	date TEXT NOT NULL,
	avgPrice REAL,
	lowestPrice REAL,
	highestPrice REAL,
	listingCount INTEGER NOT NULL,
	sellerCount INTEGER NOT NULL,
	totalStock INTEGER NOT NULL,
	PRIMARY KEY (itemKey, world, date)
);

-- Real sales (mod 2.4+). A shop's stock going down only counts as a sale when
-- the payment sitting in that chest went UP between the same two scans (see
-- detectConfirmedSales in worker.js). Before this, "sold" was any stock that
-- disappeared — restocking, moving items and delisting all looked like sales.

-- The last scan of every shop container: its stock per listing and the
-- uncollected payment, the baseline the next scan (by anyone) is compared to.
CREATE TABLE containerScans (
	world TEXT NOT NULL,
	position TEXT NOT NULL,
	sellerKey TEXT,
	currency TEXT,
	paymentCount INTEGER,            -- null: scanned by a mod that doesn't report payments
	stockJson TEXT NOT NULL DEFAULT '{}', -- {rowKey: {a: amount, p: price per item in diamonds, n: itemName, b: baseItem}}
	scannedAt TEXT NOT NULL,
	PRIMARY KEY (world, position)
);

-- Every confirmed sale, one row per listing per detection.
CREATE TABLE shopSales (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	world TEXT NOT NULL,
	position TEXT NOT NULL,
	seller TEXT NOT NULL,
	sellerKey TEXT NOT NULL,
	itemKey TEXT NOT NULL,
	itemName TEXT NOT NULL,
	baseItem TEXT,
	units INTEGER NOT NULL,
	revenueDiamonds REAL NOT NULL,
	date TEXT NOT NULL,
	detectedAt TEXT NOT NULL
);
CREATE INDEX idx_shopSales_item ON shopSales(itemKey, date);
CREATE INDEX idx_shopSales_seller ON shopSales(sellerKey, date);

-- Confirmed sales per seller/item/day, next to the old estimate (kept for history).
ALTER TABLE sellerItemDailyStats ADD COLUMN confirmedSold INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sellerItemDailyStats ADD COLUMN confirmedRevenueDiamonds REAL NOT NULL DEFAULT 0;

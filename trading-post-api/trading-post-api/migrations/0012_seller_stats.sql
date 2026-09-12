-- Per-seller-per-item-per-day snapshot, computed alongside itemDailyStats in
-- the same daily cron pass (see computeDailySnapshots in worker.js). Also
-- carries an ESTIMATED sales figure: comparing today's totalStock for a
-- (seller, itemKey, world) against yesterday's row for the same key — a drop
-- (or the row disappearing entirely) is treated as an inferred sale. This is
-- the only sales signal this project has; there's no real transaction log
-- anywhere, so it can't distinguish "sold" from "seller pulled the listing"
-- or "restocked a different amount" — every consumer of inferredSold/
-- inferredRevenueDiamonds must present it as an estimate, never a real count.
CREATE TABLE sellerItemDailyStats (
  seller TEXT NOT NULL,
  sellerKey TEXT NOT NULL,
  itemKey TEXT NOT NULL,
  itemName TEXT NOT NULL,
  world TEXT NOT NULL,
  date TEXT NOT NULL,
  totalStock INTEGER NOT NULL,
  listingCount INTEGER NOT NULL,
  avgPriceDiamonds REAL NOT NULL,
  inferredSold INTEGER NOT NULL DEFAULT 0,
  inferredRevenueDiamonds REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (sellerKey, itemKey, world, date)
);
CREATE INDEX idx_seller_item_daily_seller ON sellerItemDailyStats(sellerKey, date);
CREATE INDEX idx_seller_item_daily_item ON sellerItemDailyStats(itemKey, world);

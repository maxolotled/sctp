-- When a listing was first seen (nullable — existing rows predate this and
-- have no known first-seen date; only genuinely new INSERTs get one). Set
-- once at insert time by the app (see buildListingUpsertStmt in worker.js)
-- and deliberately left out of the upsert's ON CONFLICT...DO UPDATE SET, so
-- ordinary price/stock updates never touch it — it only changes when the
-- row is truly re-created after being pruned (item removed then re-added).
ALTER TABLE listings ADD COLUMN availableSince TEXT;

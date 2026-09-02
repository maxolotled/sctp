-- One-time data migration (not a numbered schema migration — run manually
-- once via `wrangler d1 execute <db> --remote --file=migrations/backfill-bulk-stacksize.sql`).
--
-- Why: ShopEntryFactory used to record a bulk (shulker) listing's batchSize
-- as the largest single-SLOT quantity observed (typically 64), when a bulk
-- sign's price is actually for the WHOLE shulker (27 slots) — understating
-- the real batch by ~27x and badly overstating per-item price everywhere
-- that divides by stackSize (item price graphs, the /list optimizer, etc).
-- Fixed going forward in the mod; this corrects rows already stored under
-- the old, wrong convention. Multiplying the OLD stackSize by 27 reproduces
-- what the corrected client would have sent (same logic as the code fix:
-- 27 slots * the per-slot quantity already observed for that item).
UPDATE listings
SET stacksInStock = ROUND((amount * 1.0) / (stackSize * 27), 2),
    stackSize = stackSize * 27
WHERE bulk = 1;

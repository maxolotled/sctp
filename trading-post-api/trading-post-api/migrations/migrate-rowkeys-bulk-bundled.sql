-- One-time data migration (not a numbered schema migration — run manually
-- once via `wrangler d1 execute <db> --remote --file=migrations/migrate-rowkeys-bulk-bundled.sql`
-- right after deploying the worker.js build that changes rowKey() to include
-- bulk/bundled).
--
-- Why: rowKey is listings' PRIMARY KEY. Before this change it was
-- world|seller|baseItem|itemName, so a seller selling both a normal-priced
-- stack and a bulk/bundled batch of the same item collided into one row.
-- After the fix, new uploads compute a longer key
-- (world|seller|baseItem|itemName|bulk|bundled) — without this migration,
-- every row that used to collide would just insert as a new row on next
-- scan, leaving its old-format row permanently orphaned (never upserted
-- into again, only removed if that exact position happens to get
-- rescanned). This recomputes every existing row's key up front instead.
--
-- Safe as a single UPDATE: the new key is a strict refinement of the old one
-- (same 4 parts + bulk + bundled appended), so it can only ever split a
-- previously-unique old key further apart, never collide two old keys
-- together — no duplicate-PRIMARY-KEY risk.
UPDATE listings
SET rowKey = lower(world) || '|' || lower(seller) || '|' || lower(baseItem) || '|' || lower(itemName) || '|' || bulk || '|' || bundled
WHERE rowKey != (lower(world) || '|' || lower(seller) || '|' || lower(baseItem) || '|' || lower(itemName) || '|' || bulk || '|' || bundled);

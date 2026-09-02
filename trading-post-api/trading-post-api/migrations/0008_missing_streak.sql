-- Tracks how many consecutive scans of a listing's own position reported it
-- missing, so a single incomplete read of a container (e.g. one item not
-- making it into that particular scan's report, for whatever reason) doesn't
-- immediately delete a listing that's still actually there — it now takes 2
-- in a row, mirroring the same "confirm twice before trusting a removal
-- signal" principle ShopAutoScanner#forgetGoneShops already uses client-side
-- for whole-shop removal (see MIN_TRUSTED_PRUNE_VERSION's comment).
ALTER TABLE listings ADD COLUMN missingStreak INTEGER NOT NULL DEFAULT 0;

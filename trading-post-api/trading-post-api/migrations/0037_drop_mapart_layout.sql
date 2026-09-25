-- The worker now reads/writes maparts.mapType (0036); the interim `layout`
-- column from 0035 is no longer used.
ALTER TABLE maparts DROP COLUMN layout;

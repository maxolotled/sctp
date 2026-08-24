-- Bundles are unwrapped like shulker boxes, but marked distinctly (bundled,
-- not bulk) so the site can show a "Bundled" badge instead of "Bulk".
ALTER TABLE listings ADD COLUMN bundled INTEGER NOT NULL DEFAULT 0;

-- Mapart search keywords (artists add up to 5 per piece, stored as a JSON array
-- of lowercase strings) and the build layout: 'flat' or 'staircased' (NULL =
-- not specified).
ALTER TABLE maparts ADD COLUMN keywords TEXT;
ALTER TABLE maparts ADD COLUMN layout TEXT;

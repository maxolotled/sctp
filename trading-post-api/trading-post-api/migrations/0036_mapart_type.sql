-- "Layout" turned out to be the wrong word: what artists choose is the TYPE of
-- mapart — flat or staircased. New column mapType ('flat' | 'staircased' |
-- NULL = not specified); the interim `layout` column (0035) is copied over and
-- dropped by 0037 once the worker no longer reads it.
ALTER TABLE maparts ADD COLUMN mapType TEXT;
UPDATE maparts SET mapType = layout WHERE layout IS NOT NULL;

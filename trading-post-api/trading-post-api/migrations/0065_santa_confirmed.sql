-- Secret Santa: when a player last confirmed they're still playing (their
-- personal link's "I'm still playing" button, saving their entry, a fresh
-- sign-up, or an admin setting it). NULL = not confirmed.
ALTER TABLE santaEntries ADD COLUMN confirmedAt TEXT;

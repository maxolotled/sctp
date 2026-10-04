-- The Minecraft username of whoever hosts the auction in-game (optional),
-- shown on /auction so players know who's running it.

ALTER TABLE auctions ADD COLUMN hostName TEXT;

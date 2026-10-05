-- "Let the host pick": the player leaves the price(s) to the auction host.
-- Such entries store startDia = minDia = 0 and hostPicks = 1.
ALTER TABLE auctionItems ADD COLUMN hostPicks INTEGER NOT NULL DEFAULT 0;

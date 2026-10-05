-- Where players drop off their "Registered <name>" shulker for this auction
-- (e.g. "/pw auction"). Shown in the hand-in steps; null = the default /pw auction.
ALTER TABLE auctions ADD COLUMN dropOff TEXT;

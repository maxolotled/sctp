-- Each auction is either a Dutch clock auction (price starts at the starting
-- bid and drops toward the lowest limit) or a regular one (bids go up from a
-- single starting price, which is also the minimum). For regular auctions
-- auctionItems.minDia is stored equal to startDia.

ALTER TABLE auctions ADD COLUMN type TEXT NOT NULL DEFAULT 'dutch';

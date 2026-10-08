-- An auction entry can be a lot of several of the same rare ("5 Quest Crate
-- Keys" instead of five separate entries). Prices and the sale are for the
-- whole lot; estimateDia is the lot's value. Counts as one entry for limits.
ALTER TABLE auctionItems ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1;

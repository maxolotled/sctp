-- Each auction sets its own cut percentage (was a fixed 2.5%). The minimum
-- cut (1 DB on sales over 32 DB, 3 dia otherwise) stays the same for all.

ALTER TABLE auctions ADD COLUMN cutPercent REAL NOT NULL DEFAULT 2.5;

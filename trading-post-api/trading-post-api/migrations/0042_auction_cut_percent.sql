-- Each auction sets its own cut percentage (was a fixed 2.5%), rounded up
-- to a whole diamond. (There used to be a minimum cut too; it was dropped.)

ALTER TABLE auctions ADD COLUMN cutPercent REAL NOT NULL DEFAULT 2.5;

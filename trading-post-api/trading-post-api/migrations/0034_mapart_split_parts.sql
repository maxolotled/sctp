-- When an admin splits a wrongly-merged mapart into its individual pieces (see
-- handleAdminSplitMapart), every map id from that piece is remembered here under
-- one splitId. The upload path refuses any rectangle that contains two or more
-- ids from the same split, so a re-scan can't silently stitch them back together.
CREATE TABLE mapartSplitParts (
	world TEXT NOT NULL,
	mapId INTEGER NOT NULL,
	splitId TEXT NOT NULL,
	PRIMARY KEY (world, mapId)
);
CREATE INDEX idx_mapartSplitParts_split ON mapartSplitParts(splitId);

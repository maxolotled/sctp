-- Likes ("appreciation") on mapart pieces: one per (piece, account), any
-- logged-in account. The count is joined into every public mapart response
-- (see mapartPublic) so the gallery can sort by "Most liked".

CREATE TABLE mapartLikes (
	mapartId TEXT NOT NULL,
	accountId TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	PRIMARY KEY (mapartId, accountId)
);
CREATE INDEX idx_mapartLikes_account ON mapartLikes(accountId);

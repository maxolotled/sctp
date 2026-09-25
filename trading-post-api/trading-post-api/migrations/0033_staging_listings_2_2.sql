-- TEMPORARY (see CHANGELOG 2.2 / handleUploadListingsStaging in worker.js):
-- holds mod 2.2's uploads separately from the live `listings` table while
-- its accuracy gets checked — 2.2 ships the ender-chest scan-suppression fix
-- and the adaptive scan cooldown, both touching the auto-scanner's core
-- open/read/close path, so this avoids letting a regression there pollute
-- real listings before it's confirmed good. Drop this table (and the
-- staging code path in worker.js) once 2.2 is confirmed good and its
-- uploads are folded back into normal live uploads.
CREATE TABLE stagingListings (
	rowKey TEXT PRIMARY KEY,
	itemName TEXT NOT NULL,
	baseItem TEXT NOT NULL,
	bulk INTEGER NOT NULL,
	bundled INTEGER NOT NULL DEFAULT 0,
	mixedContents INTEGER NOT NULL,
	price REAL NOT NULL,
	priceLabel TEXT NOT NULL,
	stackSize INTEGER NOT NULL,
	amount INTEGER NOT NULL,
	stacksInStock INTEGER NOT NULL,
	currency TEXT NOT NULL,
	seller TEXT NOT NULL,
	world TEXT NOT NULL,
	position TEXT NOT NULL,
	lastSeen TEXT NOT NULL,
	modVersion TEXT NOT NULL,
	uploadedAt TEXT NOT NULL
);

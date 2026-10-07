-- A notice on the website's homepage (the /beta/ hub for now), set from the
-- admin portal ("siteNotice" permission). One row, like updateNotice.
CREATE TABLE siteNotice (
	id INTEGER PRIMARY KEY,
	enabled INTEGER NOT NULL DEFAULT 0,
	level TEXT NOT NULL DEFAULT 'info',   -- info | warn | success (the banner's colour)
	message TEXT NOT NULL DEFAULT '',
	linkUrl TEXT,                         -- optional button
	linkLabel TEXT,
	updatedAt TEXT,
	updatedBy TEXT
);
INSERT INTO siteNotice (id, enabled, level, message) VALUES (1, 0, 'info', '');

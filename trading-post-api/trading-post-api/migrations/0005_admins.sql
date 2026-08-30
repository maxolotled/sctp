-- Multi-admin accounts with granular permissions, replacing the single
-- shared ADMIN_KEY as the only way in. ADMIN_KEY still works after this —
-- it's kept as an unlosable master key (see requireAdminPermission in
-- worker.js) that always has full access, even if this table is empty.
CREATE TABLE admins (
	id TEXT PRIMARY KEY,
	username TEXT NOT NULL UNIQUE,
	passwordHash TEXT NOT NULL,
	passwordSalt TEXT NOT NULL,
	isHeadAdmin INTEGER NOT NULL DEFAULT 0,
	-- JSON array of permission bucket names: reports, sharedShopRequests,
	-- faq, worldMap, manualListings, blockedSellers, rareApprovals.
	-- Ignored entirely for a head admin, who always has every bucket.
	permissions TEXT NOT NULL DEFAULT '[]',
	createdAt TEXT NOT NULL,
	createdBy TEXT NOT NULL
);

CREATE TABLE adminSessions (
	token TEXT PRIMARY KEY,
	adminId TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL
);
CREATE INDEX idx_admin_sessions_admin ON adminSessions(adminId);

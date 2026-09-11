-- Suggestions, bug reports, and player reports — see worker.js's
-- handleSubmitSuggestion/handleSubmitBugReport/handleSubmitPlayerReport and
-- the "suggestions"/"bugReports"/"playerReports" permission buckets.
CREATE TABLE suggestions (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  details TEXT NOT NULL,
  submitterName TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'pending',
  createdAt TEXT NOT NULL,
  resolvedAt TEXT
);
CREATE INDEX idx_suggestions_status ON suggestions(status);

CREATE TABLE bugReports (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  details TEXT NOT NULL,
  area TEXT NOT NULL DEFAULT 'website',
  world TEXT,
  pageUrl TEXT,
  submitterName TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'pending',
  createdAt TEXT NOT NULL,
  resolvedAt TEXT
);
CREATE INDEX idx_bugReports_status ON bugReports(status);

-- Reporting a PLAYER (their shop specifically), not a single listing —
-- resolution reuses the existing blockedSellers/delete-shop mechanisms (see
-- handleAdminResolvePlayerReport) rather than duplicating that logic.
CREATE TABLE playerReports (
  id TEXT PRIMARY KEY,
  reportedUsername TEXT NOT NULL,
  world TEXT NOT NULL,
  reason TEXT NOT NULL,
  details TEXT NOT NULL,
  reporterName TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'pending',
  actionTaken TEXT,
  createdAt TEXT NOT NULL,
  resolvedAt TEXT
);
CREATE INDEX idx_playerReports_status ON playerReports(status);

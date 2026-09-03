-- Single-row config: an admin-configurable "update available" chat message,
-- shown to mod clients below a configurable minimum version. See
-- handleGetUpdateNotice/handleAdminSetUpdateNotice in worker.js and
-- UpdateNoticeCheck in the mod.
CREATE TABLE updateNotice (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  enabled INTEGER NOT NULL DEFAULT 0,
  minVersion TEXT NOT NULL DEFAULT '',
  message TEXT NOT NULL DEFAULT '',
  updatedAt TEXT NOT NULL,
  updatedBy TEXT NOT NULL DEFAULT ''
);
INSERT INTO updateNotice (id, enabled, minVersion, message, updatedAt, updatedBy) VALUES (1, 0, '', '', datetime('now'), '');

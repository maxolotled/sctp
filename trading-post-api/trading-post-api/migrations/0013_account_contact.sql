-- Self-service contact info an account owner sets for themselves (see
-- handleSetAccountContactInfo) — shown to the other party in a trade only
-- once a bid is actually accepted (see contactInfoText in worker.js).
-- Deliberately separate from mcUsername/mcVerified, which only a head admin
-- can set.
ALTER TABLE admins ADD COLUMN contactDiscord TEXT;
ALTER TABLE admins ADD COLUMN contactTimezone TEXT;

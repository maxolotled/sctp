-- Form builder: custom forms with a configurable URL slug, optional login
-- requirement, and a per-form resubmission policy. See worker.js's forms
-- section for the full route list and question/answer JSON shapes.
CREATE TABLE forms (
	id TEXT PRIMARY KEY,
	slug TEXT NOT NULL UNIQUE,
	title TEXT NOT NULL,
	description TEXT,
	questionsJson TEXT NOT NULL, -- JSON array of {id, type, label, help, required, options?, min?, max?, minLabel?, maxLabel?}
	requireLogin INTEGER NOT NULL DEFAULT 0,
	responsePolicy TEXT NOT NULL DEFAULT 'unlimited', -- 'unlimited' | 'oncePerRespondentEditable' | 'oncePerRespondentLocked'
	status TEXT NOT NULL DEFAULT 'draft', -- 'draft' | 'open' | 'closed'
	closesAt TEXT,
	responseLimit INTEGER,
	confirmationMessage TEXT,
	createdBy TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL
);

-- One row per submission (or per respondent, when responsePolicy dedupes —
-- see recordFormResponse). dedupKey is accountId when logged in, else
-- 'anon:'+respondentToken (a client-generated, non-secret localStorage token
-- — only ever used to detect "is this the same browser", not to prove identity).
CREATE TABLE formResponses (
	id TEXT PRIMARY KEY,
	formId TEXT NOT NULL,
	accountId TEXT,
	respondentToken TEXT,
	dedupKey TEXT NOT NULL,
	respondentUsername TEXT, -- snapshot for admin display convenience; NULL for anonymous
	answersJson TEXT NOT NULL,
	submittedAt TEXT NOT NULL,
	updatedAt TEXT NOT NULL
);
CREATE INDEX idx_formResponses_form ON formResponses(formId);
CREATE INDEX idx_formResponses_dedup ON formResponses(formId, dedupKey);

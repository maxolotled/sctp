-- Written reviews on job posts: the thumbs up/down in marketplaceJobReviews
-- (0029) can now carry an optional text comment (max 500 chars, trimmed by
-- the Worker). Listed publicly per job via GET /marketplace/jobs/reviews.

ALTER TABLE marketplaceJobReviews ADD COLUMN comment TEXT;

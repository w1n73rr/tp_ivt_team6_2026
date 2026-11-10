-- Preserve existing generated tests (including repeated names) and the single V6 persistence format.
-- Rename checks use this owner-scoped, case-sensitive expression. Questions/options remain in result_json.
CREATE INDEX ix_quizzes_user_name ON quizzes(user_id, ((summary_json::jsonb)->>'name'));

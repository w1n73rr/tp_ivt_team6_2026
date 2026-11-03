-- Run via psql -v ON_ERROR_STOP=1 with category, value, available, make_default.
-- Example is in docs/generation-parameters.md. Values are quoted as SQL literals by psql.
BEGIN;
LOCK TABLE generation_options IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMPORARY TABLE generation_option_change ON COMMIT DROP AS
SELECT :'category'::varchar AS category, :'value'::varchar AS option_value,
       :'available'::boolean AS available, :'make_default'::boolean AS make_default;

DO $$
DECLARE
    change RECORD;
BEGIN
    SELECT * INTO STRICT change FROM generation_option_change;
    IF NOT EXISTS (SELECT 1 FROM generation_options
                   WHERE category = change.category AND option_value = change.option_value) THEN
        RAISE EXCEPTION 'Unsupported generation option';
    END IF;
    IF change.make_default AND NOT change.available THEN
        RAISE EXCEPTION 'Default must be available';
    END IF;
    IF change.make_default THEN
        UPDATE generation_options SET default_slot = NULL WHERE category = change.category;
    END IF;
    UPDATE generation_options
       SET available = change.available,
           default_slot = CASE WHEN change.make_default THEN 1 ELSE default_slot END
     WHERE category = change.category AND option_value = change.option_value;
    IF EXISTS (
        SELECT category FROM generation_options
        GROUP BY category HAVING count(default_slot) <> 1
    ) THEN
        RAISE EXCEPTION 'Each category must have exactly one default';
    END IF;
END $$;
COMMIT;

CREATE OR REPLACE FUNCTION assert_journal_balanced(
    p_journal_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
AS
$$
DECLARE
v_posting_count INTEGER;
BEGIN

SELECT COUNT(*)
INTO v_posting_count
FROM postings
WHERE journal_entry_id = p_journal_id;


IF v_posting_count < 2 THEN

        RAISE EXCEPTION
            'Journal % must contain at least two postings',
            p_journal_id;

END IF;


    IF EXISTS
    (
        SELECT 1
        FROM postings
        WHERE journal_entry_id = p_journal_id
        GROUP BY currency
        HAVING
            SUM(
                CASE direction
                    WHEN 'DEBIT'
                        THEN amount_minor
                    WHEN 'CREDIT'
                        THEN -amount_minor
                END
            ) <> 0
    )
    THEN

        RAISE EXCEPTION
            'Journal % is not balanced',
            p_journal_id;

END IF;

END;
$$;


CREATE OR REPLACE FUNCTION trg_check_new_journal()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    PERFORM assert_journal_balanced(
        NEW.id
    );

RETURN NULL;

END;
$$;


CREATE CONSTRAINT TRIGGER journal_must_balance

AFTER INSERT
ON journal_entries

DEFERRABLE
INITIALLY DEFERRED

FOR EACH ROW

EXECUTE FUNCTION trg_check_new_journal();


CREATE OR REPLACE FUNCTION trg_check_changed_posting()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    IF TG_OP = 'INSERT' THEN

        PERFORM assert_journal_balanced(
            NEW.journal_entry_id
        );

    ELSIF TG_OP = 'DELETE' THEN

        PERFORM assert_journal_balanced(
            OLD.journal_entry_id
        );

    ELSIF TG_OP = 'UPDATE' THEN

        PERFORM assert_journal_balanced(
            OLD.journal_entry_id
        );

        IF NEW.journal_entry_id <>
           OLD.journal_entry_id THEN

            PERFORM assert_journal_balanced(
                NEW.journal_entry_id
            );

END IF;

END IF;

RETURN NULL;

END;
$$;


CREATE CONSTRAINT TRIGGER postings_must_keep_journal_balanced

AFTER INSERT OR UPDATE OR DELETE
                ON postings

                    DEFERRABLE
                    INITIALLY DEFERRED

                    FOR EACH ROW

                    EXECUTE FUNCTION trg_check_changed_posting();
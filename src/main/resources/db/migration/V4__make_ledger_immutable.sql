CREATE OR REPLACE FUNCTION reject_ledger_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

    RAISE EXCEPTION
        'Immutable ledger violation: % is not allowed on table %',
        TG_OP,
        TG_TABLE_NAME
        USING ERRCODE = '55000';

END;
$$;


CREATE TRIGGER prevent_postings_update_delete
    BEFORE UPDATE OR DELETE
ON postings
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_mutation();


CREATE TRIGGER prevent_journal_entries_update_delete
    BEFORE UPDATE OR DELETE
ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_mutation();


CREATE TRIGGER prevent_postings_truncate
    BEFORE TRUNCATE
    ON postings
FOR EACH STATEMENT
EXECUTE FUNCTION reject_ledger_mutation();


CREATE TRIGGER prevent_journal_entries_truncate
    BEFORE TRUNCATE
    ON journal_entries
FOR EACH STATEMENT
EXECUTE FUNCTION reject_ledger_mutation();
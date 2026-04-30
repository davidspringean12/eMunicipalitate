-- ═══════════════════════════════════════════════════════════════════
-- V3__relax_audit_trigger.sql
-- Simplify the audit trigger to work with JPA/Hibernate's full-row updates.
--
-- The previous trigger rejected updates where ANY column differed,
-- but Hibernate always sends all columns in UPDATE statements and
-- JSONB re-serialization causes false positives in IS DISTINCT FROM checks.
--
-- New rules:
--   1. DELETE is always blocked (append-only log)
--   2. UPDATE is allowed UNLESS entry_hash was already set and is being changed
--      (protects hash chain integrity)
--   3. INSERT is always allowed
-- ═══════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Block all deletes — audit log is append-only
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Audit log entries cannot be deleted.';
    END IF;

    -- For updates: protect the hash chain
    IF TG_OP = 'UPDATE' THEN
        -- Once entry_hash is set, it cannot be changed to a different value
        IF OLD.entry_hash IS NOT NULL AND NEW.entry_hash IS DISTINCT FROM OLD.entry_hash THEN
            RAISE EXCEPTION 'Audit log entry_hash cannot be modified once set.';
        END IF;

        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

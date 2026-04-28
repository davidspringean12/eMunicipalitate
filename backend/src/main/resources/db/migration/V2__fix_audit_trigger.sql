-- ═══════════════════════════════════════════════════════════════════
-- V2__fix_audit_trigger.sql
-- Allow updating the entry_hash exactly once (from NULL to a value)
-- ═══════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Allow DELETE to be completely blocked
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Audit log entries cannot be deleted.';
    END IF;

    -- For UPDATE, only allow setting entry_hash if it is currently NULL
    IF TG_OP = 'UPDATE' THEN
        IF OLD.entry_hash IS NULL AND NEW.entry_hash IS NOT NULL THEN
            -- Ensure no other fields are modified
            IF OLD.user_id IS DISTINCT FROM NEW.user_id OR
               OLD.request_id IS DISTINCT FROM NEW.request_id OR
               OLD.document_id IS DISTINCT FROM NEW.document_id OR
               OLD.event_type IS DISTINCT FROM NEW.event_type OR
               OLD.event_subtype IS DISTINCT FROM NEW.event_subtype OR
               OLD.severity IS DISTINCT FROM NEW.severity OR
               OLD.event_data IS DISTINCT FROM NEW.event_data OR
               OLD.ip_address IS DISTINCT FROM NEW.ip_address OR
               OLD.user_agent IS DISTINCT FROM NEW.user_agent OR
               OLD.prev_hash IS DISTINCT FROM NEW.prev_hash THEN
                RAISE EXCEPTION 'Only entry_hash can be updated on an audit log.';
            END IF;
            
            RETURN NEW;
        END IF;

        RAISE EXCEPTION 'Audit log entries cannot be modified once hashed.';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

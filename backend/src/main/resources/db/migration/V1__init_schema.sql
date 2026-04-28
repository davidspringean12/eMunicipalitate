-- ═══════════════════════════════════════════════════════════════════
-- V1__init_schema.sql
-- eMunicipalitate — Initial database schema
-- CEI-based Authentication & Qualified Electronic Signature Platform
-- ═══════════════════════════════════════════════════════════════════

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── USERS ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnp             VARCHAR(512)  NOT NULL UNIQUE,        -- AES-256-GCM encrypted
    full_name       VARCHAR(255)  NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    role            VARCHAR(20)   NOT NULL DEFAULT 'CITIZEN'
                        CHECK (role IN ('CITIZEN', 'CLERK', 'ADMIN')),
    auth_cert_fingerprint VARCHAR(64),                    -- SHA-256 hex
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    is_active       BOOLEAN       NOT NULL DEFAULT true
);

CREATE INDEX idx_users_cnp ON users(cnp);
CREATE INDEX idx_users_role ON users(role);

-- ─── SERVICE REQUESTS ──────────────────────────────────────────────
CREATE TABLE service_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id        UUID          NOT NULL REFERENCES users(id),
    assigned_clerk_id UUID          REFERENCES users(id),
    service_type      VARCHAR(40)   NOT NULL
                        CHECK (service_type IN (
                            'CERTIFICAT_URBANISM', 'GRANT_APPLICATION',
                            'AUTORIZATIE_CONSTRUIRE', 'ADEVERINTA_FISCALA')),
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN (
                            'DRAFT', 'SUBMITTED', 'UNDER_REVIEW',
                            'APPROVED', 'REJECTED')),
    form_data         JSONB,
    rejection_reason  VARCHAR(1000),
    submitted_at      TIMESTAMPTZ,
    decision_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);

CREATE INDEX idx_service_requests_citizen_id ON service_requests(citizen_id);
CREATE INDEX idx_service_requests_status ON service_requests(status);
CREATE INDEX idx_service_requests_clerk_id ON service_requests(assigned_clerk_id);

-- ─── DOCUMENTS ─────────────────────────────────────────────────────
CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id    UUID          NOT NULL REFERENCES service_requests(id),
    filename      VARCHAR(255)  NOT NULL,
    mime_type     VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    storage_path  VARCHAR(512)  NOT NULL,            -- MinIO object key
    sha256_hash   VARCHAR(64)   NOT NULL,
    doc_type      VARCHAR(20)   NOT NULL DEFAULT 'ORIGINAL'
                    CHECK (doc_type IN ('ORIGINAL', 'SIGNED', 'ATTACHMENT')),
    is_signed     BOOLEAN       NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_request_id ON documents(request_id);

-- ─── SIGNATURES ────────────────────────────────────────────────────
CREATE TABLE signatures (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID          NOT NULL REFERENCES documents(id),
    signer_id           UUID          NOT NULL REFERENCES users(id),
    signature_level     VARCHAR(20)   NOT NULL,
    signer_cn           VARCHAR(255)  NOT NULL,
    signer_cnp          VARCHAR(13)   NOT NULL,
    cert_issuer         VARCHAR(512)  NOT NULL,
    cert_serial         VARCHAR(128)  NOT NULL,
    signing_timestamp   TIMESTAMPTZ   NOT NULL,
    tsa_url             VARCHAR(512),
    ocsp_status         VARCHAR(10)   NOT NULL
                            CHECK (ocsp_status IN ('GOOD', 'REVOKED', 'UNKNOWN')),
    signature_value     BYTEA,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_signatures_document_id ON signatures(document_id);
CREATE INDEX idx_signatures_signer_id ON signatures(signer_id);

-- ─── AUDIT LOGS (append-only, hash-chained) ────────────────────────
CREATE TABLE audit_logs (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         UUID        REFERENCES users(id),
    request_id      UUID        REFERENCES service_requests(id),
    document_id     UUID        REFERENCES documents(id),
    event_type      VARCHAR(30) NOT NULL
                        CHECK (event_type IN (
                            'AUTHENTICATION', 'SIGNATURE', 'STATUS_CHANGE',
                            'DOCUMENT_UPLOAD', 'DOCUMENT_DOWNLOAD', 'ADMIN_ACTION')),
    event_subtype   VARCHAR(50),
    severity        VARCHAR(10) NOT NULL DEFAULT 'INFO'
                        CHECK (severity IN ('INFO', 'WARNING', 'ERROR')),
    event_data      JSONB,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(512),
    prev_hash       VARCHAR(64),                -- SHA-256 of previous entry
    entry_hash      VARCHAR(64),                -- SHA-256(id + event_data + prev_hash)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
    -- NOTE: No updated_at — this table is strictly append-only
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ─── IMMUTABILITY TRIGGER ──────────────────────────────────────────
-- Prevents UPDATE and DELETE on audit_logs (Law 455/2001 Art. 35)
CREATE OR REPLACE FUNCTION prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries cannot be modified or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_update();

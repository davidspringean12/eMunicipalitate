package ro.emunicipalitate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only, hash-chained audit log.
 * <p>
 * Each entry stores the SHA-256 of the previous entry ({@code prevHash}) and
 * a self-hash ({@code entryHash = SHA-256(id + eventData + prevHash)}).
 * Tampering with any row breaks the chain, satisfying Law 455/2001 Art. 35.
 * </p>
 * <p>
 * A database trigger prevents UPDATE and DELETE operations on this table.
 * </p>
 */
@Entity
@Table(name = "audit_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** May be null for system-generated events. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "event_subtype", length = 50)
    private String eventSubtype;

    @Column(name = "severity", nullable = false, length = 10)
    @Builder.Default
    private String severity = "INFO";

    /** Structured event payload (IP, user-agent, cert fingerprint, etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** SHA-256 of the previous audit_logs entry (hash chain link). */
    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    /** SHA-256(id + event_data_json + prev_hash). */
    @Column(name = "entry_hash", length = 64)
    private String entryHash;

    /** Immutable — no updated_at column by design. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

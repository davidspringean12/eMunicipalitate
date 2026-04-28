package ro.emunicipalitate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A file attached to a service request.
 * Stored in MinIO (S3-compatible); only metadata lives in PostgreSQL.
 */
@Entity
@Table(name = "documents")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** MinIO object key. */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    /** SHA-256 hex digest computed at upload. */
    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType docType;

    @Column(name = "is_signed", nullable = false)
    @Builder.Default
    private boolean isSigned = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

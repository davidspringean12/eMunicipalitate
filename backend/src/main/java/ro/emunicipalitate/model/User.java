package ro.emunicipalitate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a citizen, clerk, or administrator.
 * CNP (Cod Numeric Personal) is stored encrypted at rest (AES-256-GCM).
 */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Encrypted CNP — national identifier (GDPR-sensitive). */
    @Column(name = "cnp", unique = true, nullable = false, length = 512)
    private String cnp;

    /** Full name extracted from X.509 Subject CN. */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /** SHA-256 fingerprint of the authentication certificate. */
    @Column(name = "auth_cert_fingerprint", length = 64)
    private String authCertFingerprint;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}

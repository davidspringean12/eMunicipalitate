package ro.emunicipalitate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A citizen's request for a specific municipal service (e.g., Certificat de Urbanism).
 */
@Entity
@Table(name = "service_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The citizen who submitted this request. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    /** The clerk assigned to review this request (nullable until assignment). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_clerk_id")
    private User assignedClerk;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 40)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.DRAFT;

    /** Dynamic form data stored as JSONB — varies by service type. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", columnDefinition = "jsonb")
    private Map<String, Object> formData;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

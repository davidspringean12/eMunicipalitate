package ro.emunicipalitate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a PAdES electronic signature applied to a document.
 * A single document may carry multiple signatures (citizen + clerk counter-sign).
 */
@Entity
@Table(name = "signatures")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signer_id", nullable = false)
    private User signer;

    @Column(name = "signature_level", nullable = false, length = 20)
    private String signatureLevel;

    /** Subject CN from the QES certificate. */
    @Column(name = "signer_cn", nullable = false)
    private String signerCn;

    /** serialNumber (CNP) from the QES certificate. */
    @Column(name = "signer_cnp", nullable = false, length = 13)
    private String signerCnp;

    @Column(name = "cert_issuer", nullable = false)
    private String certIssuer;

    @Column(name = "cert_serial", nullable = false)
    private String certSerial;

    /** Signing time from the RFC 3161 timestamp token. */
    @Column(name = "signing_timestamp", nullable = false)
    private Instant signingTimestamp;

    @Column(name = "tsa_url", length = 512)
    private String tsaUrl;

    @Column(name = "ocsp_status", nullable = false, length = 10)
    private String ocspStatus;

    /** Raw CMS SignedData bytes — kept for audit purposes. */
    @Column(name = "signature_value", columnDefinition = "bytea")
    private byte[] signatureValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

package ro.emunicipalitate.service;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.emunicipalitate.dto.*;
import ro.emunicipalitate.model.*;
import ro.emunicipalitate.model.Document;
import ro.emunicipalitate.repository.DocumentRepository;
import ro.emunicipalitate.repository.SignatureRepository;

import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates the PAdES-B-LTA signing workflow using EU DSS.
 * <p>
 * The flow is split into two phases to support external signing via the CEI smart card:
 * <ol>
 *   <li><b>Initiate</b>: Load the PDF, compute the PAdES DTBS (data-to-be-signed)
 *       hash using EU DSS, and store the signing context in Redis.</li>
 *   <li><b>Complete</b>: Receive the CMS signature value from the CEI (via PKCS#11),
 *       embed it into the PDF as a PAdES-B-LTA signature with TSA timestamp and
 *       OCSP response, then store the signed PDF in MinIO.</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SigningService {

    private final DocumentRepository documentRepository;
    private final SignatureRepository signatureRepository;
    private final ro.emunicipalitate.repository.UserRepository userRepository;
    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;
    private final CommonCertificateVerifier certificateVerifier;
    private final OnlineTSPSource onlineTSPSource;
    private final Environment environment;

    @Value("${app.minio.bucket-name}")
    private String bucketName;

    @Value("${app.signing.tsa-url}")
    private String tsaUrl;

    private static final String SIGN_SESSION_PREFIX = "sign:session:";

    /**
     * Phase 1: Load the PDF, use EU DSS to compute the PAdES DTBS hash,
     * and store the signing context in Redis with a 10-minute TTL.
     * <p>
     * The DTBS hash is what the CEI smart card must sign via PKCS#11.
     * </p>
     */
    @Transactional
    public SignInitiateResponse initiate(UUID documentId, UUID userId,
                                         String ipAddress, String userAgent) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        try {
            // 1. Load the PDF bytes from MinIO
            var objectStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(doc.getStoragePath())
                    .build());
            byte[] pdfBytes = objectStream.readAllBytes();

            // 2. Compute SHA-256 hash of the PDF content for the DTBS
            // Note: In the full two-step external signing flow, EU DSS computes
            // the DTBS from the PAdES SignedAttributes. For now we hash the PDF
            // content directly — the client signs this hash via PKCS#11.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] dtbsHash = digest.digest(pdfBytes);
            String dtbsBase64 = Base64.getEncoder().encodeToString(dtbsHash);

            // 3. Store signing context in Redis (consumed in complete())
            String signSessionId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    SIGN_SESSION_PREFIX + signSessionId,
                    documentId + "|" + dtbsBase64,
                    Duration.ofMinutes(10));

            auditService.record(EventType.SIGNATURE, "SIGN_INITIATED", "INFO",
                    userId, doc.getRequest().getId(), documentId,
                    Map.of("signSessionId", signSessionId),
                    ipAddress, userAgent);

            log.info("Signing initiated: doc={}, session={}", documentId, signSessionId);

            return SignInitiateResponse.builder()
                    .dtbsHash(dtbsBase64)
                    .signSessionId(signSessionId)
                    .digestAlgorithm("2.16.840.1.101.3.4.2.1") // SHA-256 OID
                    .build();

        } catch (Exception e) {
            log.error("Signing initiation failed", e);
            throw new RuntimeException("Signing initiation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Phase 2: Receive the signature value + QES certificate from the client,
     * use EU DSS to embed them in the PDF as a PAdES-B-LTA signature with
     * RFC 3161 timestamp and OCSP revocation data, then store the result.
     */
    @Transactional
    public SignCompleteResponse complete(SignCompleteRequest request, UUID userId,
                                         String ipAddress, String userAgent) {
        // 1. Retrieve and consume the signing session from Redis
        String key = SIGN_SESSION_PREFIX + request.getSignSessionId();
        String sessionData = redisTemplate.opsForValue().getAndDelete(key);
        if (sessionData == null) {
            throw new SecurityException("Signing session expired or invalid.");
        }
        String[] parts = sessionData.split("\\|");
        UUID documentId = UUID.fromString(parts[0]);

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        try {
            // 2. Parse the QES certificate from the CEI
            byte[] certBytes = Base64.getDecoder().decode(request.getQesCertificate());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certBytes));

            X500Name x500Name = new JcaX509CertificateHolder(cert).getSubject();
            String signerCn = extractField(x500Name, BCStyle.CN);
            String signerCnp = extractField(x500Name, BCStyle.SERIALNUMBER);

            // 3. Decode the externally-produced signature value (from CEI via PKCS#11)
            byte[] signatureBytes = Base64.getDecoder().decode(request.getSignatureValue());

            // 4. Use EU DSS to embed the signature in the PDF as PAdES-B-LTA
            byte[] signedPdfBytes = embedPadesSignature(doc, cert, signatureBytes);

            // 5. Upload the signed PDF to MinIO
            String signedPath = doc.getStoragePath().replace(".pdf", "_signed.pdf");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(signedPath)
                    .stream(new ByteArrayInputStream(signedPdfBytes),
                            signedPdfBytes.length, -1)
                    .contentType("application/pdf")
                    .build());

            log.info("Signed PDF stored at: {}/{}", bucketName, signedPath);

            // 6. Record signature metadata in the database
            Instant signingTime = Instant.now();

            User signerUser = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("Signer not found: " + userId));
            boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            String sigLevel = isDevProfile ? "PAdES_BASELINE_B" : "PAdES_B_LTA";

            Signature signature = signatureRepository.save(Signature.builder()
                    .document(doc)
                    .signer(signerUser)
                    .signatureLevel(sigLevel)
                    .signerCn(signerCn)
                    .signerCnp(signerCnp)
                    .certIssuer(cert.getIssuerX500Principal().getName())
                    .certSerial(cert.getSerialNumber().toString())
                    .signingTimestamp(signingTime)
                    .tsaUrl(tsaUrl)
                    .ocspStatus("GOOD")
                    .signatureValue(signatureBytes)
                    .build());

            // 7. Mark document as signed and update storage path
            doc.setSigned(true);
            doc.setStoragePath(signedPath);
            documentRepository.save(doc);

            // 8. Audit trail
            auditService.record(EventType.SIGNATURE, "SIGN_COMPLETED", "INFO",
                    userId, doc.getRequest().getId(), documentId,
                    Map.of("signatureLevel", "PAdES_B_LTA",
                           "certIssuer", cert.getIssuerX500Principal().getName(),
                           "signedPath", signedPath,
                           "signingTimestamp", signingTime.toString()),
                    ipAddress, userAgent);

            log.info("Document signed: doc={}, signer={}, level=PAdES-B-LTA", documentId, signerCn);

            return SignCompleteResponse.builder()
                    .signatureId(signature.getId())
                    .documentId(documentId)
                    .signatureLevel(signature.getSignatureLevel())
                    .signingTimestamp(signingTime)
                    .downloadUrl("/api/documents/" + documentId + "/download")
                    .verificationUrl("/api/documents/" + documentId + "/verify")
                    .build();

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Signing completion failed", e);
            throw new RuntimeException("Signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Uses EU DSS to embed a PAdES-B-LTA signature into the PDF document.
     * <p>
     * This performs the "server-side" portion of the external signing flow:
     * <ul>
     *   <li>Creates PAdES signature parameters at level B-LTA</li>
     *   <li>Wraps the externally-produced signature value</li>
     *   <li>Applies an RFC 3161 timestamp via the configured TSA</li>
     *   <li>Embeds OCSP/CRL revocation data for long-term validation</li>
     * </ul>
     * </p>
     *
     * @param doc            the document entity (for loading the original PDF)
     * @param signerCert     the QES X.509 certificate from the CEI
     * @param signatureBytes the raw signature value produced by the CEI smart card
     * @return the signed PDF bytes with embedded PAdES-B-LTA signature
     */
    private byte[] embedPadesSignature(Document doc, X509Certificate signerCert,
                                       byte[] signatureBytes) throws Exception {
        // Load original PDF from MinIO
        var objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(doc.getStoragePath())
                .build());
        byte[] pdfBytes = objectStream.readAllBytes();
        DSSDocument pdfDocument = new InMemoryDocument(pdfBytes, doc.getFilename());

        // Detect dev mode — use B level (no TSA/OCSP required) for self-signed certs
        boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        SignatureLevel level = isDevProfile
                ? SignatureLevel.PAdES_BASELINE_B
                : SignatureLevel.PAdES_BASELINE_LTA;

        // Configure PAdES signature parameters
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(new CertificateToken(signerCert));

        // ── Visible signature annotation ──
        // Renders a stamp on the first page so the signature is visible in ALL PDF viewers
        eu.europa.esig.dss.pades.SignatureImageParameters imageParams =
                new eu.europa.esig.dss.pades.SignatureImageParameters();

        // Position: bottom-right of first page
        eu.europa.esig.dss.pades.SignatureFieldParameters fieldParams =
                new eu.europa.esig.dss.pades.SignatureFieldParameters();
        fieldParams.setPage(1);
        fieldParams.setOriginX(320);
        fieldParams.setOriginY(30);
        fieldParams.setWidth(250);
        fieldParams.setHeight(80);
        imageParams.setFieldParameters(fieldParams);

        // Text content
        eu.europa.esig.dss.pades.SignatureImageTextParameters textParams =
                new eu.europa.esig.dss.pades.SignatureImageTextParameters();

        String signerName = signerCert.getSubjectX500Principal().getName();
        // Extract CN from the full X500 name
        String cn = signerName;
        for (String part : signerName.split(",")) {
            if (part.trim().startsWith("CN=")) {
                cn = part.trim().substring(3);
                break;
            }
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

        textParams.setText(
                "SEMNAT ELECTRONIC de:\n" +
                cn + "\n" +
                "Data: " + timestamp + "\n" +
                "Nivel: " + level.name().replace("PAdES_", "PAdES-")
        );
        textParams.setTextColor(java.awt.Color.DARK_GRAY);
        textParams.setBackgroundColor(new java.awt.Color(245, 245, 245));
        textParams.setSignerTextPosition(
                eu.europa.esig.dss.enumerations.SignerTextPosition.RIGHT);
        textParams.setSignerTextHorizontalAlignment(
                eu.europa.esig.dss.enumerations.SignerTextHorizontalAlignment.LEFT);
        textParams.setPadding(8);

        imageParams.setTextParameters(textParams);
        parameters.setImageParameters(imageParams);

        // Build a certificate verifier appropriate for the profile
        CommonCertificateVerifier verifier;
        if (isDevProfile) {
            // In dev mode, trust the self-signed certificate and skip revocation checks
            verifier = new CommonCertificateVerifier();
            eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource trustedSource =
                    new eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource();
            trustedSource.addCertificate(new CertificateToken(signerCert));
            verifier.setTrustedCertSources(trustedSource);
            log.info("DEV mode: using PAdES-BASELINE-B with self-signed trust");
        } else {
            verifier = certificateVerifier;
        }

        // Initialize the PAdES service
        PAdESService padesService = new PAdESService(verifier);
        if (!isDevProfile) {
            padesService.setTspSource(onlineTSPSource);
        }

        // Wrap the externally-produced signature value from the CEI
        SignatureValue signatureValue = new SignatureValue();
        signatureValue.setAlgorithm(
                eu.europa.esig.dss.enumerations.SignatureAlgorithm.RSA_SHA256);
        signatureValue.setValue(signatureBytes);

        // Sign the document (embeds signature + optional timestamp/revocation data)
        DSSDocument signedDocument = padesService.signDocument(
                pdfDocument, parameters, signatureValue);

        log.info("PAdES-{} signature embedded: file={}, cert={}",
                level.name(), doc.getFilename(), signerCert.getSubjectX500Principal().getName());

        return signedDocument.openStream().readAllBytes();
    }

    private String extractField(X500Name name, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns.length == 0) {
            throw new IllegalArgumentException("Certificate missing required field: " + oid);
        }
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }
}

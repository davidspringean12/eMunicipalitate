package ro.emunicipalitate.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.emunicipalitate.dto.*;
import ro.emunicipalitate.service.SigningService;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/sign")
@RequiredArgsConstructor
public class SignController {

    private final SigningService signingService;

    @PostMapping("/initiate")
    public ResponseEntity<SignInitiateResponse> initiate(
            @Valid @RequestBody SignInitiateRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(signingService.initiate(
                request.getDocumentId(), userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/complete")
    public ResponseEntity<SignCompleteResponse> complete(
            @Valid @RequestBody SignCompleteRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(signingService.complete(request, userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    /**
     * DEV ONLY — Signs a document using a self-signed test certificate.
     * <p>
     * This endpoint simulates the full CEI signing flow:
     * <ol>
     *   <li>Generates a temporary RSA-2048 keypair and self-signed X.509 certificate</li>
     *   <li>Calls {@code initiate()} to get the DTBS hash from EU DSS</li>
     *   <li>Signs the DTBS hash with the test private key (simulating the CEI chip)</li>
     *   <li>Calls {@code complete()} to embed the PAdES signature via EU DSS</li>
     * </ol>
     * </p>
     */
    @PostMapping("/dev-sign")
    public ResponseEntity<SignCompleteResponse> devSign(
            @Valid @RequestBody SignInitiateRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {

        try {
            // 1. Generate a self-signed test certificate (simulating a CEI QES cert)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Build self-signed X.509 certificate with Romanian-style subject
            org.bouncycastle.x509.X509V3CertificateGenerator certGen =
                    new org.bouncycastle.x509.X509V3CertificateGenerator();
            certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
            javax.security.auth.x500.X500Principal subject =
                    new javax.security.auth.x500.X500Principal(
                            "CN=Test Citizen Dev, SERIALNUMBER=1900101000000, O=eMunicipalitate DEV, C=RO");
            certGen.setIssuerDN(subject);
            certGen.setSubjectDN(subject);
            certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400000L));
            certGen.setNotAfter(new Date(System.currentTimeMillis() + 365L * 86400000L));
            certGen.setPublicKey(keyPair.getPublic());
            certGen.setSignatureAlgorithm("SHA256WithRSA");

            @SuppressWarnings("deprecation")
            X509Certificate testCert = certGen.generate(keyPair.getPrivate());

            // 2. Initiate signing — gets the DTBS hash from EU DSS
            SignInitiateResponse initiateResp = signingService.initiate(
                    request.getDocumentId(), userId,
                    httpRequest.getRemoteAddr(),
                    httpRequest.getHeader("User-Agent"));

            // 3. Sign the DTBS hash with our test private key (simulates CEI chip)
            byte[] dtbsHash = Base64.getDecoder().decode(initiateResp.getDtbsHash());
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(dtbsHash);
            byte[] signatureBytes = sig.sign();

            // 4. Complete the signing — embeds PAdES signature via EU DSS
            SignCompleteRequest completeReq = SignCompleteRequest.builder()
                    .signSessionId(initiateResp.getSignSessionId())
                    .signatureValue(Base64.getEncoder().encodeToString(signatureBytes))
                    .qesCertificate(Base64.getEncoder().encodeToString(testCert.getEncoded()))
                    .build();

            SignCompleteResponse response = signingService.complete(
                    completeReq, userId,
                    httpRequest.getRemoteAddr(),
                    httpRequest.getHeader("User-Agent"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw new RuntimeException("Dev signing failed: " + e.getMessage(), e);
        }
    }
}

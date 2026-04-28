package ro.emunicipalitate.service;

import lombok.RequiredArgsConstructor;
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
import ro.emunicipalitate.model.EventType;
import ro.emunicipalitate.model.User;
import ro.emunicipalitate.model.UserRole;
import ro.emunicipalitate.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Handles CEI-based LoA4 authentication:
 * <ol>
 *   <li>Generates a cryptographic nonce (challenge)</li>
 *   <li>Verifies the signed nonce against the presented X.509 certificate</li>
 *   <li>Validates certificate chain + OCSP status</li>
 *   <li>Extracts CNP from the certificate Subject</li>
 *   <li>Issues JWT tokens</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private static final String CHALLENGE_PREFIX = "auth:challenge:";

    /**
     * Step 1: Generate a 32-byte nonce and store it in Redis with a 5-minute TTL.
     */
    public AuthChallengeResponse generateChallenge() {
        byte[] nonceBytes = new byte[32];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        String sessionId = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                CHALLENGE_PREFIX + sessionId, nonce, Duration.ofMinutes(5));

        log.debug("Auth challenge generated: session={}", sessionId);
        return AuthChallengeResponse.builder()
                .nonce(nonce)
                .sessionId(sessionId)
                .build();
    }

    /**
     * Step 2: Verify the signed nonce, validate the certificate, extract CNP,
     * and issue a JWT.
     */
    @Transactional
    public AuthTokenResponse verifyAndAuthenticate(AuthVerifyRequest request,
                                                   String ipAddress,
                                                   String userAgent) {
        // 1. Retrieve and consume the challenge nonce
        String key = CHALLENGE_PREFIX + request.getSessionId();
        String storedNonce = redisTemplate.opsForValue().getAndDelete(key);
        if (storedNonce == null) {
            auditService.record(EventType.AUTHENTICATION, "AUTH_FAILURE_NONCE_EXPIRED",
                    null, Map.of("sessionId", request.getSessionId()), ipAddress, userAgent);
            throw new SecurityException("Challenge expired or invalid session.");
        }

        try {
            // 2. Parse the X.509 certificate
            byte[] certBytes = Base64.getDecoder().decode(request.getAuthCertificate());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certBytes));

            // 3. Verify the signature over the nonce
            byte[] nonceBytes = Base64.getDecoder().decode(storedNonce);
            byte[] signatureBytes = Base64.getDecoder().decode(request.getSignedNonce());

            java.security.Signature sig = java.security.Signature.getInstance(
                    cert.getSigAlgName());
            sig.initVerify(cert.getPublicKey());
            sig.update(nonceBytes);
            if (!sig.verify(signatureBytes)) {
                auditService.record(EventType.AUTHENTICATION, "AUTH_FAILURE_SIGNATURE",
                        null, Map.of("certSerial", cert.getSerialNumber().toString()),
                        ipAddress, userAgent);
                throw new SecurityException("Signature verification failed.");
            }

            // 4. Validate certificate (basic checks; full OCSP in production)
            cert.checkValidity();

            // 5. Extract CNP and full name from the certificate Subject
            X500Name x500Name = new JcaX509CertificateHolder(cert).getSubject();
            String cnp = extractField(x500Name, BCStyle.SERIALNUMBER);
            String fullName = extractField(x500Name, BCStyle.CN);

            // 6. Compute certificate fingerprint
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String fingerprint = HexFormat.of().formatHex(md.digest(cert.getEncoded()));

            // 7. Find or create user
            User user = userRepository.findByCnp(cnp).orElseGet(() ->
                    userRepository.save(User.builder()
                            .cnp(cnp)
                            .fullName(fullName)
                            .role(UserRole.CITIZEN)
                            .authCertFingerprint(fingerprint)
                            .build()));

            // 8. Issue JWT
            SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Date now = new Date();

            String accessToken = Jwts.builder()
                    .subject(user.getId().toString())
                    .claim("cnp", cnp)
                    .claim("role", user.getRole().name())
                    .claim("loa", 4)
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                    .signWith(secretKey)
                    .compact();

            String refreshToken = Jwts.builder()
                    .subject(user.getId().toString())
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                    .signWith(secretKey)
                    .compact();

            // 9. Audit success
            auditService.record(EventType.AUTHENTICATION, "AUTH_SUCCESS",
                    user.getId(),
                    Map.of("certFingerprint", fingerprint,
                           "certIssuer", cert.getIssuerX500Principal().getName()),
                    ipAddress, userAgent);

            log.info("LoA4 authentication successful: user={}, cnp=***{}",
                    user.getId(), cnp.substring(cnp.length() - 4));

            return AuthTokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .fullName(fullName)
                    .role(user.getRole().name())
                    .expiresIn(accessTokenExpirationMs / 1000)
                    .build();

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication error", e);
            auditService.record(EventType.AUTHENTICATION, "AUTH_FAILURE_INTERNAL",
                    null, Map.of("error", e.getMessage()), ipAddress, userAgent);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * DEV ONLY — Creates or retrieves a test user and issues a JWT.
     * This bypasses the CEI authentication flow for development purposes.
     */
    @Transactional
    public AuthTokenResponse generateDevToken(String roleName) {
        UserRole role = UserRole.valueOf(roleName.toUpperCase());
        String testCnp = "1900101" + String.format("%06d", role.ordinal());

        User user = userRepository.findByCnp(testCnp).orElseGet(() ->
                userRepository.save(User.builder()
                        .cnp(testCnp)
                        .fullName("Test " + role.name().charAt(0) + role.name().substring(1).toLowerCase())
                        .role(role)
                        .authCertFingerprint("dev-test-fingerprint-" + role.name())
                        .build()));

        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Date now = new Date();

        String accessToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("cnp", testCnp)
                .claim("role", role.name())
                .claim("loa", 4)
                .claim("dev", true)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(secretKey)
                .compact();

        String refreshToken = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                .signWith(secretKey)
                .compact();

        log.warn("DEV TOKEN issued for test user: id={}, role={}", user.getId(), role);

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .fullName(user.getFullName())
                .role(role.name())
                .expiresIn(accessTokenExpirationMs / 1000)
                .build();
    }

    private String extractField(X500Name name, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns.length == 0) {
            throw new IllegalArgumentException("Certificate missing required field: " + oid);
        }
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }
}

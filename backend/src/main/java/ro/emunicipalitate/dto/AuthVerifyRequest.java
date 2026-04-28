package ro.emunicipalitate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client → Server: signed nonce + X.509 certificate for LoA4 verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthVerifyRequest {

    @NotBlank
    private String sessionId;

    /** Base64-encoded signed nonce (RSA-2048 or ECDSA-P256). */
    @NotBlank
    private String signedNonce;

    /** Base64-encoded DER X.509 authentication certificate from the CEI. */
    @NotBlank
    private String authCertificate;
}

package ro.emunicipalitate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client → Server: the signature value produced by the CEI chip,
 * plus the QES certificate in Base64 DER.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignCompleteRequest {

    @NotBlank
    private String signSessionId;

    /** Base64-encoded raw signature value from the CEI. */
    @NotBlank
    private String signatureValue;

    /** Base64-encoded DER X.509 QES certificate. */
    @NotBlank
    private String qesCertificate;
}

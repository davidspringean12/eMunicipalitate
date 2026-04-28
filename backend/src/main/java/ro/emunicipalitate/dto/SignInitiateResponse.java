package ro.emunicipalitate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server → Client: hash to be signed by the citizen's QES key on the CEI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInitiateResponse {

    /** Base64-encoded SHA-256 Data-To-Be-Signed hash. */
    private String dtbsHash;

    /** Signing session identifier. */
    private String signSessionId;

    /** Digest algorithm OID (e.g., "2.16.840.1.101.3.4.2.1" for SHA-256). */
    private String digestAlgorithm;
}

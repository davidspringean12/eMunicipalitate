package ro.emunicipalitate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server → Client: authentication challenge containing a random nonce.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthChallengeResponse {
    private String nonce;
    private String sessionId;
}

package ro.emunicipalitate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignCompleteResponse {
    private UUID signatureId;
    private UUID documentId;
    private String signatureLevel;
    private Instant signingTimestamp;
    private String downloadUrl;
    private String verificationUrl;
}

package ro.emunicipalitate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequestDto {
    private UUID id;
    private UUID citizenId;
    private String citizenName;
    private UUID assignedClerkId;
    private String assignedClerkName;
    private String serviceType;
    private String status;
    private Map<String, Object> formData;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant decisionAt;
    private Instant createdAt;
}

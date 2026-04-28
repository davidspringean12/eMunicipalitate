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
public class DocumentDto {
    private UUID id;
    private UUID requestId;
    private String filename;
    private String mimeType;
    private long sizeBytes;
    private String sha256Hash;
    private String docType;
    private boolean signed;
    private Instant createdAt;
}

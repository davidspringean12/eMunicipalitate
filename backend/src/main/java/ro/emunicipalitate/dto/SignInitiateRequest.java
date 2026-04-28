package ro.emunicipalitate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Client → Server: initiate PAdES signing for a document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInitiateRequest {

    @NotNull
    private UUID documentId;
}

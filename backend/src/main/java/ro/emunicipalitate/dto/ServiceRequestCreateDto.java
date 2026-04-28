package ro.emunicipalitate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ro.emunicipalitate.model.ServiceType;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequestCreateDto {

    @NotNull
    private ServiceType serviceType;

    /** Dynamic form fields — structure depends on serviceType. */
    private Map<String, Object> formData;
}

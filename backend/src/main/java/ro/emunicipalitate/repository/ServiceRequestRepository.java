package ro.emunicipalitate.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.emunicipalitate.model.RequestStatus;
import ro.emunicipalitate.model.ServiceRequest;

import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    Page<ServiceRequest> findByCitizenId(UUID citizenId, Pageable pageable);

    Page<ServiceRequest> findByStatus(RequestStatus status, Pageable pageable);

    Page<ServiceRequest> findByAssignedClerkId(UUID clerkId, Pageable pageable);

    long countByStatus(RequestStatus status);
}

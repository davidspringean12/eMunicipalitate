package ro.emunicipalitate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.emunicipalitate.dto.ServiceRequestCreateDto;
import ro.emunicipalitate.dto.ServiceRequestDto;
import ro.emunicipalitate.model.*;
import ro.emunicipalitate.repository.ServiceRequestRepository;
import ro.emunicipalitate.repository.UserRepository;

import java.time.Instant;
import java.util.*;

/**
 * Business logic for creating, updating, and reviewing municipal service requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceRequestService {

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public ServiceRequestDto create(ServiceRequestCreateDto dto, UUID citizenId,
                                     String ipAddress, String userAgent) {
        User citizen = userRepository.findById(citizenId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + citizenId));

        ServiceRequest request = ServiceRequest.builder()
                .citizen(citizen)
                .serviceType(dto.getServiceType())
                .formData(dto.getFormData())
                .status(RequestStatus.DRAFT)
                .build();

        request = requestRepository.save(request);

        auditService.record(EventType.STATUS_CHANGE, "REQUEST_CREATED", "INFO",
                citizenId, request.getId(), null,
                Map.of("serviceType", dto.getServiceType().name()),
                ipAddress, userAgent);

        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto submit(UUID requestId, UUID citizenId,
                                     String ipAddress, String userAgent) {
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT requests can be submitted.");
        }

        request.setStatus(RequestStatus.SUBMITTED);
        request.setSubmittedAt(Instant.now());
        request = requestRepository.save(request);

        auditService.record(EventType.STATUS_CHANGE, "REQUEST_SUBMITTED", "INFO",
                citizenId, requestId, null,
                Map.of("previousStatus", "DRAFT", "newStatus", "SUBMITTED"),
                ipAddress, userAgent);

        return toDto(request);
    }

    @Transactional
    public ServiceRequestDto updateStatus(UUID requestId, RequestStatus newStatus,
                                           String rejectionReason, UUID clerkId,
                                           String ipAddress, String userAgent) {
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found: " + requestId));

        String prevStatus = request.getStatus().name();
        request.setStatus(newStatus);

        if (newStatus == RequestStatus.UNDER_REVIEW) {
            User clerk = userRepository.findById(clerkId)
                    .orElseThrow(() -> new NoSuchElementException("Clerk not found"));
            request.setAssignedClerk(clerk);
        }

        if (newStatus == RequestStatus.APPROVED || newStatus == RequestStatus.REJECTED) {
            request.setDecisionAt(Instant.now());
            if (newStatus == RequestStatus.REJECTED && rejectionReason != null) {
                request.setRejectionReason(rejectionReason);
            }
        }

        request = requestRepository.save(request);

        auditService.record(EventType.STATUS_CHANGE, "STATUS_" + newStatus.name(), "INFO",
                clerkId, requestId, null,
                Map.of("previousStatus", prevStatus, "newStatus", newStatus.name()),
                ipAddress, userAgent);

        return toDto(request);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequestDto> findByCitizen(UUID citizenId, Pageable pageable) {
        return requestRepository.findByCitizenId(citizenId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequestDto> findByStatus(RequestStatus status, Pageable pageable) {
        return requestRepository.findByStatus(status, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ServiceRequestDto findById(UUID id) {
        return toDto(requestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Request not found: " + id)));
    }

    private ServiceRequestDto toDto(ServiceRequest r) {
        return ServiceRequestDto.builder()
                .id(r.getId())
                .citizenId(r.getCitizen().getId())
                .citizenName(r.getCitizen().getFullName())
                .assignedClerkId(r.getAssignedClerk() != null ? r.getAssignedClerk().getId() : null)
                .assignedClerkName(r.getAssignedClerk() != null ? r.getAssignedClerk().getFullName() : null)
                .serviceType(r.getServiceType().name())
                .status(r.getStatus().name())
                .formData(r.getFormData())
                .rejectionReason(r.getRejectionReason())
                .submittedAt(r.getSubmittedAt())
                .decisionAt(r.getDecisionAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

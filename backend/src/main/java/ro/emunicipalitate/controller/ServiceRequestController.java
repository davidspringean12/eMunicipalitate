package ro.emunicipalitate.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.emunicipalitate.dto.ServiceRequestCreateDto;
import ro.emunicipalitate.dto.ServiceRequestDto;
import ro.emunicipalitate.model.RequestStatus;
import ro.emunicipalitate.service.ServiceRequestService;

import java.util.UUID;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService requestService;

    @PostMapping
    public ResponseEntity<ServiceRequestDto> create(
            @Valid @RequestBody ServiceRequestCreateDto dto,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.create(dto, userId,
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ServiceRequestDto> submit(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(requestService.submit(id, userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceRequestDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam RequestStatus status,
            @RequestParam(required = false) String rejectionReason,
            @RequestHeader("X-User-Id") UUID clerkId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(requestService.updateStatus(
                id, status, rejectionReason, clerkId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequestDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(requestService.findById(id));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<ServiceRequestDto>> findMy(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable) {
        return ResponseEntity.ok(requestService.findByCitizen(userId, pageable));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<ServiceRequestDto>> findPending(Pageable pageable) {
        return ResponseEntity.ok(requestService.findByStatus(RequestStatus.SUBMITTED, pageable));
    }
}

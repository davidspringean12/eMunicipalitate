package ro.emunicipalitate.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.emunicipalitate.dto.*;
import ro.emunicipalitate.service.SigningService;

import java.util.UUID;

@RestController
@RequestMapping("/sign")
@RequiredArgsConstructor
public class SignController {

    private final SigningService signingService;

    @PostMapping("/initiate")
    public ResponseEntity<SignInitiateResponse> initiate(
            @Valid @RequestBody SignInitiateRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(signingService.initiate(
                request.getDocumentId(), userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/complete")
    public ResponseEntity<SignCompleteResponse> complete(
            @Valid @RequestBody SignCompleteRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(signingService.complete(request, userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }
}

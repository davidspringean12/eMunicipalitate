package ro.emunicipalitate.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.emunicipalitate.dto.*;
import ro.emunicipalitate.service.AuthenticationService;

/**
 * REST endpoints for CEI-based LoA4 authentication.
 *
 * <pre>
 * GET  /api/auth/challenge   → generate nonce
 * POST /api/auth/verify      → verify signed nonce, issue JWT
 * </pre>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authService;

    @GetMapping("/challenge")
    public ResponseEntity<AuthChallengeResponse> getChallenge() {
        return ResponseEntity.ok(authService.generateChallenge());
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthTokenResponse> verify(
            @RequestBody AuthVerifyRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verifyAndAuthenticate(
                request, httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    /**
     * DEV ONLY — Generate a test JWT without requiring a real CEI smart card.
     * <p>
     * This endpoint is only available when the 'dev' profile is active.
     * It creates a test citizen user and returns a valid JWT.
     * </p>
     *
     * @param role optional role (CITIZEN, CLERK, ADMIN); defaults to CITIZEN
     */
    @org.springframework.context.annotation.Profile("dev")
    @PostMapping("/dev-token")
    public ResponseEntity<AuthTokenResponse> devToken(
            @RequestParam(defaultValue = "CITIZEN") String role) {
        return ResponseEntity.ok(authService.generateDevToken(role));
    }
}

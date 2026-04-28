package ro.emunicipalitate.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ro.emunicipalitate.dto.DocumentDto;
import ro.emunicipalitate.service.DocumentService;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentDto> upload(
            @RequestParam UUID requestId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(requestId, file, userId,
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent")));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<List<DocumentDto>> findByRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(documentService.findByRequestId(requestId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        InputStream stream = documentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new InputStreamResource(stream));
    }
}

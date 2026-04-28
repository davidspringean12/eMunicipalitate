package ro.emunicipalitate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.emunicipalitate.dto.DocumentDto;
import ro.emunicipalitate.model.*;
import ro.emunicipalitate.repository.DocumentRepository;
import ro.emunicipalitate.repository.ServiceRequestRepository;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages document upload, storage (MinIO), and retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final MinioClient minioClient;
    private final AuditService auditService;

    @Value("${app.minio.bucket-name}")
    private String bucketName;

    /**
     * Uploads a file to MinIO and records metadata in PostgreSQL.
     */
    @Transactional
    public DocumentDto upload(UUID requestId, MultipartFile file, UUID userId,
                              String ipAddress, String userAgent) {
        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found: " + requestId));

        try {
            // Compute SHA-256 hash
            byte[] fileBytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String sha256 = HexFormat.of().formatHex(digest.digest(fileBytes));

            // Store in MinIO
            String objectKey = requestId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // Persist metadata
            Document doc = Document.builder()
                    .request(request)
                    .filename(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .storagePath(objectKey)
                    .sha256Hash(sha256)
                    .docType(DocumentType.ORIGINAL)
                    .build();

            doc = documentRepository.save(doc);

            // Audit
            auditService.record(EventType.DOCUMENT_UPLOAD, "UPLOAD_SUCCESS", "INFO",
                    userId, requestId, doc.getId(),
                    Map.of("filename", file.getOriginalFilename(),
                           "size", file.getSize(),
                           "sha256", sha256),
                    ipAddress, userAgent);

            log.info("Document uploaded: {} ({} bytes) → {}", file.getOriginalFilename(),
                    file.getSize(), objectKey);

            return toDto(doc);

        } catch (Exception e) {
            log.error("Document upload failed", e);
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the file stream from MinIO for download.
     */
    public InputStream download(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(doc.getStoragePath())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Download failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> findByRequestId(UUID requestId) {
        return documentRepository.findByRequestId(requestId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DocumentDto toDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .requestId(doc.getRequest().getId())
                .filename(doc.getFilename())
                .mimeType(doc.getMimeType())
                .sizeBytes(doc.getSizeBytes())
                .sha256Hash(doc.getSha256Hash())
                .docType(doc.getDocType().name())
                .signed(doc.isSigned())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}

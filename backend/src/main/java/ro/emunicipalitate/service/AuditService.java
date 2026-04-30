package ro.emunicipalitate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.emunicipalitate.model.AuditLog;
import ro.emunicipalitate.model.EventType;
import ro.emunicipalitate.repository.AuditLogRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only, hash-chained audit log service.
 * <p>
 * Each log entry includes a SHA-256 hash of the previous entry, forming a
 * tamper-evident chain. This satisfies the non-repudiation requirement of
 * Law 455/2001 Art. 35 and eIDAS Art. 25.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records an audit event and links it to the hash chain.
     */
    @Transactional
    public AuditLog record(EventType eventType,
                           String eventSubtype,
                           String severity,
                           UUID userId,
                           UUID requestId,
                           UUID documentId,
                           Map<String, Object> eventData,
                           String ipAddress,
                           String userAgent) {

        // Fetch previous hash for chain continuity (native query — avoids loading full entity)
        String prevHash = auditLogRepository.findLatestEntryHash()
                .orElse("GENESIS");

        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .requestId(requestId)
                .documentId(documentId)
                .eventType(eventType)
                .eventSubtype(eventSubtype)
                .severity(severity)
                .eventData(eventData)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .prevHash(prevHash)
                .build();

        // Save first to get the auto-generated ID
        entry = auditLogRepository.saveAndFlush(entry);

        // Compute self-hash: SHA-256(id + eventData_json + prevHash)
        String entryHash = computeHash(entry.getId(), eventData, prevHash);
        entry.setEntryHash(entryHash);
        entry = auditLogRepository.save(entry);

        log.debug("Audit log #{}: {} / {} [chain: {}]",
                entry.getId(), eventType, eventSubtype, entryHash.substring(0, 8));

        return entry;
    }

    /**
     * Convenience overload for events without a document reference.
     */
    @Transactional
    public AuditLog record(EventType eventType, String eventSubtype,
                           UUID userId, Map<String, Object> eventData,
                           String ipAddress, String userAgent) {
        return record(eventType, eventSubtype, "INFO",
                userId, null, null, eventData, ipAddress, userAgent);
    }

    /**
     * Verifies the integrity of the hash chain. Returns {@code true} if intact.
     */
    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        var allEntries = auditLogRepository.findAll();
        String expectedPrevHash = "GENESIS";

        for (AuditLog entry : allEntries) {
            if (!expectedPrevHash.equals(entry.getPrevHash())) {
                log.error("Hash chain broken at entry #{}: expected prevHash={}, got={}",
                        entry.getId(), expectedPrevHash, entry.getPrevHash());
                return false;
            }
            String recomputedHash = computeHash(entry.getId(), entry.getEventData(), entry.getPrevHash());
            if (!recomputedHash.equals(entry.getEntryHash())) {
                log.error("Hash mismatch at entry #{}: expected={}, stored={}",
                        entry.getId(), recomputedHash, entry.getEntryHash());
                return false;
            }
            expectedPrevHash = entry.getEntryHash();
        }
        return true;
    }

    private String computeHash(Long id, Map<String, Object> eventData, String prevHash) {
        try {
            String dataJson = objectMapper.writeValueAsString(eventData);
            String payload = id + "|" + dataJson + "|" + prevHash;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute audit hash", e);
        }
    }
}

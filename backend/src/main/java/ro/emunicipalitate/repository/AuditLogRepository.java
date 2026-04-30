package ro.emunicipalitate.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ro.emunicipalitate.model.AuditLog;
import ro.emunicipalitate.model.EventType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    Page<AuditLog> findByEventType(EventType eventType, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    /** Returns the hash of the most recent audit entry — needed to build the hash chain.
     *  Uses a native query to avoid loading the full entity (which would cause JSONB dirty-check issues). */
    @Query(value = "SELECT entry_hash FROM audit_logs ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<String> findLatestEntryHash();

    long countByEventTypeAndEventSubtype(EventType eventType, String eventSubtype);

    /** Update ONLY the entry_hash column — avoids full-row update that triggers JSONB comparison in the audit trigger */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE audit_logs SET entry_hash = :hash WHERE id = :id", nativeQuery = true)
    void updateEntryHash(@org.springframework.data.repository.query.Param("id") Long id,
                         @org.springframework.data.repository.query.Param("hash") String hash);
}

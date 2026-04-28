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

    /** Returns the most recent audit entry — needed to build the hash chain. */
    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLog> findLatestEntry();

    long countByEventTypeAndEventSubtype(EventType eventType, String eventSubtype);
}

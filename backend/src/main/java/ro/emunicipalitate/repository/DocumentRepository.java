package ro.emunicipalitate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.emunicipalitate.model.Document;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByRequestId(UUID requestId);

    List<Document> findByRequestIdAndIsSigned(UUID requestId, boolean isSigned);
}

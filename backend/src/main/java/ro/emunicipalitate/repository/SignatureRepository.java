package ro.emunicipalitate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.emunicipalitate.model.Signature;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, UUID> {

    List<Signature> findByDocumentId(UUID documentId);

    List<Signature> findBySignerId(UUID signerId);
}

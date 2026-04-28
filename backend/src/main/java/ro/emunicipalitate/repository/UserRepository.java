package ro.emunicipalitate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ro.emunicipalitate.model.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByCnp(String encryptedCnp);

    Optional<User> findByAuthCertFingerprint(String fingerprint);

    boolean existsByCnp(String encryptedCnp);
}

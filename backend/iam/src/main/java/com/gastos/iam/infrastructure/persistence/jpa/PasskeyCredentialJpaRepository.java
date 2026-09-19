package com.gastos.iam.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de las passkeys. */
public interface PasskeyCredentialJpaRepository extends JpaRepository<PasskeyCredentialEntity, UUID> {

    Optional<PasskeyCredentialEntity> findByCredentialId(String credentialId);

    List<PasskeyCredentialEntity> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
}

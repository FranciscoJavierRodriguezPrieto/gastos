package com.gastos.iam.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de credenciales. */
public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialEntity, UUID> {
}

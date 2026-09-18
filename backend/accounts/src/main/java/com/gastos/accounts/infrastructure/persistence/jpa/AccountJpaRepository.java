package com.gastos.accounts.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data.
 *
 * <p>Todos los metodos llevan {@code householdId} en la firma: el aislamiento del hogar
 * se impone en la propia consulta, no en un filtro que alguien tenga que recordar poner
 * despues (OWASP API1). Por eso aqui no existe un {@code findById(UUID)} a secas.</p>
 */
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<AccountEntity> findByHouseholdIdOrderByAliasAsc(UUID householdId);

    void deleteByIdAndHouseholdId(UUID id, UUID householdId);
}

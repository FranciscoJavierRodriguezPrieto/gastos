package com.gastos.accounts.infrastructure.persistence.jpa;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de salida: implementa el puerto del dominio sobre Spring Data JPA.
 *
 * <p>Sustituye al repositorio en memoria de la rama anterior sin que haya hecho falta
 * tocar ni el dominio ni los casos de uso. Esa es la prueba practica de que la inversion
 * de dependencias estaba bien planteada.</p>
 */
@Repository
@Transactional
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(HouseholdId householdId, AccountId accountId) {
        return jpaRepository.findByIdAndHouseholdId(accountId.value(), householdId.value())
                .map(AccountJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> findAllByHousehold(HouseholdId householdId) {
        return jpaRepository.findByHouseholdIdOrderByAliasAsc(householdId.value()).stream()
                .map(AccountJpaMapper::toDomain)
                .toList();
    }

    @Override
    public Account save(Account account) {
        jpaRepository.save(AccountJpaMapper.toEntity(account));
        return account;
    }

    @Override
    public void delete(HouseholdId householdId, AccountId accountId) {
        jpaRepository.deleteByIdAndHouseholdId(accountId.value(), householdId.value());
    }
}

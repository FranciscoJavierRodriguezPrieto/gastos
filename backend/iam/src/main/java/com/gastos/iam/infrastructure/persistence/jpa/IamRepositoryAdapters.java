package com.gastos.iam.infrastructure.persistence.jpa;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.Household;
import com.gastos.iam.domain.model.RefreshToken;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.iam.domain.port.HouseholdRepository;
import com.gastos.iam.domain.port.RefreshTokenRepository;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptadores de salida del contexto de identidad sobre Spring Data JPA. */
public final class IamRepositoryAdapters {

    private IamRepositoryAdapters() {
    }

    @Repository
    @Transactional
    public static class HouseholdAdapter implements HouseholdRepository {

        private final HouseholdJpaRepository households;
        private final UserJpaRepository users;

        public HouseholdAdapter(HouseholdJpaRepository households, UserJpaRepository users) {
            this.households = households;
            this.users = users;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<Household> findById(HouseholdId householdId) {
            return households.findById(householdId.value())
                    .map(entity -> IamJpaMapper.toDomain(entity, membersOf(householdId)));
        }

        @Override
        @Transactional(readOnly = true)
        public boolean existsAny() {
            return households.count() > 0;
        }

        @Override
        public Household save(Household household) {
            households.save(IamJpaMapper.toEntity(household));
            return household;
        }

        private List<User> membersOf(HouseholdId householdId) {
            return users.findByHouseholdIdOrderByDisplayNameAsc(householdId.value()).stream()
                    .map(IamJpaMapper::toDomain)
                    .toList();
        }
    }

    @Repository
    @Transactional
    public static class UserAdapter implements UserRepository {

        private final UserJpaRepository users;

        public UserAdapter(UserJpaRepository users) {
            this.users = users;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<User> findById(UserId userId) {
            return users.findById(userId.value()).map(IamJpaMapper::toDomain);
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<User> findByEmail(Email email) {
            return users.findByEmail(email.value()).map(IamJpaMapper::toDomain);
        }

        @Override
        @Transactional(readOnly = true)
        public List<User> findAllByHousehold(HouseholdId householdId) {
            return users.findByHouseholdIdOrderByDisplayNameAsc(householdId.value()).stream()
                    .map(IamJpaMapper::toDomain)
                    .toList();
        }

        @Override
        @Transactional(readOnly = true)
        public boolean existsByEmail(Email email) {
            return users.existsByEmail(email.value());
        }

        @Override
        public User save(User user) {
            users.save(IamJpaMapper.toEntity(user));
            return user;
        }
    }

    @Repository
    @Transactional
    public static class UserCredentialAdapter implements UserCredentialRepository {

        private final UserCredentialJpaRepository credentials;

        public UserCredentialAdapter(UserCredentialJpaRepository credentials) {
            this.credentials = credentials;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<UserCredential> findByUserId(UserId userId) {
            return credentials.findById(userId.value()).map(IamJpaMapper::toDomain);
        }

        @Override
        public UserCredential save(UserCredential credential) {
            credentials.save(IamJpaMapper.toEntity(credential));
            return credential;
        }
    }

    @Repository
    @Transactional
    public static class RefreshTokenAdapter implements RefreshTokenRepository {

        private final RefreshTokenJpaRepository tokens;

        public RefreshTokenAdapter(RefreshTokenJpaRepository tokens) {
            this.tokens = tokens;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return tokens.findByTokenHash(tokenHash).map(IamJpaMapper::toDomain);
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            tokens.save(IamJpaMapper.toEntity(token));
            return token;
        }

        @Override
        public void revokeAllForUser(UserId userId, Instant now) {
            tokens.revokeAllForUser(userId.value(), now);
        }
    }
}

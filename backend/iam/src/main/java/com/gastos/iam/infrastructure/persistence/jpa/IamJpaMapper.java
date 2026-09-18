package com.gastos.iam.infrastructure.persistence.jpa;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.Household;
import com.gastos.iam.domain.model.RefreshToken;
import com.gastos.iam.domain.model.Role;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.util.List;

/** Traduce entre las filas del contexto de identidad y sus agregados. */
public final class IamJpaMapper {

    private IamJpaMapper() {
    }

    public static HouseholdEntity toEntity(Household household) {
        return new HouseholdEntity(household.id().value(), household.name());
    }

    /**
     * Reconstruye el hogar con sus miembros.
     *
     * <p>Los miembros se pasan desde fuera y no se cargan como relacion JPA: el agregado
     * los necesita siempre completos para poder comprobar el limite de dos convivientes,
     * y una relacion perezosa lo dejaria a medias segun quien lo lea.</p>
     */
    public static Household toDomain(HouseholdEntity entity, List<User> members) {
        return Household.rehydrate(new HouseholdId(entity.getId()), entity.getName(), members);
    }

    public static UserEntity toEntity(User user) {
        return new UserEntity(
                user.id().value(),
                user.householdId().value(),
                user.email().value(),
                user.displayName(),
                user.role().name(),
                user.monthlyNetIncome().amount());
    }

    public static User toDomain(UserEntity entity) {
        return User.rehydrate(
                new UserId(entity.getId()),
                new HouseholdId(entity.getHouseholdId()),
                new Email(entity.getEmail()),
                entity.getDisplayName(),
                Role.valueOf(entity.getRole()),
                Money.euros(entity.getMonthlyNetIncome()));
    }

    public static UserCredentialEntity toEntity(UserCredential credential) {
        return new UserCredentialEntity(credential.userId().value(), credential.passwordHash(),
                credential.updatedAt());
    }

    public static UserCredential toDomain(UserCredentialEntity entity) {
        return UserCredential.of(new UserId(entity.getUserId()), entity.getPasswordHash(),
                entity.getUpdatedAt());
    }

    public static RefreshTokenEntity toEntity(RefreshToken token) {
        return new RefreshTokenEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                token.revokedAt(),
                token.replacedBy());
    }

    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.rehydrate(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedBy());
    }
}

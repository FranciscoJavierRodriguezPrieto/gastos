package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.User;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Puerto de salida de usuarios. */
public interface UserRepository {

    Optional<User> findById(UserId userId);

    /** Busqueda por correo: es la unica consulta que no puede acotarse por hogar, porque
     *  el hogar se descubre precisamente al autenticar. */
    Optional<User> findByEmail(Email email);

    List<User> findAllByHousehold(HouseholdId householdId);

    boolean existsByEmail(Email email);

    User save(User user);
}

package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.util.Locale;
import java.util.regex.Pattern;

/** Correo normalizado y validado en el dominio, no solo en el formulario. */
public record Email(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[A-Za-z0-9_.+-]{1,64}@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)+$");
    private static final int MAX_LENGTH = 254;

    public Email {
        Guard.notBlank(value, "email");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new DomainException("Formato de correo no valido");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

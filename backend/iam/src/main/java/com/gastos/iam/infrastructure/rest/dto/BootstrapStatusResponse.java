package com.gastos.iam.infrastructure.rest.dto;

/** Estado inicial de la instalacion, para que la pantalla sepa que ofrecer. */
public record BootstrapStatusResponse(boolean needsBootstrap) {
}

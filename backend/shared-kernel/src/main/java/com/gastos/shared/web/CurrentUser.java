package com.gastos.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta en el controlador al usuario autenticado, extraido del token.
 *
 * <pre>
 *   public List&lt;ExpenseResponse&gt; list(&#64;CurrentUser AuthenticatedUser user) { ... }
 * </pre>
 *
 * <p>La anotacion es Java puro y vive en el kernel compartido para que cualquier
 * contexto pueda usarla. Quien la resuelve es un {@code HandlerMethodArgumentResolver}
 * en la raiz de composicion, que es el unico sitio que conoce Spring Security.</p>
 *
 * <p>Asi el controlador no toca el {@code SecurityContextHolder} ni interpreta claims:
 * recibe una identidad ya verificada y no tiene forma de saltarse la comprobacion.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}

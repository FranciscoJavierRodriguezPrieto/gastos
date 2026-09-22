package com.gastos.web;

import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import com.gastos.shared.web.CurrentUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Convierte el JWT verificado en un {@link AuthenticatedUser}.
 *
 * <p>Es el unico punto de toda la aplicacion que lee claims. Los controladores reciben
 * una identidad ya construida y no tienen forma de equivocarse leyendo el token, ni de
 * confiar en una cabecera que el cliente controle.</p>
 *
 * <p>Si falta el token o le falta algun claim, se responde 401 en vez de construir un
 * usuario a medias: una identidad incompleta es peor que ninguna.</p>
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String CLAIM_HOUSEHOLD = "hid";
    private static final String CLAIM_ROLE = "role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthenticatedUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String subject = jwt.getSubject();
        String householdId = jwt.getClaimAsString(CLAIM_HOUSEHOLD);
        String role = jwt.getClaimAsString(CLAIM_ROLE);

        if (subject == null || householdId == null || role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        try {
            return new AuthenticatedUser(UserId.of(subject), HouseholdId.of(householdId), role);
        } catch (RuntimeException e) {
            // Token con formato valido pero contenido incoherente: tampoco se admite.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}

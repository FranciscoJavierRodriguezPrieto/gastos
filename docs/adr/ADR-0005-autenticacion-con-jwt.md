# ADR-0005: Autenticación con JWT y refresco rotatorio

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Rama:** `feature/security-jwt`

## Contexto

Hasta ahora la identidad llegaba en las cabeceras `X-Household-Id` y `X-User-Id`. Era un
andamio consciente: **cualquiera que inventara una cabecera era cualquier hogar**. Todo
el trabajo de aislamiento por hogar de las ramas anteriores era, en la práctica,
decorativo mientras eso siguiera así.

Requisito del proyecto: JWT/OAuth2 o Passkeys, para exactamente dos usuarios.

## Decisión

### Dos tipos de token, cada uno por su motivo

| | Acceso | Refresco |
|---|---|---|
| Formato | JWT firmado (HS256) | Cadena aleatoria opaca de 256 bits |
| Vida | 15 minutos | 30 días |
| Dónde vive | Sólo en el cliente | Hash SHA-256 en base de datos |
| Se valida | Verificando la firma, sin tocar la BD | Consultando la BD |

El de acceso se valida en **cada** petición, así que no debe requerir una consulta: lleva
la identidad dentro y va firmado. A cambio **no se puede revocar**, y por eso dura poco.

El de refresco se usa cada varias horas y **sí** tiene que poder revocarse al instante,
así que se consulta en base de datos. Si fuera un JWT haría falta igualmente una lista
negra: se ganaría nada y se complicaría todo.

### Rotación con detección de reutilización

Cada refresco consume el token y emite otro. Si un token **ya consumido** vuelve a
presentarse, sólo hay una explicación razonable —alguien lo copió— y se revoca **toda la
cadena de sesión**, no sólo ese token. Es el mecanismo que convierte un robo de token en
una sesión cortada en lugar de en un acceso permanente.

### Hashes distintos para cosas distintas

- **Contraseñas → BCrypt, coste 12.** Las elige una persona, así que hay diccionario
  posible: el hash tiene que ser deliberadamente lento. Se paga sólo al iniciar sesión.
- **Tokens de refresco → SHA-256.** Son 256 bits aleatorios: no hay diccionario que
  valga, y un hash lento sólo añadiría latencia a cada refresco.

### Sin registro abierto

El alta inicial sólo funciona **mientras no exista ningún hogar**. A partir de ahí, la
única forma de entrar es que el `OWNER` dé de alta al otro conviviente. Es una
instalación doméstica para dos personas: dejar el registro abierto sería regalar una
cuenta a quien encuentre la URL.

### La identidad se resuelve en un único sitio

Los controladores reciben `@CurrentUser AuthenticatedUser`. Un solo
`HandlerMethodArgumentResolver` lee los claims; nadie más toca el `SecurityContext`. Así
ningún controlador puede equivocarse interpretando el token, ni volver a confiar en una
cabecera.

`AuthenticatedUser` y la anotación `@CurrentUser` viven en el kernel compartido y son
Java puro. Eso permite que cualquier contexto las use **sin depender de `iam`**, que es
lo que prohíbe la regla de arquitectura entre contextos.

### Sin cookies, luego sin CSRF

Los tokens viajan en `Authorization` y la API no tiene estado. CSRF se desactiva porque
**no hay vector**: sin cookies no existe petición que el navegador autentique solo. Si
algún día se usaran cookies, habría que reactivarlo con `SameSite=Strict`.

### La clave de firma no tiene valor por defecto

Sin `JWT_SECRET` la aplicación **no arranca**, y es intencionado. Una clave por defecto en
el código es una clave pública: quien la conozca puede fabricarse un token para cualquier
hogar.

## Consecuencias

**Positivas**

- Se cierra OWASP API2 y con ello se desbloquea el despliegue público.
- El aislamiento por hogar pasa de ser una convención a estar respaldado por una firma.
- El limitador de peticiones ahora cuenta por usuario autenticado y no por IP, que es
  trivial de rotar.

**Negativas y mitigaciones**

- *Un token de acceso robado vale 15 minutos.* Es el precio de no consultar la base de
  datos en cada petición. Se acota con la vida corta.
- *El cliente tiene que guardar los tokens.* La rama de PWA decidirá dónde; `localStorage`
  es vulnerable a XSS, así que habrá que valorarlo junto con la CSP.
- *Cambio incompatible en toda la API.* Las cabeceras de identidad desaparecen. Asumible
  porque todavía no hay cliente.

## Lo que queda fuera

**Passkeys (WebAuthn) no entra en esta rama.** Es un mecanismo con su propio ciclo de
registro de credenciales, gestión de autenticadores y recuperación. Entregarlo a medias
sería peor que no entregarlo. Queda como `feature/passkeys`, y encaja encima de esto sin
rehacer nada: sería otra forma de llegar al mismo `AuthenticationResult`.

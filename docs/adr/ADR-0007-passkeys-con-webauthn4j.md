# ADR-0007: Passkeys con WebAuthn4J y retos en base de datos

- **Estado:** Aceptada
- **Fecha:** 2026-09-19
- **Rama:** `feature/passkeys`

## Contexto

El encargo inicial pedía «autenticación mediante JWT/OAuth2 o **Passkeys**». Se resolvió
primero con contraseña + JWT ([ADR-0005](ADR-0005-autenticacion-con-jwt.md)) porque hacía
falta algo con lo que entrar desde el primer día. Ahora toca la otra mitad.

Una passkey no es una contraseña más cómoda: es un par de claves donde **la privada no
sale nunca del dispositivo**. El servidor guarda sólo la pública, así que una base de
datos robada no permite suplantar a nadie — cosa que con `user_credential` no es cierta,
por bien que esté el BCrypt. Para una aplicación con el histórico financiero de una casa,
esa diferencia justifica el trabajo.

Tres decisiones había que tomar: qué biblioteca, dónde vive el reto, y qué pasa con la
contraseña.

## Decisión 1: WebAuthn4J, no Spring Security ni código propio

### A. Implementarlo a mano

Verificar un alta es analizar CBOR, descodificar una clave COSE y comprobar una firma.
Es código criptográfico donde un error no da un fallo visible, sino un agujero silencioso.
Descartada sin discusión.

### B. El soporte WebAuthn de Spring Security 6.4+

Existe y está bien hecho, pero está construido alrededor de la **sesión de servidor** y
del `formLogin`: expone sus propios endpoints y guarda el reto en la `HttpSession`. Esta
API es `STATELESS` a propósito y no tiene sesiones. Usarlo obligaría a reintroducir
sesiones sólo para la autenticación, que es justo lo que se evitó en el ADR-0005.

### C. WebAuthn4J *(elegida)*

Es una biblioteca de verificación pura: no sabe nada de HTTP ni de sesiones. Encaja detrás
del puerto `WebAuthnCeremony` igual que Nimbus encaja detrás de `TokenService`, y ninguna
clase suya cruza hacia `application` ni `domain`.

Se fija la versión **0.30.3** y no la última (0.31.x). A partir de la 0.31 la biblioteca
salta a **Jackson 3** (`tools.jackson`), que convive con el Jackson 2 de Spring Boot
porque son paquetes distintos, pero obligaría a meter dos árboles de dependencias enteros
en el mismo JAR. En una instancia con 256 MB eso no es gratis, y no se gana nada.

## Decisión 2: el reto vive en la base de datos

Un reto WebAuthn se emite en una llamada y se comprueba en la siguiente. Lo normal es
guardarlo en la sesión de servidor; aquí no hay.

Alternativa considerada: **firmar el reto** y mandarlo al cliente, sin guardar nada
(patrón *stateless challenge*). Se descartó porque un reto firmado no se puede invalidar:
haría falta igualmente una lista de los ya usados para impedir que se repita una respuesta
capturada, y esa lista es exactamente la tabla que se quería evitar.

Así que hay tabla `passkey_challenge`. A cambio:

- La aplicación sigue pudiendo correr en varias instancias sin sesiones pegajosas.
- El reto se puede **consumir antes de verificar**, de modo que un fallo no deja vivo un
  reto reutilizable.
- Cada emisión barre los caducados, así que la tabla no crece.

El reto se guarda **en claro**, al contrario que los tokens de refresco y de
restablecimiento. No es una credencial: es un número aleatorio que hay que devolver
firmado. Conocerlo no sirve de nada sin la clave privada, y el servidor necesita el valor
original para compararlo.

## Decisión 3: la passkey no sustituye a la contraseña

Se valoró desactivar la contraseña en cuanto hubiera una passkey registrada, que es lo
más seguro sobre el papel. No se ha hecho.

Son dos personas y una instalación doméstica. Perder el móvil no puede significar perder
el acceso al histórico financiero de la casa, y no hay un departamento de soporte al que
llamar. La contraseña se queda como vía de recuperación, con su restablecimiento por
correo, y la passkey es el camino rápido de todos los días.

## Consecuencias

- Entrar es un toque: `residentKey: required` guarda el identificador de usuario en el
  dispositivo, así que **no hay que escribir el correo**.
- Se exige `userVerification: required`: el autenticador tiene que pedir huella, cara o
  PIN. Un móvil desbloqueado sobre una mesa no abre las cuentas.
- **Hay un parámetro nuevo que hay que acertar al desplegar**: `WEBAUTHN_RP_ID` es el
  dominio del *frontend*, no el de la API. Si se equivoca, el navegador rechaza la
  ceremonia sin explicar por qué. Está documentado en
  [OPERACION.md](../OPERACION.md).
- Fuera de `localhost`, WebAuthn **exige HTTPS**. No es una recomendación: sin contexto
  seguro el navegador ni siquiera expone la API, y por eso el botón se oculta en lugar de
  fallar al pulsarlo.
- Se guarda el indicador **BE** (la credencial puede copiarse a la nube del fabricante) y
  no el BS (está copiada ahora mismo): BE no cambia nunca, así que se puede comprobar que
  sigue igual en cada acceso, y además es lo que de verdad le interesa a la persona antes
  de borrar la única passkey que le queda.
- El contador de firmas se comprueba, pero **un contador que se queda a cero no es
  sospechoso**: las passkeys sincronizadas —las de Apple, Google o un gestor de
  contraseñas— no lo llevan, precisamente porque viven en varios sitios. Tratarlo como
  clonado dejaría inservible la forma más común de passkey.

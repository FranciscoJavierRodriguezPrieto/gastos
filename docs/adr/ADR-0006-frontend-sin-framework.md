# ADR-0006: Frontend sin framework y sin paso de compilación

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Rama:** `feature/pwa-shell`

## Contexto

Toca construir la interfaz. El producto son **cuatro pantallas** para **dos usuarios**, y
se despliega como estáticos en Cloudflare Pages.

La decisión se había dejado abierta a propósito: el `frontend/README.md` de la primera
rama decía que los tokens de color debían servir *"sea cual sea la capa de vista que se
elija después"*. Ese después es ahora.

## Opciones consideradas

### A. React + Vite

Lo estándar. A cambio: `node_modules` con cientos de dependencias transitivas para cuatro
pantallas, un paso de compilación entre lo que escribes y lo que se ejecuta, y un flujo
de actualizaciones que hay que mantener vivo. Para dos usuarios y cuatro pantallas, el
coste de mantenimiento supera al beneficio.

### B. Svelte o Preact + Vite

Menos peso en el navegador, pero el paso de compilación y el árbol de dependencias siguen
ahí. El ahorro es de kilobytes, no de complejidad.

### C. HTML + CSS + módulos ES, sin compilar *(elegida)*

El navegador ejecuta exactamente el fichero que hay en el repositorio. Sin bundler, sin
`node_modules`, sin transpilación.

## Decisión

**Opción C.** Los módulos ES nativos, `fetch`, `URLSearchParams` e `Intl` cubren todo lo
que necesita esta aplicación. Ninguna dependencia de terceros en el navegador.

Razones, por orden de peso:

1. **Coherencia con lo ya decidido.** `docs/SECURITY.md` dice que no se carga nada de
   terceros: ni analítica, ni trazas externas, ni fuentes remotas. Un árbol de
   dependencias de npm contradice ese principio mucho más que cualquier ventaja de
   ergonomía.
2. **El despliegue es copiar ficheros.** Cloudflare Pages sirve `frontend/` tal cual.
   Sin build en CI que pueda romperse.
3. **Superficie de ataque mínima.** Cero dependencias en el navegador significa cero
   dependencias que auditar, actualizar o que puedan comprometerse aguas arriba.
4. **Lo que se depura es lo que está escrito.** Sin *source maps* ni código
   transformado de por medio.

Se aceptan las dos consecuencias evidentes: hay que escribir manipulación de DOM a mano,
y no hay reactividad automática. Con cuatro pantallas y formularios sencillos, eso son
unas decenas de líneas, no un problema de arquitectura.

## Consecuencias

**Positivas**

- Sin `node_modules` en el repositorio y sin paso de compilación.
- Los tests corren con el ejecutor incorporado de Node (`node --test`): tampoco hay
  dependencias de desarrollo.
- Un fichero, una responsabilidad: `api/` habla con el servidor, `views/` pinta, `ui/`
  son utilidades compartidas.

**Negativas y mitigaciones**

- *Manipulación de DOM manual.* Se concentra en `ui/dom.js`, un puñado de funciones.
- *Sin reactividad.* Cada vista se vuelve a pintar entera al cambiar los datos. Con estos
  volúmenes es imperceptible y es mucho más fácil de razonar que un estado compartido.
- *Si la aplicación crece mucho, esto se queda corto.* La puerta de salida existe:
  `api/` y `ui/` son independientes de la vista, así que introducir un framework
  afectaría a `views/` y poco más.

## Dónde se guarda el token

Decisión tomada aquí porque condiciona el resto:

| Token | Dónde | Por qué |
|---|---|---|
| Acceso (15 min) | **Sólo en memoria** | Se pierde al recargar y se recupera con el de refresco. Nunca llega a disco, así que no sobrevive a la pestaña. |
| Refresco (30 días) | `localStorage` | Sin él habría que iniciar sesión en cada recarga, y una PWA instalada en el móvil que pide contraseña cada vez no la usa nadie. |

**El riesgo se asume con los ojos abiertos:** `localStorage` es accesible desde
JavaScript, así que un XSS se llevaría el token de refresco. Las mitigaciones son que no
se carga código de terceros (ver punto 3), que se sirve con una CSP restrictiva, y que
reutilizar un token robado revoca toda la sesión ([ADR-0005](ADR-0005-autenticacion-con-jwt.md)).

La alternativa —cookie `HttpOnly`— protegería del XSS pero reintroduciría el CSRF y
obligaría a compartir dominio entre API y frontend, que hoy no es el caso. Si algún día
se sirven desde el mismo dominio, conviene reconsiderarlo.

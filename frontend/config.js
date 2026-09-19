/**
 * Configuración del frontend, leída en tiempo de ejecución.
 *
 * Este fichero existe para que **el mismo código funcione en local y desplegado sin
 * recompilar nada**. No es un módulo ES a propósito: se carga con un `<script>` normal
 * antes que `app.js`, de modo que la variable ya esté puesta cuando `client.js` la lee al
 * evaluarse.
 *
 * Al desplegar se reescribe con `scripts/preparar-frontend.mjs`, que además ajusta el
 * `connect-src` de la CSP en `_headers`. Los dos valores tienen que apuntar al mismo
 * sitio: si sólo se cambiara uno, la aplicación llamaría a la API y el navegador
 * bloquearía la petición.
 */
window.GASTOS_API_BASE = 'http://localhost:8080/api/v1';

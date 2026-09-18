# Frontend (PWA)

Esta rama (`feature/architecture-setup`) entrega únicamente los **cimientos del
frontend**: el sistema de color y el manifiesto de la PWA. La aplicación se construye en
las ramas `feature/pwa-shell` y siguientes, según el plan de
[docs/GIT_WORKFLOW.md](../docs/GIT_WORKFLOW.md).

## Qué hay aquí ahora

| Fichero | Contenido |
|---|---|
| `src/styles/tokens.css` | Paleta Verde Salvia Cálido / Pistacho Suave, tipografía, espaciado y forma. |
| `public/manifest.webmanifest` | Manifiesto de la PWA: instalable, `standalone`, tema claro. |

## Decisiones ya tomadas

**Instalación en iOS.** Safari ignora parte del manifiesto, así que el `index.html`
deberá incluir además `apple-mobile-web-app-capable`,
`apple-mobile-web-app-status-bar-style="default"` y los `apple-touch-icon`. El aspecto
nativo se apoya en `env(safe-area-inset-*)`, ya declarado como token.

**Sin dependencia de terceros para el look.** La paleta se define con variables CSS
propias, de modo que el mismo sistema de color sirva tanto si la capa de vista termina
siendo React, Svelte o HTML con Web Components.

**Contraste antes que estética.** Los verdes son superficie y acento; el texto siempre
va en antracita. Ningún dato financiero se comunica sólo con color: el signo y la
etiqueta acompañan siempre al tono.

**Estrategia offline.** El *service worker* usará *stale-while-revalidate* para el
resumen y *network-first* para los movimientos, de modo que abrir la aplicación sin
cobertura muestre los últimos datos conocidos en lugar de una pantalla vacía.

## Iconos pendientes

`public/icons/` aún no contiene los PNG (192, 512 y 512 *maskable*) referenciados por el
manifiesto. Se generarán en `feature/pwa-shell`.

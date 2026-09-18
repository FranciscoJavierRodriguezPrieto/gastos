# Frontend (PWA)

Aplicación instalable, sin framework y sin paso de compilación: el navegador ejecuta
exactamente los ficheros que hay en este directorio. El porqué está en
[ADR-0006](../docs/adr/ADR-0006-frontend-sin-framework.md).

## Estructura

```
frontend/
├── index.html          armazón de la página
├── manifest.webmanifest
├── sw.js               service worker: arranque sin conexión
├── _headers            cabeceras para Cloudflare Pages (CSP incluida)
├── icons/              iconos de instalación, incluido el maskable
├── src/
│   ├── app.js          arranque, navegación y sesión
│   ├── api/            cliente HTTP y sesión
│   ├── ui/             utilidades de DOM y de formato
│   ├── views/          una pantalla por fichero
│   └── styles/         tokens de color y estilos
└── test/               tests con el ejecutor incorporado de Node
```

La raíz que se sirve es `frontend/` tal cual: no hay carpeta `dist` ni `public` porque no
hay nada que construir.

## Arrancar

Con todo en Docker, la aplicación queda en <http://localhost:5173>:

```bash
docker compose -f infra/docker-compose.yml up --build
```

Con la API por separado, cualquier servidor de estáticos vale:

```bash
python -m http.server 5173 -d frontend
```

El puerto 5173 no es casual: es el origen que la API trae autorizado en CORS por defecto.

## Tests

```bash
node --test "frontend/test/*.test.js"
```

Se prueba la lógica de sesión y del cliente HTTP, que es donde de verdad se puede meter la
pata: dónde acaba guardado cada token, y cómo se encadenan los refrescos cuando caduca el
de acceso. El pintado no se prueba aquí.

## Decisiones que conviene conocer

**El token de acceso sólo vive en memoria.** Al recargar se pierde y se recupera con el de
refresco, que sí se guarda en `localStorage`. El riesgo y sus mitigaciones están en el
ADR-0006.

**Varias peticiones caducadas comparten un único refresco.** El token de refresco rota en
cada uso, así que dos canjes simultáneos harían que el segundo pareciera una reutilización
y el servidor revocaría la sesión entera.

**El service worker va a red primero.** Es más lento que servir de caché, pero evita el
problema clásico de las PWA: olvidarse de subir la versión de la caché en un despliegue y
dejar a la gente con la versión anterior pegada. Las respuestas de la API no se cachean
nunca: un saldo desactualizado que parece actual es peor que no ver nada.

**Nunca se usa `innerHTML` con datos.** Todo el texto entra por `textContent`. Un
concepto de gasto con `<script>` dentro se ve como texto, que es lo que es.

## Pendiente

- **Pantalla de hipoteca**, con los deslizadores y el selector de programa. El motor de
  cálculo ya está completo en el backend; falta la interfaz.
- **`connect-src` de la CSP** en `_headers` apunta a `http://localhost:8080`. Al desplegar
  hay que cambiarlo al dominio real de la API o la aplicación no podrá hablar con ella.
- **Cambio de contraseña y gestión de miembros** desde la interfaz; hoy sólo por API.

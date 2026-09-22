# Frontend (PWA)

Aplicación instalable, sin framework y sin paso de compilación: el navegador ejecuta
exactamente los ficheros que hay en este directorio. El porqué está en
[ADR-0006](../docs/adr/ADR-0006-frontend-sin-framework.md).

## Estructura

```
frontend/
├── index.html          armazón de la página
├── config.js           dirección de la API, leída en tiempo de ejecución
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

Para **desplegar** sí se genera un `dist/`, pero no es una compilación: el script
`scripts/preparar-frontend.mjs` copia esta carpeta y cambia dos valores —la dirección de
la API en `config.js` y el `connect-src` de la CSP en `_headers`— que tienen que
coincidir entre sí. Lo que se publica sigue siendo, línea por línea, lo que hay aquí.

## Arrancar

Con todo en Docker, la aplicación queda en <http://localhost:5173>:

```bash
docker compose -f infra/docker-compose.yml up --build
```

El puerto 5173 no es casual: es el origen que la API trae autorizado en CORS por defecto.

Sin Docker, hay un servidor equivalente que manda **las mismas cabeceras**:

```bash
node scripts/servir-frontend.mjs
```

**No uses `python -m http.server`.** No manda `Cache-Control` ni CSP, y las dos cosas
muerden: el navegador se queda con la versión anterior de lo que edites, y no estás
probando la política que sí se aplica en producción. Las barras de distribución y de DTI
salían vacías desplegadas por culpa de eso y en local no se veía.

Y si lanzas algo suelto en el 5173, **acuérdate de pararlo**: le gana el puerto al
contenedor sin que Docker dé ningún error.

## Tests

```bash
node --test "frontend/test/*.test.js"
```

Y los del script de despliegue, que también corren en CI:

```bash
node --test "scripts/test/*.test.js"
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

## La pantalla de hipoteca

No calcula nada. Cada movimiento de un deslizador reenvía el escenario completo al
servidor y pinta lo que devuelve. Es más tráfico que calcular la cuota en el navegador,
pero evita el problema de verdad: dos implementaciones de la misma fórmula financiera que
acaban divergiendo sin que nadie se entere.

Los deslizadores van con retardo de 250 ms. Sin él, arrastrar uno lanzaría decenas de
peticiones por segundo y además llegarían desordenadas: la respuesta de un valor
intermedio podría pintarse después de la del valor final.

Los ingresos y las deudas se precargan con lo que la aplicación ya sabe (miembros del
hogar y compromisos recurrentes de Gastos). Obligar a reescribirlos a mano es la forma
más rápida de que la simulación se haga con cifras desactualizadas.

Las barras de DTI llevan **marcada la línea del límite**: un 28% no dice si es bueno o
malo hasta que se ve contra qué se compara.

## Recuperar el acceso

Dos rutas públicas, accesibles sin sesión: `#/olvide` para pedir el enlace y
`#/restablecer?token=...`, a la que llega el enlace del correo.

El token va en el **fragmento** y no en la query: lo que va detrás de la almohadilla no se
envía al servidor ni aparece en los registros de acceso.

El mensaje de confirmación es **el mismo exista o no el correo**, porque así responde el
servidor. Si esta pantalla dijera «no encontramos esa cuenta», echaría por tierra todo el
cuidado puesto en el servidor para no delatar qué cuentas hay.

## Pendiente

- **`connect-src` de la CSP** en `_headers` apunta a `http://localhost:8080`. Al desplegar
  hay que cambiarlo al dominio real de la API o la aplicación no podrá hablar con ella.

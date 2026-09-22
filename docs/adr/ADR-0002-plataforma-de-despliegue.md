# ADR-0002: Plataforma de despliegue gratuita

- **Estado:** Propuesta (pendiente de validar con una prueba real de despliegue)
- **Fecha:** 2026-09-18
- **Rama:** `feature/architecture-setup`

## Contexto

Requisito explícito: desplegar sin coste y **sin usar Vercel ni Render**. Hay que
colocar tres piezas: una API Java, una base de datos PostgreSQL y un frontend
estático (PWA).

> **Este requisito ha cambiado.** El veto a Render se levantó el 21/09/2026, al haber ya
> cuenta abierta y horas de sobra en ella. El razonamiento está al final del documento.
> Lo que se mantiene es el coste cero.

## Opciones evaluadas

| Plataforma | Rol | Capa gratuita | Valoración |
|---|---|---|---|
| **Oracle Cloud Free Tier** | API + BD | 4 vCPU ARM / 24 GB RAM *always free* | La más generosa con diferencia; a cambio, administras tú la VM (parcheo, TLS, backups). Disponibilidad de instancias ARM irregular según región. |
| **Fly.io** | API | VM pequeña, escala a cero | Despliegue por `Dockerfile`, cercano a la experiencia PaaS. Exige tarjeta. Arranque en frío tras la inactividad. |
| **Koyeb** | API | 1 servicio web gratuito | Sencillo, soporta Docker. Recursos más ajustados. |
| **Neon** | PostgreSQL | BD serverless con escalado a cero | Separa el ciclo de vida del dato del de la aplicación. Arranque en frío de la BD. |
| **Supabase** | PostgreSQL | BD gestionada + auth | Alternativa a Neon; la autenticación propia no se usa, ya que va con JWT/Passkeys propios. |
| **Cloudflare Pages** | PWA | Estáticos + CDN ilimitados | Ideal para el frontend: HTTP/3, cabeceras de seguridad configurables, dominio propio gratis. |
| **Netlify** | PWA | Alternativa equivalente | Reserva por si Pages da problemas. |

## Decisión

> **Superada por la revisión del 21/09/2026 (2)**, al final de este documento: la API va
> a Render, no a Fly.io. Se conserva lo de abajo porque explica por qué se descartó cada
> alternativa, y ese razonamiento sigue siendo válido.

Arquitectura de despliegue en tres piezas:

1. **Frontend PWA → Cloudflare Pages.** Estáticos en CDN, cabeceras de seguridad
   (CSP, HSTS, `X-Content-Type-Options`) definidas en `_headers`.
2. **API Java → Fly.io** como opción principal (despliegue reproducible desde el
   `Dockerfile`, sin administrar servidor), con **Oracle Cloud Free Tier** como plan B
   si la capa gratuita de Fly deja de ser suficiente.
3. **PostgreSQL → Neon**, con cifrado en reposo del proveedor y TLS obligatorio en
   tránsito.

## Consecuencias

- **Arranque en frío:** con escalado a cero, la primera petición tras un periodo de
  inactividad puede tardar varios segundos. Aceptable para uso doméstico; la PWA lo
  disimula mostrando datos cacheados mientras revalida.
- **Imagen ligera obligatoria:** se usa JRE 21 *slim* con capas de Spring Boot y
  `-XX:MaxRAMPercentage` para ajustar el heap al contenedor.
- **Portabilidad:** todo se define en `Dockerfile` y `docker-compose.yml`, de modo que
  cambiar de proveedor sea cuestión de horas y no de semanas.
- **Pendiente de verificar:** las condiciones de las capas gratuitas cambian con
  frecuencia. Antes de fijar esta decisión hay que hacer un despliegue real de prueba
  y medir consumo y arranque en frío.

## Estado a 2026-09-19 (rama `chore/deployment-pipeline`)

Todo lo que se puede preparar sin tener las cuentas está hecho: `fly.toml` ajustado a
512 MB con escalado a cero, `scripts/preparar-frontend.mjs` para que la PWA apunte a la
API sin editar ficheros a mano, un workflow de despliegue manual y el procedimiento
completo en [DESPLIEGUE.md](../DESPLIEGUE.md).

**La decisión sigue en estado Propuesta**, y seguirá así hasta que haya un despliegue de
verdad. Preparar los ficheros no demuestra que la capa gratuita de Fly siga siendo como
se describe en la tabla de arriba, ni cuánto tarda en frío la combinación Fly + Neon,
que son las dos cosas que este ADR decía que había que medir.

## Revisión del 21/09/2026: las capas gratuitas han cambiado

Antes de desplegar se volvieron a mirar las condiciones, y dos de las tres piezas de API
ya no son lo que dice la tabla:

| Plataforma | Situación actual |
|---|---|
| **Fly.io** | **Sin capa gratuita para cuentas nuevas** desde octubre de 2024. Pago por uso: una máquina de 256 MB encendida todo el mes ronda los 2 $, y con escalado a cero se paga sólo el tiempo encendida. |
| **Oracle Cloud Always Free** | Recortada a la mitad en junio de 2026: **2 OCPU ARM y 12 GB**. Sigue sobrando para esta aplicación, pero hay que administrar la máquina. |
| **Koyeb** | Sigue con una instancia gratuita: 512 MB, 0,1 vCPU, Frankfurt o Washington, sin volúmenes. Llega justo para la JVM: el arranque en frío será lento. |
| **Neon** | Sigue gratis: 0,5 GB, 100 CU-hora al mes, escala a cero. Sobra para dos personas. |
| **Cloudflare Pages** | Sigue gratis. |

**La decisión queda abierta** entre pagar unos euros al mes por Fly.io, quedarse a coste
cero con Koyeb aceptando arranques lentos, o Oracle Cloud a cambio de mantener una
máquina. El requisito original era coste cero, así que Fly.io deja de ser la opción
principal por defecto.

## Revisión del 21/09/2026 (2): se cierra la decisión con la API en Render

El contexto de este ADR excluía Render. Esa exclusión **se levanta**, y conviene dejar
escrito el camino porque la primera respuesta fue la contraria.

### El argumento en contra, y por qué no se sostenía

Render reparte **750 horas de instancia al mes por *workspace*, no por servicio**, y un
mes tiene unas 730. De ahí salió la objeción: la bolsa da para *un* servicio despierto, y
en esa cuenta ya hay dos cosas —una aplicación de entrenamientos y una web de boda—, así
que meter una tercera parecía temerario. Agrava la pinta que **al agotar la bolsa Render
suspende todos los servicios gratuitos del workspace**, no sólo el que se pasó.

El fallo del razonamiento fue medir el riesgo por el número de servicios en lugar de por
sus horas. **Un servicio sólo gasta mientras está despierto**, y se duerme a los 15
minutos sin tráfico. Con los números reales de la cuenta:

| Servicio | h/mes |
|---|---|
| Entrenamientos, mantenida despierta de 8:30 a 21:00 | ~390 |
| **Esta aplicación**, dos personas abriéndola a ratos | **~15** |
| Libre (web de boda y lo que venga) | ~345 |

Esta aplicación cuesta **un 2% de la bolsa**. No es lo que la pone en riesgo: lo que
consume las horas es el servicio que alguien mantiene despierto a propósito, y ése ya
estaba ahí y cabe.

### Lo que sí hay que vigilar

El riesgo real no es el consumo de esta aplicación, es **la concentración**: tres
servicios compartiendo una bolsa cuyo agotamiento los tumba a los tres a la vez. Lo que
puede agotarla no es el uso normal, sino:

- **un segundo servicio mantenido despierto** por un *ping* periódico —cada ventana de
  8:30 a 21:00 son ~390 h, y dos no caben—;
- **un rastreador** indexando una web pública, que la mantiene despierta sin que figure
  en ninguna previsión.

La mitigación es mirar *Billing → Usage* de vez en cuando. Y si un mes se acerca al
límite, la aplicación que conviene mover es **ésta**: es la que menos gasta y la única
que no tiene público al que dejar tirado. `fly.toml` sigue preparado para eso.

### Lo que no cambia

**La base de datos no va en Render.** Su PostgreSQL gratuito son 256 MB y **caduca a los
30 días** de crearla. Neon es gratis, no caduca, y separa el ciclo de vida del dato del de
la aplicación, que es lo que decía la decisión original.

**Vercel no entra para la API**: no ejecuta contenedores de larga vida. Sólo serviría para
la PWA, que ya está resuelta y gratis en Cloudflare Pages; moverla sería cambiar de sitio
algo que funciona, y habría que portar el `_headers` con la CSP, que es la parte delicada.

### Decisión final

1. **PWA → Cloudflare Pages.** Sin cambios.
2. **PostgreSQL → Neon.** Plan gratuito: 0,5 GB, 100 CU-hora al mes por proyecto,
   escalado a cero a los 5 minutos. Para dos personas sobra de largo.
3. **API → Render**, plan gratuito, Fráncfort, construida desde `infra/Dockerfile`. La
   configuración vive en `render.yaml`, en la raíz.

Render gana a Koyeb, que era la otra opción a coste cero, por tres cosas concretas: la
cuenta ya existe, **construye desde el repositorio** en vez de exigir publicar la imagen
en un registro, y el despliegue se declara en un fichero versionado en lugar de a mano en
un panel. Koyeb tenía una ventaja —duerme a la hora en lugar de a los 15 minutos, así que
se notan menos los arranques en frío—, pero no compensa las otras tres.

### Lo que se acepta a cambio

- **Arranque en frío de uno o dos minutos.** Render duerme el servicio a los 15 minutos
  sin tráfico, y Spring Boot con 0,1 vCPU no se levanta deprisa; Neon suma lo suyo. Con
  dos usuarios se puede vivir con ello, y el armazón de la PWA se sirve de la caché del
  service worker, así que la aplicación *se ve* al instante aunque los datos tarden. Si un
  día estorba, Fly.io por 1-4 € al mes lo quita.
- **Bolsa de horas compartida**, con el castigo colectivo descrito arriba.
- **Construcción lenta.** Un `mvn clean package` de seis módulos dentro del contenedor, en
  una máquina de construcción modesta.

**Estado: sigue en Propuesta.** Esto se ha decidido leyendo las condiciones publicadas y
las horas reales de la cuenta, no desplegando. Lo que convierte este ADR en Aceptado es
una primera ejecución real, que es lo que [DESPLIEGUE.md](../DESPLIEGUE.md) describe paso
a paso.

# ADR-0002: Plataforma de despliegue gratuita

- **Estado:** Propuesta (pendiente de validar con una prueba real de despliegue)
- **Fecha:** 2026-09-18
- **Rama:** `feature/architecture-setup`

## Contexto

Requisito explícito: desplegar sin coste y **sin usar Vercel ni Render**. Hay que
colocar tres piezas: una API Java, una base de datos PostgreSQL y un frontend
estático (PWA).

> El veto a Render se volvió a examinar el 21/09/2026, al haber ya cuenta abierta, y se
> mantiene: no por la restricción original, sino por un motivo nuevo y más fuerte que
> está al final de este documento.

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
> a Koyeb, no a Fly.io. Se conserva lo de abajo porque explica por qué se descartó cada
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

## Revisión del 21/09/2026 (2): se cierra la decisión, y Render queda descartado

Se replanteó usar **Render**, que el contexto de este ADR excluía, porque ya hay cuenta
abierta y con dos servicios dentro: una aplicación de entrenamientos y una web de boda.
Al mirar las condiciones, Render no sólo no encaja: es **activamente peligroso para lo
que ya está desplegado ahí**.

| | Render |
|---|---|
| Web service gratuito | 512 MB, 0,1 vCPU, se duerme a los 15 min sin tráfico |
| Horas de instancia | **750 al mes por *workspace*, no por servicio** |
| Al agotarlas | **Render suspende *todos* los servicios gratuitos del workspace** hasta el mes siguiente |
| PostgreSQL gratuito | 256 MB, y **caduca a los 30 días** de crearla |

Dos consecuencias, y las dos son decisivas:

1. **Un mes tiene unas 730 horas.** La bolsa de 750 está dimensionada para *un* servicio
   despierto todo el mes, no para dos, y mucho menos para tres. Con la web de una boda
   —que recibe visitas y por tanto se mantiene despierta— el margen se consume solo.
2. **El castigo es colectivo.** No se suspende el servicio que se pasó, sino todos los
   gratuitos de la cuenta. Meter esta aplicación ahí significa aceptar que un mes con
   visitas puede dejar la web de la boda fuera de servicio. Eso no es un riesgo que
   compense por ahorrarse abrir una cuenta.

Y su PostgreSQL gratuito caduca a los 30 días, así que la base de datos tendría que ir a
Neon de todas formas.

**Vercel tampoco entra en la ecuación para la API**: no ejecuta contenedores de larga
vida, así que sólo serviría para la PWA, que ya está resuelta y gratis en Cloudflare
Pages. Mover el frontend allí sería cambiar de sitio algo que funciona, y habría que
portar el `_headers` con la CSP, que es la parte delicada.

### Decisión final

1. **PWA → Cloudflare Pages.** Sin cambios.
2. **PostgreSQL → Neon.** Plan gratuito: 0,5 GB de almacenamiento, 100 CU-hora al mes por
   proyecto y escalado a cero a los 5 minutos. Para dos personas sobra de largo.
3. **API → Koyeb.** Una instancia gratuita por organización: 512 MB, 0,1 vCPU, 2 GB de
   disco, Fráncfort o Washington, imagen de Docker desde Docker Hub o GHCR. Es una bolsa
   **independiente de la de Render**, así que esta aplicación no puede tumbar lo que ya
   está desplegado allí.

Con **Fly.io** (1-4 €/mes) como salida si el arranque en frío molesta, y **Oracle Cloud
Always Free** si en algún momento compensa administrar una máquina a cambio de no tener
arranque en frío ninguno. `fly.toml` se conserva justo para eso.

### Lo que se acepta a cambio

- **Arranque en frío largo.** Koyeb baja a cero tras **una hora** sin tráfico —bastante
  mejor que los 15 minutos de Render— pero una JVM con 0,1 vCPU tarda lo suyo en
  levantarse. Cuenta con medio minuto largo en la primera petición del día. El armazón de
  la PWA se sirve de la caché del service worker, así que la aplicación *se ve* al
  instante aunque los datos tarden.
- **Sin volúmenes** en el plan gratuito. No importa: el único estado vive en Neon.
- **Sólo Fráncfort o Washington.** Fráncfort, y Neon en `eu-central-1`, para no cruzar el
  Atlántico en cada consulta.

**Estado: sigue en Propuesta.** Esto se ha decidido leyendo las condiciones publicadas,
no desplegando. Lo que convierte este ADR en Aceptado es una primera ejecución real
contra las cuentas, que es lo que [DESPLIEGUE.md](../DESPLIEGUE.md) describe paso a paso.

# ADR-0002: Plataforma de despliegue gratuita

- **Estado:** Propuesta (pendiente de validar con una prueba real de despliegue)
- **Fecha:** 2026-09-18
- **Rama:** `feature/architecture-setup`

## Contexto

Requisito explícito: desplegar sin coste y **sin usar Vercel ni Render**. Hay que
colocar tres piezas: una API Java, una base de datos PostgreSQL y un frontend
estático (PWA).

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

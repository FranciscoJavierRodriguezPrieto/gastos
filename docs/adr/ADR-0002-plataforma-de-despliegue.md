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

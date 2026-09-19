#!/usr/bin/env node
/**
 * Servidor de estáticos para desarrollo, cuando no se puede usar Docker.
 *
 *   node scripts/servir-frontend.mjs [puerto]
 *
 * Existe para no recomendar `python -m http.server`, que no manda ni `Cache-Control` ni
 * CSP. Eso tiene dos consecuencias feas, y las dos nos han pasado:
 *
 *  - Sin `Cache-Control`, el navegador aplica su caché heurística: editas un fichero,
 *    recargas, y sigues viendo el anterior sin entender por qué.
 *  - Sin CSP, no estás probando lo que se despliega. Las barras de distribución y de DTI
 *    se pintaban vacías en producción porque `style-src` bloquea el atributo style, y en
 *    local no se veía porque en local no había CSP.
 *
 * Manda las mismas cabeceras que `infra/nginx.conf` y que `frontend/_headers`. Sin
 * dependencias, como el resto del frontend (ADR-0006).
 */

import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'frontend');
const PUERTO = Number(process.argv[2] ?? 5173);

/** La misma política que `frontend/_headers`, con la API en local. */
const SEGURIDAD = {
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; "
    + "img-src 'self' data:; font-src 'self'; connect-src 'self' http://localhost:8080; "
    + "manifest-src 'self'; worker-src 'self'; frame-ancestors 'none'; base-uri 'none'; "
    + "form-action 'none'",
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'geolocation=(), camera=(), microphone=(), payment=(), '
    + 'publickey-credentials-get=(self), publickey-credentials-create=(self)',
};

const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

/** Los iconos no cambian; el resto es código y se revalida siempre. */
function cache(ruta) {
  return ruta.startsWith('/icons/') ? 'public, max-age=604800' : 'no-cache';
}

/** Impide salirse de `frontend/` con `..`, aunque aquí sólo escuche en loopback. */
function resolverSeguro(ruta) {
  const limpia = decodeURIComponent(ruta.split('?')[0]);
  const destino = path.resolve(RAIZ, `.${limpia}`);
  return destino.startsWith(RAIZ) ? destino : null;
}

async function ficheroDe(ruta) {
  const destino = resolverSeguro(ruta);
  if (!destino) {
    return null;
  }
  try {
    const info = await stat(destino);
    return info.isDirectory() ? path.join(destino, 'index.html') : destino;
  } catch {
    // Las rutas de la aplicación van en el fragmento, así que cualquier navegación que
    // no exista como fichero cae en index.html, igual que el try_files de nginx.
    return path.join(RAIZ, 'index.html');
  }
}

http.createServer(async (peticion, respuesta) => {
  const fichero = await ficheroDe(peticion.url);
  if (!fichero) {
    respuesta.writeHead(403).end('Prohibido');
    return;
  }

  try {
    await stat(fichero);
  } catch {
    respuesta.writeHead(404, SEGURIDAD).end('No encontrado');
    return;
  }

  respuesta.writeHead(200, {
    ...SEGURIDAD,
    'Content-Type': TIPOS[path.extname(fichero)] ?? 'application/octet-stream',
    'Cache-Control': cache(peticion.url),
  });
  createReadStream(fichero).pipe(respuesta);
// Sólo loopback: esto no es un servidor para exponer a la red.
}).listen(PUERTO, '127.0.0.1', () => {
  console.log(`Frontend en http://localhost:${PUERTO} (sirviendo ${RAIZ})`);
  console.log('Cabeceras de seguridad y de caché iguales que en producción.');
});

#!/usr/bin/env node
/**
 * Prepara el frontend para desplegarlo, apuntando a una API concreta.
 *
 *   node scripts/preparar-frontend.mjs https://gastos-api.fly.dev [destino]
 *
 * Copia `frontend/` tal cual a `dist/` y cambia **exactamente dos cosas**:
 *
 *   1. `config.js`, que es de donde el cliente saca la dirección de la API.
 *   2. El `connect-src` de la CSP en `_headers`.
 *
 * Existe porque esos dos valores tienen que coincidir y hasta ahora había que acordarse
 * de tocarlos a mano al desplegar. Si sólo se cambia uno, la aplicación llama a la API y
 * el navegador bloquea la petición sin que nada en el servidor lo delate: el síntoma es
 * una pantalla que no carga y un error en la consola que nadie está mirando.
 *
 * **No es un paso de compilación** y no contradice al [ADR-0006]. No transpila, no empaqueta
 * y no minifica: sustituye una URL en dos ficheros. Lo que se despliega sigue siendo,
 * línea por línea, lo que hay en el repositorio.
 */

import { cp, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/** Deja `window.GASTOS_API_BASE` apuntando a la API indicada. */
export function reescribirConfig(texto, baseDeApi) {
  const reemplazado = texto.replace(
    /window\.GASTOS_API_BASE\s*=\s*'[^']*';/,
    `window.GASTOS_API_BASE = '${baseDeApi}';`,
  );
  if (reemplazado === texto) {
    // Un reemplazo que no reemplaza nada es justo el fallo silencioso que este script
    // viene a evitar: mejor romper el despliegue que publicarlo mal.
    throw new Error('config.js no contiene la asignación esperada de GASTOS_API_BASE');
  }
  return reemplazado;
}

/** Sustituye el origen de desarrollo por el real en el `connect-src` de la CSP. */
export function reescribirCabeceras(texto, origenDeApi) {
  const reemplazado = texto.replace(
    /connect-src 'self' [^;]*;/,
    `connect-src 'self' ${origenDeApi};`,
  );
  if (reemplazado === texto) {
    throw new Error("_headers no contiene una directiva connect-src reconocible");
  }
  return reemplazado;
}

/** `https://api.example.com/api/v1` -> `https://api.example.com` */
export function origenDe(baseDeApi) {
  const url = new URL(baseDeApi);
  if (url.protocol !== 'https:' && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') {
    // Fuera de local, HTTP no vale: sin contexto seguro no hay passkeys ni service
    // worker, y el token de acceso viajaría en claro.
    throw new Error(`La API tiene que ir por HTTPS: ${baseDeApi}`);
  }
  return url.origin;
}

/** Completa la URL a la base de la API si se pasó sólo el dominio. */
export function normalizarBase(argumento) {
  const url = new URL(argumento);
  const ruta = url.pathname.replace(/\/+$/, '');
  return ruta === '' ? `${url.origin}/api/v1` : `${url.origin}${ruta}`;
}

async function principal() {
  const [argumento, destinoRelativo = 'dist'] = process.argv.slice(2);
  if (!argumento) {
    console.error('Uso: node scripts/preparar-frontend.mjs <url-de-la-api> [destino]');
    console.error('Ejemplo: node scripts/preparar-frontend.mjs https://gastos-api.fly.dev');
    process.exit(1);
  }

  const baseDeApi = normalizarBase(argumento);
  const origen = origenDe(baseDeApi);
  const origenFrontend = path.join(RAIZ, 'frontend');
  const destino = path.resolve(RAIZ, destinoRelativo);

  await rm(destino, { recursive: true, force: true });
  await cp(origenFrontend, destino, { recursive: true });
  // El directorio de tests no pinta nada en lo que se publica.
  await rm(path.join(destino, 'test'), { recursive: true, force: true });

  const config = path.join(destino, 'config.js');
  await writeFile(config, reescribirConfig(await readFile(config, 'utf8'), baseDeApi));

  const cabeceras = path.join(destino, '_headers');
  await writeFile(cabeceras, reescribirCabeceras(await readFile(cabeceras, 'utf8'), origen));

  console.log(`Frontend preparado en ${destino}`);
  console.log(`  API:        ${baseDeApi}`);
  console.log(`  connect-src ${origen}`);
}

// Sólo se ejecuta si se invoca como programa; al importarlo desde un test, no.
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await principal();
}

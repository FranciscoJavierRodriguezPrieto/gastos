import {
  normalizarBase, origenDe, reescribirCabeceras, reescribirConfig,
} from '../preparar-frontend.mjs';
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

/**
 * Tests del script de despliegue.
 *
 * Un script de despliegue que falla a medias es peor que uno que no existe: publica algo
 * que parece bien y sólo se rompe en el navegador de quien lo use. Por eso se prueba
 * sobre todo que **avise en vez de callar** cuando no encuentra lo que va a sustituir.
 */
describe('preparar el frontend para desplegar', () => {

  const CONFIG = "window.GASTOS_API_BASE = 'http://localhost:8080/api/v1';\n";
  const CABECERAS = "  Content-Security-Policy: default-src 'self'; "
    + "connect-src 'self' http://localhost:8080; frame-ancestors 'none'\n";

  it('apunta la configuracion a la API indicada', () => {
    const resultado = reescribirConfig(CONFIG, 'https://gastos-api.fly.dev/api/v1');

    assert.match(resultado, /window\.GASTOS_API_BASE = 'https:\/\/gastos-api\.fly\.dev\/api\/v1';/);
    assert.equal(resultado.includes('localhost'), false);
  });

  it('ajusta el connect-src sin tocar el resto de la CSP', () => {
    const resultado = reescribirCabeceras(CABECERAS, 'https://gastos-api.fly.dev');

    assert.match(resultado, /connect-src 'self' https:\/\/gastos-api\.fly\.dev;/);
    assert.match(resultado, /default-src 'self'/);
    assert.match(resultado, /frame-ancestors 'none'/);
  });

  /* Los dos casos que de verdad importan: si el fichero cambia de forma, hay que
     enterarse al desplegar y no cuando la aplicacion no cargue. */
  it('falla si config.js ya no tiene la asignacion esperada', () => {
    assert.throws(() => reescribirConfig('const otraCosa = 1;', 'https://x.dev/api/v1'),
      /GASTOS_API_BASE/);
  });

  it('falla si _headers ya no tiene connect-src', () => {
    assert.throws(() => reescribirCabeceras("  X-Frame-Options: DENY\n", 'https://x.dev'),
      /connect-src/);
  });

  it('rechaza una API por HTTP fuera de local', () => {
    assert.throws(() => origenDe('http://gastos-api.example.com/api/v1'), /HTTPS/);
    // En local sí se admite: es donde se desarrolla.
    assert.equal(origenDe('http://localhost:8080/api/v1'), 'http://localhost:8080');
  });

  it('completa la base si solo se pasa el dominio', () => {
    assert.equal(normalizarBase('https://gastos-api.fly.dev'), 'https://gastos-api.fly.dev/api/v1');
    assert.equal(normalizarBase('https://gastos-api.fly.dev/'), 'https://gastos-api.fly.dev/api/v1');
    // Y respeta una ruta distinta si se indica a proposito.
    assert.equal(normalizarBase('https://example.com/gastos/api/v1'),
      'https://example.com/gastos/api/v1');
  });

  it('el origen de la CSP no lleva la ruta de la API', () => {
    assert.equal(origenDe('https://gastos-api.fly.dev/api/v1'), 'https://gastos-api.fly.dev');
  });
});

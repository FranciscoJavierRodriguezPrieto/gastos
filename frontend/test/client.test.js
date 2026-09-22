import { respuesta, restaurarAlmacenamiento } from './entorno.js';
import { api, ApiError } from '../src/api/client.js';
import { session } from '../src/api/session.js';
import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';

describe('cliente de la API', () => {
  let llamadas;

  beforeEach(() => {
    restaurarAlmacenamiento();
    session.clear();
    llamadas = [];
  });

  function responderCon(...respuestas) {
    let indice = 0;
    globalThis.fetch = async (url, opciones) => {
      llamadas.push({ url, opciones });
      return respuestas[indice++] ?? respuesta(500, { message: 'sin respuesta preparada' });
    };
  }

  it('envia el token de acceso en la cabecera Authorization', async () => {
    session.start({ accessToken: 'jwt-de-acceso', refreshToken: 'refresco' });
    responderCon(respuesta(200, { ok: true }));

    await api.get('/accounts');

    assert.equal(llamadas[0].opciones.headers.Authorization, 'Bearer jwt-de-acceso');
  });

  it('ante un 401 refresca el token y repite la peticion', async () => {
    session.start({ accessToken: 'caducado', refreshToken: 'refresco' });
    responderCon(
      respuesta(401, { message: 'Token caducado' }),
      respuesta(200, { accessToken: 'jwt-nuevo', refreshToken: 'refresco-nuevo' }),
      respuesta(200, { totalBalance: '100.00' }),
    );

    const resultado = await api.get('/accounts/total-balance');

    assert.equal(llamadas.length, 3);
    assert.ok(llamadas[1].url.endsWith('/auth/refresh'));
    // La peticion repetida ya lleva el token nuevo.
    assert.equal(llamadas[2].opciones.headers.Authorization, 'Bearer jwt-nuevo');
    assert.equal(resultado.totalBalance, '100.00');
    assert.equal(session.refreshToken, 'refresco-nuevo');
  });

  it('si el refresco tambien falla, cierra la sesion y no reintenta en bucle', async () => {
    session.start({ accessToken: 'caducado', refreshToken: 'refresco-invalido' });
    responderCon(
      respuesta(401, { message: 'Token caducado' }),
      respuesta(401, { message: 'Credenciales no validas' }),
    );

    await assert.rejects(
      () => api.get('/accounts'),
      (e) => e instanceof ApiError && e.status === 401,
    );

    assert.equal(llamadas.length, 2);
    assert.equal(session.isAuthenticated, false);
  });

  it('varias peticiones caducadas a la vez comparten un unico refresco', async () => {
    session.start({ accessToken: 'caducado', refreshToken: 'refresco' });

    // El token de refresco rota en cada uso: dos canjes simultaneos harian que el
    // segundo pareciera una reutilizacion y el servidor revocaria la sesion entera.
    let refrescos = 0;
    globalThis.fetch = async (url, opciones) => {
      llamadas.push({ url, opciones });
      if (url.endsWith('/auth/refresh')) {
        refrescos += 1;
        await new Promise((listo) => { setTimeout(listo, 10); });
        return respuesta(200, { accessToken: 'jwt-nuevo', refreshToken: 'refresco-nuevo' });
      }
      return opciones.headers.Authorization === 'Bearer jwt-nuevo'
        ? respuesta(200, { ok: true })
        : respuesta(401, { message: 'Token caducado' });
    };

    await Promise.all([api.get('/accounts'), api.get('/expenses'), api.get('/auth/me')]);

    assert.equal(refrescos, 1);
  });

  it('el error de validacion expone el detalle del campo, que es mas util', async () => {
    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });
    responderCon(respuesta(400, {
      error: 'VALIDATION_ERROR',
      message: 'La peticion contiene campos no validos',
      details: ['amount: El importe debe ser mayor que cero'],
    }));

    await assert.rejects(() => api.post('/expenses', {}), (e) => {
      assert.equal(e.status, 400);
      assert.equal(e.userMessage, 'amount: El importe debe ser mayor que cero');
      return true;
    });
  });

  it('sin red devuelve un error propio en vez de reventar', async () => {
    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };

    await assert.rejects(() => api.get('/accounts'), (e) => {
      assert.ok(e instanceof ApiError);
      assert.equal(e.status, 0);
      assert.match(e.userMessage, /No se puede conectar/);
      return true;
    });
  });

  it('las peticiones no autenticadas no llevan cabecera Authorization', async () => {
    responderCon(respuesta(200, { needsBootstrap: true }));

    await api.get('/auth/status', { autenticada: false });

    assert.equal(llamadas[0].opciones.headers.Authorization, undefined);
  });

  it('un 204 no intenta interpretar cuerpo', async () => {
    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });
    responderCon(respuesta(204));

    assert.equal(await api.delete('/expenses/abc'), null);
  });
});

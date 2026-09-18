import { almacenamiento, restaurarAlmacenamiento } from './entorno.js';
import { session } from '../src/api/session.js';
import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';

/**
 * Tests del cliente, con el ejecutor incorporado de Node:
 *
 *   node --test "frontend/test/*.test.js"
 *
 * Sin dependencias de desarrollo, igual que el frontend no tiene dependencias de
 * ejecución (ADR-0006).
 *
 * Se prueba la lógica de sesión y del cliente HTTP, que es donde de verdad se puede meter
 * la pata: guardar el token donde no toca o encadenar mal los refrescos. El pintado no se
 * prueba aquí.
 */
describe('sesion', () => {

  beforeEach(() => {
    restaurarAlmacenamiento();
    session.clear();
  });

  it('el token de acceso no llega al almacenamiento', () => {
    session.start({ accessToken: 'jwt-de-acceso', refreshToken: 'refresco' });

    assert.equal(session.accessToken, 'jwt-de-acceso');
    // Lo unico persistido es el de refresco: el de acceso vive solo en memoria.
    assert.deepEqual([...almacenamiento.values()], ['refresco']);
  });

  it('se considera autenticado si queda token de refresco aunque caduque el de acceso', () => {
    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });
    session.start({ accessToken: null });

    assert.equal(session.accessToken, null);
    assert.equal(session.isAuthenticated, true);
  });

  it('cerrar sesion borra tambien lo persistido', () => {
    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });

    session.clear();

    assert.equal(session.isAuthenticated, false);
    assert.equal(session.refreshToken, null);
    assert.equal(almacenamiento.size, 0);
  });

  it('avisa a quien escucha cuando la sesion cambia', () => {
    let avisos = 0;
    const dejarDeEscuchar = session.onChange(() => { avisos += 1; });

    session.start({ accessToken: 'jwt', refreshToken: 'refresco' });
    session.clear();
    dejarDeEscuchar();

    assert.equal(avisos, 2);
  });

  it('sobrevive a un localStorage que lanza excepciones', () => {
    // Safari en navegacion privada hace justo esto.
    globalThis.window.localStorage = {
      getItem() { throw new Error('bloqueado'); },
      setItem() { throw new Error('bloqueado'); },
      removeItem() { throw new Error('bloqueado'); },
    };

    assert.doesNotThrow(() => session.start({ accessToken: 'jwt', refreshToken: 'refresco' }));
    assert.equal(session.accessToken, 'jwt');
    assert.equal(session.refreshToken, null);
  });
});

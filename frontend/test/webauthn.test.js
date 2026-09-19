import './entorno.js';
import {
  aBase64Url, deBase64Url, opcionesDeAcceso, opcionesDeAlta, soportaPasskeys,
} from '../src/api/webauthn.js';
import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';

/**
 * Tests de la traducción entre el base64url de la API y los buffers del navegador.
 *
 * Es el único sitio del frontend donde un fallo silencioso rompe la autenticación sin dar
 * la cara: si el reto se decodifica mal, la firma no cuadra y el servidor devuelve un 401
 * indistinguible de una passkey equivocada. Por eso se prueba byte a byte.
 *
 * Las ceremonias en sí no se prueban aquí: dependen de `navigator.credentials`, que no
 * existe en Node. La verificación de verdad está en el backend, contra un autenticador
 * emulado que firma de verdad (PasskeyApiTest).
 */
describe('base64url', () => {

  it('ida y vuelta conserva los bytes exactos', () => {
    const original = new Uint8Array([0, 1, 127, 128, 254, 255]);

    assert.deepEqual(deBase64Url(aBase64Url(original)), original);
  });

  it('decodifica aunque falte el relleno, que es como lo manda la API', () => {
    // 'A' -> un solo byte. En base64 normal llevaría '===' detrás; en base64url no.
    assert.deepEqual(deBase64Url('AQ'), new Uint8Array([1]));
    assert.deepEqual(deBase64Url('AQI'), new Uint8Array([1, 2]));
    assert.deepEqual(deBase64Url('AQID'), new Uint8Array([1, 2, 3]));
  });

  it('usa el alfabeto seguro para URL y no el normal', () => {
    // Estos dos bytes producen '+' y '/' en base64 clásico.
    const conSimbolos = aBase64Url(new Uint8Array([251, 255, 190]));

    assert.equal(conSimbolos.includes('+'), false);
    assert.equal(conSimbolos.includes('/'), false);
    assert.equal(conSimbolos.includes('='), false);
    assert.deepEqual(deBase64Url(conSimbolos), new Uint8Array([251, 255, 190]));
  });

  it('acepta lo que devuelve el servidor, que trae guiones y guiones bajos', () => {
    assert.deepEqual(deBase64Url('-_8'), new Uint8Array([251, 255]));
  });
});

describe('opciones de la ceremonia', () => {

  const respuestaDeAlta = {
    challenge: 'AQID',
    rp: { id: 'localhost', name: 'Gastos' },
    user: { id: 'BAUG', name: 'titular@ejemplo.es', displayName: 'Javi' },
    pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
    excludeCredentials: [{ type: 'public-key', id: 'BwgJ' }],
    timeout: 300000,
    attestation: 'none',
    authenticatorSelection: { residentKey: 'required', userVerification: 'required' },
  };

  it('el reto y el identificador de usuario llegan al navegador como bytes', () => {
    const { publicKey } = opcionesDeAlta(respuestaDeAlta);

    assert.deepEqual(publicKey.challenge, new Uint8Array([1, 2, 3]));
    assert.deepEqual(publicKey.user.id, new Uint8Array([4, 5, 6]));
    assert.deepEqual(publicKey.excludeCredentials[0].id, new Uint8Array([7, 8, 9]));
  });

  it('se respetan las exigencias del servidor y no se relajan por el camino', () => {
    const { publicKey } = opcionesDeAlta(respuestaDeAlta);

    assert.equal(publicKey.authenticatorSelection.userVerification, 'required');
    assert.equal(publicKey.authenticatorSelection.residentKey, 'required');
    assert.equal(publicKey.attestation, 'none');
  });

  it('sin passkeys previas no se rompe por la lista que falta', () => {
    const { publicKey } = opcionesDeAlta({ ...respuestaDeAlta, excludeCredentials: undefined });

    assert.deepEqual(publicKey.excludeCredentials, []);
  });

  /*
   * Lista vacía a propósito: es lo que hace que el navegador ofrezca las passkeys
   * guardadas y se pueda entrar sin escribir el correo. Si algún día se llenara, se
   * perdería eso y además la API estaría diciendo a un anónimo qué credenciales existen.
   */
  it('el acceso no pide credenciales concretas', () => {
    const { publicKey } = opcionesDeAcceso({
      challenge: 'AQID', rpId: 'localhost', timeout: 300000, userVerification: 'required',
    });

    assert.deepEqual(publicKey.allowCredentials, []);
    assert.equal(publicKey.rpId, 'localhost');
    assert.equal(publicKey.userVerification, 'required');
  });
});

describe('deteccion de soporte', () => {

  afterEach(() => {
    delete globalThis.window.PublicKeyCredential;
    delete globalThis.window.navigator;
  });

  it('sin PublicKeyCredential no se ofrece el boton', () => {
    assert.equal(soportaPasskeys(), false);
  });

  /* Safari en modo privado y algún navegador antiguo exponen el tipo pero no la API. */
  it('con el tipo pero sin credentials.create tampoco', () => {
    globalThis.window.PublicKeyCredential = function PublicKeyCredential() {};
    globalThis.window.navigator = { credentials: {} };

    assert.equal(soportaPasskeys(), false);
  });

  it('con las dos cosas si', () => {
    globalThis.window.PublicKeyCredential = function PublicKeyCredential() {};
    globalThis.window.navigator = { credentials: { create: () => {}, get: () => {} } };

    assert.equal(soportaPasskeys(), true);
  });
});

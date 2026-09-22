import { api } from './client.js';

/**
 * Passkeys (WebAuthn) en el navegador.
 *
 * Aquí sólo pasan dos cosas, y ninguna es criptografía: traducir entre el base64url que
 * habla la API y los `ArrayBuffer` que exige `navigator.credentials`, y convertir los
 * fallos del navegador en frases que una persona entienda.
 *
 * **El servidor no se fía de nada de esto.** Cada campo que sale de aquí se verifica
 * contra la clave pública guardada; este fichero no puede relajar ninguna comprobación,
 * sólo estropear la experiencia si se equivoca.
 *
 * Requiere contexto seguro: HTTPS, o `localhost`. En cualquier otra dirección por HTTP el
 * navegador ni siquiera expone la API, y por eso `soportaPasskeys()` devuelve `false` en
 * lugar de fallar al pulsar el botón.
 */

/** Bytes → base64url, que es como viaja todo lo binario en esta API. */
export function aBase64Url(bytes) {
  const octetos = new Uint8Array(bytes);
  let binario = '';
  for (const octeto of octetos) {
    binario += String.fromCharCode(octeto);
  }
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** base64url → Uint8Array. Se repone el relleno que el formato omite. */
export function deBase64Url(texto) {
  const normalizado = texto.replace(/-/g, '+').replace(/_/g, '/');
  const relleno = normalizado.length % 4 === 0 ? '' : '='.repeat(4 - (normalizado.length % 4));
  const binario = atob(normalizado + relleno);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i += 1) {
    bytes[i] = binario.charCodeAt(i);
  }
  return bytes;
}

/**
 * ¿Este navegador puede con passkeys?
 *
 * Se comprueba antes de enseñar el botón. Ofrecer algo que va a fallar es peor que no
 * ofrecerlo: la persona no sabría si el problema es suyo, de su dispositivo o de la
 * aplicación.
 */
export function soportaPasskeys() {
  return typeof window !== 'undefined'
    && typeof window.PublicKeyCredential === 'function'
    && Boolean(window.navigator?.credentials?.create);
}

/** Traduce las opciones del servidor a lo que espera `credentials.create()`. */
export function opcionesDeAlta(respuesta) {
  return {
    publicKey: {
      challenge: deBase64Url(respuesta.challenge),
      rp: respuesta.rp,
      user: {
        id: deBase64Url(respuesta.user.id),
        name: respuesta.user.name,
        displayName: respuesta.user.displayName,
      },
      pubKeyCredParams: respuesta.pubKeyCredParams,
      excludeCredentials: (respuesta.excludeCredentials ?? []).map((credencial) => ({
        type: credencial.type,
        id: deBase64Url(credencial.id),
      })),
      timeout: respuesta.timeout,
      attestation: respuesta.attestation,
      authenticatorSelection: respuesta.authenticatorSelection,
    },
  };
}

/** Traduce las opciones del servidor a lo que espera `credentials.get()`. */
export function opcionesDeAcceso(respuesta) {
  return {
    publicKey: {
      challenge: deBase64Url(respuesta.challenge),
      rpId: respuesta.rpId,
      timeout: respuesta.timeout,
      userVerification: respuesta.userVerification,
      // Vacío a propósito: el navegador ofrece las passkeys que ya tiene guardadas para
      // este dominio, y por eso se entra sin escribir el correo.
      allowCredentials: [],
    },
  };
}

/**
 * Da de alta una passkey para la sesión actual.
 *
 * @param {string} nombre  cómo quiere la persona reconocer este aparato
 */
export async function registrarPasskey(nombre) {
  const opciones = await api.post('/auth/passkeys/registration/options');

  let credencial;
  try {
    credencial = await window.navigator.credentials.create(opcionesDeAlta(opciones));
  } catch (e) {
    throw new PasskeyError(motivo(e, 'alta'));
  }
  if (!credencial) {
    throw new PasskeyError('El navegador no ha creado ninguna passkey');
  }

  return api.post('/auth/passkeys/registration', {
    challenge: opciones.challenge,
    clientDataJSON: aBase64Url(credencial.response.clientDataJSON),
    attestationObject: aBase64Url(credencial.response.attestationObject),
    label: nombre,
  });
}

/**
 * Entra con una passkey.
 *
 * Devuelve la misma respuesta que `/auth/login`: para el resto de la aplicación no hay
 * diferencia entre haber entrado con contraseña o con la huella.
 */
export async function entrarConPasskey() {
  const opciones = await api.post('/auth/passkeys/authentication/options', undefined,
    { autenticada: false });

  let credencial;
  try {
    credencial = await window.navigator.credentials.get(opcionesDeAcceso(opciones));
  } catch (e) {
    throw new PasskeyError(motivo(e, 'acceso'));
  }
  if (!credencial) {
    throw new PasskeyError('No se ha elegido ninguna passkey');
  }

  return api.post('/auth/passkeys/authentication', {
    challenge: opciones.challenge,
    credentialId: aBase64Url(credencial.rawId),
    clientDataJSON: aBase64Url(credencial.response.clientDataJSON),
    authenticatorData: aBase64Url(credencial.response.authenticatorData),
    signature: aBase64Url(credencial.response.signature),
    userHandle: credencial.response.userHandle
      ? aBase64Url(credencial.response.userHandle)
      : null,
  }, { autenticada: false });
}

/** Fallo de la ceremonia en el navegador, ya con un mensaje presentable. */
export class PasskeyError extends Error {
  constructor(mensaje) {
    super(mensaje);
    this.name = 'PasskeyError';
  }
}

/**
 * Qué decirle a la persona.
 *
 * El navegador es deliberadamente parco: para no filtrar qué passkeys hay en el
 * dispositivo, agrupa en `NotAllowedError` tanto "he cancelado" como "se ha agotado el
 * tiempo" o "no tengo ninguna para este sitio". Por eso el mensaje menciona las tres.
 */
function motivo(e, ceremonia) {
  if (e?.name === 'InvalidStateError') {
    return 'Este dispositivo ya tiene una passkey de esta cuenta';
  }
  if (e?.name === 'NotAllowedError') {
    return ceremonia === 'alta'
      ? 'Se ha cancelado la creación de la passkey'
      : 'No se ha podido usar la passkey: puede que se haya cancelado, que haya '
        + 'caducado el tiempo o que este dispositivo no tenga ninguna guardada';
  }
  if (e?.name === 'SecurityError') {
    // Casi siempre es rp-id mal configurado en el servidor; el manual de operación lo
    // explica, pero la persona sólo necesita saber que no es culpa suya.
    return 'Este dominio no está configurado para usar passkeys';
  }
  return 'No se ha podido completar la operación con la passkey';
}

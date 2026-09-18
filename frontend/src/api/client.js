import { session } from './session.js';

/**
 * Cliente HTTP de la API.
 *
 * Dos responsabilidades que conviene no repartir por las vistas:
 *
 *  1. Poner la cabecera Authorization.
 *  2. Renovar el token cuando caduca. El de acceso dura 15 minutos, así que durante una
 *     sesión normal va a caducar varias veces. En lugar de echar al usuario, se canjea el
 *     de refresco y se repite la petición.
 *
 * El reintento se hace UNA sola vez. Si el refresco también falla, la sesión se acabó de
 * verdad y volver a intentarlo sería un bucle.
 */

export const API_BASE = window.GASTOS_API_BASE ?? 'http://localhost:8080/api/v1';

/** Error de la API con su código y el detalle que devolvió el servidor. */
export class ApiError extends Error {
  constructor(status, cuerpo) {
    super(cuerpo?.message ?? 'Error de conexion con el servidor');
    this.name = 'ApiError';
    this.status = status;
    this.code = cuerpo?.error ?? null;
    this.details = cuerpo?.details ?? [];
  }

  /** Mensaje para el usuario: si hay detalle de campo, ese es más útil que el genérico. */
  get userMessage() {
    if (this.details.length > 0) {
      return this.details.join('. ');
    }
    return this.message;
  }
}

async function leerCuerpo(respuesta) {
  if (respuesta.status === 204) {
    return null;
  }
  const texto = await respuesta.text();
  if (!texto) {
    return null;
  }
  try {
    return JSON.parse(texto);
  } catch {
    return null;
  }
}

let refrescoEnCurso = null;

/**
 * Canjea el token de refresco.
 *
 * Si llegan varias peticiones caducadas a la vez, todas esperan al MISMO canje: el token
 * rota en cada uso, así que dos canjes simultáneos harían que el segundo pareciera una
 * reutilización y el servidor revocaría la sesión entera.
 */
async function refrescarSesion() {
  if (refrescoEnCurso) {
    return refrescoEnCurso;
  }
  const refreshToken = session.refreshToken;
  if (!refreshToken) {
    return false;
  }

  refrescoEnCurso = (async () => {
    try {
      const respuesta = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!respuesta.ok) {
        session.clear();
        return false;
      }
      session.start(await respuesta.json());
      return true;
    } catch {
      return false;
    } finally {
      refrescoEnCurso = null;
    }
  })();

  return refrescoEnCurso;
}

async function peticion(metodo, ruta, { body, autenticada = true, reintentar = true } = {}) {
  const cabeceras = {};
  if (body !== undefined) {
    cabeceras['Content-Type'] = 'application/json';
  }
  if (autenticada && session.accessToken) {
    cabeceras.Authorization = `Bearer ${session.accessToken}`;
  }

  let respuesta;
  try {
    respuesta = await fetch(`${API_BASE}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    // Sin red o servidor caído: no es un error de la API, no tiene cuerpo que leer.
    throw new ApiError(0, { message: 'No se puede conectar con el servidor' });
  }

  if (respuesta.status === 401 && autenticada && reintentar) {
    if (await refrescarSesion()) {
      return peticion(metodo, ruta, { body, autenticada, reintentar: false });
    }
    session.clear();
  }

  const cuerpo = await leerCuerpo(respuesta);
  if (!respuesta.ok) {
    throw new ApiError(respuesta.status, cuerpo);
  }
  return cuerpo;
}

export const api = {
  get: (ruta, opciones) => peticion('GET', ruta, opciones),
  post: (ruta, body, opciones) => peticion('POST', ruta, { ...opciones, body }),
  put: (ruta, body, opciones) => peticion('PUT', ruta, { ...opciones, body }),
  delete: (ruta, opciones) => peticion('DELETE', ruta, opciones),
};

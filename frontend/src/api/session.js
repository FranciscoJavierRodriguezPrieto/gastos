/**
 * Sesión del usuario.
 *
 * El token de acceso vive SOLO en memoria: se pierde al recargar y se recupera con el de
 * refresco. Así no llega nunca a disco.
 *
 * El de refresco sí se guarda en localStorage, porque sin él habría que iniciar sesión en
 * cada recarga y una PWA que pide contraseña cada vez no la usa nadie. El riesgo y sus
 * mitigaciones están razonados en docs/adr/ADR-0006-frontend-sin-framework.md.
 */

const CLAVE_REFRESCO = 'gastos.refreshToken';

let tokenAcceso = null;
let usuario = null;
const oyentes = new Set();

/** localStorage puede fallar: modo privado de Safari, almacenamiento lleno, permisos. */
function leerAlmacen(clave) {
  try {
    return window.localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribirAlmacen(clave, valor) {
  try {
    if (valor === null) {
      window.localStorage.removeItem(clave);
    } else {
      window.localStorage.setItem(clave, valor);
    }
  } catch {
    // Sin persistencia la aplicación sigue funcionando; sólo habrá que volver a
    // iniciar sesión al recargar.
  }
}

export const session = {
  get accessToken() {
    return tokenAcceso;
  },

  get refreshToken() {
    return leerAlmacen(CLAVE_REFRESCO);
  },

  get user() {
    return usuario;
  },

  /** Hay algo con lo que intentar entrar, aunque el token de acceso haya caducado. */
  get isAuthenticated() {
    return Boolean(tokenAcceso || leerAlmacen(CLAVE_REFRESCO));
  },

  /** Guarda la respuesta de /auth/login, /auth/register o /auth/refresh. */
  start({ accessToken, refreshToken, user }) {
    tokenAcceso = accessToken ?? null;
    if (refreshToken !== undefined) {
      escribirAlmacen(CLAVE_REFRESCO, refreshToken);
    }
    if (user) {
      usuario = user;
    }
    notificar();
  },

  /** Datos del usuario, que llegan aparte del login con /auth/me. */
  setUser(datos) {
    usuario = datos;
    notificar();
  },

  clear() {
    tokenAcceso = null;
    usuario = null;
    escribirAlmacen(CLAVE_REFRESCO, null);
    notificar();
  },

  /** Avisa a la aplicación cuando la sesión cambia, para repintar la navegación. */
  onChange(oyente) {
    oyentes.add(oyente);
    return () => oyentes.delete(oyente);
  },
};

function notificar() {
  for (const oyente of oyentes) {
    oyente(session);
  }
}

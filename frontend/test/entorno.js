/**
 * Dobles mínimos del navegador para los tests.
 *
 * Se importa el PRIMERO de todo: los módulos ES se evalúan en el orden en que se
 * declaran, así que esto deja `window` en su sitio antes de que `client.js` lea
 * `window.GASTOS_API_BASE` al cargarse.
 *
 * Los módulos se importan una sola vez y se reinicia su estado entre tests, en lugar de
 * recargarlos con un sufijo único: `client.js` importa `session.js` por su ruta real, y
 * dos copias del módulo darían dos sesiones distintas que no se ven entre sí.
 */

const almacen = new Map();

globalThis.window = {
  localStorage: {
    getItem: (clave) => (almacen.has(clave) ? almacen.get(clave) : null),
    setItem: (clave, valor) => almacen.set(clave, String(valor)),
    removeItem: (clave) => almacen.delete(clave),
  },
  GASTOS_API_BASE: 'http://api.test/api/v1',
};

export const almacenamiento = almacen;

/** Devuelve el almacén a su estado normal tras un test que lo haya roto a propósito. */
export function restaurarAlmacenamiento() {
  almacen.clear();
  globalThis.window.localStorage = {
    getItem: (clave) => (almacen.has(clave) ? almacen.get(clave) : null),
    setItem: (clave, valor) => almacen.set(clave, String(valor)),
    removeItem: (clave) => almacen.delete(clave),
  };
}

/** Respuesta de fetch simulada. */
export function respuesta(status, cuerpo) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (cuerpo === undefined ? '' : JSON.stringify(cuerpo)),
    json: async () => cuerpo,
  };
}

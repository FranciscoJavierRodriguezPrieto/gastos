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

/** Lo que responde `prefers-color-scheme: dark`; lo mueve `fijarModoDelSistema`. */
let sistemaEnOscuro = false;
const oyentesDelSistema = new Set();

globalThis.window = {
  localStorage: {
    getItem: (clave) => (almacen.has(clave) ? almacen.get(clave) : null),
    setItem: (clave, valor) => almacen.set(clave, String(valor)),
    removeItem: (clave) => almacen.delete(clave),
  },
  GASTOS_API_BASE: 'http://api.test/api/v1',
  matchMedia: (consulta) => ({
    media: consulta,
    matches: consulta.includes('dark') && sistemaEnOscuro,
    addEventListener: (_evento, oyente) => oyentesDelSistema.add(oyente),
    removeEventListener: (_evento, oyente) => oyentesDelSistema.delete(oyente),
  }),
};

/**
 * Documento mínimo.
 *
 * Sólo lo que necesita src/ui/tema.js: el dataset donde escribe el tema resuelto, la
 * búsqueda de la etiqueta theme-color y los estilos calculados de los que saca el color.
 * No es un DOM: no se prueba aquí el pintado.
 */
globalThis.document = {
  documentElement: { dataset: {} },
  querySelector: () => null,
};
globalThis.getComputedStyle = () => ({ getPropertyValue: () => '' });

export const almacenamiento = almacen;

/** Simula que el sistema operativo cambia de modo, y avisa a quien esté escuchando. */
export function fijarModoDelSistema(oscuro) {
  sistemaEnOscuro = oscuro;
  for (const oyente of oyentesDelSistema) {
    oyente({ matches: oscuro });
  }
}

/** Tema que ha quedado escrito en <html>. */
export function temaDelDocumento() {
  return globalThis.document.documentElement.dataset.tema;
}

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

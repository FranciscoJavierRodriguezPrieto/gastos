/**
 * Tema claro y oscuro.
 *
 * Se distinguen dos cosas que es tentador confundir:
 *
 *   - La **preferencia**, que es lo que ha elegido la persona: 'claro', 'oscuro' o
 *     'sistema'. Se guarda en localStorage y es lo que enseñan los controles.
 *   - El **tema efectivo**, que es el que se pinta: 'claro' u 'oscuro'. Se escribe en
 *     `data-tema` del <html> y es lo único que mira el CSS.
 *
 * Resolver aquí 'sistema' en lugar de dejarlo en el atributo tiene una consecuencia
 * práctica: la paleta oscura de tokens.css se escribe UNA vez. Si el atributo llevara la
 * preferencia, harían falta dos bloques idénticos de tokens —uno para la elección manual
 * y otro dentro de `@media (prefers-color-scheme: dark)`— y dos sitios donde olvidarse de
 * cambiar un color.
 *
 * El precio es que sin JavaScript no hay tema oscuro. Es asumible: sin JavaScript esta
 * aplicación no pinta nada, y ya lo avisa el <noscript>.
 */

/** Tiene que coincidir con la de frontend/tema-inicial.js; hay un test que lo vigila. */
const CLAVE = 'gastos.tema';

export const PREFERENCIAS = ['sistema', 'claro', 'oscuro'];

const DEL_SISTEMA = '(prefers-color-scheme: dark)';

/**
 * Controles que hay que repintar cuando cambia el tema, con el nodo al que pertenecen.
 *
 * Se guardan con su nodo para poder darlos de baja solos cuando deja de estar en el
 * documento. Sin eso, cada repintado de la navegación dejaría un oyente apuntando a un
 * botón que ya no existe: no se nota en pantalla, pero es una fuga que sólo crece.
 */
const oyentes = new Set();

function leerPreferencia() {
  try {
    const guardada = window.localStorage.getItem(CLAVE);
    return PREFERENCIAS.includes(guardada) ? guardada : 'sistema';
  } catch {
    return 'sistema';
  }
}

function guardarPreferencia(preferencia) {
  try {
    if (preferencia === 'sistema') {
      // Quitarla, y no escribir 'sistema', deja el valor por defecto en un solo sitio.
      window.localStorage.removeItem(CLAVE);
    } else {
      window.localStorage.setItem(CLAVE, preferencia);
    }
  } catch {
    // Sin persistencia el tema vale para esta sesión y se olvida al recargar.
  }
}

function prefiereOscuroElSistema() {
  return window.matchMedia(DEL_SISTEMA).matches;
}

/** 'claro' u 'oscuro': lo que de verdad se está pintando. */
export function temaEfectivo() {
  const preferencia = leerPreferencia();
  if (preferencia !== 'sistema') {
    return preferencia;
  }
  return prefiereOscuroElSistema() ? 'oscuro' : 'claro';
}

/** 'sistema', 'claro' u 'oscuro': lo que ha elegido la persona. */
export function preferenciaDeTema() {
  return leerPreferencia();
}

/** Cambia la preferencia y repinta al momento, sin recargar. */
export function fijarTema(preferencia) {
  guardarPreferencia(PREFERENCIAS.includes(preferencia) ? preferencia : 'sistema');
  aplicar();
}

/**
 * Salta de claro a oscuro y al revés.
 *
 * Deja siempre una preferencia explícita: quien toca el interruptor está diciendo que
 * quiere ESE tema, no el que le toque al sistema dentro de un rato.
 */
export function alternarTema() {
  fijarTema(temaEfectivo() === 'oscuro' ? 'claro' : 'oscuro');
}

/**
 * Registra un control que se repinta cuando cambia el tema.
 *
 * @param {Node} nodo  el elemento al que pertenece; su baja es automática
 * @param {Function} repintar
 */
export function alCambiarTema(nodo, repintar) {
  oyentes.add({ nodo, repintar });
}

/** Escribe el tema resuelto en <html> y avisa a los controles vivos. */
function aplicar() {
  const tema = temaEfectivo();
  document.documentElement.dataset.tema = tema;
  sincronizarBarraDelNavegador(tema);

  for (const oyente of oyentes) {
    if (!oyente.nodo.isConnected) {
      oyentes.delete(oyente);
      continue;
    }
    oyente.repintar(tema);
  }
}

/**
 * Pone la barra del navegador a juego.
 *
 * Es lo que colorea la parte de arriba en Android y en la PWA instalada. Sin esto, con
 * el tema oscuro puesto quedaría una franja verde clara sobre una aplicación oscura.
 */
function sincronizarBarraDelNavegador(tema) {
  const etiqueta = document.querySelector('meta[name="theme-color"]');
  if (!etiqueta) {
    return;
  }
  const estilo = getComputedStyle(document.documentElement);
  const color = estilo.getPropertyValue(
    tema === 'oscuro' ? '--color-background' : '--color-primary').trim();
  if (color) {
    etiqueta.setAttribute('content', color);
  }
}

/**
 * Arranque.
 *
 * `tema-inicial.js` ya ha dejado el atributo puesto antes de pintar; esto sólo añade lo
 * que aquel no puede hacer: seguir al sistema si cambia de modo con la aplicación
 * abierta, y poner la barra del navegador a juego.
 */
export function iniciarTema() {
  window.matchMedia(DEL_SISTEMA).addEventListener('change', () => {
    // Quien ha elegido claro u oscuro a mano no quiere que se le cambie solo.
    if (leerPreferencia() === 'sistema') {
      aplicar();
    }
  });
  aplicar();
}

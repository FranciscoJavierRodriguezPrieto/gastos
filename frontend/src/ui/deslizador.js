import { el } from './dom.js';

/**
 * Deslizador con caja numérica sincronizada.
 *
 * Las dos cosas a la vez porque sirven para cosas distintas: el deslizador es para
 * explorar —mover y ver cómo responde la cuota— y la caja para cuando ya sabes la cifra
 * exacta y no quieres pelearte con el ratón.
 *
 * @param {object} config
 * @param {Function} config.alCambiar  se llama con el valor ya convertido a número
 * @param {Function} [config.formato]  cómo se muestra el valor junto a la etiqueta
 */
export function deslizador({
  id, etiqueta, min, max, paso, valor, sufijo = '', formato = null, ayuda = null, alCambiar,
}) {
  const mostrar = formato ?? ((v) => `${v}${sufijo}`);

  const lectura = el('output', { class: 'deslizador__valor', for: id, text: mostrar(valor) });

  const rango = el('input', {
    id,
    class: 'deslizador__rango',
    type: 'range',
    min, max, step: paso, value: valor,
    'aria-describedby': ayuda ? `${id}-ayuda` : null,
  });

  const caja = el('input', {
    class: 'deslizador__caja',
    type: 'number',
    min, max, step: paso, value: valor,
    'aria-label': `${etiqueta}, valor exacto`,
  });

  // Se sincronizan sin disparar el evento del otro, para no entrar en bucle.
  const propagar = (nuevo, origen) => {
    const acotado = Math.min(Math.max(Number(nuevo), Number(min)), Number(max));
    if (origen !== rango) {
      rango.value = acotado;
    }
    if (origen !== caja) {
      caja.value = acotado;
    }
    lectura.textContent = mostrar(acotado);
    alCambiar(acotado);
  };

  rango.addEventListener('input', () => propagar(rango.value, rango));
  // 'change' y no 'input': con 'input', teclear "2" en un campo que va a ser "280000"
  // dispararia una simulacion con un precio absurdo en cada tecla.
  caja.addEventListener('change', () => propagar(caja.value, caja));

  const contenedor = el('div', { class: 'deslizador' }, [
    el('div', { class: 'deslizador__cabecera' }, [
      el('label', { class: 'deslizador__etiqueta', for: id, text: etiqueta }),
      lectura,
    ]),
    el('div', { class: 'deslizador__controles' }, [rango, caja]),
    ayuda ? el('p', { class: 'deslizador__ayuda', id: `${id}-ayuda`, text: ayuda }) : null,
  ]);

  contenedor.fijar = (nuevo) => propagar(nuevo, null);
  return contenedor;
}

/**
 * Retrasa una función hasta que deja de llamarse durante `espera` ms.
 *
 * Arrastrar un deslizador lanza decenas de eventos por segundo. Sin esto serían decenas
 * de peticiones, y además llegarían desordenadas: la respuesta de un valor intermedio
 * podría pintarse después de la del valor final.
 */
export function conRetardo(funcion, espera = 250) {
  let temporizador = null;
  return (...argumentos) => {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => funcion(...argumentos), espera);
  };
}

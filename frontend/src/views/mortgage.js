import { el, tarjeta } from '../ui/dom.js';

/**
 * Herramientas de hipoteca.
 *
 * Pendiente: llega en `feature/mortgage-ui`. El motor de cálculo ya está entero y probado
 * en el backend —cuota, gastos de compraventa, DTI, veredicto y catálogo de programas—;
 * lo que falta es la pantalla con los deslizadores.
 *
 * Se deja un aviso explícito en lugar de una pestaña vacía: una pantalla en blanco parece
 * una aplicación rota.
 */
export function vistaHipoteca(contenedor) {
  const raiz = el('div', { class: 'vista' }, [
    el('header', { class: 'vista__cabecera' }, [
      el('h1', { class: 'vista__titulo', text: 'Herramientas de Hipoteca' }),
    ]),
    tarjeta('En construcción', [
      el('p', { class: 'texto-apoyo', text:
        'El motor de cálculo ya funciona: cuota por amortización francesa, gastos '
        + 'iniciales no financiables, DTI de vivienda y total, veredicto de viabilidad y '
        + 'catálogo de programas de ayuda editable.' }),
      el('p', { class: 'texto-apoyo', text:
        'Lo que falta es esta pantalla, con los deslizadores de precio, ahorro, tipo y '
        + 'plazo, y el selector de programa. Llega en la siguiente tanda de trabajo.' }),
      el('p', { class: 'texto-apoyo texto-apoyo--tenue', text:
        'Mientras tanto se puede usar desde la API, en POST /api/v1/mortgage/simulations.' }),
    ]),
  ]);

  contenedor.replaceChildren(raiz);
}

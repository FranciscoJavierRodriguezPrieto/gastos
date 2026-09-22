/**
 * Utilidades de DOM.
 *
 * Todo el manipular nodos a mano que exige no usar framework vive aquí, para que las
 * vistas se lean como lo que son: qué se pinta, no cómo.
 *
 * Regla que no se rompe: **nunca se usa innerHTML con datos**. Todo texto entra por
 * textContent, que escapa por definición. Un alias de cuenta con `<script>` dentro es
 * texto y se ve como texto.
 *
 * Los estilos calculados se pasan como objeto (`style: { width: '40%' }`) y se aplican
 * por el CSSOM, nunca como cadena en el atributo: la CSP prohíbe los estilos en línea.
 */

/**
 * Crea un elemento.
 *
 * @param {string} etiqueta
 * @param {object} atributos  class, id, type, ... y on* para eventos
 * @param {Array|string|Node} hijos
 */
export function el(etiqueta, atributos = {}, hijos = []) {
  const nodo = document.createElement(etiqueta);

  for (const [clave, valor] of Object.entries(atributos)) {
    if (valor === null || valor === undefined || valor === false) {
      continue;
    }
    if (clave.startsWith('on') && typeof valor === 'function') {
      nodo.addEventListener(clave.slice(2).toLowerCase(), valor);
    } else if (clave === 'class') {
      nodo.className = valor;
    } else if (clave === 'text') {
      nodo.textContent = valor;
    } else if (clave === 'dataset') {
      Object.assign(nodo.dataset, valor);
    } else if (clave === 'style') {
      // Se aplica por el CSSOM y NO con setAttribute('style', ...). La CSP lleva
      // `style-src 'self'` sin 'unsafe-inline', que bloquea el atributo style: las
      // barras de distribución y las de DTI se quedaban con ancho cero, sin más aviso
      // que una línea en la consola. Por el CSSOM la política no se toca.
      Object.assign(nodo.style, valor);
    } else {
      nodo.setAttribute(clave, valor === true ? '' : String(valor));
    }
  }

  for (const hijo of [].concat(hijos)) {
    if (hijo === null || hijo === undefined || hijo === false) {
      continue;
    }
    nodo.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }

  return nodo;
}

export function vaciar(contenedor) {
  contenedor.replaceChildren();
  return contenedor;
}

/** Bloque de carga, para que la pantalla no parpadee en blanco. */
export function cargando(mensaje = 'Cargando...') {
  return el('p', { class: 'estado estado--cargando', text: mensaje });
}

/** Mensaje de error, con rol de alerta para que un lector de pantalla lo anuncie. */
export function error(mensaje) {
  return el('p', { class: 'estado estado--error', role: 'alert', text: mensaje });
}

/** Estado vacío: qué es esto y qué hacer, en lugar de una lista en blanco. */
export function vacio(titulo, ayuda) {
  return el('div', { class: 'estado estado--vacio' }, [
    el('p', { class: 'estado__titulo', text: titulo }),
    ayuda ? el('p', { class: 'estado__ayuda', text: ayuda }) : null,
  ]);
}

export function tarjeta(titulo, contenido, acciones = null) {
  return el('section', { class: 'tarjeta' }, [
    el('header', { class: 'tarjeta__cabecera' }, [
      el('h2', { class: 'tarjeta__titulo', text: titulo }),
      acciones,
    ]),
    el('div', { class: 'tarjeta__cuerpo' }, contenido),
  ]);
}

/** Campo de formulario con su etiqueta asociada, que es lo que lo hace accesible. */
export function campo(id, etiqueta, atributos = {}) {
  const entrada = el('input', { id, name: id, class: 'campo__control', ...atributos });
  const contenedor = el('div', { class: 'campo' }, [
    el('label', { class: 'campo__etiqueta', for: id, text: etiqueta }),
    entrada,
  ]);
  contenedor.control = entrada;
  return contenedor;
}

export function seleccion(id, etiqueta, opciones, valorInicial = null) {
  const control = el('select', { id, name: id, class: 'campo__control' },
    opciones.map(({ value, label }) =>
      el('option', { value, selected: value === valorInicial }, label)));

  const contenedor = el('div', { class: 'campo' }, [
    el('label', { class: 'campo__etiqueta', for: id, text: etiqueta }),
    control,
  ]);
  contenedor.control = control;
  return contenedor;
}

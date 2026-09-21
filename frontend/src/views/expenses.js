import { api, ApiError } from '../api/client.js';
import {
  campo, cargando, el, error as bloqueError, seleccion, tarjeta, vacio,
} from '../ui/dom.js';
import {
  desplazarMes, euros, fecha, hoyIso, mesActual, nombreMes,
} from '../ui/format.js';
import { tarjetaGastosFijos } from './fijos.js';

/**
 * Gastos del mes.
 *
 * El catálogo de categorías y periodicidades se pide al servidor en lugar de escribirlo
 * aquí: si algún día se añade una categoría, esta pantalla la muestra sola.
 *
 * Los gastos fijos viven en su propia tarjeta, pero sus gastos aparecen en el listado
 * como uno más: **al pedir el mes, el servidor los genera si faltaban**. Por eso aquí no
 * hay nada que disparar ni ningún orden que respetar; basta con pedir el mes.
 */

let mesVisible = mesActual();
let catalogo = null;

export async function vistaGastos(contenedor) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  await pintar(raiz);
}

async function pintar(raiz) {
  raiz.replaceChildren(cabecera(raiz), cargando());

  try {
    if (!catalogo) {
      catalogo = await api.get('/expenses/catalog');
    }
    const [resumen, gastos] = await Promise.all([
      api.get(`/expenses/summary?month=${mesVisible}`),
      api.get(`/expenses?month=${mesVisible}`),
    ]);

    raiz.replaceChildren(
      cabecera(raiz),
      totales(resumen),
      formularioAlta(raiz),
      listado(gastos, raiz),
      // Al tocar un gasto fijo hay que repintar el mes: puede haber aparecido o
      // desaparecido un movimiento, y los totales cambian.
      tarjetaGastosFijos({ alCambiar: () => pintar(raiz) }),
    );
  } catch (e) {
    raiz.replaceChildren(
      cabecera(raiz),
      bloqueError(e instanceof ApiError ? e.userMessage : 'No se pueden cargar los gastos'),
    );
  }
}

function cabecera(raiz) {
  const irA = async (meses) => {
    mesVisible = desplazarMes(mesVisible, meses);
    await pintar(raiz);
  };

  return el('header', { class: 'vista__cabecera' }, [
    el('h1', { class: 'vista__titulo', text: 'Gastos' }),
    el('div', { class: 'selector-mes' }, [
      el('button', {
        class: 'boton boton--icono', type: 'button',
        'aria-label': 'Mes anterior', onClick: () => irA(-1),
      }, '‹'),
      el('span', { class: 'selector-mes__valor', text: nombreMes(mesVisible) }),
      el('button', {
        class: 'boton boton--icono', type: 'button',
        'aria-label': 'Mes siguiente', onClick: () => irA(1),
      }, '›'),
    ]),
  ]);
}

function totales(resumen) {
  const total = Number(resumen.total);

  return el('div', { class: 'rejilla-kpi' }, [
    kpi('Gastado este mes', euros(resumen.total), 'negativo'),
    kpi('Compromisos mensuales', euros(resumen.monthlyCommitments),
      null, 'Lo que la banca cuenta como deuda al estudiar una hipoteca'),
    resumen.byCategory.length > 0 ? desglose(resumen.byCategory, total) : null,
  ]);
}

function kpi(etiqueta, valor, tono = null, ayuda = null) {
  return el('div', { class: 'kpi' }, [
    el('span', { class: 'kpi__etiqueta', text: etiqueta }),
    el('span', { class: `kpi__valor${tono ? ` kpi__valor--${tono}` : ''}`, text: valor }),
    ayuda ? el('span', { class: 'kpi__ayuda', text: ayuda }) : null,
  ]);
}

/** Barra apilada más leyenda. El color nunca va solo: siempre lleva etiqueta e importe. */
function desglose(porCategoria, total) {
  const barra = el('div', {
    class: 'barra-apilada',
    role: 'img',
    'aria-label': `Desglose del gasto: ${porCategoria
      .map((c) => `${c.label}, ${euros(c.amount)}`).join('; ')}`,
  }, porCategoria.map((categoria, indice) => el('span', {
    class: `barra-apilada__tramo barra-apilada__tramo--${(indice % 6) + 1}`,
    style: { flex: `${Number(categoria.amount)} 0 0` },
  })));

  const leyenda = el('ul', { class: 'leyenda' }, porCategoria.map((categoria, indice) =>
    el('li', { class: 'leyenda__item' }, [
      el('span', { class: `leyenda__punto leyenda__punto--${(indice % 6) + 1}` }),
      el('span', { class: 'leyenda__texto', text: categoria.label }),
      el('span', { class: 'leyenda__importe amount', text: euros(categoria.amount) }),
    ])));

  return el('div', { class: 'kpi kpi--ancho' }, [
    el('span', { class: 'kpi__etiqueta', text: 'En qué se ha ido' }),
    total > 0 ? barra : null,
    leyenda,
  ]);
}

function formularioAlta(raiz) {
  const descripcion = campo('descripcion', 'Concepto', { required: true, maxlength: 140 });
  const importe = campo('importe', 'Importe (€)', {
    type: 'number', required: true, min: '0.01', step: '0.01', inputmode: 'decimal',
  });
  const categoria = seleccion('categoria', 'Categoría',
    catalogo.categories.map((c) => ({ value: c.value, label: c.label })), 'ALIMENTACION');
  const periodicidad = seleccion('periodicidad', 'Periodicidad',
    catalogo.recurrences.map((r) => ({ value: r.value, label: etiquetaPeriodicidad(r.value) })),
    'PUNTUAL');
  const dia = campo('dia', 'Fecha', { type: 'date', required: true, value: hoyIso() });

  const aviso = el('div', { class: 'formulario__aviso' });

  const formulario = el('form', {
    class: 'formulario formulario--linea',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      try {
        await api.post('/expenses', {
          description: descripcion.control.value.trim(),
          amount: Number(importe.control.value),
          category: categoria.control.value,
          recurrence: periodicidad.control.value,
          incurredOn: dia.control.value,
        });
        await pintar(raiz);
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
      }
    },
  }, [
    descripcion, importe, categoria, periodicidad, dia,
    el('button', { class: 'boton boton--principal', type: 'submit' }, 'Añadir'),
    aviso,
  ]);

  return tarjeta('Anotar un gasto', formulario);
}

function etiquetaPeriodicidad(valor) {
  const etiquetas = {
    PUNTUAL: 'Puntual',
    MENSUAL: 'Mensual',
    BIMESTRAL: 'Bimestral',
    TRIMESTRAL: 'Trimestral',
    SEMESTRAL: 'Semestral',
    ANUAL: 'Anual',
  };
  return etiquetas[valor] ?? valor;
}

function listado(gastos, raiz) {
  if (gastos.length === 0) {
    return tarjeta('Movimientos', vacio(
      'Ningún gasto en ' + nombreMes(mesVisible),
      'Anota el primero con el formulario de arriba.'));
  }

  return tarjeta('Movimientos', el('ul', { class: 'lista' },
    gastos.map((gasto) => filaGasto(gasto, raiz))));
}

function filaGasto(gasto, raiz) {
  const fila = el('li', { class: 'movimiento' }, [
    el('div', { class: 'movimiento__principal' }, [
      el('span', { class: 'movimiento__concepto', text: gasto.description }),
      el('span', { class: 'movimiento__meta' }, [
        gasto.categoryLabel,
        ' · ',
        fecha(gasto.incurredOn),
        gasto.fixedExpenseId
          ? el('span', {
            class: 'etiqueta etiqueta--fijo',
            text: 'fijo',
            title: 'Viene de un gasto fijo. Editarlo aquí cambia sólo este mes.',
          })
          : null,
        gasto.stableCommitment
          ? el('span', {
            class: 'etiqueta etiqueta--compromiso',
            text: 'compromiso',
            title: 'Cuenta como deuda estable en el análisis de hipoteca',
          })
          : null,
      ]),
    ]),
    el('span', { class: 'movimiento__importe amount amount--negative', text: euros(gasto.amount) }),
    el('div', { class: 'movimiento__acciones' }, [
      el('button', {
        class: 'boton boton--sutil',
        type: 'button',
        'aria-label': `Editar ${gasto.description}`,
        // Editar en el sitio y no en un diálogo: es la forma de corregir el importe de
        // un mes de un gasto fijo, que es lo que más se va a hacer aquí.
        onClick: () => fila.replaceWith(formularioEdicion(gasto, raiz)),
      }, 'Editar'),
      el('button', {
        class: 'boton boton--icono boton--peligro',
        type: 'button',
        'aria-label': `Borrar ${gasto.description}`,
        onClick: async () => {
          await api.delete(`/expenses/${gasto.id}`);
          await pintar(raiz);
        },
      }, '×'),
    ]),
  ]);

  return fila;
}

/**
 * Edición de un gasto ya anotado.
 *
 * Si el gasto viene de una plantilla, **esto cambia sólo este mes**: la plantilla no se
 * toca y el mes que viene vuelve a salir por su importe. Es justo lo que hace falta para
 * la luz o el agua, que varían cada mes, y por eso se dice en la propia pantalla en vez
 * de dejarlo a que se adivine.
 */
function formularioEdicion(gasto, raiz) {
  const sufijo = gasto.id;
  const descripcion = campo(`editDescripcion-${sufijo}`, 'Concepto', {
    required: true, maxlength: 140, value: gasto.description,
  });
  const importe = campo(`editImporte-${sufijo}`, 'Importe (€)', {
    type: 'number', required: true, min: '0.01', step: '0.01', inputmode: 'decimal',
    value: gasto.amount,
  });
  const categoria = seleccion(`editCategoria-${sufijo}`, 'Categoría',
    catalogo.categories.map((c) => ({ value: c.value, label: c.label })), gasto.category);
  const periodicidad = seleccion(`editPeriodicidad-${sufijo}`, 'Periodicidad',
    catalogo.recurrences.map((r) => ({ value: r.value, label: etiquetaPeriodicidad(r.value) })),
    gasto.recurrence);
  const dia = campo(`editDia-${sufijo}`, 'Fecha', {
    type: 'date', required: true, value: gasto.incurredOn,
  });

  const aviso = el('div', { class: 'formulario__aviso' });

  return el('li', { class: 'movimiento movimiento--editando' }, [
    el('form', {
      class: 'formulario formulario--linea',
      novalidate: true,
      onSubmit: async (evento) => {
        evento.preventDefault();
        aviso.replaceChildren();
        try {
          await api.put(`/expenses/${gasto.id}`, {
            description: descripcion.control.value.trim(),
            amount: Number(importe.control.value),
            category: categoria.control.value,
            recurrence: periodicidad.control.value,
            incurredOn: dia.control.value,
          });
          await pintar(raiz);
        } catch (e) {
          aviso.replaceChildren(bloqueError(
            e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
        }
      },
    }, [
      descripcion, importe, categoria, periodicidad, dia,
      el('button', { class: 'boton boton--principal', type: 'submit' }, 'Guardar'),
      el('button', {
        class: 'boton boton--sutil', type: 'button', onClick: () => pintar(raiz),
      }, 'Cancelar'),
      gasto.fixedExpenseId
        ? el('p', { class: 'texto-apoyo', text:
          'Viene de un gasto fijo. Esto cambia sólo este mes: la plantilla no se toca y '
          + 'el mes que viene volverá a salir por su importe.' })
        : null,
      aviso,
    ]),
  ]);
}

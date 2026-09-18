import { api, ApiError } from '../api/client.js';
import {
  campo, cargando, el, error as bloqueError, seleccion, tarjeta, vacio,
} from '../ui/dom.js';
import {
  desplazarMes, euros, fecha, hoyIso, mesActual, nombreMes,
} from '../ui/format.js';

/**
 * Gastos del mes.
 *
 * El catálogo de categorías y periodicidades se pide al servidor en lugar de escribirlo
 * aquí: si algún día se añade una categoría, esta pantalla la muestra sola.
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
    style: `flex: ${Number(categoria.amount)} 0 0`,
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

  const filas = gastos.map((gasto) => el('li', { class: 'movimiento' }, [
    el('div', { class: 'movimiento__principal' }, [
      el('span', { class: 'movimiento__concepto', text: gasto.description }),
      el('span', { class: 'movimiento__meta' }, [
        gasto.categoryLabel,
        ' · ',
        fecha(gasto.incurredOn),
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
    el('button', {
      class: 'boton boton--icono boton--peligro',
      type: 'button',
      'aria-label': `Borrar ${gasto.description}`,
      onClick: async () => {
        await api.delete(`/expenses/${gasto.id}`);
        await pintar(raiz);
      },
    }, '×'),
  ]));

  return tarjeta('Movimientos', el('ul', { class: 'lista' }, filas));
}

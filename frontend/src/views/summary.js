import { api, ApiError } from '../api/client.js';
import { cargando, el, error as bloqueError, tarjeta, vacio } from '../ui/dom.js';
import { euros, mesActual, nombreMes, porcentaje } from '../ui/format.js';

/**
 * Resumen del hogar.
 *
 * No calcula nada por su cuenta salvo restas evidentes: el prorrateo de las
 * periodicidades y el desglose por categoría vienen ya resueltos del servidor. Si esta
 * pantalla replicara esas fórmulas, tarde o temprano divergirían.
 */
export async function vistaResumen(contenedor) {
  const raiz = el('div', { class: 'vista' });
  // El indicador va DENTRO de raiz: si fuera hermano, al repintar raiz se quedaria
  // colgando bajo el contenido ya cargado.
  contenedor.replaceChildren(raiz);
  raiz.replaceChildren(cargando());

  const mes = mesActual();

  try {
    const [patrimonio, resumen, miembros, cuentas] = await Promise.all([
      api.get('/accounts/total-balance'),
      api.get(`/expenses/summary?month=${mes}`),
      api.get('/auth/members'),
      api.get('/accounts'),
    ]);

    const ingresos = miembros.reduce((suma, m) => suma + Number(m.monthlyNetIncome ?? 0), 0);
    const gastos = Number(resumen.total);
    const superavit = ingresos - gastos;
    const tasaAhorro = ingresos > 0 ? (superavit / ingresos) * 100 : 0;

    raiz.replaceChildren(
      el('header', { class: 'vista__cabecera' }, [
        el('h1', { class: 'vista__titulo', text: 'Resumen' }),
        el('span', { class: 'vista__subtitulo', text: nombreMes(mes) }),
      ]),
      el('div', { class: 'rejilla-kpi' }, [
        kpi('Patrimonio', euros(patrimonio.totalBalance), null,
          `${cuentas.length} ${cuentas.length === 1 ? 'cuenta' : 'cuentas'}`),
        kpi('Ingresos del hogar', euros(ingresos), 'positivo',
          `${miembros.length} ${miembros.length === 1 ? 'miembro' : 'miembros'}`),
        kpi('Gastos del mes', euros(gastos), 'negativo'),
        kpi('Superávit mensual', euros(superavit), superavit >= 0 ? 'positivo' : 'negativo',
          ingresos > 0 ? `Tasa de ahorro del ${porcentaje(tasaAhorro)}` : null),
      ]),
      distribucion(resumen, gastos),
      compromisos(resumen),
    );
  } catch (e) {
    raiz.replaceChildren(
      bloqueError(e instanceof ApiError ? e.userMessage : 'No se puede cargar el resumen'));
  }
}

function kpi(etiqueta, valor, tono = null, ayuda = null) {
  return el('div', { class: 'kpi' }, [
    el('span', { class: 'kpi__etiqueta', text: etiqueta }),
    el('span', { class: `kpi__valor amount${tono ? ` kpi__valor--${tono}` : ''}`, text: valor }),
    ayuda ? el('span', { class: 'kpi__ayuda', text: ayuda }) : null,
  ]);
}

function distribucion(resumen, total) {
  if (resumen.byCategory.length === 0) {
    return tarjeta('Distribución del gasto', vacio(
      'Todavía no hay gastos este mes',
      'Anótalos en la pestaña Gastos y aparecerán aquí.'));
  }

  const barra = el('div', {
    class: 'barra-apilada barra-apilada--alta',
    role: 'img',
    'aria-label': `Distribución del gasto: ${resumen.byCategory
      .map((c) => `${c.label}, ${euros(c.amount)}`).join('; ')}`,
  }, resumen.byCategory.map((categoria, indice) => el('span', {
    class: `barra-apilada__tramo barra-apilada__tramo--${(indice % 6) + 1}`,
    style: `flex: ${Number(categoria.amount)} 0 0`,
    title: `${categoria.label}: ${euros(categoria.amount)}`,
  })));

  const leyenda = el('ul', { class: 'leyenda leyenda--columna' },
    resumen.byCategory.map((categoria, indice) => el('li', { class: 'leyenda__item' }, [
      el('span', { class: `leyenda__punto leyenda__punto--${(indice % 6) + 1}` }),
      el('span', { class: 'leyenda__texto', text: categoria.label }),
      el('span', { class: 'leyenda__peso', text: porcentaje(categoria.share) }),
      el('span', { class: 'leyenda__importe amount', text: euros(categoria.amount) }),
    ])));

  return tarjeta('Distribución del gasto', [total > 0 ? barra : null, leyenda]);
}

function compromisos(resumen) {
  const cuantia = Number(resumen.monthlyCommitments);

  return tarjeta('Compromisos mensuales', [
    el('p', { class: 'dato-grande amount', text: euros(cuantia) }),
    el('p', {
      class: 'texto-apoyo',
      text: cuantia > 0
        ? 'Gastos recurrentes que una entidad contaría como deuda estable al estudiar '
          + 'vuestra hipoteca. Es el valor que entra en el DTI total.'
        : 'No hay gastos recurrentes marcados como compromiso. Si tenéis préstamo del '
          + 'coche o seguros, anotadlos con su periodicidad para que cuenten en el DTI.',
    }),
  ]);
}

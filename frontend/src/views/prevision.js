import { api, ApiError } from '../api/client.js';
import { cargando, el, error as bloqueError, tarjeta } from '../ui/dom.js';
import { desplazarMes, euros, mesActual, nombreMes } from '../ui/format.js';
import { prever } from './prevision-calculo.js';

/**
 * Previsión de ahorro: cuánto habrá ahorrado cada persona, la cuenta conjunta y el hogar
 * entero en el mes que se elija.
 *
 * El plan se edita aquí mismo y el resultado se recalcula con cada tecla; «Guardar» lo
 * deja en el servidor para que lo vea también la otra persona del hogar.
 */
export async function vistaPrevision(contenedor) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  raiz.replaceChildren(cargando());

  let plan;
  let sumaFijos;
  try {
    const [guardado, miembros, fijos] = await Promise.all([
      api.get('/savings-plan'),
      api.get('/auth/members'),
      api.get('/fixed-expenses'),
    ]);
    sumaFijos = fijos.filter((f) => f.active).reduce((s, f) => s + Number(f.amount), 0);
    plan = guardado ?? planInicial(miembros, sumaFijos);
  } catch (e) {
    raiz.replaceChildren(
      bloqueError(e instanceof ApiError ? e.userMessage : 'No se puede cargar la previsión'));
    return;
  }

  let hasta = desplazarMes(plan.startMonth, 23);
  const resultado = el('div');
  const formulario = el('div');

  const recalcular = () => resultado.replaceChildren(...pintarResultado(plan, hasta));
  const repintarFormulario = () => {
    formulario.replaceChildren(formularioPlan(plan, sumaFijos, recalcular, repintarFormulario));
    recalcular();
  };

  const selectorHasta = el('input', {
    id: 'previsionHasta', type: 'month', class: 'campo__control', value: hasta, required: true,
    onInput: (evento) => {
      if (evento.target.value) {
        hasta = evento.target.value;
        recalcular();
      }
    },
  });

  raiz.replaceChildren(
    el('header', { class: 'vista__cabecera' }, [
      el('h1', { class: 'vista__titulo', text: 'Previsión de ahorro' }),
    ]),
    el('div', { class: 'campo prevision__hasta' }, [
      el('label', { class: 'campo__etiqueta', for: 'previsionHasta', text: '¿Hasta qué mes?' }),
      selectorHasta,
    ]),
    resultado,
    formulario,
  );
  repintarFormulario();
}

function planInicial(miembros, sumaFijos) {
  return {
    startMonth: mesActual(),
    people: miembros.map((m) => persona(m.displayName, Number(m.monthlyNetIncome ?? 0))),
    joint: {
      initialBalance: 0, extraIncome: 0, extraIncomeFrom: null,
      monthlyExpenses: Math.round(sumaFijos * 100) / 100, oneOffExpenses: [],
    },
  };
}

function persona(nombre = '', sueldo = 0) {
  return {
    name: nombre, initialBalance: 0, monthlyIncome: sueldo, extraIncome: 0,
    bonusAmount: 0, bonusMonths: [], jointContribution: 0, expenses: [],
  };
}

function pintarResultado(plan, hasta) {
  if (!plan.startMonth) {
    return [bloqueError('Indica el mes de inicio del plan.')];
  }
  const filas = prever(plan, hasta);
  if (filas.length === 0) {
    return [bloqueError(`Elige un mes a partir de ${nombreMes(plan.startMonth)}.`)];
  }
  const final = filas.at(-1);
  const tono = (v) => (v >= 0 ? 'positivo' : 'negativo');

  const kpis = el('div', { class: 'rejilla-kpi' }, [
    ...plan.people.map((p, i) => kpi(p.name || `Persona ${i + 1}`, final.personas[i], tono(final.personas[i]))),
    kpi('Cuenta conjunta', final.conjunta, tono(final.conjunta)),
    kpi('Total del hogar', final.total, tono(final.total), 'kpi--destacado'),
  ]);

  const tabla = el('div', { class: 'prevision__tabla' }, el('table', {}, [
    el('thead', {}, el('tr', {}, [
      el('th', { scope: 'col', text: 'Mes' }),
      ...plan.people.map((p, i) => el('th', { scope: 'col', text: p.name || `Persona ${i + 1}` })),
      el('th', { scope: 'col', text: 'Conjunta' }),
      el('th', { scope: 'col', text: 'Total' }),
    ])),
    el('tbody', {}, filas.map((f) => el('tr', {}, [
      el('th', { scope: 'row', text: nombreMes(f.mes) }),
      ...f.personas.map((v) => el('td', { class: 'amount', text: euros(v) })),
      el('td', { class: 'amount', text: euros(f.conjunta) }),
      el('td', { class: 'amount', text: euros(f.total) }),
    ]))),
  ]));

  return [
    el('p', { class: 'texto-apoyo', text:
      `Ahorro acumulado a final de ${nombreMes(hasta)} (${filas.length} `
      + `${filas.length === 1 ? 'mes' : 'meses'} desde ${nombreMes(plan.startMonth)}).` }),
    kpis,
    tarjeta('Mes a mes', tabla),
  ];
}

function kpi(etiqueta, valor, tono, extra = '') {
  return el('div', { class: `kpi ${extra}` }, [
    el('span', { class: 'kpi__etiqueta', text: etiqueta }),
    el('span', { class: `kpi__valor amount kpi__valor--${tono}`, text: euros(valor) }),
  ]);
}

// ---------------------------------------------------------------- formulario del plan

let secuencia = 0;

/**
 * Campo ligado a una propiedad del plan: cada cambio se escribe en `objeto[clave]` y
 * recalcula. Los ids se numeran para que cada etiqueta apunte a su control.
 */
function ligado(objeto, clave, etiqueta, alCambiar, tipo = 'number') {
  const id = `prevision${(secuencia += 1)}`;
  const valor = objeto[clave];
  const atributos = tipo === 'number'
    ? { type: 'number', step: '0.01', inputmode: 'decimal', value: valor ?? 0 }
    : { type: tipo, value: valor ?? '' };

  const control = el('input', {
    id, class: 'campo__control', ...atributos,
    onInput: (evento) => {
      const crudo = evento.target.value;
      objeto[clave] = tipo === 'number' ? Number(crudo || 0) : (crudo || null);
      alCambiar();
    },
  });
  return el('div', { class: 'campo' }, [
    el('label', { class: 'campo__etiqueta', for: id, text: etiqueta }), control,
  ]);
}

/** Los meses de paga extra se escriben como «6, 12». */
function mesesDePaga(p, alCambiar) {
  const id = `prevision${(secuencia += 1)}`;
  return el('div', { class: 'campo' }, [
    el('label', { class: 'campo__etiqueta', for: id, text: 'Meses de paga extra (ej. 6, 12)' }),
    el('input', {
      id, class: 'campo__control', inputmode: 'numeric', value: p.bonusMonths.join(', '),
      onInput: (evento) => {
        p.bonusMonths = [...new Set(evento.target.value.split(/[^0-9]+/)
          .map(Number).filter((m) => m >= 1 && m <= 12))];
        alCambiar();
      },
    }),
  ]);
}

function botonSutil(texto, alPulsar, etiqueta = null) {
  return el('button', {
    class: 'boton boton--sutil', type: 'button', onClick: alPulsar, 'aria-label': etiqueta,
  }, texto);
}

/** Lista editable de filas (gastos personales, gastos puntuales). */
function filas(lista, nueva, pintarFila, textoAnadir, repintar) {
  return el('div', { class: 'prevision__filas' }, [
    ...lista.map((item, i) => el('div', { class: 'formulario formulario--linea' }, [
      ...pintarFila(item),
      botonSutil('Quitar', () => { lista.splice(i, 1); repintar(); }, `Quitar ${item.name || 'fila'}`),
    ])),
    botonSutil(textoAnadir, () => { lista.push(nueva()); repintar(); }),
  ]);
}

function formularioPlan(plan, sumaFijos, recalcular, repintar) {
  const aviso = el('div', { class: 'formulario__aviso' });

  const guardar = el('button', { class: 'boton boton--principal', type: 'button' }, 'Guardar plan');
  guardar.addEventListener('click', async () => {
    aviso.replaceChildren();
    guardar.disabled = true;
    try {
      await api.put('/savings-plan', plan);
      aviso.replaceChildren(el('p', { class: 'texto-apoyo', role: 'status', text: 'Plan guardado.' }));
    } catch (e) {
      aviso.replaceChildren(bloqueError(e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
    } finally {
      guardar.disabled = false;
    }
  });

  const personas = plan.people.map((p, i) => tarjeta(p.name || `Persona ${i + 1}`, [
    el('div', { class: 'formulario formulario--linea' }, [
      ligado(p, 'name', 'Nombre', recalcular, 'text'),
      ligado(p, 'initialBalance', 'Ahorro actual (€)', recalcular),
      ligado(p, 'monthlyIncome', 'Sueldo neto mensual (€)', recalcular),
      ligado(p, 'extraIncome', 'Otros ingresos al mes (€)', recalcular),
      ligado(p, 'bonusAmount', 'Importe de cada paga extra (€)', recalcular),
      mesesDePaga(p, recalcular),
      ligado(p, 'jointContribution', 'Aportación a la conjunta (€/mes)', recalcular),
    ]),
    el('h3', { class: 'texto-apoyo', text: 'Gastos personales fijos' }),
    filas(p.expenses, () => ({ name: '', amount: 0, until: null }), (g) => [
      ligado(g, 'name', 'Concepto', recalcular, 'text'),
      ligado(g, 'amount', 'Importe (€/mes)', recalcular),
      ligado(g, 'until', 'Hasta (incluido)', recalcular, 'month'),
    ], 'Añadir gasto personal', repintar),
  ], botonSutil('Quitar persona', () => { plan.people.splice(i, 1); repintar(); })));

  const { joint } = plan;
  const conjunta = tarjeta('Cuenta conjunta', [
    el('div', { class: 'formulario formulario--linea' }, [
      ligado(plan, 'startMonth', 'Mes de inicio', recalcular, 'month'),
      ligado(joint, 'initialBalance', 'Saldo actual (€)', recalcular),
      ligado(joint, 'monthlyExpenses', 'Gastos comunes (€/mes)', recalcular),
      ligado(joint, 'extraIncome', 'Ingreso extra (€/mes)', recalcular),
      ligado(joint, 'extraIncomeFrom', 'Ingreso extra desde', recalcular, 'month'),
    ]),
    el('p', { class: 'texto-apoyo texto-apoyo--tenue', text:
      `Los gastos fijos dados de alta en Gastos suman ${euros(sumaFijos)} al mes, `
      + 'incluidos los que son de una sola persona.' }),
    el('h3', { class: 'texto-apoyo', text: 'Gastos puntuales' }),
    filas(joint.oneOffExpenses, () => ({ name: '', amount: 0, month: plan.startMonth }), (g) => [
      ligado(g, 'name', 'Concepto', recalcular, 'text'),
      ligado(g, 'amount', 'Importe (€)', recalcular),
      ligado(g, 'month', 'Mes', recalcular, 'month'),
    ], 'Añadir gasto puntual', repintar),
  ]);

  return el('div', { class: 'prevision__plan' }, [
    el('h2', { class: 'vista__titulo', text: 'Datos del plan' }),
    ...personas,
    botonSutil('Añadir persona', () => { plan.people.push(persona()); repintar(); }),
    conjunta,
    guardar,
    aviso,
  ]);
}

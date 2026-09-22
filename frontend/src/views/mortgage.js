import { api, ApiError } from '../api/client.js';
import { conRetardo, deslizador } from '../ui/deslizador.js';
import { cargando, el, error as bloqueError, seleccion, tarjeta } from '../ui/dom.js';
import { euros, eurosRedondos, mesActual, porcentaje } from '../ui/format.js';
import { vistaProgramas } from './programs.js';

/**
 * Herramienta de hipoteca.
 *
 * La pantalla no calcula nada. Cada movimiento de un deslizador reenvía el escenario
 * completo al servidor y pinta lo que devuelve. Es más tráfico que calcular la cuota en
 * el navegador, pero evita el problema de verdad: dos implementaciones de la misma
 * fórmula financiera que acaban divergiendo sin que nadie se entere.
 */

const ESTADO_INICIAL = {
  propertyPrice: 280000,
  availableSavings: 50000,
  targetReserve: 5000,
  annualNominalRate: 3.0,
  termYears: 30,
  netMonthlyIncome: 3400,
  otherMonthlyDebts: 225,
  applicantAge: 32,
  firstHome: true,
  familyWithChildren: false,
  largeFamily: false,
  primaryResidence: true,
  financingMode: 'AUTOMATICO',
  programId: null,
  manualLoanToValue: null,
};

let escenario = { ...ESTADO_INICIAL };
let programas = [];
let pestana = 'simulador';

export async function vistaHipoteca(contenedor) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  raiz.replaceChildren(cabecera(contenedor), cargando());

  try {
    programas = await api.get('/mortgage/programs');
    await precargarDelHogar();
  } catch (e) {
    raiz.replaceChildren(
      cabecera(contenedor),
      bloqueError(e instanceof ApiError ? e.userMessage : 'No se puede cargar la herramienta'));
    return;
  }

  if (pestana === 'programas') {
    await vistaProgramas(raiz, cabecera(contenedor), async () => {
      programas = await api.get('/mortgage/programs');
      pestana = 'simulador';
      await vistaHipoteca(contenedor);
    });
    return;
  }

  const panelResultado = el('div', { class: 'resultado' }, cargando('Calculando...'));

  const simular = async () => {
    try {
      panelResultado.replaceChildren(await construirResultado(await api.post(
        '/mortgage/simulations', cuerpoDeLaPeticion())));
    } catch (e) {
      // Se registra ademas en consola: si el fallo no es de la API sino al pintar, el
      // mensaje generico no basta para saber que ha pasado.
      if (!(e instanceof ApiError)) {
        console.error('Fallo al pintar la simulacion', e);
      }
      panelResultado.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se ha podido simular'));
    }
  };
  const simularConRetardo = conRetardo(simular, 250);

  raiz.replaceChildren(
    cabecera(contenedor),
    el('div', { class: 'hipoteca' }, [
      el('div', { class: 'hipoteca__controles' }, [
        panelVivienda(simularConRetardo),
        panelHogar(simularConRetardo),
        panelFinanciacion(simularConRetardo, contenedor),
      ]),
      panelResultado,
    ]),
  );

  await simular();
}

/**
 * Rellena los ingresos y las deudas con lo que ya sabe la aplicación.
 *
 * Que el usuario tenga que reescribir a mano datos que ya ha metido en Gastos es la
 * forma más rápida de que la simulación se haga con cifras desactualizadas.
 */
async function precargarDelHogar() {
  try {
    const [miembros, resumen] = await Promise.all([
      api.get('/auth/members'),
      api.get(`/expenses/summary?month=${mesActual()}`),
    ]);

    const ingresos = miembros.reduce((suma, m) => suma + Number(m.monthlyNetIncome ?? 0), 0);
    if (ingresos > 0) {
      escenario.netMonthlyIncome = ingresos;
    }
    const compromisos = Number(resumen.monthlyCommitments ?? 0);
    if (compromisos > 0) {
      escenario.otherMonthlyDebts = compromisos;
    }
  } catch {
    // Sin precarga se usan los valores de partida; no es motivo para no simular.
  }
}

function cuerpoDeLaPeticion() {
  const cuerpo = { ...escenario };
  if (cuerpo.financingMode !== 'PROGRAMA') {
    delete cuerpo.programId;
  }
  if (cuerpo.financingMode !== 'MANUAL') {
    delete cuerpo.manualLoanToValue;
  }
  return cuerpo;
}

function cabecera(contenedor) {
  const pestanas = [
    { clave: 'simulador', texto: 'Simulador' },
    { clave: 'programas', texto: `Programas de ayuda (${programas.length})` },
  ];

  return el('header', { class: 'vista__cabecera' }, [
    el('h1', { class: 'vista__titulo', text: 'Herramientas de Hipoteca' }),
    el('div', { class: 'pestanas', role: 'tablist' }, pestanas.map((p) => el('button', {
      class: `pestanas__boton${pestana === p.clave ? ' pestanas__boton--activa' : ''}`,
      type: 'button',
      role: 'tab',
      'aria-selected': String(pestana === p.clave),
      onClick: async () => {
        pestana = p.clave;
        await vistaHipoteca(contenedor);
      },
    }, p.texto))),
  ]);
}

// ---------------------------------------------------------------- paneles de entrada

function panelVivienda(alCambiar) {
  const actualizar = (campo) => (valor) => {
    escenario[campo] = valor;
    alCambiar();
  };

  return tarjeta('La vivienda', el('div', { class: 'controles' }, [
    deslizador({
      id: 'precio',
      etiqueta: 'Precio de la vivienda',
      min: 50000, max: 900000, paso: 5000,
      valor: escenario.propertyPrice,
      formato: eurosRedondos,
      alCambiar: actualizar('propertyPrice'),
    }),
    deslizador({
      id: 'ahorro',
      etiqueta: 'Ahorro disponible',
      min: 0, max: 400000, paso: 1000,
      valor: escenario.availableSavings,
      formato: eurosRedondos,
      alCambiar: actualizar('availableSavings'),
    }),
    deslizador({
      id: 'reserva',
      etiqueta: 'Fondo de emergencia',
      min: 0, max: 60000, paso: 500,
      valor: escenario.targetReserve,
      formato: eurosRedondos,
      ayuda: 'Ahorro que NO quieres destinar a la entrada. Se reserva antes de calcular.',
      alCambiar: actualizar('targetReserve'),
    }),
    deslizador({
      id: 'tipo',
      etiqueta: 'Tipo de interés (TIN)',
      min: 0, max: 8, paso: 0.05,
      valor: escenario.annualNominalRate,
      formato: (v) => `${Number(v).toFixed(2).replace('.', ',')} %`,
      alCambiar: actualizar('annualNominalRate'),
    }),
    deslizador({
      id: 'plazo',
      etiqueta: 'Plazo',
      min: 5, max: 40, paso: 1,
      valor: escenario.termYears,
      formato: (v) => `${v} años`,
      alCambiar: actualizar('termYears'),
    }),
  ]));
}

function panelHogar(alCambiar) {
  const actualizar = (campo) => (valor) => {
    escenario[campo] = valor;
    alCambiar();
  };

  const casilla = (campo, texto) => el('label', { class: 'casilla' }, [
    el('input', {
      type: 'checkbox',
      checked: escenario[campo],
      onChange: (evento) => {
        escenario[campo] = evento.target.checked;
        alCambiar();
      },
    }),
    el('span', { text: texto }),
  ]);

  return tarjeta('Vuestra situación', el('div', { class: 'controles' }, [
    deslizador({
      id: 'ingresos',
      etiqueta: 'Ingresos netos del hogar',
      min: 800, max: 12000, paso: 50,
      valor: escenario.netMonthlyIncome,
      formato: (v) => `${eurosRedondos(v)}/mes`,
      ayuda: 'Precargado con los ingresos de los miembros del hogar.',
      alCambiar: actualizar('netMonthlyIncome'),
    }),
    deslizador({
      id: 'deudas',
      etiqueta: 'Otras deudas mensuales',
      min: 0, max: 3000, paso: 25,
      valor: escenario.otherMonthlyDebts,
      formato: (v) => `${eurosRedondos(v)}/mes`,
      ayuda: 'Precargado con los compromisos recurrentes de la pestaña Gastos.',
      alCambiar: actualizar('otherMonthlyDebts'),
    }),
    deslizador({
      id: 'edad',
      // La del MAYOR: Mi Primera Vivienda exige que todas las personas que compran
      // cumplan el tope de edad, así que el que decide es el de más edad.
      etiqueta: 'Edad del mayor de los dos',
      min: 18, max: 75, paso: 1,
      valor: escenario.applicantAge,
      formato: (v) => `${v} años`,
      ayuda: 'Mi Primera Vivienda financia hasta el 100% hasta los 40, el 95% hasta los 45 '
        + 'y el 90% hasta los 50. Cuenta la edad de quien tenga más.',
      alCambiar: actualizar('applicantAge'),
    }),
    casilla('firstHome', 'Es nuestra primera vivienda en propiedad'),
    casilla('primaryResidence', 'Vamos a vivir en ella (vivienda habitual)'),
    casilla('familyWithChildren',
      'Tenemos hijos menores a cargo, o somos familia numerosa o monoparental'),
    casilla('largeFamily', 'Tenemos título de familia numerosa'),
    el('p', { class: 'texto-apoyo', text:
      'Con hijos menores, Mi Primera Vivienda llega al 100% a cualquier edad. El título de '
      + 'familia numerosa además baja el ITP al 4%.' }),
  ]));
}

function panelFinanciacion(alCambiar, contenedor) {
  const activos = programas.filter((p) => p.active);

  const modo = seleccion('modo', 'Cómo se decide cuánto te prestan', [
    { value: 'AUTOMATICO', label: 'Automático: el mejor programa que cumpláis' },
    { value: 'PROGRAMA', label: 'Un programa concreto' },
    { value: 'MANUAL', label: 'Lo fijo yo a mano' },
  ], escenario.financingMode);

  const elegirPrograma = seleccion('programa', 'Programa',
    activos.map((p) => ({ value: p.id, label: `${p.name} (${porcentaje(p.maxLoanToValue)})` })),
    escenario.programId ?? activos[0]?.id);

  const ltvManual = deslizador({
    id: 'ltv',
    etiqueta: 'LTV máximo',
    min: 50, max: 100, paso: 1,
    valor: escenario.manualLoanToValue ?? 80,
    formato: (v) => `${v} %`,
    ayuda: 'No se comprueba ningún requisito: confirma con la entidad que financia ese porcentaje.',
    alCambiar: (valor) => {
      escenario.manualLoanToValue = valor;
      alCambiar();
    },
  });

  const extra = el('div', { class: 'controles__extra' });

  const pintarExtra = () => {
    if (escenario.financingMode === 'PROGRAMA') {
      extra.replaceChildren(activos.length > 0
        ? elegirPrograma
        : el('p', { class: 'texto-apoyo', text:
          'No hay programas activos. Dalos de alta en la pestaña de programas.' }));
      escenario.programId = elegirPrograma.control?.value ?? null;
    } else if (escenario.financingMode === 'MANUAL') {
      extra.replaceChildren(ltvManual);
      escenario.manualLoanToValue = Number(ltvManual.querySelector('input').value);
    } else {
      extra.replaceChildren();
    }
  };

  modo.control.addEventListener('change', () => {
    escenario.financingMode = modo.control.value;
    pintarExtra();
    alCambiar();
  });

  elegirPrograma.control.addEventListener('change', () => {
    escenario.programId = elegirPrograma.control.value;
    alCambiar();
  });

  pintarExtra();

  const irAProgramas = el('button', {
    class: 'boton boton--sutil', type: 'button',
    onClick: async () => {
      pestana = 'programas';
      await vistaHipoteca(contenedor);
    },
  }, 'Gestionar programas');

  return tarjeta('Financiación', el('div', { class: 'controles' }, [
    modo, extra, irAProgramas,
  ]));
}

// ---------------------------------------------------------------- resultado

function construirResultado(resultado) {
  const veredicto = resultado.viability;

  return el('div', { class: 'resultado__contenido' }, [
    cuotaYVeredicto(resultado, veredicto),
    tarjeta('Endeudamiento', ratios(veredicto)),
    tarjeta('Lo que hay que poner el día de la firma', desembolso(resultado)),
    tarjeta('Cómo queda la financiación', financiacion(resultado)),
    motivos(veredicto, resultado.financingDecision),
  ]);
}

function cuotaYVeredicto(resultado, veredicto) {
  const tono = { INVIABLE: 'inviable', VIABLE_AJUSTADA: 'ajustada', OPTIMA: 'optima' }[
    veredicto.verdict];

  return el('div', { class: `veredicto veredicto--${tono}` }, [
    el('div', { class: 'veredicto__cuota' }, [
      el('span', { class: 'veredicto__etiqueta', text: 'Cuota mensual estimada' }),
      el('span', { class: 'veredicto__importe amount', text: euros(resultado.monthlyPayment) }),
    ]),
    el('span', { class: `distintivo distintivo--${tono}`, text: veredicto.verdictLabel }),
  ]);
}

/**
 * Los dos DTI con su límite marcado.
 *
 * El límite se dibuja sobre la barra porque el número solo no dice nada: un 28% parece
 * bueno o malo según con qué se compare.
 */
function ratios(veredicto) {
  return el('div', { class: 'ratios' }, [
    barraRatio('DTI vivienda', veredicto.housingDti, 30,
      'Cuota de la hipoteca sobre los ingresos netos'),
    barraRatio('DTI total', veredicto.totalDti, 40,
      'Cuota más el resto de deudas sobre los ingresos netos'),
    el('div', { class: 'ratios__residual' }, [
      el('span', { class: 'kpi__etiqueta', text: 'Renta disponible tras todas las cuotas' }),
      el('span', { class: 'kpi__valor amount', text: `${euros(veredicto.residualIncome)}/mes` }),
    ]),
  ]);
}

function barraRatio(etiqueta, valor, limite, ayuda) {
  const porcentajeValor = Number(valor);
  const excede = porcentajeValor > limite;
  // La escala llega al 60% para que superar el límite se vea, en vez de toparse al borde.
  const escala = 60;

  return el('div', { class: 'ratio' }, [
    el('div', { class: 'ratio__cabecera' }, [
      el('span', { class: 'ratio__etiqueta', text: etiqueta }),
      el('span', {
        class: `ratio__valor amount${excede ? ' ratio__valor--excede' : ''}`,
        text: porcentaje(valor),
      }),
    ]),
    el('div', {
      class: 'ratio__pista',
      role: 'img',
      'aria-label': `${etiqueta}: ${porcentaje(valor)}, límite ${limite} %`,
    }, [
      el('span', {
        class: `ratio__relleno${excede ? ' ratio__relleno--excede' : ''}`,
        style: { width: `${Math.min((porcentajeValor / escala) * 100, 100)}%` },
      }),
      el('span', {
        class: 'ratio__limite',
        style: { left: `${(limite / escala) * 100}%` },
        title: `Límite: ${limite} %`,
      }),
    ]),
    el('p', { class: 'ratio__ayuda', text: `${ayuda}. Límite: ${limite} %.` }),
  ]);
}

function desembolso(resultado) {
  const gastos = resultado.upfrontCosts;
  const plan = resultado.financing;

  return el('div', {}, [
    el('p', { class: 'dato-grande amount', text: euros(resultado.cashRequiredAtSigning) }),
    el('ul', { class: 'desglose' }, [
      linea('Entrada', plan.downPayment),
      linea(`Impuesto de transmisiones (ITP, ${porcentaje(gastos.transferTaxRate)})`,
        gastos.transferTax),
      linea('Notaría, registro y gestoría', gastos.ancillaryCosts),
    ]),
    // Por qué ese tipo y no otro: es lo primero que conviene contrastar con el notario.
    gastos.transferTaxBasis
      ? el('p', { class: 'texto-apoyo', text: gastos.transferTaxBasis })
      : null,
    !plan.savingsSufficient
      ? el('p', { class: 'estado estado--error', role: 'alert',
        text: `Faltan ${euros(plan.shortfall)} para poder firmar.` })
      : el('p', { class: 'texto-apoyo',
        text: `Tras la firma os quedarían ${euros(plan.savingsBuffer)}.` }),
  ]);
}

function financiacion(resultado) {
  const plan = resultado.financing;
  const decision = resultado.financingDecision;

  return el('div', {}, [
    el('ul', { class: 'desglose' }, [
      linea('Importe del préstamo', plan.loanAmount),
      linea('Intereses en todo el plazo', resultado.totalInterest),
      linea('Coste total de la operación', resultado.totalCostOfOwnership),
    ]),
    el('p', { class: 'texto-apoyo', text:
      `LTV efectivo del ${porcentaje(plan.effectiveLoanToValue)} sobre un máximo del `
      + `${porcentaje(decision.appliedLoanToValue)}`
      + (decision.appliedProgramName ? `, aplicando «${decision.appliedProgramName}»` : '') + '.' }),
  ]);
}

function linea(etiqueta, importe) {
  return el('li', { class: 'desglose__linea' }, [
    el('span', { text: etiqueta }),
    el('span', { class: 'amount', text: euros(importe) }),
  ]);
}

/** Por qué sale así. El dominio ya redacta los motivos; aquí sólo se listan. */
function motivos(veredicto, decision) {
  const bloques = [];

  if (veredicto.blockingReasons.length > 0) {
    bloques.push(lista('Por qué no sale', veredicto.blockingReasons, 'motivos--bloqueo'));
  }
  if (veredicto.warnings.length > 0) {
    bloques.push(lista('A tener en cuenta', veredicto.warnings, 'motivos--aviso'));
  }
  if (decision.notes.length > 0) {
    bloques.push(lista('Sobre la financiación', decision.notes, 'motivos--nota'));
  }

  const evaluados = decision.evaluations ?? [];
  if (evaluados.length > 0) {
    bloques.push(el('div', { class: 'motivos' }, [
      el('h3', { class: 'motivos__titulo', text: 'Programas evaluados' }),
      el('ul', { class: 'lista' }, evaluados.map((programa) => el('li', { class: 'programa-eval' }, [
        el('span', {
          class: `distintivo distintivo--${programa.eligible ? 'optima' : 'neutro'}`,
          text: programa.eligible ? 'Cumplís' : (programa.active ? 'No cumplís' : 'Desactivado'),
        }),
        el('div', { class: 'programa-eval__datos' }, [
          el('span', { class: 'programa-eval__nombre', text: programa.programName }),
          el('span', { class: 'programa-eval__meta',
            text: `Hasta el ${porcentaje(programa.maxLoanToValue)}` }),
          ...programa.unmetCriteria.map((criterio) =>
            el('span', { class: 'programa-eval__falta', text: criterio })),
        ]),
      ]))),
    ]));
  }

  return bloques.length > 0 ? tarjeta('El porqué', bloques) : null;
}

function lista(titulo, elementos, clase) {
  return el('div', { class: `motivos ${clase}` }, [
    el('h3', { class: 'motivos__titulo', text: titulo }),
    el('ul', { class: 'motivos__lista' },
      elementos.map((texto) => el('li', { text: texto }))),
  ]);
}

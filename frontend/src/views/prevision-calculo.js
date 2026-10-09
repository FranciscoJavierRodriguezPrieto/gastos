/**
 * Cálculo de la previsión de ahorro, sin DOM, para poder probarlo con node --test.
 *
 * Todo va en céntimos enteros: sumar 24 meses de 724,97 en coma flotante deja colas de
 * decimales que luego no cuadran con la hoja de cálculo.
 *
 * Un mes cuenta entero: el primero es `plan.startMonth` y el último, `hasta`, ambos
 * incluidos.
 */

const centimos = (importe) => Math.round(Number(importe ?? 0) * 100);

/** Meses 'yyyy-MM' de `desde` a `hasta`, ambos incluidos. Vacío si `hasta` es anterior. */
export function mesesEntre(desde, hasta) {
  const meses = [];
  let [anio, mes] = desde.split('-').map(Number);
  // La comparación de cadenas 'yyyy-MM' ordena igual que las fechas.
  for (let actual = desde; actual <= hasta;) {
    meses.push(actual);
    mes += 1;
    if (mes > 12) {
      mes = 1;
      anio += 1;
    }
    actual = `${anio}-${String(mes).padStart(2, '0')}`;
  }
  return meses;
}

/** Lo que ahorra una persona en su cuenta personal ese mes. */
function ahorroPersonal(persona, mes) {
  const numeroMes = Number(mes.slice(5));
  const entra = centimos(persona.monthlyIncome) + centimos(persona.extraIncome)
    + (persona.bonusMonths.includes(numeroMes) ? centimos(persona.bonusAmount) : 0);
  const gastos = persona.expenses
    .filter((gasto) => !gasto.until || mes <= gasto.until)
    .reduce((suma, gasto) => suma + centimos(gasto.amount), 0);
  return entra - centimos(persona.jointContribution) - gastos;
}

function ahorroConjunto(plan, mes) {
  const { joint } = plan;
  const aportaciones = plan.people.reduce((suma, p) => suma + centimos(p.jointContribution), 0);
  const extra = !joint.extraIncomeFrom || mes >= joint.extraIncomeFrom
    ? centimos(joint.extraIncome) : 0;
  const puntuales = joint.oneOffExpenses
    .filter((gasto) => gasto.month === mes)
    .reduce((suma, gasto) => suma + centimos(gasto.amount), 0);
  return aportaciones + extra - centimos(joint.monthlyExpenses) - puntuales;
}

/**
 * Saldo acumulado al cierre de cada mes, de cada persona, de la conjunta y del total.
 *
 * @returns {{ mes: string, personas: number[], conjunta: number, total: number }[]}
 *   importes en euros
 */
export function prever(plan, hasta) {
  const personas = plan.people.map((p) => centimos(p.initialBalance));
  let conjunta = centimos(plan.joint.initialBalance);

  return mesesEntre(plan.startMonth, hasta).map((mes) => {
    plan.people.forEach((persona, i) => { personas[i] += ahorroPersonal(persona, mes); });
    conjunta += ahorroConjunto(plan, mes);
    const total = personas.reduce((a, b) => a + b, 0) + conjunta;
    return {
      mes,
      personas: personas.map((c) => c / 100),
      conjunta: conjunta / 100,
      total: total / 100,
    };
  });
}

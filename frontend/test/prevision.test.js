import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { mesesEntre, prever } from '../src/views/prevision-calculo.js';

/**
 * El caso de prueba es la hoja de cálculo con la que el hogar hizo la previsión a mano,
 * de octubre de 2026 a septiembre de 2028. Si el cálculo se desvía un céntimo de ella,
 * algo se ha roto.
 */
const PLAN = {
  startMonth: '2026-10',
  people: [
    {
      name: 'Pareja', initialBalance: 0, monthlyIncome: 1600, extraIncome: 0,
      bonusAmount: 1600, bonusMonths: [6, 12], jointContribution: 850, expenses: [],
    },
    {
      name: 'Fran', initialBalance: 0, monthlyIncome: 1600, extraIncome: 200,
      bonusAmount: 0, bonusMonths: [], jointContribution: 850,
      expenses: [{ name: 'Préstamo coche', amount: 225.03, until: '2030-09' }],
    },
  ],
  joint: {
    initialBalance: 0, extraIncome: 425, extraIncomeFrom: '2026-11', monthlyExpenses: 1680,
    oneOffExpenses: [
      { name: 'Seguro coche', amount: 318.06, month: '2026-10' },
      { name: 'Seguro coche', amount: 312.04, month: '2026-11' },
      { name: 'Seguro coche', amount: 312.04, month: '2027-01' },
    ],
  },
};

describe('previsión de ahorro', () => {
  it('cuadra con la hoja de cálculo a 24 meses', () => {
    const filas = prever(PLAN, '2028-09');
    const final = filas.at(-1);

    assert.equal(filas.length, 24);
    assert.deepEqual(final.personas, [24400, 17399.28]);
    assert.equal(final.conjunta, 9312.86);
    assert.equal(final.total, 51112.14);
  });

  it('el primer mes la conjunta queda en negativo por el seguro', () => {
    assert.equal(prever(PLAN, '2026-10')[0].conjunta, -298.06);
  });

  it('un gasto personal deja de restar después de su mes de fin', () => {
    const plan = structuredClone(PLAN);
    plan.people[1].expenses[0].until = '2026-10';
    assert.equal(prever(plan, '2026-11')[1].personas[1], 724.97 + 950);
  });

  it('los meses cruzan el cambio de año y un final anterior no da filas', () => {
    assert.deepEqual(mesesEntre('2026-11', '2027-02'), ['2026-11', '2026-12', '2027-01', '2027-02']);
    assert.deepEqual(prever(PLAN, '2026-09'), []);
  });
});

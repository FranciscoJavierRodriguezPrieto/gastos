/**
 * Formato de números y fechas.
 *
 * Los formateadores de Intl se crean una sola vez: construirlos es caro y aquí se usan en
 * bucles sobre listas.
 */

const EUROS = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const EUROS_REDONDOS = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
});

const PORCENTAJE = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const FECHA_LARGA = new Intl.DateTimeFormat('es-ES', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const MES_LARGO = new Intl.DateTimeFormat('es-ES', { month: 'long', year: 'numeric' });

export function euros(importe) {
  return EUROS.format(Number(importe ?? 0));
}

/** Para titulares grandes, donde los céntimos sólo añaden ruido. */
export function eurosRedondos(importe) {
  return EUROS_REDONDOS.format(Number(importe ?? 0));
}

export function porcentaje(valor) {
  return `${PORCENTAJE.format(Number(valor ?? 0))} %`;
}

/** Las fechas de la API son yyyy-MM-dd y no llevan zona: partirlas evita el desfase. */
export function fecha(iso) {
  if (!iso) {
    return '';
  }
  const [anio, mes, dia] = iso.split('-').map(Number);
  return FECHA_LARGA.format(new Date(anio, mes - 1, dia));
}

/** '2026-09' -> 'septiembre de 2026' */
export function nombreMes(yyyyMM) {
  const [anio, mes] = yyyyMM.split('-').map(Number);
  return MES_LARGO.format(new Date(anio, mes - 1, 1));
}

/** Mes actual en el formato que espera la API. */
export function mesActual() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`;
}

export function hoyIso() {
  const hoy = new Date();
  return [
    hoy.getFullYear(),
    String(hoy.getMonth() + 1).padStart(2, '0'),
    String(hoy.getDate()).padStart(2, '0'),
  ].join('-');
}

/** Desplaza un mes 'yyyy-MM' en n meses, para los botones de anterior y siguiente. */
export function desplazarMes(yyyyMM, meses) {
  const [anio, mes] = yyyyMM.split('-').map(Number);
  const fecha = new Date(anio, mes - 1 + meses, 1);
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
}

/*
 * Aplica el tema guardado ANTES de la primera pintura.
 *
 * Esto no es un módulo y va en el <head> con un <script src> normal, a propósito: los
 * módulos son diferidos, así que si el tema se aplicara desde src/ui/tema.js quien tiene
 * el modo oscuro puesto vería un fogonazo blanco en cada arranque. La solución habitual
 * —cuatro líneas dentro del propio HTML— aquí no vale: la CSP lleva `script-src 'self'`
 * sin 'unsafe-inline' y el navegador las bloquearía.
 *
 * Su único cometido es dejar `data-tema` resuelto en <html>. Cambiar de tema, recordar
 * la preferencia y pintar los controles es cosa de src/ui/tema.js, que es quien manda.
 *
 * La clave de almacenamiento está escrita en los dos sitios porque este fichero no puede
 * importar nada. frontend/test/tema.test.js comprueba que no se separen.
 */
(function aplicarTemaGuardado() {
  var CLAVE = 'gastos.tema';

  var preferencia = null;
  try {
    preferencia = window.localStorage.getItem(CLAVE);
  } catch (e) {
    // Modo privado de Safari, almacenamiento bloqueado: se cae al tema del sistema.
  }

  // Sin preferencia guardada, o con cualquier valor que no reconozcamos, manda el
  // sistema operativo. Es lo que espera quien ya tiene el móvil en modo noche.
  var oscuro = preferencia === 'oscuro'
    || (preferencia !== 'claro'
      && window.matchMedia('(prefers-color-scheme: dark)').matches);

  document.documentElement.dataset.tema = oscuro ? 'oscuro' : 'claro';
})();

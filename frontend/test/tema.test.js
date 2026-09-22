import {
  almacenamiento, fijarModoDelSistema, restaurarAlmacenamiento, temaDelDocumento,
} from './entorno.js';
import {
  alCambiarTema, alternarTema, fijarTema, iniciarTema, preferenciaDeTema, temaEfectivo,
} from '../src/ui/tema.js';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { beforeEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Tests del tema.
 *
 * Lo que se prueba es la regla, no el pintado: que 'sistema' siga al sistema, que una
 * elección a mano deje de seguirlo, y que el interruptor rápido deje siempre una
 * preferencia explícita.
 */
describe('tema', () => {

  beforeEach(() => {
    restaurarAlmacenamiento();
    fijarModoDelSistema(false);
  });

  it('sin preferencia guardada manda el sistema', () => {
    assert.equal(preferenciaDeTema(), 'sistema');
    assert.equal(temaEfectivo(), 'claro');

    fijarModoDelSistema(true);
    assert.equal(temaEfectivo(), 'oscuro');
  });

  it('una eleccion a mano gana al sistema', () => {
    fijarModoDelSistema(true);
    fijarTema('claro');

    assert.equal(temaEfectivo(), 'claro');
    assert.equal(temaDelDocumento(), 'claro');
  });

  it('volver a «sistema» no deja nada guardado', () => {
    fijarTema('oscuro');
    assert.equal(almacenamiento.get('gastos.tema'), 'oscuro');

    fijarTema('sistema');
    // El valor por defecto vive en un solo sitio: la ausencia de clave.
    assert.equal(almacenamiento.has('gastos.tema'), false);
    assert.equal(preferenciaDeTema(), 'sistema');
  });

  it('un valor guardado que no reconocemos se trata como «sistema»', () => {
    almacenamiento.set('gastos.tema', 'fucsia');

    assert.equal(preferenciaDeTema(), 'sistema');
    assert.equal(temaEfectivo(), 'claro');
  });

  it('el interruptor rapido deja preferencia explicita, no «sistema»', () => {
    fijarModoDelSistema(true);
    // Arranca en oscuro porque el sistema lo dice, pero sin preferencia guardada.
    assert.equal(preferenciaDeTema(), 'sistema');

    alternarTema();

    assert.equal(preferenciaDeTema(), 'claro');
    assert.equal(temaEfectivo(), 'claro');
    // Y ya no se mueve aunque el sistema vuelva a cambiar.
    fijarModoDelSistema(false);
    fijarModoDelSistema(true);
    assert.equal(temaEfectivo(), 'claro');
  });

  it('con «sistema» puesto, cambiar el modo del sistema repinta', () => {
    iniciarTema();
    assert.equal(temaDelDocumento(), 'claro');

    fijarModoDelSistema(true);
    assert.equal(temaDelDocumento(), 'oscuro');
  });

  it('los controles desconectados se dan de baja solos', () => {
    let vivo = 0;
    let muerto = 0;
    alCambiarTema({ isConnected: true }, () => { vivo += 1; });
    alCambiarTema({ isConnected: false }, () => { muerto += 1; });

    fijarTema('oscuro');
    fijarTema('claro');

    assert.equal(vivo, 2);
    assert.equal(muerto, 0, 'un control que ya no esta en el documento no deberia repintarse');
  });

  /*
   * tema-inicial.js no puede importar nada: va en el <head> como script normal para
   * aplicar el tema antes de la primera pintura, así que la clave de almacenamiento está
   * escrita dos veces. Esto es lo que impide que se separen sin que nadie se entere: el
   * síntoma sería un fogonazo blanco al arrancar en modo oscuro, y nada más.
   */
  it('el script de arranque usa la misma clave que el modulo', async () => {
    const [arranque, modulo] = await Promise.all([
      readFile(path.join(RAIZ, 'tema-inicial.js'), 'utf8'),
      readFile(path.join(RAIZ, 'src/ui/tema.js'), 'utf8'),
    ]);

    const claveDe = (texto) => texto.match(/CLAVE = '([^']+)'/)?.[1];

    assert.equal(claveDe(arranque), 'gastos.tema');
    assert.equal(claveDe(modulo), claveDe(arranque));
  });

  it('el script de arranque y el modulo entienden los mismos valores', async () => {
    const arranque = await readFile(path.join(RAIZ, 'tema-inicial.js'), 'utf8');

    // Si el módulo añadiera un tercer tema, aquí habría que tocarlo también.
    assert.match(arranque, /=== 'oscuro'/);
    assert.match(arranque, /!== 'claro'/);
  });
});

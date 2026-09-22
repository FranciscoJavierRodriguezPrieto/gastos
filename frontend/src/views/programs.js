import { api, ApiError } from '../api/client.js';
import { campo, el, error as bloqueError, tarjeta, vacio } from '../ui/dom.js';
import { euros, porcentaje } from '../ui/format.js';

/**
 * Catálogo de programas de ayuda.
 *
 * Esta pantalla es la razón de ser del ADR-0004: las condiciones de una convocatoria se
 * corrigen aquí, no con un despliegue.
 *
 * Los límites vacíos significan «sin límite», y se envían como null. Es distinto de cero:
 * un programa sin tope de precio no es un programa con tope de cero euros.
 */
export async function vistaProgramas(raiz, cabecera, alVolver) {
  const pintar = async () => {
    raiz.replaceChildren(cabecera, el('p', { class: 'estado estado--cargando', text: 'Cargando...' }));
    try {
      const programas = await api.get('/mortgage/programs');
      raiz.replaceChildren(
        cabecera,
        aviso(),
        listado(programas, pintar, alVolver),
        formularioAlta(pintar),
      );
    } catch (e) {
      raiz.replaceChildren(cabecera, bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se pueden cargar los programas'));
    }
  };

  await pintar();
}

function aviso() {
  return el('div', { class: 'aviso' }, [
    el('p', { class: 'texto-apoyo', text:
      'El catálogo de partida recoge Mi Primera Vivienda según la Orden de 27 de julio de '
      + '2026 (BOCM nº 186, en vigor desde el 7 de agosto). Las normas cambian: revisad la '
      + 'fecha de la nota de cada programa, y contrastad cualquier cifra que añadáis a mano, '
      + 'antes de tomar una decisión.' }),
  ]);
}

/**
 * Botón de instalar el catálogo de partida.
 *
 * Se deshabilita y cuenta lo que está pasando mientras dura la llamada. No es un adorno:
 * la API duerme cuando no se usa y el primer despertar tarda casi un minuto, así que sin
 * esto el botón parecía roto y se acababa pulsando cuatro veces.
 *
 * El fallo se enseña en el propio botón en lugar de tirar la pantalla: el resto del
 * catálogo sigue siendo útil aunque esto no haya salido.
 */
function botonInstalar(repintar, { principal = false } = {}) {
  const aviso = el('div', { class: 'formulario__aviso' });
  const boton = el('button', {
    class: `boton ${principal ? 'boton--principal' : 'boton--sutil'}`,
    type: 'button',
  }, principal ? 'Instalar Mi Primera Vivienda (Madrid)' : 'Instalar catálogo vigente');

  const textoOriginal = boton.textContent;
  boton.addEventListener('click', async () => {
    aviso.replaceChildren();
    boton.disabled = true;
    boton.textContent = 'Instalando...';
    try {
      await api.post('/mortgage/programs/reference-catalog', {});
      await repintar();
    } catch (e) {
      aviso.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se ha podido instalar el catálogo'));
      boton.disabled = false;
      boton.textContent = textoOriginal;
    }
  });

  return { boton, aviso };
}

function listado(programas, repintar, alVolver) {
  if (programas.length === 0) {
    const { boton, aviso } = botonInstalar(repintar, { principal: true });
    return tarjeta('Programas', [
      vacio('Todavía no hay ningún programa',
        'Sin programas, el simulador aplica la financiación estándar del 80%.'),
      el('p', { class: 'texto-apoyo', text:
        'El catálogo de partida son las cuatro vías de Mi Primera Vivienda de la Comunidad '
        + 'de Madrid: hasta 40 años financia el 100%, hasta 45 el 95%, hasta 50 el 90%, y '
        + 'las familias con hijos el 100% sin tope de edad. Todas exigen primera vivienda y '
        + 'un precio máximo de 425.000 €.' }),
      el('p', { class: 'texto-apoyo texto-apoyo--tenue', text:
        'Se instalan como datos tuyos: luego puedes cambiarlos, desactivarlos o borrarlos. '
        + 'Pulsar dos veces no los duplica.' }),
      el('div', { class: 'invitacion__acciones' }, boton),
      aviso,
    ]);
  }

  const { boton: instalar, aviso: avisoInstalar } = botonInstalar(repintar);

  const filas = programas.map((programa) => el('li', { class: 'programa' }, [
    el('div', { class: 'programa__datos' }, [
      el('span', { class: 'programa__nombre' }, [
        programa.name,
        !programa.active
          ? el('span', { class: 'etiqueta etiqueta--apagado', text: 'desactivado' })
          : null,
      ]),
      el('span', { class: 'programa__meta', text: requisitos(programa) }),
      programa.sourceNote
        ? el('span', { class: 'programa__origen', text: programa.sourceNote })
        : null,
    ]),
    el('span', { class: 'programa__ltv amount', text: porcentaje(programa.maxLoanToValue) }),
    el('div', { class: 'programa__acciones' }, [
      el('button', {
        class: 'boton boton--sutil', type: 'button',
        onClick: async () => {
          await api.put(`/mortgage/programs/${programa.id}`, {
            ...aCuerpo(programa), active: !programa.active,
          });
          await repintar();
        },
      }, programa.active ? 'Desactivar' : 'Activar'),
      el('button', {
        class: 'boton boton--icono boton--peligro', type: 'button',
        'aria-label': `Borrar ${programa.name}`,
        onClick: async () => {
          if (!window.confirm(`¿Borrar «${programa.name}»?`)) {
            return;
          }
          await api.delete(`/mortgage/programs/${programa.id}`);
          await repintar();
        },
      }, '×'),
    ]),
  ]));

  const acciones = el('div', { class: 'tarjeta__acciones' }, [
    instalar,
    el('button', { class: 'boton boton--principal', type: 'button', onClick: alVolver },
      'Volver al simulador'),
  ]);

  // El aviso va en el cuerpo y no junto al botón: la cabecera de la tarjeta es una fila
  // y un error metido ahí descoloca el título.
  return tarjeta('Programas', [el('ul', { class: 'lista' }, filas), avisoInstalar], acciones);
}

function requisitos(programa) {
  const partes = [];
  partes.push(programa.maxApplicantAge ? `hasta ${programa.maxApplicantAge} años` : 'sin tope de edad');
  partes.push(programa.maxPropertyPrice
    ? `vivienda hasta ${euros(programa.maxPropertyPrice)}`
    : 'sin tope de precio');
  if (programa.requiresFirstHome) {
    partes.push('primera vivienda');
  }
  if (programa.requiresFamily) {
    partes.push('familias con hijos, numerosas o monoparentales');
  }
  return partes.join(' · ');
}

function aCuerpo(programa) {
  return {
    name: programa.name,
    maxLoanToValue: programa.maxLoanToValue,
    maxPropertyPrice: programa.maxPropertyPrice ?? null,
    maxApplicantAge: programa.maxApplicantAge ?? null,
    requiresFirstHome: programa.requiresFirstHome,
    // Sin esto, activar o desactivar un programa familiar desde la lista le quitaría el
    // requisito y pasaría a aplicarse a cualquiera.
    requiresFamily: Boolean(programa.requiresFamily),
    active: programa.active,
    sourceNote: programa.sourceNote ?? '',
  };
}

function formularioAlta(repintar) {
  const nombre = campo('nombre', 'Nombre del programa', { required: true, maxlength: 80 });
  const ltv = campo('ltvPrograma', 'Financia hasta el (%)', {
    type: 'number', required: true, min: '1', max: '100', step: '0.01', value: '95',
  });
  const precioMax = campo('precioMax', 'Precio máximo de la vivienda (€)', {
    type: 'number', min: '1', step: '0.01', placeholder: 'vacío = sin límite',
  });
  const edadMax = campo('edadMax', 'Edad máxima', {
    type: 'number', min: '18', max: '120', placeholder: 'vacío = sin límite',
  });
  const origen = campo('origen', 'De dónde salen estas cifras', {
    maxlength: 300, placeholder: 'Ej.: BOCM de ... , pendiente de verificar',
  });

  const primeraVivienda = el('label', { class: 'casilla' }, [
    el('input', { type: 'checkbox', checked: true }),
    el('span', { text: 'Exige que sea la primera vivienda' }),
  ]);
  const soloFamilias = el('label', { class: 'casilla' }, [
    el('input', { type: 'checkbox' }),
    el('span', { text: 'Sólo para familias con hijos, numerosas o monoparentales' }),
  ]);
  const activo = el('label', { class: 'casilla' }, [
    el('input', { type: 'checkbox' }),
    el('span', { text: 'Activarlo ya (sólo si has verificado las cifras)' }),
  ]);

  const aviso = el('div', { class: 'formulario__aviso' });

  const formulario = el('form', {
    class: 'formulario formulario--linea',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      try {
        await api.post('/mortgage/programs', {
          name: nombre.control.value.trim(),
          maxLoanToValue: Number(ltv.control.value),
          // Vacío es null, no cero: «sin límite» es un estado distinto.
          maxPropertyPrice: precioMax.control.value === '' ? null : Number(precioMax.control.value),
          maxApplicantAge: edadMax.control.value === '' ? null : Number(edadMax.control.value),
          requiresFirstHome: primeraVivienda.querySelector('input').checked,
          requiresFamily: soloFamilias.querySelector('input').checked,
          active: activo.querySelector('input').checked,
          sourceNote: origen.control.value.trim(),
        });
        await repintar();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
      }
    },
  }, [
    nombre, ltv, precioMax, edadMax, origen,
    el('div', { class: 'formulario__casillas' }, [primeraVivienda, soloFamilias, activo]),
    el('button', { class: 'boton boton--principal', type: 'submit' }, 'Añadir programa'),
    aviso,
  ]);

  return tarjeta('Nuevo programa', formulario);
}

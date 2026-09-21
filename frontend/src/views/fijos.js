import { api, ApiError } from '../api/client.js';
import {
  campo, cargando, el, error as bloqueError, seleccion, tarjeta, vacio,
} from '../ui/dom.js';
import { euros, nombreMes } from '../ui/format.js';

/**
 * Gastos fijos: el alquiler, el teléfono, la cuota del gimnasio.
 *
 * Lo que se gestiona aquí son **plantillas**, no gastos. Cada mes que se abre en la
 * pantalla de Gastos, la plantilla genera su gasto de ese mes, y a partir de ahí es un
 * gasto normal que se edita y se borra desde allí.
 *
 * Esa distinción es lo único que hay que explicar bien, porque de ella salen los dos
 * comportamientos que sorprenden si no se cuentan:
 *
 *  - Cambiar el importe aquí **no toca los meses ya generados**. Si sube el alquiler,
 *    lo pagado sigue siendo lo pagado.
 *  - Para corregir un mes concreto —la luz de enero fue más cara— se edita el gasto en
 *    Gastos, no la plantilla.
 */

let catalogo = null;

export function tarjetaGastosFijos({ alCambiar } = {}) {
  const cuerpo = el('div', { class: 'fijos' }, cargando());

  const recargar = async () => {
    try {
      if (!catalogo) {
        catalogo = await api.get('/expenses/catalog');
      }
      const fijos = await api.get('/fixed-expenses');
      cuerpo.replaceChildren(
        fijos.length === 0 ? sinFijos() : listado(fijos, recargar, alCambiar),
        formularioAlta(recargar, alCambiar),
      );
    } catch (e) {
      cuerpo.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se pueden cargar los gastos fijos'));
    }
  };
  recargar();

  return tarjeta('Gastos fijos', cuerpo);
}

function sinFijos() {
  return vacio(
    'Todavía no hay gastos fijos',
    'Son los que se repiten cada mes: alquiler, teléfono, seguros. Se anotan una vez y '
    + 'aparecen solos en todos los meses.');
}

function listado(fijos, recargar, alCambiar) {
  const activos = fijos.filter((f) => f.active);
  const total = activos.reduce((suma, f) => suma + Number(f.amount), 0);

  const filas = fijos.map((fijo) => fila(fijo, recargar, alCambiar));

  return el('div', {}, [
    el('ul', { class: 'lista' }, filas),
    activos.length > 0
      ? el('p', { class: 'fijos__total' }, [
        el('span', { text: `${activos.length} gasto${activos.length === 1 ? '' : 's'} fijo${activos.length === 1 ? '' : 's'} al mes` }),
        el('span', { class: 'amount', text: euros(total) }),
      ])
      : null,
  ]);
}

function fila(fijo, recargar, alCambiar) {
  const detalle = [
    fijo.categoryLabel,
    ` · día ${fijo.dayOfMonth}`,
    fijo.active ? '' : ` · sin efecto desde ${nombreMes(fijo.endMonth)}`,
  ].join('');

  return el('li', { class: `movimiento${fijo.active ? '' : ' movimiento--inactivo'}` }, [
    el('div', { class: 'movimiento__principal' }, [
      el('span', { class: 'movimiento__concepto' }, [
        fijo.description,
        fijo.active ? null : el('span', { class: 'etiqueta', text: 'dado de baja' }),
      ]),
      el('span', { class: 'movimiento__meta', text: detalle }),
    ]),
    el('span', { class: 'movimiento__importe amount amount--negative', text: euros(fijo.amount) }),
    el('div', { class: 'fijos__acciones' }, [
      botonEditar(fijo, recargar, alCambiar),
      fijo.active
        ? accion('Dar de baja', 'boton--sutil',
          `/fixed-expenses/${fijo.id}/discontinue`, recargar, alCambiar)
        : accion('Reactivar', 'boton--sutil',
          `/fixed-expenses/${fijo.id}/reactivate`, recargar, alCambiar),
      botonBorrar(fijo, recargar, alCambiar),
    ]),
  ]);
}

function accion(etiqueta, clase, ruta, recargar, alCambiar) {
  const boton = el('button', { class: `boton ${clase}`, type: 'button' }, etiqueta);
  boton.addEventListener('click', async () => {
    boton.disabled = true;
    try {
      await api.post(ruta, undefined);
      await recargar();
      await alCambiar?.();
    } catch {
      boton.disabled = false;
      boton.textContent = 'No se ha podido';
    }
  });
  return boton;
}

/**
 * Borrar la plantilla.
 *
 * Se avisa de qué hace exactamente, porque «borrar» aquí no significa lo que parece: los
 * gastos que ya generó se quedan, y son dinero que de verdad se pagó. Quien sólo quiera
 * dejar de pagarlo a partir de ahora tiene «Dar de baja» al lado.
 */
function botonBorrar(fijo, recargar, alCambiar) {
  const boton = el('button', {
    class: 'boton boton--icono boton--peligro',
    type: 'button',
    'aria-label': `Borrar el gasto fijo ${fijo.description}`,
    title: 'Borra la plantilla. Los gastos ya anotados en meses anteriores se conservan.',
  }, '×');

  boton.addEventListener('click', async () => {
    const confirmado = window.confirm(
      `¿Borrar el gasto fijo "${fijo.description}"?\n\n`
      + 'Dejará de generarse. Los gastos que ya anotó en meses anteriores se conservan: '
      + 'son dinero que se pagó.');
    if (!confirmado) {
      return;
    }
    boton.disabled = true;
    try {
      await api.delete(`/fixed-expenses/${fijo.id}`);
      await recargar();
      await alCambiar?.();
    } catch {
      boton.disabled = false;
    }
  });

  return boton;
}

function botonEditar(fijo, recargar, alCambiar) {
  const boton = el('button', { class: 'boton boton--sutil', type: 'button' }, 'Editar');

  boton.addEventListener('click', () => {
    const contenedor = boton.closest('.movimiento');
    contenedor.replaceWith(formularioEdicion(fijo, recargar, alCambiar));
  });

  return boton;
}

function camposDe(fijo) {
  const descripcion = campo('fijoDescripcion', 'Concepto', {
    required: true, maxlength: 140, value: fijo?.description ?? '',
  });
  const importe = campo('fijoImporte', 'Importe (€)', {
    type: 'number', required: true, min: '0.01', step: '0.01', inputmode: 'decimal',
    value: fijo?.amount ?? '',
  });
  const categoria = seleccion('fijoCategoria', 'Categoría',
    catalogo.categories.map((c) => ({ value: c.value, label: c.label })),
    fijo?.category ?? 'VIVIENDA');
  // Hasta 28 y no 31: un cargo el 31 no existe en febrero.
  const dia = campo('fijoDia', 'Día de cargo', {
    type: 'number', required: true, min: '1', max: '28', inputmode: 'numeric',
    value: fijo?.dayOfMonth ?? 1,
  });

  return { descripcion, importe, categoria, dia };
}

function cuerpoDe({ descripcion, importe, categoria, dia }) {
  return {
    description: descripcion.control.value.trim(),
    amount: Number(importe.control.value),
    category: categoria.control.value,
    dayOfMonth: Number(dia.control.value),
  };
}

function formularioEdicion(fijo, recargar, alCambiar) {
  const campos = camposDe(fijo);
  const aviso = el('div', { class: 'formulario__aviso' });

  return el('form', {
    class: 'formulario formulario--linea',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      try {
        await api.put(`/fixed-expenses/${fijo.id}`, cuerpoDe(campos));
        await recargar();
        await alCambiar?.();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
      }
    },
  }, [
    campos.descripcion, campos.importe, campos.categoria, campos.dia,
    el('button', { class: 'boton boton--principal', type: 'submit' }, 'Guardar'),
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: () => recargar(),
    }, 'Cancelar'),
    el('p', { class: 'texto-apoyo', text:
      'El cambio vale a partir del mes que viene. Este mes, si ya está anotado, conserva '
      + 'su importe: para cambiarlo, edítalo en la lista de movimientos.' }),
    aviso,
  ]);
}

function formularioAlta(recargar, alCambiar) {
  const campos = camposDe(null);
  const aviso = el('div', { class: 'formulario__aviso' });

  return el('form', {
    class: 'formulario formulario--linea',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      try {
        // Sin startMonth: el servidor lo pone en el mes en curso, que es lo que se
        // espera al dar de alta algo que ya se está pagando.
        await api.post('/fixed-expenses', cuerpoDe(campos));
        await recargar();
        await alCambiar?.();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
      }
    },
  }, [
    campos.descripcion, campos.importe, campos.categoria, campos.dia,
    el('button', { class: 'boton boton--principal', type: 'submit' }, 'Añadir gasto fijo'),
    el('p', { class: 'texto-apoyo', text:
      'Empieza a contar este mes y aparecerá solo en los siguientes.' }),
    aviso,
  ]);
}

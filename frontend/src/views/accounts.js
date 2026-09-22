import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import {
  campo, cargando, el, error as bloqueError, seleccion, tarjeta, vacio,
} from '../ui/dom.js';
import { euros } from '../ui/format.js';

/**
 * Mis Cuentas.
 *
 * Las operaciones sobre el saldo son tres botones distintos —ingreso, cargo y
 * conciliación— y no un campo editable, porque en la API también son tres verbos con
 * significado contable distinto. Conciliar no es lo mismo que ingresar la diferencia.
 */

const TIPOS = [
  { value: 'CORRIENTE', label: 'Cuenta corriente' },
  { value: 'AHORRO', label: 'Cuenta de ahorro' },
  { value: 'EFECTIVO', label: 'Efectivo' },
  { value: 'INVERSION', label: 'Inversión' },
  { value: 'TARJETA_CREDITO', label: 'Tarjeta de crédito' },
];

export async function vistaCuentas(contenedor) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  await pintar(raiz);
}

async function pintar(raiz) {
  raiz.replaceChildren(cabecera(), cargando());

  try {
    const [cuentas, total, miembros] = await Promise.all([
      api.get('/accounts'),
      api.get('/accounts/total-balance'),
      api.get('/auth/members'),
    ]);

    raiz.replaceChildren(
      cabecera(),
      el('div', { class: 'rejilla-kpi' }, [
        el('div', { class: 'kpi kpi--destacado' }, [
          el('span', { class: 'kpi__etiqueta', text: 'Patrimonio del hogar' }),
          el('span', { class: 'kpi__valor amount', text: euros(total.totalBalance) }),
        ]),
      ]),
      listado(cuentas, raiz),
      formularioAlta(miembros, raiz),
    );
  } catch (e) {
    raiz.replaceChildren(
      cabecera(),
      bloqueError(e instanceof ApiError ? e.userMessage : 'No se pueden cargar las cuentas'),
    );
  }
}

function cabecera() {
  return el('header', { class: 'vista__cabecera' }, [
    el('h1', { class: 'vista__titulo', text: 'Mis Cuentas' }),
  ]);
}

function listado(cuentas, raiz) {
  if (cuentas.length === 0) {
    return tarjeta('Cuentas', vacio(
      'Todavía no hay ninguna cuenta',
      'Da de alta la primera con el formulario de abajo.'));
  }

  return tarjeta('Cuentas', el('ul', { class: 'lista' },
    cuentas.map((cuenta) => filaCuenta(cuenta, raiz))));
}

function filaCuenta(cuenta, raiz) {
  const saldo = Number(cuenta.balance);
  const etiquetaTipo = TIPOS.find((t) => t.value === cuenta.type)?.label ?? cuenta.type;

  return el('li', { class: 'cuenta' }, [
    el('div', { class: 'cuenta__datos' }, [
      el('span', { class: 'cuenta__alias', text: cuenta.alias }),
      el('span', { class: 'cuenta__meta' }, [
        cuenta.bankName,
        ' · ',
        etiquetaTipo,
        cuenta.ownership === 'CONJUNTA'
          ? el('span', { class: 'etiqueta etiqueta--conjunta', text: 'conjunta' })
          : null,
      ]),
    ]),
    el('span', {
      class: `cuenta__saldo amount ${saldo < 0 ? 'amount--negative' : 'amount--positive'}`,
      text: euros(cuenta.balance),
    }),
    operaciones(cuenta, raiz),
  ]);
}

function operaciones(cuenta, raiz) {
  const ejecutar = async (ruta, metodo, textoPregunta) => {
    const valor = window.prompt(textoPregunta);
    if (valor === null || valor.trim() === '') {
      return;
    }
    const importe = Number(valor.replace(',', '.'));
    if (!Number.isFinite(importe)) {
      window.alert('Eso no es un importe');
      return;
    }
    try {
      await api[metodo](`/accounts/${cuenta.id}${ruta}`, { amount: importe });
      await pintar(raiz);
    } catch (e) {
      window.alert(e instanceof ApiError ? e.userMessage : 'No se ha podido guardar');
    }
  };

  return el('div', { class: 'cuenta__acciones' }, [
    el('button', {
      class: 'boton boton--sutil', type: 'button', title: 'Registrar un ingreso',
      onClick: () => ejecutar('/credit', 'post', `Ingreso en ${cuenta.alias} (€)`),
    }, 'Ingreso'),
    el('button', {
      class: 'boton boton--sutil', type: 'button', title: 'Registrar un cargo',
      onClick: () => ejecutar('/debit', 'post', `Cargo en ${cuenta.alias} (€)`),
    }, 'Cargo'),
    el('button', {
      class: 'boton boton--sutil', type: 'button',
      title: 'Ajustar el saldo al del extracto bancario',
      onClick: () => ejecutar('/balance', 'put', `Saldo real de ${cuenta.alias} (€)`),
    }, 'Conciliar'),
  ]);
}

function formularioAlta(miembros, raiz) {
  const alias = campo('alias', 'Nombre', { required: true, maxlength: 60 });
  const banco = campo('banco', 'Banco', { required: true, maxlength: 60 });
  const tipo = seleccion('tipo', 'Tipo', TIPOS, 'CORRIENTE');
  const titularidad = seleccion('titularidad', 'Titularidad', [
    { value: 'INDIVIDUAL', label: 'Individual' },
    { value: 'CONJUNTA', label: 'Conjunta' },
  ], 'INDIVIDUAL');
  const saldo = campo('saldo', 'Saldo actual (€)', {
    type: 'number', required: true, step: '0.01', value: '0', inputmode: 'decimal',
  });

  const aviso = el('div', { class: 'formulario__aviso' });

  const formulario = el('form', {
    class: 'formulario formulario--linea',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();

      // Una cuenta conjunta necesita dos titulares, y el dominio lo rechaza si no los
      // hay. Se avisa aquí para no gastar una ida y vuelta al servidor.
      const conjunta = titularidad.control.value === 'CONJUNTA';
      if (conjunta && miembros.length < 2) {
        aviso.replaceChildren(bloqueError(
          'Una cuenta conjunta necesita dos titulares, y en el hogar sólo hay uno.'));
        return;
      }

      const titulares = conjunta
        ? miembros.map((m) => m.id)
        : [session.user?.id ?? miembros[0].id];

      try {
        await api.post('/accounts', {
          alias: alias.control.value.trim(),
          bankName: banco.control.value.trim(),
          type: tipo.control.value,
          ownership: titularidad.control.value,
          holders: titulares,
          initialBalance: Number(saldo.control.value || 0),
        });
        await pintar(raiz);
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se ha podido guardar'));
      }
    },
  }, [
    alias, banco, tipo, titularidad, saldo,
    el('button', { class: 'boton boton--principal', type: 'submit' }, 'Añadir cuenta'),
    aviso,
  ]);

  return tarjeta('Nueva cuenta', formulario);
}

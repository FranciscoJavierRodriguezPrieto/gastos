import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import { campo, cargando, el, error as bloqueError, tarjeta } from '../ui/dom.js';
import { euros } from '../ui/format.js';

/**
 * Tu cuenta: quién eres, quién más está en el hogar y cambiar la contraseña.
 *
 * No es una pestaña principal porque no se usa a diario; se llega desde el pie de la
 * navegación.
 */
export async function vistaCuenta(contenedor) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  raiz.replaceChildren(cabecera(), cargando());

  try {
    const [yo, miembros] = await Promise.all([
      api.get('/auth/me'),
      api.get('/auth/members'),
    ]);
    session.setUser(yo);

    raiz.replaceChildren(
      cabecera(),
      datosPersonales(yo),
      listaMiembros(miembros, yo),
      formularioContrasena(),
    );
  } catch (e) {
    raiz.replaceChildren(cabecera(), bloqueError(
      e instanceof ApiError ? e.userMessage : 'No se pueden cargar tus datos'));
  }
}

function cabecera() {
  return el('header', { class: 'vista__cabecera' }, [
    el('h1', { class: 'vista__titulo', text: 'Tu cuenta' }),
  ]);
}

function datosPersonales(yo) {
  return tarjeta('Tus datos', el('ul', { class: 'desglose' }, [
    linea('Nombre', yo.displayName),
    linea('Correo', yo.email),
    linea('Papel en el hogar', yo.role === 'OWNER' ? 'Titular' : 'Miembro'),
    linea('Ingresos netos declarados', euros(yo.monthlyNetIncome)),
  ]));
}

function linea(etiqueta, valor) {
  return el('li', { class: 'desglose__linea' }, [
    el('span', { text: etiqueta }),
    el('span', { text: String(valor) }),
  ]);
}

function listaMiembros(miembros, yo) {
  const filas = miembros.map((miembro) => el('li', { class: 'movimiento' }, [
    el('div', { class: 'movimiento__principal' }, [
      el('span', { class: 'movimiento__concepto' }, [
        miembro.displayName,
        miembro.id === yo.id ? el('span', { class: 'etiqueta', text: 'tú' }) : null,
      ]),
      el('span', { class: 'movimiento__meta',
        text: `${miembro.email} · ${miembro.role === 'OWNER' ? 'Titular' : 'Miembro'}` }),
    ]),
    el('span', { class: 'movimiento__importe amount', text: euros(miembro.monthlyNetIncome) }),
  ]));

  const ayuda = miembros.length < 2 && yo.role === 'OWNER'
    ? el('p', { class: 'texto-apoyo', text:
      'Todavía sois uno. Para dar de alta al otro conviviente hace falta llamar a '
      + 'POST /api/v1/auth/members; la pantalla para hacerlo llegará más adelante.' })
    : null;

  return tarjeta('Miembros del hogar', [el('ul', { class: 'lista' }, filas), ayuda]);
}

function formularioContrasena() {
  const actual = campo('actual', 'Contraseña actual', {
    type: 'password', required: true, autocomplete: 'current-password',
  });
  const nueva = campo('nueva', 'Contraseña nueva', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });
  const repetir = campo('repetir', 'Repite la nueva', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });

  const aviso = el('div', { class: 'formulario__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' },
    'Cambiar contraseña');

  const formulario = el('form', {
    class: 'formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();

      if (nueva.control.value.length < 12) {
        aviso.replaceChildren(bloqueError('La contraseña nueva debe tener al menos 12 caracteres'));
        return;
      }
      if (nueva.control.value !== repetir.control.value) {
        aviso.replaceChildren(bloqueError('Las dos contraseñas no coinciden'));
        return;
      }

      boton.disabled = true;
      try {
        await api.post('/auth/password', {
          currentPassword: actual.control.value,
          newPassword: nueva.control.value,
        });
        // El servidor revoca las demas sesiones, incluida la del token de refresco de
        // este navegador, asi que hay que volver a entrar.
        aviso.replaceChildren(el('p', { class: 'estado', text:
          'Contraseña cambiada. Se han cerrado las sesiones abiertas; vuelve a entrar.' }));
        setTimeout(() => {
          session.clear();
        }, 2500);
      } catch (e) {
        const mensaje = e instanceof ApiError && e.status === 401
          ? 'La contraseña actual no es correcta'
          : (e instanceof ApiError ? e.userMessage : 'No se ha podido cambiar');
        aviso.replaceChildren(bloqueError(mensaje));
        boton.disabled = false;
      }
    },
  }, [actual, nueva, repetir, aviso, boton]);

  return tarjeta('Cambiar la contraseña', formulario);
}

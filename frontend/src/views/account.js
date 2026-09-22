import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import { PasskeyError, registrarPasskey, soportaPasskeys } from '../api/webauthn.js';
import { tarjetaInvitacion } from './invitacion.js';
import { alCambiarTema, fijarTema, preferenciaDeTema } from '../ui/tema.js';
import { campo, cargando, el, error as bloqueError, tarjeta } from '../ui/dom.js';
import { euros, fechaHora } from '../ui/format.js';

/**
 * Tu cuenta: quién eres, quién más está en el hogar, el aspecto y la contraseña.
 *
 * No es una pestaña principal porque no se usa a diario; se llega desde el pie de la
 * navegación en escritorio y desde la última pestaña en móvil.
 *
 * @param {object} opciones
 * @param {Function} opciones.alSalir  cerrar sesión lo resuelve app.js, que es quien
 *   sabe repintar el armazón; aquí sólo se ofrece el botón. Pasarlo como parámetro
 *   evita que este módulo importe app.js, que a su vez importa éste.
 */
export async function vistaCuenta(contenedor, { alSalir } = {}) {
  const raiz = el('div', { class: 'vista' });
  contenedor.replaceChildren(raiz);
  raiz.replaceChildren(cabecera(), cargando());

  try {
    const [yo, miembros] = await Promise.all([
      api.get('/auth/me'),
      api.get('/auth/members'),
    ]);
    session.setUser(yo);

    // El filtro no es cosmético: `replaceChildren` convierte null en el texto «null» y
    // lo pinta. Aquí hay dos tarjetas que pueden no estar —la de invitar, que sólo ve el
    // titular, y la de cerrar sesión—, así que sin esto a tu pareja le salía un «null»
    // suelto en mitad de la pantalla.
    raiz.replaceChildren(...[
      cabecera(),
      datosPersonales(yo),
      listaMiembros(miembros, yo),
      tarjetaInvitacion(yo),
      tarjetaAspecto(),
      tarjetaPasskeys(),
      formularioContrasena(),
      alSalir ? tarjetaSesion(alSalir) : null,
    ].filter(Boolean));
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
      'Todavía sois uno. Invita al otro conviviente desde aquí abajo.' })
    : null;

  return tarjeta('Miembros del hogar', [el('ul', { class: 'lista' }, filas), ayuda]);
}

const OPCIONES_DE_TEMA = [
  { valor: 'sistema', etiqueta: 'El del sistema', icono: '◐' },
  { valor: 'claro', etiqueta: 'Claro', icono: '☀' },
  { valor: 'oscuro', etiqueta: 'Oscuro', icono: '☾' },
];

/**
 * Aspecto: claro, oscuro o el del sistema.
 *
 * Son botones de radio de verdad, no botones normales con apariencia de elegidos. Con
 * radios el teclado los recorre con las flechas, el lector de pantalla dice cuál está
 * marcado y el navegador se encarga de que sólo haya uno: nada de eso sale gratis si se
 * imita con <button> y una clase.
 *
 * «El del sistema» es el valor de partida y va el primero a propósito: si el teléfono
 * está en modo noche, la aplicación ya sale oscura sin tocar nada.
 */
function tarjetaAspecto() {
  const elegida = preferenciaDeTema();

  const opciones = OPCIONES_DE_TEMA.map(({ valor, etiqueta, icono }) => {
    const radio = el('input', {
      type: 'radio',
      name: 'tema',
      id: `tema-${valor}`,
      value: valor,
      checked: valor === elegida,
      onChange: () => fijarTema(valor),
    });
    return el('label', { class: 'opcion-tema', for: `tema-${valor}` }, [
      radio,
      el('span', { class: 'opcion-tema__icono', 'aria-hidden': 'true', text: icono }),
      el('span', { text: etiqueta }),
    ]);
  });

  const grupo = el('div', { class: 'opciones-tema', role: 'radiogroup',
    'aria-label': 'Tema de la aplicación' }, opciones);

  // El interruptor rápido de la navegación cambia el tema sin pasar por aquí; si esta
  // pantalla está abierta, hay que mover la marca o enseñaría un valor que ya no es.
  alCambiarTema(grupo, () => {
    const actual = preferenciaDeTema();
    for (const radio of grupo.querySelectorAll('input')) {
      radio.checked = radio.value === actual;
    }
  });

  return tarjeta('Aspecto', [
    grupo,
    el('p', { class: 'texto-apoyo', text:
      'Se recuerda en este navegador, no en tu cuenta: puedes tener el móvil en oscuro y '
      + 'el ordenador en claro.' }),
  ]);
}

/**
 * Cerrar sesión.
 *
 * En escritorio el botón está también en el pie de la navegación. Aquí hace falta
 * porque en móvil esa barra no existe, y sin esto no había forma de salir desde el
 * teléfono.
 */
function tarjetaSesion(alSalir) {
  return tarjeta('Sesión', [
    el('p', { class: 'texto-apoyo', text:
      'Al salir se revoca la sesión en el servidor, no sólo en este navegador.' }),
    el('button', { class: 'boton boton--sutil', type: 'button', onClick: alSalir },
      'Cerrar sesión'),
  ]);
}

/**
 * Passkeys registradas.
 *
 * Se pinta con su propia carga en lugar de esperar al resto de la pantalla: es una lista
 * que cambia sola (al dar de alta, al borrar) y no tiene sentido que un fallo suyo deje
 * sin ver los datos personales.
 */
function tarjetaPasskeys() {
  const cuerpo = el('div', { class: 'passkeys' }, cargando());

  const recargar = async () => {
    try {
      const passkeys = await api.get('/auth/passkeys');
      cuerpo.replaceChildren(
        passkeys.length === 0 ? sinPasskeys() : el('ul', { class: 'lista' },
          passkeys.map((passkey) => filaPasskey(passkey, recargar))),
        altaDePasskey(recargar),
      );
    } catch (e) {
      cuerpo.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se pueden cargar tus passkeys'));
    }
  };
  recargar();

  return tarjeta('Passkeys', cuerpo);
}

function sinPasskeys() {
  return el('p', { class: 'texto-apoyo', text:
    'Todavía no tienes ninguna. Una passkey te deja entrar con la huella, la cara o el '
    + 'PIN del dispositivo, sin escribir la contraseña. Tu contraseña sigue funcionando '
    + 'igual: la passkey no la sustituye, se suma.' });
}

function filaPasskey(passkey, alCambiar) {
  const detalle = [
    passkey.lastUsedAt ? `Usada el ${fechaHora(passkey.lastUsedAt)}` : 'Sin usar todavía',
    // Si sólo vive en ese aparato, perderlo es perder la passkey. Conviene saberlo antes
    // de borrar la otra.
    passkey.syncedToCloud ? 'Copiada en tu cuenta del dispositivo' : 'Sólo en este dispositivo',
  ].join(' · ');

  const borrar = el('button', { class: 'boton boton--sutil', type: 'button' }, 'Quitar');
  borrar.addEventListener('click', async () => {
    borrar.disabled = true;
    try {
      await api.delete(`/auth/passkeys/${passkey.id}`);
      await alCambiar();
    } catch {
      borrar.disabled = false;
      borrar.textContent = 'No se ha podido quitar';
    }
  });

  return el('li', { class: 'movimiento' }, [
    el('div', { class: 'movimiento__principal' }, [
      el('span', { class: 'movimiento__concepto', text: passkey.label }),
      el('span', { class: 'movimiento__meta', text: detalle }),
    ]),
    borrar,
  ]);
}

function altaDePasskey(alCambiar) {
  if (!soportaPasskeys()) {
    return el('p', { class: 'texto-apoyo', text:
      'Este navegador no admite passkeys. Prueba desde el móvil o desde un navegador '
      + 'actualizado, y recuerda que fuera de localhost hace falta HTTPS.' });
  }

  const nombre = campo('etiquetaPasskey', 'Nombre del dispositivo', {
    required: true, maxlength: 60, placeholder: 'iPhone de Javi',
  });
  const aviso = el('div', { class: 'formulario__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' },
    'Añadir passkey');

  return el('form', {
    class: 'formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();

      const etiqueta = nombre.control.value.trim();
      if (!etiqueta) {
        aviso.replaceChildren(bloqueError('Ponle un nombre para reconocerlo luego'));
        nombre.control.focus();
        return;
      }

      boton.disabled = true;
      boton.textContent = 'Esperando al dispositivo...';
      try {
        await registrarPasskey(etiqueta);
        nombre.control.value = '';
        await alCambiar();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof PasskeyError ? e.message
            : (e instanceof ApiError ? e.userMessage : 'No se ha podido añadir la passkey')));
      } finally {
        boton.disabled = false;
        boton.textContent = 'Añadir passkey';
      }
    },
  }, [nombre, aviso, boton]);
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

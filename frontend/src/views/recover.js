import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import { campo, el, error as bloqueError } from '../ui/dom.js';

/**
 * Recuperar el acceso.
 *
 * Dos pantallas que comparten marco:
 *
 *  - Pedir el enlace, desde el formulario de acceso.
 *  - Fijar la contraseña nueva, a la que se llega desde el enlace del correo.
 *
 * El mensaje de confirmación es **el mismo exista o no el correo**, porque así responde
 * el servidor. Si esta pantalla dijera «no encontramos esa cuenta», echaría por tierra
 * todo el cuidado que se ha puesto en el servidor para no delatar qué cuentas hay.
 */

function marca(subtitulo) {
  return el('div', { class: 'login__marca' }, [
    el('img', { class: 'login__logo', src: '/icons/icon-192.png', alt: '', width: 56, height: 56 }),
    el('h1', { class: 'login__titulo', text: 'Gastos' }),
    el('p', { class: 'login__subtitulo', text: subtitulo }),
  ]);
}

/** Formulario para pedir el enlace. */
export function vistaOlvide(contenedor, { alVolver }) {
  const marco = el('div', { class: 'login' });
  contenedor.replaceChildren(marco);

  const correo = campo('email', 'Correo', {
    type: 'email', required: true, autocomplete: 'username', inputmode: 'email',
  });
  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' },
    'Enviarme el enlace');

  const formulario = el('form', {
    class: 'login__formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      boton.disabled = true;
      boton.textContent = 'Enviando...';
      try {
        await api.post('/auth/forgot-password',
          { email: correo.control.value.trim() }, { autenticada: false });
      } catch {
        // Da igual: la respuesta es siempre la misma, y un fallo de red tampoco debe
        // revelar nada. Se muestra la confirmación igualmente.
      }
      marco.replaceChildren(confirmacion(alVolver));
    },
  }, [
    marca('Recuperar el acceso'),
    correo,
    el('p', { class: 'login__pista', text:
      'Si ese correo tiene cuenta, te llegará un enlace para poner una contraseña nueva. '
      + 'Caduca en 30 minutos y sólo sirve una vez.' }),
    aviso,
    boton,
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: alVolver,
    }, 'Volver'),
  ]);

  marco.replaceChildren(formulario);
}

function confirmacion(alVolver) {
  return el('div', { class: 'login__formulario' }, [
    marca('Revisa tu correo'),
    el('p', { class: 'texto-apoyo', text:
      'Si ese correo tiene cuenta, el enlace ya está en camino. Mira también la carpeta '
      + 'de no deseado.' }),
    el('button', { class: 'boton boton--principal', type: 'button', onClick: alVolver },
      'Volver al acceso'),
  ]);
}

/** Pantalla de contraseña nueva, a la que llega el enlace del correo. */
export function vistaRestablecer(contenedor, token, { alEntrar, alVolver }) {
  const marco = el('div', { class: 'login' });
  contenedor.replaceChildren(marco);

  if (!token) {
    marco.replaceChildren(el('div', { class: 'login__formulario' }, [
      marca('Enlace incompleto'),
      bloqueError('Este enlace no trae el código. Pide uno nuevo desde la pantalla de acceso.'),
      el('button', { class: 'boton boton--principal', type: 'button', onClick: alVolver },
        'Volver al acceso'),
    ]));
    return;
  }

  const clave = campo('password', 'Contraseña nueva', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });
  const repetir = campo('password2', 'Repite la contraseña', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });
  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' },
    'Guardar y entrar');

  const formulario = el('form', {
    class: 'login__formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();

      if (clave.control.value.length < 12) {
        aviso.replaceChildren(bloqueError('La contraseña debe tener al menos 12 caracteres'));
        return;
      }
      if (clave.control.value !== repetir.control.value) {
        aviso.replaceChildren(bloqueError('Las dos contraseñas no coinciden'));
        repetir.control.value = '';
        repetir.control.focus();
        return;
      }

      boton.disabled = true;
      boton.textContent = 'Guardando...';
      try {
        const sesion = await api.post('/auth/reset-password', {
          token,
          newPassword: clave.control.value,
        }, { autenticada: false });
        session.start(sesion);
        await alEntrar();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se puede conectar con el servidor'));
        boton.disabled = false;
        boton.textContent = 'Guardar y entrar';
      }
    },
  }, [
    marca('Elige una contraseña nueva'),
    clave,
    repetir,
    el('p', { class: 'login__pista', text:
      'Mínimo 12 caracteres. Mejor una frase larga y fácil de recordar que una '
      + 'contraseña corta con símbolos.' }),
    el('p', { class: 'login__pista', text:
      'Al guardarla se cerrarán todas las sesiones abiertas, también en otros '
      + 'dispositivos.' }),
    aviso,
    boton,
  ]);

  marco.replaceChildren(formulario);
}

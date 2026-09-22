import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import { PasskeyError, entrarConPasskey, soportaPasskeys } from '../api/webauthn.js';
import { campo, el, error as bloqueError } from '../ui/dom.js';

/**
 * Pantalla de entrada.
 *
 * Tiene dos caras según el estado de la instalación, que se consulta a /auth/status:
 *
 *  - Instalación nueva: formulario de alta del hogar. Sólo aparece una vez en la vida de
 *    la instalación, porque no hay registro abierto.
 *  - Instalación ya montada: formulario de acceso.
 *
 * Al usuario no se le pide que elija: la pantalla ya sabe cuál toca.
 */
export async function vistaLogin(contenedor, { alEntrar, alOlvidar, alUnirse }) {
  const marco = el('div', { class: 'login' });
  contenedor.replaceChildren(marco);

  let necesitaAlta = false;
  try {
    const estado = await api.get('/auth/status', { autenticada: false });
    necesitaAlta = Boolean(estado?.needsBootstrap);
  } catch {
    // Si el servidor no responde, se ofrece el acceso: es lo que necesita el 99% de
    // las veces, y el error concreto ya saldrá al enviar.
    necesitaAlta = false;
  }

  marco.replaceChildren(necesitaAlta
    ? formularioAlta(alEntrar)
    : formularioAcceso(alEntrar, alOlvidar, alUnirse));
}

function marca(subtitulo) {
  return el('div', { class: 'login__marca' }, [
    el('img', { class: 'login__logo', src: '/icons/icon-192.png', alt: '', width: 56, height: 56 }),
    el('h1', { class: 'login__titulo', text: 'Gastos' }),
    el('p', { class: 'login__subtitulo', text: subtitulo }),
  ]);
}

function formularioAcceso(alEntrar, alOlvidar, alUnirse) {
  const correo = campo('email', 'Correo', {
    type: 'email',
    required: true,
    autocomplete: 'username',
    inputmode: 'email',
  });
  const clave = campo('password', 'Contraseña', {
    type: 'password',
    required: true,
    autocomplete: 'current-password',
  });

  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' }, 'Entrar');

  const formulario = el('form', {
    class: 'login__formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      boton.disabled = true;
      boton.textContent = 'Entrando...';
      try {
        const sesion = await api.post('/auth/login', {
          email: correo.control.value.trim(),
          password: clave.control.value,
        }, { autenticada: false });
        session.start(sesion);
        await alEntrar();
      } catch (e) {
        // 401 siempre dice lo mismo, a propósito: la pantalla no debe delatar si el
        // correo existe.
        const mensaje = e instanceof ApiError && e.status === 401
          ? 'Correo o contraseña incorrectos'
          : (e instanceof ApiError ? e.userMessage : 'No se puede conectar con el servidor');
        aviso.replaceChildren(bloqueError(mensaje));
        clave.control.value = '';
        clave.control.focus();
      } finally {
        boton.disabled = false;
        boton.textContent = 'Entrar';
      }
    },
  }, [
    marca('Las cuentas de casa'),
    accesoConPasskey(alEntrar, aviso),
    correo,
    clave,
    aviso,
    boton,
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: alOlvidar,
    }, 'He olvidado mi contraseña'),
    // Quien llega con el enlace de invitación va directo a la pantalla de alta; esto es
    // para quien sólo tiene el código, dictado o copiado suelto.
    alUnirse ? el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: alUnirse,
    }, 'Tengo un código de invitación') : null,
  ]);

  return formulario;
}

/**
 * Botón de passkey, sólo si el navegador puede con ellas.
 *
 * Va **antes** del formulario porque, para quien tenga una registrada, es la vía rápida:
 * un toque y dentro, sin escribir el correo. Quien no la tenga sigue teniendo el
 * formulario justo debajo, sin haber perdido nada.
 */
function accesoConPasskey(alEntrar, aviso) {
  if (!soportaPasskeys()) {
    return null;
  }

  const boton = el('button', { class: 'boton boton--passkey', type: 'button' },
    'Entrar con passkey');

  boton.addEventListener('click', async () => {
    aviso.replaceChildren();
    boton.disabled = true;
    boton.textContent = 'Esperando al dispositivo...';
    try {
      session.start(await entrarConPasskey());
      await alEntrar();
      return;
    } catch (e) {
      const mensaje = e instanceof PasskeyError
        ? e.message
        : (e instanceof ApiError && e.status === 401
          ? 'Esa passkey no vale para esta aplicación'
          : 'No se puede conectar con el servidor');
      aviso.replaceChildren(bloqueError(mensaje));
    } finally {
      boton.disabled = false;
      boton.textContent = 'Entrar con passkey';
    }
  });

  return el('div', { class: 'login__passkey' }, [
    boton,
    el('p', { class: 'login__separador', text: 'o con tu contraseña' }),
  ]);
}

function formularioAlta(alEntrar) {
  const hogar = campo('householdName', 'Nombre del hogar', {
    required: true, maxlength: 60, value: 'Nuestra casa',
  });
  const nombre = campo('displayName', 'Tu nombre', {
    required: true, maxlength: 60, autocomplete: 'name',
  });
  const correo = campo('email', 'Correo', {
    type: 'email', required: true, autocomplete: 'username', inputmode: 'email',
  });
  const clave = campo('password', 'Contraseña', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });
  const ingresos = campo('monthlyNetIncome', 'Tus ingresos netos mensuales (€)', {
    type: 'number', required: true, min: '0', step: '0.01', inputmode: 'decimal',
  });

  const pista = el('p', {
    class: 'login__pista',
    text: 'Mínimo 12 caracteres. Mejor una frase larga que fácil de recordar que una '
      + 'contraseña corta con símbolos.',
  });

  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' }, 'Crear el hogar');

  return el('form', {
    class: 'login__formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      if (clave.control.value.length < 12) {
        aviso.replaceChildren(bloqueError('La contraseña debe tener al menos 12 caracteres'));
        clave.control.focus();
        return;
      }
      boton.disabled = true;
      boton.textContent = 'Creando...';
      try {
        const sesion = await api.post('/auth/register', {
          householdName: hogar.control.value.trim(),
          email: correo.control.value.trim(),
          displayName: nombre.control.value.trim(),
          password: clave.control.value,
          monthlyNetIncome: Number(ingresos.control.value || 0),
        }, { autenticada: false });
        session.start(sesion);
        await alEntrar();
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se puede conectar con el servidor'));
      } finally {
        boton.disabled = false;
        boton.textContent = 'Crear el hogar';
      }
    },
  }, [
    marca('Vamos a crear vuestro hogar'),
    hogar, nombre, correo, clave, pista, ingresos, aviso, boton,
  ]);
}

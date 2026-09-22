import { api, ApiError } from '../api/client.js';
import { session } from '../api/session.js';
import { campo, el, error as bloqueError } from '../ui/dom.js';

/**
 * Alta con código de invitación.
 *
 * Es la única forma de entrar en un hogar ya creado, y la única pantalla pública que
 * termina creando una cuenta. Va en dos pasos a propósito:
 *
 *  1. **Comprobar el código.** Antes de pedir nada se valida contra el servidor y se
 *     enseña de qué hogar es y quién invita. Así quien se ha equivocado de código lo ve
 *     antes de escribir sus datos, y no después de enviar el formulario.
 *  2. **Los datos.** Correo, nombre, contraseña e ingresos. Los elige quien entra: es
 *     toda la diferencia con el alta directa a la que esto sustituye, donde el titular
 *     escogía la contraseña de su pareja.
 *
 * El código llega normalmente en la URL (`#/unirse?codigo=...`), desde el enlace que ha
 * compartido el titular. Si no viene, se pide a mano.
 */
export async function vistaUnirse(contenedor, codigoDeLaUrl, { alEntrar, alVolver }) {
  const marco = el('div', { class: 'login' });
  contenedor.replaceChildren(marco);

  if (codigoDeLaUrl) {
    // Con el código en la URL no tiene sentido pedirlo otra vez: se comprueba solo.
    await comprobar(marco, codigoDeLaUrl, { alEntrar, alVolver });
    return;
  }
  marco.replaceChildren(formularioCodigo(marco, { alEntrar, alVolver }));
}

function marca(subtitulo) {
  return el('div', { class: 'login__marca' }, [
    el('img', { class: 'login__logo', src: '/icons/icon-192.png', alt: '', width: 56, height: 56 }),
    el('h1', { class: 'login__titulo', text: 'Gastos' }),
    el('p', { class: 'login__subtitulo', text: subtitulo }),
  ]);
}

function formularioCodigo(marco, acciones) {
  const codigo = campo('codigo', 'Código de invitación', {
    required: true,
    maxlength: 20,
    placeholder: 'ABCD-EFGH-JKMN',
    autocomplete: 'off',
    // Los códigos son mayúsculas y el corrector del móvil sólo estorba aquí.
    autocapitalize: 'characters',
    autocorrect: 'off',
    spellcheck: 'false',
  });

  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' }, 'Continuar');

  return el('form', {
    class: 'login__formulario',
    novalidate: true,
    onSubmit: async (evento) => {
      evento.preventDefault();
      aviso.replaceChildren();
      const valor = codigo.control.value.trim();
      if (!valor) {
        aviso.replaceChildren(bloqueError('Escribe el código que te han pasado'));
        codigo.control.focus();
        return;
      }
      boton.disabled = true;
      boton.textContent = 'Comprobando...';
      await comprobar(marco, valor, acciones, aviso);
      boton.disabled = false;
      boton.textContent = 'Continuar';
    },
  }, [
    marca('Te han invitado'),
    codigo,
    el('p', { class: 'login__pista', text:
      'Da igual si lo escribes con guiones o sin ellos, y en mayúsculas o en minúsculas.' }),
    aviso,
    boton,
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: acciones.alVolver,
    }, 'Volver al acceso'),
  ]);
}

/** Valida el código y, si vale, pasa al formulario de datos. */
async function comprobar(marco, codigo, acciones, avisoExistente = null) {
  try {
    const invitacion = await api.post('/auth/invitations/check', { code: codigo },
      { autenticada: false });
    marco.replaceChildren(formularioAlta(marco, codigo, invitacion, acciones));
  } catch (e) {
    const mensaje = e instanceof ApiError
      ? e.userMessage
      : 'No se puede conectar con el servidor';

    if (avisoExistente) {
      avisoExistente.replaceChildren(bloqueError(mensaje));
      return;
    }
    // Se llegó con un código en la URL y no vale: se enseña el error y el formulario
    // para teclear otro, en lugar de dejar la pantalla muerta.
    marco.replaceChildren(formularioCodigo(marco, acciones));
    marco.querySelector('.login__aviso')?.replaceChildren(bloqueError(mensaje));
  }
}

function formularioAlta(marco, codigo, invitacion, { alEntrar, alVolver }) {
  const nombre = campo('displayName', 'Tu nombre', {
    required: true, maxlength: 60, autocomplete: 'name',
  });
  const correo = campo('email', 'Tu correo', {
    type: 'email', required: true, autocomplete: 'username', inputmode: 'email',
  });
  const clave = campo('password', 'Tu contraseña', {
    type: 'password', required: true, minlength: 12, autocomplete: 'new-password',
  });
  const ingresos = campo('monthlyNetIncome', 'Tus ingresos netos mensuales (€)', {
    type: 'number', required: true, min: '0', step: '0.01', inputmode: 'decimal',
  });

  const aviso = el('div', { class: 'login__aviso' });
  const boton = el('button', { class: 'boton boton--principal', type: 'submit' }, 'Entrar en el hogar');

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
      boton.textContent = 'Entrando...';
      try {
        const sesion = await api.post('/auth/join', {
          code: codigo,
          email: correo.control.value.trim(),
          displayName: nombre.control.value.trim(),
          password: clave.control.value,
          monthlyNetIncome: Number(ingresos.control.value || 0),
        }, { autenticada: false });
        session.start(sesion);
        await alEntrar();
        return;
      } catch (e) {
        aviso.replaceChildren(bloqueError(
          e instanceof ApiError ? e.userMessage : 'No se puede conectar con el servidor'));
      } finally {
        boton.disabled = false;
        boton.textContent = 'Entrar en el hogar';
      }
    },
  }, [
    marca(`Te unes a ${invitacion.householdName}`),
    el('p', { class: 'texto-apoyo', text:
      `${invitacion.invitedBy} te ha invitado. A partir de ahora veréis los mismos gastos, `
      + 'las mismas cuentas y la misma hipoteca.' }),
    nombre,
    correo,
    clave,
    el('p', { class: 'login__pista', text:
      'Mínimo 12 caracteres. La eliges tú y no la ve nadie más, tampoco quien te ha '
      + 'invitado.' }),
    ingresos,
    el('p', { class: 'login__pista', text:
      'Se usan para calcular los ingresos del hogar en el análisis de la hipoteca.' }),
    aviso,
    boton,
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: alVolver,
    }, 'Volver al acceso'),
  ]);
}

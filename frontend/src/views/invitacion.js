import { api, ApiError } from '../api/client.js';
import { cargando, el, error as bloqueError, tarjeta } from '../ui/dom.js';
import { fechaHora } from '../ui/format.js';

/**
 * Invitar a la pareja, desde «Tu cuenta».
 *
 * Sustituye al texto que remitía a llamar a la API a mano. Sólo la ve el titular: es
 * quien puede invitar, y enseñar el botón a quien va a recibir un 403 al pulsarlo no
 * ayuda a nadie.
 *
 * **El código se enseña una sola vez.** El servidor guarda su hash, no el código, así
 * que no hay forma de volver a consultarlo; la pantalla tiene que decirlo con todas las
 * letras y dejar a mano las dos formas de pasarlo: compartir el enlace o copiar el
 * código. Quien lo pierda genera otro, que invalida el anterior.
 */
export function tarjetaInvitacion(yo) {
  if (yo.role !== 'OWNER') {
    return null;
  }

  const cuerpo = el('div', { class: 'invitacion' }, cargando());

  const recargar = async () => {
    try {
      const estado = await api.get('/auth/invitations');
      cuerpo.replaceChildren(segunEstado(estado, recargar, cuerpo));
    } catch (e) {
      cuerpo.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se puede consultar la invitación'));
    }
  };
  recargar();

  return tarjeta('Invitar a tu pareja', cuerpo);
}

function segunEstado(estado, recargar, cuerpo) {
  if (estado.householdFull) {
    return el('p', { class: 'texto-apoyo', text:
      'Ya sois dos en el hogar, que es el máximo. No hay a quien invitar.' });
  }

  if (estado.pending) {
    return el('div', {}, [
      el('p', { class: 'texto-apoyo', text:
        `Hay una invitación activa, válida hasta el ${fechaHora(estado.expiresAt)}. El `
        + 'código sólo se enseña al generarlo, así que si lo has perdido, genera otro: '
        + 'el anterior dejará de valer.' }),
      el('div', { class: 'invitacion__acciones' }, [
        botonGenerar('Generar otro código', recargar, cuerpo),
        botonAnular(recargar),
      ]),
    ]);
  }

  return el('div', {}, [
    el('p', { class: 'texto-apoyo', text:
      'Genera un código y pásaselo. Con él se da de alta ella misma y elige su propia '
      + 'contraseña, que no verás ni tú. Vale una sola vez y caduca en siete días.' }),
    botonGenerar('Generar código de invitación', recargar, cuerpo),
  ]);
}

function botonGenerar(etiqueta, recargar, cuerpo) {
  const boton = el('button', { class: 'boton boton--principal', type: 'button' }, etiqueta);

  boton.addEventListener('click', async () => {
    boton.disabled = true;
    boton.textContent = 'Generando...';
    try {
      const invitacion = await api.post('/auth/invitations', undefined);
      cuerpo.replaceChildren(codigoRecienGenerado(invitacion, recargar));
    } catch (e) {
      cuerpo.replaceChildren(bloqueError(
        e instanceof ApiError ? e.userMessage : 'No se ha podido generar el código'));
    }
  });

  return boton;
}

function botonAnular(recargar) {
  const boton = el('button', { class: 'boton boton--sutil', type: 'button' }, 'Anular invitación');

  boton.addEventListener('click', async () => {
    boton.disabled = true;
    try {
      await api.delete('/auth/invitations');
      await recargar();
    } catch {
      boton.disabled = false;
      boton.textContent = 'No se ha podido anular';
    }
  });

  return boton;
}

/** La única pantalla donde existe el código. Se avisa de ello sin rodeos. */
function codigoRecienGenerado(invitacion, recargar) {
  const aviso = el('div', { class: 'invitacion__aviso' });

  return el('div', {}, [
    el('p', { class: 'invitacion__codigo', text: invitacion.code }),
    el('p', { class: 'texto-apoyo', text:
      `Válido hasta el ${fechaHora(invitacion.expiresAt)}, y una sola vez. Apúntalo o `
      + 'mándalo ahora: en cuanto salgas de aquí no se puede volver a ver.' }),
    el('div', { class: 'invitacion__acciones' }, [
      botonCompartir(invitacion, aviso),
      botonCopiar('Copiar el código', invitacion.code, aviso),
    ]),
    aviso,
    el('button', {
      class: 'boton boton--sutil', type: 'button', onClick: recargar,
    }, 'Ya lo he mandado'),
  ]);
}

/**
 * Compartir el enlace por donde sea (WhatsApp, mensajes...).
 *
 * En el móvil `navigator.share` abre el selector del sistema, que es justo lo que hace
 * falta aquí. En escritorio casi nunca existe, así que se cae a copiar el enlace.
 */
function botonCompartir(invitacion, aviso) {
  if (!navigator.share) {
    return botonCopiar('Copiar el enlace', invitacion.joinUrl, aviso);
  }

  const boton = el('button', { class: 'boton boton--principal', type: 'button' },
    'Compartir el enlace');

  boton.addEventListener('click', async () => {
    try {
      await navigator.share({
        title: 'Gastos',
        text: 'Te invito a nuestras cuentas de casa. Entra con este enlace:',
        url: invitacion.joinUrl,
      });
    } catch {
      // Cancelar el diálogo del sistema no es un error y no merece mensaje.
    }
  });

  return boton;
}

function botonCopiar(etiqueta, texto, aviso) {
  const boton = el('button', { class: 'boton boton--sutil', type: 'button' }, etiqueta);

  boton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(texto);
      aviso.replaceChildren(el('p', { class: 'estado', text: 'Copiado al portapapeles' }));
    } catch {
      // Sin permiso de portapapeles (o sin HTTPS) no se puede copiar: se enseña el
      // texto para seleccionarlo a mano, que es mejor que un botón que no hace nada.
      aviso.replaceChildren(el('p', { class: 'texto-apoyo', text:
        `No se ha podido copiar. Cópialo a mano: ${texto}` }));
    }
  });

  return boton;
}

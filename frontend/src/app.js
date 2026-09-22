import { api } from './api/client.js';
import { session } from './api/session.js';
import { el } from './ui/dom.js';
import { alCambiarTema, alternarTema, iniciarTema, temaEfectivo } from './ui/tema.js';
import { vistaCuentas } from './views/accounts.js';
import { vistaGastos } from './views/expenses.js';
import { vistaHipoteca } from './views/mortgage.js';
import { vistaCuenta } from './views/account.js';
import { vistaLogin } from './views/login.js';
import { vistaOlvide, vistaRestablecer } from './views/recover.js';
import { vistaResumen } from './views/summary.js';
import { vistaUnirse } from './views/unirse.js';

/**
 * Arranque y navegación.
 *
 * El router usa el fragmento de la URL (#/gastos) en lugar de la History API: así no hace
 * falta configurar el servidor estático para que devuelva index.html en cualquier ruta, y
 * recargar sobre una pantalla interna sigue funcionando.
 */

const SECCIONES = [
  { ruta: 'resumen', etiqueta: 'Resumen', icono: '◴', vista: vistaResumen },
  { ruta: 'gastos', etiqueta: 'Gastos', icono: '⌸', vista: vistaGastos },
  { ruta: 'cuentas', etiqueta: 'Cuentas', icono: '▤', vista: vistaCuentas },
  { ruta: 'hipoteca', etiqueta: 'Hipoteca', icono: '⌂', vista: vistaHipoteca },
];

const raiz = document.getElementById('app');

function rutaActual() {
  const destino = window.location.hash.replace(/^#\/?/, '') || 'resumen';
  return SECCIONES.some((s) => s.ruta === destino) ? destino : 'resumen';
}

/**
 * Rutas accesibles sin sesion.
 *
 * `restablecer` llega desde el enlace del correo y `unirse` desde el enlace de
 * invitacion, los dos con su credencial en la propia URL: tienen que funcionar sin haber
 * iniciado sesion.
 */
function rutaPublica() {
  const destino = window.location.hash.replace(/^#\/?/, '');
  if (destino.startsWith('restablecer')) {
    return { nombre: 'restablecer', token: new URLSearchParams(destino.split('?')[1] ?? '').get('token') };
  }
  if (destino.startsWith('unirse')) {
    return { nombre: 'unirse', codigo: new URLSearchParams(destino.split('?')[1] ?? '').get('codigo') };
  }
  if (destino.startsWith('olvide')) {
    return { nombre: 'olvide' };
  }
  return null;
}

async function pintarAplicacion() {
  const publica = rutaPublica();

  if (publica?.nombre === 'restablecer') {
    document.body.dataset.estado = 'anonimo';
    vistaRestablecer(raiz, publica.token, { alEntrar: entrar, alVolver: irAlAcceso });
    return;
  }

  if (publica?.nombre === 'unirse') {
    document.body.dataset.estado = 'anonimo';
    await vistaUnirse(raiz, publica.codigo, { alEntrar: entrar, alVolver: irAlAcceso });
    return;
  }

  if (!session.isAuthenticated) {
    document.body.dataset.estado = 'anonimo';
    if (publica?.nombre === 'olvide') {
      vistaOlvide(raiz, { alVolver: irAlAcceso });
      return;
    }
    await vistaLogin(raiz, {
      alEntrar: entrar,
      alOlvidar: () => { window.location.hash = '#/olvide'; },
      alUnirse: () => { window.location.hash = '#/unirse'; },
    });
    return;
  }

  document.body.dataset.estado = 'autenticado';
  const contenido = el('main', { class: 'contenido', id: 'contenido' });
  raiz.replaceChildren(barraLateral(), contenido, barraInferior());
  await pintarSeccion(contenido);
}

async function pintarSeccion(contenido = document.getElementById('contenido')) {
  if (!contenido) {
    return;
  }
  if (window.location.hash.replace(/^#\/?/, '') === 'cuenta') {
    marcarActiva('cuenta');
    document.title = 'Tu cuenta · Gastos';
    await vistaCuenta(contenido, { alSalir: salir });
    return;
  }

  const seccion = SECCIONES.find((s) => s.ruta === rutaActual());
  marcarActiva(seccion.ruta);
  document.title = `${seccion.etiqueta} · Gastos`;
  await seccion.vista(contenido);
}

/** `ruta` a null desmarca todas: se usa en pantallas que no son pestaña. */
function marcarActiva(ruta) {
  for (const enlace of document.querySelectorAll('[data-ruta]')) {
    const activa = enlace.dataset.ruta === ruta;
    enlace.classList.toggle('navegacion__enlace--activa', activa);
    if (activa) {
      enlace.setAttribute('aria-current', 'page');
    } else {
      enlace.removeAttribute('aria-current');
    }
  }
}

function enlaces(claseBase) {
  return SECCIONES.map((seccion) => el('a', {
    class: `${claseBase}__enlace navegacion__enlace`,
    href: `#/${seccion.ruta}`,
    dataset: { ruta: seccion.ruta },
  }, [
    el('span', { class: 'navegacion__icono', 'aria-hidden': 'true', text: seccion.icono }),
    el('span', { class: 'navegacion__texto', text: seccion.etiqueta }),
  ]));
}

function barraLateral() {
  return el('nav', { class: 'lateral', 'aria-label': 'Secciones' }, [
    el('div', { class: 'lateral__marca' }, [
      el('img', { class: 'lateral__logo', src: '/icons/icon-192.png', alt: '', width: 32, height: 32 }),
      el('span', { class: 'lateral__nombre', text: 'Gastos' }),
    ]),
    el('div', { class: 'lateral__enlaces' }, enlaces('lateral')),
    el('div', { class: 'lateral__pie' }, [
      fichaUsuario(),
      el('div', { class: 'lateral__acciones' }, [
        botonDeTema(),
        el('button', {
          class: 'boton boton--sutil lateral__salir', type: 'button', onClick: salir,
        }, 'Cerrar sesión'),
      ]),
    ]),
  ]);
}

/** Primera letra del nombre, en mayúscula, para el círculo del avatar. */
function inicialDe(nombre) {
  const limpio = nombre.trim();
  return limpio ? limpio[0].toUpperCase() : '·';
}

/**
 * Ficha del usuario en el pie de la navegación.
 *
 * Era un nombre suelto colgando bajo una línea, sin nada que dijera que se podía pulsar.
 * Enmarcarlo y darle una inicial lo convierte en lo que de verdad es: el acceso a tu
 * cuenta, con el mismo peso visual que una pestaña.
 */
function fichaUsuario() {
  const nombre = session.user?.displayName ?? 'Tu cuenta';
  return el('a', {
    class: 'ficha',
    href: '#/cuenta',
    dataset: { ruta: 'cuenta' },
    'aria-label': `Tu cuenta: ${nombre}`,
  }, [
    el('span', { class: 'ficha__inicial', 'aria-hidden': 'true', text: inicialDe(nombre) }),
    el('span', { class: 'ficha__datos' }, [
      el('span', { class: 'ficha__nombre', text: nombre }),
      el('span', { class: 'ficha__pie', text: 'Ver tu cuenta' }),
    ]),
  ]);
}

/**
 * Interruptor rápido de tema.
 *
 * El control completo (claro / oscuro / el del sistema) está en la pantalla de cuenta;
 * esto es el atajo para el gesto habitual, que es cambiar al otro. El icono enseña a
 * dónde se va, no dónde se está: con el tema oscuro puesto se ve un sol.
 */
function botonDeTema() {
  const boton = el('button', {
    class: 'boton boton--icono', type: 'button', onClick: alternarTema,
  });

  const repintar = () => {
    const oscuro = temaEfectivo() === 'oscuro';
    boton.textContent = oscuro ? '☀' : '☾';
    const etiqueta = oscuro ? 'Cambiar al modo claro' : 'Cambiar al modo oscuro';
    boton.setAttribute('aria-label', etiqueta);
    boton.setAttribute('title', etiqueta);
  };

  repintar();
  alCambiarTema(boton, repintar);
  return boton;
}

/**
 * En móvil la navegación va abajo: es donde llega el pulgar.
 *
 * Lleva además la entrada a la cuenta, que en móvil no tenía ninguna otra puerta: la
 * barra lateral está oculta, así que desde el teléfono no había forma de invitar a
 * nadie, cambiar la contraseña ni cerrar sesión.
 */
function barraInferior() {
  const nombre = session.user?.displayName ?? 'Tu cuenta';
  const cuenta = el('a', {
    class: 'inferior__enlace navegacion__enlace',
    href: '#/cuenta',
    dataset: { ruta: 'cuenta' },
    'aria-label': `Tu cuenta: ${nombre}`,
  }, [
    el('span', { class: 'navegacion__icono ficha__inicial ficha__inicial--pequena',
      'aria-hidden': 'true', text: inicialDe(nombre) }),
    el('span', { class: 'navegacion__texto', text: 'Tú' }),
  ]);

  return el('nav', { class: 'inferior', 'aria-label': 'Secciones' },
    [...enlaces('inferior'), cuenta]);
}

async function entrar() {
  try {
    session.setUser(await api.get('/auth/me'));
  } catch {
    // Que falle /auth/me no impide usar la aplicación: sólo deja el nombre sin mostrar.
  }
  const destino = window.location.hash.replace(/^#\/?/, '');
  if (!destino || destino.startsWith('restablecer') || destino.startsWith('olvide')
      || destino.startsWith('unirse')) {
    window.location.hash = '#/resumen';
  }
  await pintarAplicacion();
}

async function salir() {
  const refreshToken = session.refreshToken;
  session.clear();
  if (refreshToken) {
    // Revocar en el servidor, no sólo olvidar en el cliente: si no, el token seguiría
    // siendo válido 30 días para quien lo tuviera.
    try {
      await api.post('/auth/logout', { refreshToken }, { autenticada: false });
    } catch {
      // Sin red no se puede revocar ahora; la sesión local ya está cerrada.
    }
  }
  window.location.hash = '';
  await pintarAplicacion();
}

window.addEventListener('hashchange', () => {
  // Las rutas publicas y el login cambian el armazon entero, no solo el contenido.
  if (!session.isAuthenticated || rutaPublica()) {
    pintarAplicacion();
    return;
  }
  pintarSeccion();
});

function irAlAcceso() {
  window.location.hash = '';
  pintarAplicacion();
}

// Si el cliente descubre que la sesión murió (refresco fallido), vuelve al login solo.
session.onChange((estado) => {
  if (!estado.isAuthenticated && document.body.dataset.estado === 'autenticado') {
    pintarAplicacion();
  }
});

async function arrancar() {
  // tema-inicial.js ya ha pintado con el tema correcto; esto engancha el seguimiento del
  // modo del sistema y pone la barra del navegador a juego.
  iniciarTema();

  // Quien llega desde el enlace del correo o desde el de invitacion no debe pasar por el
  // login, aunque tenga una sesion vieja guardada en este navegador.
  const publicaAlArrancar = rutaPublica()?.nombre;
  if (publicaAlArrancar === 'restablecer' || publicaAlArrancar === 'unirse') {
    await pintarAplicacion();
    registrarServiceWorker();
    return;
  }

  // Con token de refresco pero sin el de acceso —caso normal tras recargar— se canjea
  // antes de pintar, para no enseñar el login un instante a quien ya tiene sesión.
  if (!session.accessToken && session.refreshToken) {
    try {
      await api.get('/auth/me');
    } catch {
      // El cliente ya habrá limpiado la sesión si el refresco no valía.
    }
  }
  await (session.isAuthenticated ? entrar() : pintarAplicacion());
  registrarServiceWorker();
}

function registrarServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Sin service worker la aplicación funciona igual, sólo pierde el arranque offline.
    });
  }
}

arrancar();

import { api } from './api/client.js';
import { session } from './api/session.js';
import { el } from './ui/dom.js';
import { vistaCuentas } from './views/accounts.js';
import { vistaGastos } from './views/expenses.js';
import { vistaHipoteca } from './views/mortgage.js';
import { vistaLogin } from './views/login.js';
import { vistaResumen } from './views/summary.js';

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

async function pintarAplicacion() {
  if (!session.isAuthenticated) {
    document.body.dataset.estado = 'anonimo';
    await vistaLogin(raiz, { alEntrar: entrar });
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
  const seccion = SECCIONES.find((s) => s.ruta === rutaActual());
  marcarActiva(seccion.ruta);
  document.title = `${seccion.etiqueta} · Gastos`;
  await seccion.vista(contenido);
}

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
      el('span', { class: 'lateral__usuario', text: session.user?.displayName ?? '' }),
      el('button', {
        class: 'boton boton--sutil', type: 'button', onClick: salir,
      }, 'Cerrar sesión'),
    ]),
  ]);
}

/** En móvil la navegación va abajo: es donde llega el pulgar. */
function barraInferior() {
  return el('nav', { class: 'inferior', 'aria-label': 'Secciones' }, enlaces('inferior'));
}

async function entrar() {
  try {
    session.setUser(await api.get('/auth/me'));
  } catch {
    // Que falle /auth/me no impide usar la aplicación: sólo deja el nombre sin mostrar.
  }
  if (!window.location.hash) {
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
  if (session.isAuthenticated) {
    pintarSeccion();
  }
});

// Si el cliente descubre que la sesión murió (refresco fallido), vuelve al login solo.
session.onChange((estado) => {
  if (!estado.isAuthenticated && document.body.dataset.estado === 'autenticado') {
    pintarAplicacion();
  }
});

async function arrancar() {
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

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Sin service worker la aplicación funciona igual, sólo pierde el arranque offline.
    });
  }
}

arrancar();

/**
 * Service worker.
 *
 * Su único cometido es que la aplicación **arranque** sin conexión: cachea el armazón
 * (HTML, CSS, JS, iconos) y nada más.
 *
 * Las respuestas de la API NO se cachean, y es deliberado. Son datos económicos del
 * hogar: guardarlos en la caché del navegador los dejaría en disco fuera del control de
 * la aplicación, y además un saldo desactualizado que parece actual es peor que no ver
 * nada. Si no hay red, la pantalla dice que no hay red.
 *
 * **Estrategia: red primero, caché de respaldo** para el código del armazón. La
 * alternativa —caché primero, más rápida— obliga a acordarse de subir VERSION en cada
 * despliegue; olvidarlo una sola vez deja a los usuarios con la versión anterior pegada y
 * sin forma evidente de salir de ahí. Los iconos sí van de caché, porque no cambian.
 */

const VERSION = 'gastos-v2';

const ARMAZON = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/src/app.js',
  '/src/api/client.js',
  '/src/api/session.js',
  '/src/ui/dom.js',
  '/src/ui/format.js',
  '/src/views/accounts.js',
  '/src/views/expenses.js',
  '/src/views/login.js',
  '/src/views/mortgage.js',
  '/src/views/summary.js',
  '/src/styles/tokens.css',
  '/src/styles/app.css',
  '/icons/icon-192.png',
  '/icons/apple-touch-icon.png',
];

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches.open(VERSION)
      // addAll falla entera si un solo recurso falla; se usa a propósito, porque un
      // armazón cacheado a medias arrancaría roto.
      .then((cache) => cache.addAll(ARMAZON))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((claves) => Promise.all(
        claves.filter((clave) => clave !== VERSION).map((clave) => caches.delete(clave)),
      ))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evento) => {
  const peticion = evento.request;

  if (peticion.method !== 'GET') {
    return;
  }

  const url = new URL(peticion.url);

  // Nada de la API pasa por aquí: ni se guarda ni se sirve desde la caché.
  if (url.pathname.startsWith('/api/') || url.origin !== self.location.origin) {
    return;
  }

  // Los iconos no cambian: caché primero y a correr.
  if (url.pathname.startsWith('/icons/')) {
    evento.respondWith(
      caches.match(peticion).then((cacheada) => cacheada ?? fetch(peticion)),
    );
    return;
  }

  evento.respondWith(redPrimero(peticion));
});

/**
 * Red primero, caché de respaldo.
 *
 * Con conexión siempre se sirve la versión desplegada; sin ella, la última que se llegó a
 * ver. Una navegación que falla cae a index.html, para que abrir la PWA sin cobertura
 * muestre la aplicación en lugar del dinosaurio del navegador.
 */
async function redPrimero(peticion) {
  try {
    const respuesta = await fetch(peticion);
    if (respuesta.ok) {
      const copia = respuesta.clone();
      const cache = await caches.open(VERSION);
      await cache.put(peticion, copia);
    }
    return respuesta;
  } catch (error) {
    const cacheada = await caches.match(peticion);
    if (cacheada) {
      return cacheada;
    }
    if (peticion.mode === 'navigate') {
      const armazon = await caches.match('/index.html');
      if (armazon) {
        return armazon;
      }
    }
    throw error;
  }
}

// Prueba de carga de STAGING con k6 (https://k6.io). Simula tres tipos de usuario a la vez:
//   navegantes   visitantes anónimos: catálogo con filtros, búsqueda, orden y páginas; ficha, relacionados y eventos
//   clientes     inician sesión UNA vez y luego: perfil, mis pedidos, validación del carrito (no compran: sin Webpay)
//   admins       panel: productos, pedidos, usuarios, eventos, cierres de caja, reportes del día y auditoría
//
// Uso (desde la raíz del repo de la API; la contraseña es la que usaste en poblar-staging.sh):
//   k6 run -e CLAVE='...' scripts/carga/staging.js                       perfil "suave" (por defecto)
//   (requiere poblar-staging.sh --escala 5 o más: los clientes usan las cuentas carga-0001..0040; -e CUENTAS=N)
//   k6 run -e CLAVE='...' -e PERFIL=medio scripts/carga/staging.js
//   sin instalar k6:  docker run --rm -i grafana/k6 run -e CLAVE='...' -e PERFIL=medio - < scripts/carga/staging.js
//
// OJO: staging comparte el VPS con producción. "fuerte" puede ralentizar runagard.cl: úsalo en horario sin clientes
// y mira Grafana mientras corre. No hace compras (checkout llamaría a Transbank) ni registros (enviaría correos).
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'https://staging.runagard.cl';
const API = `${BASE}/api/v1`;
const CLAVE = __ENV.CLAVE;
const PERFIL = __ENV.PERFIL || 'suave';

if (!CLAVE) {
  throw new Error('Falta -e CLAVE=... (la contraseña de las cuentas demo de staging)');
}

// Usuarios simultáneos por tipo y duración de cada etapa.
const PERFILES = {
  suave:  { navegantes: 20,  clientes: 5,  admins: 1, subida: '1m', meseta: '3m' },
  medio:  { navegantes: 60,  clientes: 15, admins: 2, subida: '2m', meseta: '5m' },
  fuerte: { navegantes: 150, clientes: 40, admins: 3, subida: '3m', meseta: '5m' },
};
const P = PERFILES[PERFIL];
if (!P) {
  throw new Error(`PERFIL debe ser suave, medio o fuerte (llegó "${PERFIL}")`);
}

const rampa = (vus) => [
  { duration: P.subida, target: vus },
  { duration: P.meseta, target: vus },
  { duration: '30s', target: 0 },
];

export const options = {
  scenarios: {
    navegantes: { executor: 'ramping-vus', exec: 'navegante', startVUs: 0, stages: rampa(P.navegantes) },
    clientes:   { executor: 'ramping-vus', exec: 'cliente',   startVUs: 0, stages: rampa(P.clientes) },
    admins:     { executor: 'ramping-vus', exec: 'admin',     startVUs: 0, stages: rampa(P.admins) },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],                                  // < 1 % de errores
    'http_req_duration{tipo:catalogo}': ['p(95)<800'],               // catálogo: 95 % bajo 0,8 s
    'http_req_duration{tipo:cuenta}': ['p(95)<1000'],
    'http_req_duration{tipo:admin}': ['p(95)<2000'],                 // reportes y listados pesados
    login_ms: ['p(95)<1500'],                                        // BCrypt costo 12 por login
    checks: ['rate>0.99'],
  },
};

const loginMs = new Trend('login_ms', true);
const errores4xx = new Rate('errores_4xx');

const CATEGORIAS = ['todos', 'tcg', 'figuras', 'venta-videojuegos', 'consolas', 'accesorios-consolas'];
const ORDENES = ['recomendados', 'recientes', 'precio-asc', 'precio-desc'];
const BUSQUEDAS = ['pokemon', 'pikachu', 'one piece', 'sobre', 'booster', 'naruto', 'switch', 'control', 'funko', 'mazo'];
const FRANQUICIAS = ['Pokémon', 'One Piece', 'Naruto', 'Dragon Ball', 'Yu-Gi-Oh!'];

const elegir = (lista) => lista[Math.floor(Math.random() * lista.length)];
const pausa = (min, max) => sleep(min + Math.random() * (max - min));
const hoyChile = () => new Date(Date.now() - 3 * 3600 * 1000).toISOString().slice(0, 10);

function get(url, tipo, token) {
  const params = { tags: { tipo }, headers: { Accept: 'application/json' } };
  if (token) params.headers.Authorization = `Bearer ${token}`;
  const r = http.get(url, params);
  errores4xx.add(r.status >= 400 && r.status < 500);
  check(r, { [`${tipo} 200`]: (x) => x.status === 200 });
  return r;
}

function login(email) {
  const r = http.post(`${API}/auth/login`, JSON.stringify({ email, password: CLAVE }), {
    headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'pp-web' },
    tags: { tipo: 'login' },
  });
  loginMs.add(r.timings.duration);
  const ok = check(r, { 'login 200': (x) => x.status === 200 && !!x.json('accessToken') });
  return ok ? r.json('accessToken') : null;
}

// ---------- navegantes ----------
export function navegante() {
  group('catálogo', () => {
    if (Math.random() < 0.3) {
      get(`${API}/categorias`, 'catalogo');
      get(`${API}/franquicias`, 'catalogo');
      get(`${API}/productos/destacados?limit=8`, 'catalogo');
    }
    const filtros = [`sort=${elegir(ORDENES)}`, `page=${Math.floor(Math.random() * 3)}`, 'size=24'];
    const cat = elegir(CATEGORIAS);
    if (cat !== 'todos') filtros.push(`categoria=${cat}`);
    if (Math.random() < 0.35) filtros.push(`q=${encodeURIComponent(elegir(BUSQUEDAS))}`);
    if (Math.random() < 0.25) filtros.push(`franquicia=${encodeURIComponent(elegir(FRANQUICIAS))}`);
    if (Math.random() < 0.2) filtros.push('enStock=true');
    if (Math.random() < 0.15) filtros.push('precioMin=5000&precioMax=60000');
    const lista = get(`${API}/productos?${filtros.join('&')}`, 'catalogo');
    pausa(1, 3);

    const productos = lista.status === 200 ? lista.json('content') || [] : [];
    if (productos.length) {
      const id = elegir(productos).id;
      get(`${API}/productos/${id}`, 'catalogo');
      get(`${API}/productos/${id}/relacionados?limit=4`, 'catalogo');
      pausa(2, 5);
    }
  });
  if (Math.random() < 0.25) {
    get(`${API}/eventos`, 'catalogo');
    pausa(1, 3);
  }
}

// ---------- clientes (sesión por usuario virtual, se renueva antes de que venza el token de 15 min) ----------
const sesion = { token: null, desde: 0 };

export function cliente() {
  if (!sesion.token || Date.now() - sesion.desde > 12 * 60 * 1000) {
    // poblar-staging.sh --escala N crea (N−1)×10 cuentas carga-NNNN; CUENTAS dice cuántas hay (0 = solo
    // cliente@primerplayer.cl). Por defecto 40 (escala 5 o más).
    const cuentas = Number(__ENV.CUENTAS ?? 40);
    const email = cuentas > 0
      ? `carga-${String(1 + ((__VU - 1) % cuentas)).padStart(4, '0')}@demo.primerplayer.cl`
      : 'cliente@primerplayer.cl';
    sesion.token = login(email);
    sesion.desde = Date.now();
    if (!sesion.token) { pausa(5, 10); return; }
  }
  const t = sesion.token;
  group('cuenta', () => {
    get(`${API}/me`, 'cuenta', t);
    get(`${API}/me/pedidos?page=0&size=10`, 'cuenta', t);
    pausa(1, 3);
    const lista = get(`${API}/productos?size=24&sort=recomendados`, 'catalogo');
    const productos = lista.status === 200 ? lista.json('content') || [] : [];
    if (productos.length) {
      const lineas = [1, 2].map(() => ({ type: 'product', itemId: elegir(productos).id, quantity: 1 }));
      const r = http.post(`${API}/carrito/validacion`, JSON.stringify({ lineas }), {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` },
        tags: { tipo: 'cuenta' },
      });
      // 200, o 409/422 si justo no hay stock: también es una respuesta correcta del negocio.
      check(r, { 'carrito validado': (x) => [200, 409, 422].includes(x.status) });
    }
  });
  pausa(3, 8);
}

// ---------- admins ----------
export function setup() {
  const token = login('admin@primerplayer.cl');
  if (!token) throw new Error('No se pudo iniciar sesión como admin@primerplayer.cl: revisa CLAVE');
  return { admin: token };
}

export function admin(datos) {
  const t = datos.admin;
  group('panel', () => {
    get(`${API}/admin/productos?page=0&size=25`, 'admin', t);
    get(`${API}/admin/pedidos?page=0&size=25`, 'admin', t);
    get(`${API}/admin/usuarios?page=0&size=25`, 'admin', t);
    get(`${API}/admin/eventos?page=0&size=25`, 'admin', t);
    get(`${API}/admin/caja/cierres`, 'admin', t);
    get(`${API}/admin/reportes/dia?fecha=${hoyChile()}`, 'admin', t);
    get(`${API}/admin/reportes/por-hora?fecha=${hoyChile()}`, 'admin', t);
    get(`${API}/admin/auditoria?page=0&size=25`, 'admin', t);
  });
  pausa(5, 10);
}

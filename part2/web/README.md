# Book-01 · cliente web de Part 2A

Cliente responsive en español para el chat contable de solo lectura. El shell está conectado por código al SDK oficial `@supabase/supabase-js` y a `POST /v1/chat`, pero **no hay un proyecto Supabase, servicio HTTPS ni proveedor de acceso configurados o desplegados**. Sin configuración, el envío permanece deshabilitado. No se han usado credenciales o datos contables reales.

## Desarrollo y comprobaciones

Se requiere Node.js 20+ y npm. Desde esta carpeta:

```sh
npm ci
npm run check
```

`npm run check` construye el sitio con Vite y ejecuta pruebas DOM/seguridad con Node.js `node:test` y jsdom. La salida de build queda en `dist/`; no se versiona ni se publica mediante CI. Para ver el build:

```sh
npm run build
python3 -m http.server 8000 --directory dist
```

## Configuración no secreta del navegador

Configura estas variables `VITE_...` en el entorno que ejecute Vite/build o el hosting estático. La URL de Supabase y la base del servicio deben ser URLs HTTPS sin usuario/contraseña, query ni fragmento:

- `VITE_SUPABASE_URL`: URL HTTPS del proyecto Supabase.
- `VITE_SUPABASE_PUBLISHABLE_KEY`: clave publicable de Supabase (antes denominada anon key). Esta clave es pública y se incorpora al bundle del navegador; nunca uses una clave `service_role`.
- `VITE_CHAT_SERVICE_BASE_URL`: base HTTPS del servicio Part 2A, sin `/v1/chat` (por ejemplo, la URL de despliegue seguida de la ruta prefijada que corresponda).

No hay valores reales en el repositorio. No se deben poner JWT, claves de base de datos, `service_role` o `GROQ_API_KEY` en estas variables, el HTML, el bundle, archivos versionados ni almacenamiento del navegador. La clave Groq solo pertenece al entorno servidor.

El formulario implementa inicio/cierre de sesión con contraseña usando solo `signInWithPassword` de Supabase Auth; no ofrece autorregistro. El token de acceso y la sesión del SDK se mantienen en memoria (`persistSession: false`, `autoRefreshToken: false`, `detectSessionInUrl: false`) y no se usan cookies, `localStorage` ni `sessionStorage`. Los campos de contraseña se limpian al intentar iniciar sesión. Si el servicio rechaza el bearer con 401, el cliente cierra la sesión local y pide iniciar sesión otra vez.

Para enviar, el cliente exige configuración HTTPS, sesión y UUID válido escrito en el campo «ID del libro». Envía únicamente `{ bookId, message }` con `Authorization: Bearer <access token>` a `POST /v1/chat`; la llamada omite cookies. **El ID de libro lo suministra el usuario y no se deriva del token**: el servicio transmite ese ID y bearer a la API contable, que debe autorizar el acceso (RLS/membresías) antes de pasar la pregunta al modelo.

## Limitaciones de dominio y seguridad

- La UI no ofrece filtros de periodo/moneda/filtros separados: el endpoint solo recibe el ID de libro y la pregunta. Los filtros que se describan dentro de la pregunta dependen de las herramientas conversacionales disponibles; no se simula que un formulario los aplica. La API actual soporta USD únicamente.
- Solo se representa respuesta de solo lectura. No se incluyen escrituras, borradores, confirmaciones o contabilización. El chat no persiste al recargar.
- Texto de modelo, API, filas y resumen se insertan como texto mediante `textContent`/nodos DOM; no se usa `innerHTML`. La tabla limita filas/columnas y valores renderizados.
- CORS del servicio está deshabilitado por defecto. Cuando cliente y servicio estén en orígenes separados, `CORS_ALLOWED_ORIGINS` del servicio debe incluir el **origen exacto HTTPS** del hosting (sin ruta ni wildcard). También se puede usar same-origin routing. CORS no reemplaza autenticación ni autorización.

## Bloqueadores antes de un uso real

1. Crear y configurar el proyecto Supabase: proveedor de Auth, usuarios, migraciones, issuer/audience, exposición/grants y RLS; probar acceso entre usuarios/libros con JWT reales.
2. Desplegar la API contable y el servicio Part 2A por HTTPS; establecer `ACCOUNTING_API_BASE_URL`, `GROQ_API_KEY` y `SUPABASE_URL`/clave publicable del API en secretos del servidor. Aprobar el tratamiento de prompts/resultados antes de activar Groq.
3. Configurar el hosting estático HTTPS, las variables `VITE_...` y el origen exacto de CORS o el routing same-origin; configurar redirect URLs/allowlist del proveedor Auth conforme al hosting.
4. Ejecutar una validación real de extremo a extremo de autenticación, autorización de libro, política de datos y CORS. Las pruebas sintéticas no reemplazan este paso.

CI verifica build y pruebas del cliente, pero no despliega ni publica `dist/`.

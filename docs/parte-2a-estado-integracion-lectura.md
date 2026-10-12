# Parte 2A — corte técnico reproducible de solo lectura

Estado al 12 de octubre de 2026: se cerraron brechas de esquema/contrato y se añadieron pruebas offline; **no** es una integración live ni completa. La selección de FastAPI sigue siendo provisional y reversible.

## Qué está preparado

- La API FastAPI consulta Supabase PostgREST por HTTPS enviando el JWT del usuario y solo la publishable/anon key en `apikey`; sin clave `service_role`, SQL directo, conexión de base de datos ni endpoints de escritura.
- Las respuestas ahora reflejan campos y enums del esquema migrado: entradas `draft|posted|void`, cuentas `asset|liability|equity|income|expense`, importes USD exactos y descripción/fecha/estado/líneas. Se retiraron del borrador OpenAPI los campos no persistidos de referencia, contraparte, jerarquía de cuenta, reversa y costo. `idempotency_key` no se expone como referencia contable.
- Listado de asientos admite búsqueda literal por descripción, filtro por cuenta/moneda/estado, fechas inclusivas y cursor. Los importes de la página se suman con `Decimal` desde las líneas realmente leídas. Listado de cuentas no completa datos faltantes con valores inventados.
- Balance por cuenta y balance de comprobación calculan movimientos `posted`; USD y precision decimal permanecen acotados. El estado de resultados devuelve `501 not_implemented` si encuentra asientos contabilizados porque el esquema no diferencia costos de gastos.
- El servicio conversacional tiene cinco herramientas GET, usa por defecto USD, inyecta el `bookId` del request autenticado, reenvía el bearer solo al API contable y falla cerrado si falta endpoint/key. La web estática permanece desconectada, no conserva chats y no usa `localStorage`/`sessionStorage`.
- Se añadieron suites offline con `httpx.MockTransport` y una comprobación de compatibilidad entre YAML/OpenAPI runtime; workflow de CI para el servicio Part 2.

## Verificación reproducible local

Desde la raíz:

```sh
python -m pip install -r backend/requirements.txt
python -m pytest backend/tests
python -m pip install -r part2/service/requirements.txt
python -m pytest part2/service/tests
node --check part2/web/app.js
```

Las pruebas usan únicamente transportes sintéticos; no llaman Supabase, Groq ni una URL de producción. Los workflows correspondientes son `.github/workflows/backend-api.yml` y `.github/workflows/part2-service.yml`.

## Qué falta para probar con datos reales

No hay valores de un proyecto Supabase, usuarios/JWT, clave Groq, despliegue ni hosting disponibles, por lo que no se configuró ni afirmó una conexión live. Antes del despliegue, owner/backend deberá determinar/configurar:

**Ajustes no secretos del proyecto/hosting:** URL y project ref de Supabase (`SUPABASE_URL`); URL issuer, audience, proveedor(es) Auth y reglas de sesión; exposición REST del esquema `public`, grants de `authenticated`, migraciones, membership/RLS y libros/usuarios de prueba; URL HTTPS del API contable que incluya `/api/v1` (`ACCOUNTING_API_BASE_URL`); URL HTTPS del servicio y origen web; CORS restringido o routing same-origin; nombre de modelo aprobado (`GROQ_MODEL`, opcional).

**Settings/secret store del entorno servidor (no chat, no repositorio):** publishable/anon key para PostgREST (`SUPABASE_PUBLISHABLE_KEY` o `SUPABASE_ANON_KEY`; no usar service-role); `GROQ_API_KEY` solo después de aprobación de privacidad/procesamiento. Configurar `SUPPORTED_CURRENCIES=USD`, acorde al CHECK de la migración. Nunca persistir bearer JWT de usuario en archivos, logs, localStorage o sessionStorage.

También falta un login web aprobado que mantenga el JWT solo en memoria y lo envíe al servicio Part 2 por HTTPS; la web actual no hace solicitudes de red. Después de configurar el proyecto, ejecutar pruebas reales de issuer/audience, RLS/no acceso entre libros, solo GET, errores/expiración de JWT y contrato end-to-end con datos de prueba. Sin ello, este hito solo es una integración de código testeable offline.

## Revisión de contrato pendiente

La propuesta OpenAPI revisada describe el esquema actual, pero decisiones de producto requieren revisión del owner/backend antes de declararla canónica: estado/status que el asistente debe exponer a usuario final (por defecto conserva `posted`; `draft`/`void` solo si se solicitan), si y cómo se clasifican costo vs. gasto, y si más adelante se añaden referencia, counterparty, parent account o reversas al modelo con migración aditiva, mismas-book FKs, RLS y pruebas. Hasta esas decisiones, la API no deduce esos atributos. La contabilización sigue bloqueada por diseño; este alcance no añade writes.

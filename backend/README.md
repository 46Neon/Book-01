# Backend contable — scaffold FastAPI de solo lectura

**Estado: scaffold acotado, no producción. No desplegar ni conectar a un proyecto real.** No hay proyecto/credenciales Supabase disponibles aquí y no se hicieron pruebas contra Supabase en vivo. FastAPI se elige provisionalmente porque el servicio existente de Parte 2A ya lo utiliza; es una decisión reversible, no una selección de plataforma de despliegue.

Esta implementación solo prepara endpoints `GET` para el contrato de [`../docs/parte-2a-openapi-propuesta.yaml`](../docs/parte-2a-openapi-propuesta.yaml). No modifica el servicio web/chat existente ni Android. No hay `POST`/`PATCH`/`DELETE`, contabilización, Groq, conexión directa a PostgreSQL ni clave `service_role`.

## Interfaz y seguridad

- Base de ruta: `/api/v1`; únicamente las cinco operaciones GET del borrador OpenAPI.
- El futuro cliente Android/chat debe tratar esta API como única interfaz de datos contables; no leer Supabase directamente.
- Cada solicitud requiere `Authorization: Bearer <JWT del usuario>`. El scaffold reenvía ese mismo bearer a PostgREST y añade únicamente `SUPABASE_PUBLISHABLE_KEY` o, alternativamente, `SUPABASE_ANON_KEY` como `apikey`. Nunca verifica/reconstruye identidad con claims proporcionados por el cliente, cuerpo del modelo o tenant del request.
- Se comprueba el UUID `bookId`, se consulta `books` con RLS para comprobar acceso y todas las lecturas de recursos se filtran además por `book_id=eq.<bookId>`. La política RLS de Supabase es la autorización final. Una fila `books` oculta por RLS da un 404 indistinguible de un libro inexistente.
- La URL Supabase debe usar HTTPS. HTTPX usa TLS verificado, redirecciones deshabilitadas, límites de conexión y timeout corto; resultados/paginación/reportes tienen cotas explícitas. No se propagan cuerpos de error, tokens ni mensajes upstream al cliente.
- Importes se leen como cadenas y se calculan con `Decimal`; respuestas los serializan como cadenas decimales, nunca JSON float.

## Operaciones preparadas

- `GET /api/v1/books/{bookId}/accounts`: listado paginado, filtro `activeOnly` (default true), `pageSize` 1–200 y cursor opaco con desplazamiento acotado.
- `GET /api/v1/books/{bookId}/journal-entries`: periodo inclusivo obligatorio; status por default `posted`; filtros de cuenta/moneda; `pageSize` 1–200. Cuando una fila real no puede representarse según el contrato, devuelve error tipado `501` (`ApiError`) en lugar de inventar un `reference`, contraparte o reversa.
- `GET /api/v1/books/{bookId}/accounts/{accountId}/balance`: calcula saldo a la fecha desde líneas contabilizadas visibles.
- `GET /api/v1/books/{bookId}/reports/trial-balance`: suma movimientos contabilizados previos y del rango inclusivo, en USD, con `Decimal`; acotado a 1.000 cuentas, 1.000 asientos y 5.000 líneas.
- `GET /api/v1/books/{bookId}/reports/income-statement`: si no hay asientos contabilizados, devuelve arrays vacíos y neto cero. Si hay movimientos, falla con `501 not_implemented`: el esquema migrado no distingue costos de gastos, por lo que no puede clasificarlos fielmente.

Todos los reportes incluyen solamente `posted`. La migración actual bloquea deliberadamente la contabilización y solo permite crear/editar borradores, por eso resultados vacíos son esperables y no se fabrican saldos.

### Desajustes explícitos del contrato y la migración

La migración `202610120001_initial_core.sql` no contiene `parent_account_id`, referencia contable separada, contraparte, relación de reversa, tipo de cuenta `cost`, ni status `reversed`/`voided` (su valor equivalente es `void`). El listado de cuentas emite `parentAccountId: null`, campo no almacenado. El endpoint de asientos no confunde la clave técnica `idempotency_key` con la referencia contable del contrato: si hay asientos que devolver, o se usa un filtro de contraparte/referencia que no puede satisfacerse, responde `501` con el mismo formato tipado `ApiError`, sin inventar valores. Los `501` comunican incompatibilidades detectadas y los `502`/`503`/`504` cubren fallos de configuración/upstream/timeout con el mismo `ApiError`; son respuestas adicionales documentadas por el scaffold, no cambian el borrador del contrato.

El borrador OpenAPI permite moneda de tres letras, pero la migración solo admite USD; otros códigos dan un error tipado `422`. El borrador incluye el tipo `cost`, ausente del CHECK del esquema migrado.

## Configuración y ejecución local

Requiere Python 3.11+. Desde `backend/`:

```sh
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export SUPABASE_URL='https://<project-ref>.supabase.co'
export SUPABASE_ANON_KEY='<anon-or-publishable-key>'
# o usar SUPABASE_PUBLISHABLE_KEY en lugar de SUPABASE_ANON_KEY
uvicorn app:app --host 127.0.0.1 --port 8000
```

No guardar valores reales en archivos versionados ni pasar JWT de usuarios como variables permanentes. Usa secretos/variables de entorno del entorno que ejecute el servicio; la clave pública de Supabase no reemplaza el JWT de usuario y no concede por sí misma acceso a datos. No se incluyen claves ni `.env` de ejemplo con valores reales.

## Pruebas

Desde la raíz del repositorio:

```sh
python -m pip install -r backend/requirements.txt
python -m pytest backend/tests
```

La suite usa `httpx.MockTransport`, no conecta a Supabase y no requiere credenciales: prueba bearer, aislamiento de libro, errores tipados/redacción, filtros/rangos inclusivos, USD, formas de contrato y cálculos exactos. Las pruebas de despliegue de JWT/RLS y el contrato real contra Supabase quedan pendientes.

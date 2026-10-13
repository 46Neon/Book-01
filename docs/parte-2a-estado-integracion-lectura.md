# Parte 2A — migración a Supabase Edge Functions y paridad de lectura

Estado al 13 de octubre de 2026. La arquitectura objetivo aprobada conserva Vite y Android como clientes y migra ambos servicios de servidor a Supabase Edge Functions (Deno/TypeScript). FastAPI permanece en el repositorio para revisión comparada; no se afirma despliegue ni paridad live.

## Rutas contables cubiertas por `accounting-api`

Las rutas se exponen bajo `/functions/v1/accounting-api/api/v1` y conservan sus contratos `/api/v1`:

| Ruta | Estado de código Edge |
|---|---|
| `GET /books` | Lista paginada bajo RLS. |
| `GET /books/{bookId}/accounts` | Lista paginada, `activeOnly`, jerarquía. Verifica acceso al libro antes de consultar cuentas. |
| `GET /books/{bookId}/journal-entries` | Fechas inclusivas, filtro de estado (valores de esquema `draft|posted|void`, por defecto `posted`), cuenta, moneda USD, búsqueda literal y cursor estable; totales corresponden a las líneas de la página. |
| `GET /books/{bookId}/accounts/{accountId}/balance` | Saldo hasta `asOf` calculado solo con entradas `posted` y lado normal por tipo de cuenta. |
| `GET /books/{bookId}/reports/trial-balance` | Débitos/créditos iniciales, del período y finales; fechas inclusivas y estados `posted`. |
| `GET /books/{bookId}/reports/income-statement` | Devuelve cero únicamente cuando no hay entradas `posted` en el período; devuelve `501 not_implemented` si las hay porque el esquema no distingue costo de gasto. No inventa clasificación ni importes. |

Todos los importes se calculan con centavos enteros `BigInt`, preservando el texto numérico de PostgREST antes de analizar JSON; no se convierten a `Number` para sumas. Las lecturas tienen límites de filas/respuesta, orden estable por clave de ordenación más UUID, parámetros allow-list y errores tipados que no propagan cuerpos del upstream. No hay DML ni SQL arbitrario.

## Seguridad e integración

- El bearer se valida con `GET /auth/v1/user`; las consultas PostgREST reenvían el mismo bearer y usan solamente `SUPABASE_PUBLISHABLE_KEY` o `SUPABASE_ANON_KEY`. RLS sigue siendo la autorización final; ausencia y falta de acceso al libro son indistinguibles.
- La URL destino se fija por configuración del proyecto. No hay URL introducida por usuario, proxy, redirecciones, `service_role`, ni integración Groq.
- Las rutas aceptan solo GET/OPTIONS y rechazan parámetros desconocidos, duplicados ambiguos, rango/UUID/moneda/estado inválidos, cursores fuera del límite y escrituras.
- Se conserva el esquema aditivo reciente de jerarquía de cuentas y contraparte. No se alteró ni eliminó la migración de propuestas/RPC.

## Pruebas y alcance de verificación

La suite Deno en `supabase/functions/accounting-api/index_test.ts` usa `fetch` simulado y cubre Auth, bearer/public key, aislamiento por libro, paginación y validación, cálculos decimales exactos, errores redactados, escritura rechazada e indicación de `redirect: "error"`. Se ejecuta con:

```sh
deno test supabase/functions/accounting-api/index_test.ts
```

El workflow `.github/workflows/supabase-functions.yml` verifica Edge Functions. El workflow `.github/workflows/supabase-db.yml` ejecuta Postgres 16, aplica migraciones y prueba RLS, invariantes, jerarquía/contraparte, RPCs y permisos REST. Son resultados separados: las pruebas mock de Deno no sustituyen Postgres.

**No se usó Supabase CLI, Dashboard, base remota, despliegue ni llamadas live durante esta fase.** No se inspeccionaron efectos de preview hospedado. La verificación con Auth/RLS real queda pendiente.

## Pendiente

- Portar el servicio conversacional como otra Edge Function; no se utilizó Groq ni se hicieron búsquedas web. Falta política aprobada de procesamiento de datos para Groq y proveedor de búsqueda aprobado.
- Fase 2B: wrapper/UI para propuestas y confirmación de RPC, separado de las lecturas; ningún endpoint Edge de escritura se habilita aquí.
- Pruebas reales de Supabase Auth/RLS, publicación del esquema y aislamiento multiusuario; configurar y verificar el hosting estático Vite.
- Comparación final de FastAPI contra Edge y aprobación del contrato canónico antes de retirar servicios FastAPI.

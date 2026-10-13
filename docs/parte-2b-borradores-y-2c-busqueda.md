# Parte 2B — borradores confirmados y Parte 2C — búsqueda web

**Estado: solo está implementada la capa transaccional de base de datos para propuestas y confirmación explícita de borradores.** La RPC no habilita API, interfaz, llamadas externas ni despliegue; no hay proveedor de búsqueda configurado. La migración aún no se ha aplicado al proyecto Supabase alojado y la función debe permanecer inutilizable desde clientes reales hasta completar pruebas live y revisar las políticas provisionales.

## 2B: contrato previo para borradores

La única operación futura permitida aquí sería crear o editar un asiento en estado `draft`. Nunca se debe contabilizar (`posted`), anular, cerrar un período ni ejecutar cambios de estado. El modelo puede proponer contenido, pero no puede autorizar una escritura, invocar una herramienta de escritura ni decidir que la confirmación ocurrió.

### Flujo obligatorio

1. Con el bearer del usuario autenticado y un `bookId` explícito, el servicio genera una propuesta tipada. La API contable devuelve el payload completo y canónico que se presentará; no usa SQL ni deja que el modelo invente identificadores de usuario, libro, período o cuentas fuera del payload que se valida.
2. La interfaz presenta **todos** los campos del payload: libro, fecha, período, descripción, moneda, cada cuenta/código/nombre, descripción de línea, debe y haber, y totales. No se permite confirmación implícita por continuar la conversación, respuesta ambigua, checkbox preseleccionado, tool call del modelo ni confirmación del lado del navegador que solo envíe `confirmed: true`.
3. El usuario pulsa una acción inequívoca de crear/guardar borrador tras ver el payload exacto. La confirmación queda vinculada a una propuesta inmutable, a su hash canónico, al `user_id`, al `book_id` y a una caducidad. Si cualquier campo cambia, incluso tras editar el borrador, se requiere mostrar y confirmar una propuesta nueva.
4. En el momento de confirmar, la API de confianza valida de nuevo bearer, identidad, rol y pertenencia al libro mediante la autorización de Supabase/RLS; no confía en que la propuesta o la membresía sigan vigentes desde el paso anterior. Solo `owner`, `admin` o `editor` pueden crear/editar un borrador; `viewer`, usuarios revocados y libros inaccesibles se rechazan. No aceptar identidad/rol del cuerpo ni un bearer sustituto.
5. Una sola operación transaccional inserta o actualiza el asiento y todas sus líneas. Solo persiste `status='draft'`; cualquier estado enviado por el cliente/modelo se rechaza. Debe ser idempotente: reintentar la misma confirmación y payload devuelve el mismo borrador, mientras que reusar su clave con otro hash falla sin duplicar datos.

### Validaciones del payload en servidor/base de datos

- El `book_id` está autorizado y todos los períodos, cuentas, asientos y líneas pertenecen al mismo libro. La clave externa compuesta existente protege la integridad entre libros.
- El período existe, está abierto en el instante de escritura y contiene la fecha del asiento. El disparador actual ya valida que la fecha cae en los límites del período, pero la escritura futura también debe validar `is_open` dentro de la transacción.
- Las cuentas existen, están activas y son del mismo libro; se acepta únicamente la moneda habilitada por el esquema actual (USD). No realizar conversiones ni inferir cuentas/clasificaciones.
- Descripción no vacía; al menos dos líneas y un límite finito de líneas por borrador; importes `NUMERIC` no negativos, finitos y con precisión de dos decimales; exactamente un lado positivo por línea; débitos y créditos iguales. Las sumas deben calcularse en servidor con aritmética decimal exacta.
- El servidor genera/controla la clave idempotente y comprueba su resultado existente antes de crear datos. El esquema ya impone unicidad `(book_id, idempotency_key)`; no exponer esa clave como una referencia contable.
- Los disparadores de auditoría existentes deben registrar actor y cambios en la misma transacción. Un error en cualquier línea debe revertir todo; no se admiten asientos parciales.

### Situación de permisos REST

La migración inicial `202610120001_initial_core.sql` sí concedía DML directo limitado a roles autenticados. La migración posterior `202610120002_read_only_rest_access.sql` revoca privilegios de todas las tablas Book-01 para `PUBLIC`, `anon` y `authenticated`, y conserva únicamente `SELECT` para `authenticated`. `supabase/tests/read_only_permissions.sql` comprueba que las escrituras directas sean denegadas. **Esto cierra el bypass en la configuración versionada una vez aplicadas ambas migraciones**, pero todavía no prueba el proyecto alojado: el README de Supabase registra que `Book-01-dev` existe y que las migraciones aún no se han aplicado allí.

La migración forward-only `202610130001_draft_proposals.sql` implementa únicamente la capa de base de datos: `public.create_draft_proposal(...)` crea un snapshot canónico inmutable ligado a `auth.uid()`, libro, hash SHA-256 y expiración de 10 minutos; `public.confirm_draft_proposal(proposal_id)` recibe solo el UUID opaco y confirma atómicamente un asiento `draft` con todas sus líneas. Revalida rol, propuesta, período abierto/fecha, cuentas activas del mismo libro y USD, precisión de importes y balance; serializa confirmaciones repetidas y devuelve el mismo asiento al reintentar. Las dos RPC tienen `search_path` vacío y objetos cualificados; `EXECUTE` se concede solo a `authenticated`. La tabla privada de propuestas no concede acceso directo a `anon` ni a `authenticated`, y siguen intactos los permisos read-only de las ocho tablas contables. No hay ruta para `posted` o `void`.

Los valores de política son **provisionales pendientes de revisión del owner**: roles habilitados `owner`/`admin`/`editor`, máximo 50 líneas y caducidad de 10 minutos. La creación/confirmación vía API, bearer real, presentación completa del payload y clic explícito humano no están implementados; ninguna interfaz/modelo puede llamar aún a estas RPC. La migración tampoco se ha aplicado a Book-01-dev. La ejecución de CI de Postgres 16 verifica solo el entorno simulado del repositorio, no Supabase alojado ni Auth real. Mantener la función sin uso externo hasta completar las pruebas live aisladas y aprobar esos valores.

La propuesta de escritura y la confirmación deben quedar vinculadas a usuario, libro, snapshot/hash canónico y expiración. La llamada de confirmación no aceptará del modelo ni del navegador un `status`, `user_id` o payload alternativo. Una reejecución idempotente del mismo consentimiento devolverá el mismo asiento; un intento de cambiar el contenido o usar otra propuesta fallará. Cualquier RPC privilegiada necesita `search_path` fijo vacío, objetos calificados, `EXECUTE` revocado por defecto y concedido solo a `authenticated` si la función autentica y valida explícitamente al llamante.

### Pruebas y bloqueos antes de habilitar escritura

`supabase/tests/draft_proposals.sql` y `supabase/tests/read_only_permissions.sql` ejercitan en CI la RPC con claims Auth simulados: idempotencia secuencial, solo draft, actor de auditoría, inmutabilidad/hash, autorización de viewer/otro actor/libro/membresía revocada, expiración, período cerrado/fecha/libro incorrectos, cuenta inactiva/ajena, formato/balance/límites de importes y líneas, rollback atómico ante fallo de línea y ausencia de DML/lectura directa. `supabase/tests/concurrent_confirmation.sh` además lanza dos sesiones PostgreSQL simultáneas para el mismo consentimiento y exige el mismo UUID y una única fila draft.

Sigue siendo obligatorio probar en un proyecto no productivo con JWT reales y usuarios/libros aislados, comprobar PostgREST directo denegado y que no exista ruta para `posted`/`void`. CI con Auth simulado no sustituye esa evidencia; la migración no está aplicada en Book-01-dev.

## 2C: contrato neutral para búsqueda web

No se encontró ni se aprobó proveedor de búsqueda en el repositorio. No se añadió uno. Una futura integración puede definir una interfaz interna de solo lectura (`search(query, limit, locale) -> results[]`) y mantener la elección del proveedor detrás del servidor; no debe aceptar URL arbitraria para fetch/proxy ni exponer claves al navegador.

Cada resultado debe tener como mínimo título, URL HTTPS validada, nombre de fuente, extracto no confiable y fecha/hora de recuperación. La respuesta diferencia hechos citados de inferencias y conserva URLs para que la persona compruebe las fuentes. Las páginas y extractos son datos no confiables: no obedecer instrucciones incluidas, no ejecutar código, no seguir herramientas de escritura y limitar longitud, cantidad, redirecciones y tiempo. No usar búsqueda para consultar cuentas privadas, nombres de clientes/proveedores, saldos, importes, prompts con datos contables u otra información del libro; no mezclar herramientas web y contables en una misma solicitud ni enviar resultados contables al proveedor web.

Antes de una llamada live se requiere proveedor concreto aprobado, documentación de autenticación/cuotas/región/retención/uso de datos, clave guardada únicamente como secreto del servidor, presupuesto/límites acordados y aprobación explícita de la política de datos. Sin esas decisiones, el contrato anterior no habilita llamadas externas. Las pruebas locales deben ser de contrato con respuestas sintéticas; cualquier prueba de proveedor debe ser opt-in y usar consultas públicas/no sensibles.

## Información/aprobaciones necesarias para avanzar

- `Book-01-dev` está documentado como proyecto de desarrollo en `supabase/README.md`, pero las migraciones aún no se han aplicado. Se requiere autorización expresa para aplicarlas allí (nunca producción), más dos usuarios Auth de prueba en libros separados y roles definidos. Guardar sus credenciales solo en Auth/secret store, no en el repositorio, logs ni esta conversación.
- Aprobación backend/owner del cambio de privilegios de tabla y del diseño de RPC transaccional anterior; confirmar que borradores los pueden crear `owner`, `admin` y `editor`, cuál será el límite de líneas y la caducidad de confirmación, y cómo se preserva la clave de idempotencia/auditoría.
- Para 2A/2B con modelo: aprobación explícita de enviar preguntas y resultados contables al proveedor/modelo seleccionado y su política de procesamiento/retención; después, guardar la clave del proveedor solo en secretos del hosting. No usarla para habilitar producción sin pruebas.
- Para 2C: proveedor de búsqueda y política de datos aprobados (incluidos región, retención, límites/costo); la clave se entrega al secret store del servidor, nunca al chat o al navegador.
- HTTPS, endpoint de API/chat y origen web de prueba, settings Auth issuer/audience, clave publishable/anon para PostgREST (no `service_role`), y autorización para ejecutar las pruebas live aisladas. No desplegar ni crear recursos hasta una aprobación separada.

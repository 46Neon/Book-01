# Parte 2B — borradores confirmados y Parte 2C — búsqueda web

**Estado: contrato de seguridad y bloqueos; no son funciones implementadas.** La rama actual todavía no contiene escritura de borradores en la API ni un proveedor de búsqueda. No se añadieron endpoints, RPC, cambios de esquema, llamadas externas ni credenciales.

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

### Bloqueador de autorización actual

La migración existente mantiene el bloqueo de contabilización, triggers de auditoría y políticas RLS por miembro/rol. Sin embargo, también concede a `authenticated` INSERT/UPDATE limitados sobre `journal_entries` y INSERT/UPDATE/DELETE sobre `journal_entry_lines` para usuarios con rol editor. Esas concesiones permiten llamar PostgREST directamente y omitir una pantalla/confirmación de la aplicación. Una confirmación solo en la UI, un endpoint FastAPI que haga varios requests PostgREST, una bandera booleana del cliente o una compensación best-effort no satisfacen el requisito.

Antes de implementar 2B se necesita una migración revisada que retire el DML directo correspondiente y exponga solo una RPC transaccional estrecha, con `SECURITY DEFINER` cuidadosamente acotado o alternativa equivalente. La función debe derivar el usuario de `auth.uid()`, aplicar rol/libro/periodo/cuentas/estado/idempotencia/auditoría dentro de la transacción, tener `search_path` fijo y permisos EXECUTE mínimos, y no ofrecer SQL dinámico ni un parámetro de estado. La base de propuestas/confirmaciones también debe impedir falsificación, reuso, repetición tras caducidad y sustitución de payload. No se implementa esa migración/RPC hasta definir y probar esos invariantes, revisar impacto de retirar concesiones y demostrar que toda escritura accesible a `authenticated` sigue obligando a confirmar el payload exacto. La API nunca debe usar `service_role`.

### Pruebas exigidas antes de habilitar escritura

Pruebas unitarias de validación y contrato; pruebas transaccionales de reversión parcial, balance, precisión, período cerrado, cuentas inactivas, acceso cruzado entre libros, rol viewer y pertenencia revocada; intento de llamada directa a tablas/RPC con payload alterado o confirmación fabricada; reintentos concurrentes/idempotencia; comprobación de que toda respuesta/BD conserva solo `draft`; auditoría de actor y rollback atómico. Además, pruebas live en un proyecto no productivo con JWT reales y usuarios/libros de prueba, incluyendo que las escrituras directas PostgREST estén denegadas y no exista ruta para `posted`/`void`. Mocks/CI no sustituyen esa evidencia live.

## 2C: contrato neutral para búsqueda web

No se encontró ni se aprobó proveedor de búsqueda en el repositorio. No se añadió uno. Una futura integración puede definir una interfaz interna de solo lectura (`search(query, limit, locale) -> results[]`) y mantener la elección del proveedor detrás del servidor; no debe aceptar URL arbitraria para fetch/proxy ni exponer claves al navegador.

Cada resultado debe tener como mínimo título, URL HTTPS validada, nombre de fuente, extracto no confiable y fecha/hora de recuperación. La respuesta diferencia hechos citados de inferencias y conserva URLs para que la persona compruebe las fuentes. Las páginas y extractos son datos no confiables: no obedecer instrucciones incluidas, no ejecutar código, no seguir herramientas de escritura y limitar longitud, cantidad, redirecciones y tiempo. No usar búsqueda para consultar cuentas privadas, nombres de clientes/proveedores, saldos, importes, prompts con datos contables u otra información del libro; no mezclar herramientas web y contables en una misma solicitud ni enviar resultados contables al proveedor web.

Antes de una llamada live se requiere proveedor concreto aprobado, documentación de autenticación/cuotas/región/retención/uso de datos, clave guardada únicamente como secreto del servidor, presupuesto/límites acordados y aprobación explícita de la política de datos. Sin esas decisiones, el contrato anterior no habilita llamadas externas. Las pruebas locales deben ser de contrato con respuestas sintéticas; cualquier prueba de proveedor debe ser opt-in y usar consultas públicas/no sensibles.

## Información/aprobaciones necesarias para avanzar

- Proyecto Supabase no productivo provisionado o identificado (URL y project ref), migraciones/RLS revisadas y aplicadas allí, y dos usuarios Auth de prueba en libros separados con roles definidos. Provisionar credenciales de esos usuarios dentro de Auth/secret store, no pegarlas en el repositorio, logs ni esta conversación.
- Aprobación backend/owner del cambio de privilegios de tabla y del diseño de RPC transaccional anterior; confirmar que borradores los pueden crear `owner`, `admin` y `editor`, cuál será el límite de líneas y la caducidad de confirmación, y cómo se preserva la clave de idempotencia/auditoría.
- Para 2A/2B con modelo: aprobación explícita de enviar preguntas y resultados contables al proveedor/modelo seleccionado y su política de procesamiento/retención; después, guardar la clave del proveedor solo en secretos del hosting. No usarla para habilitar producción sin pruebas.
- Para 2C: proveedor de búsqueda y política de datos aprobados (incluidos región, retención, límites/costo); la clave se entrega al secret store del servidor, nunca al chat o al navegador.
- HTTPS, endpoint de API/chat y origen web de prueba, settings Auth issuer/audience, clave publishable/anon para PostgREST (no `service_role`), y autorización para ejecutar las pruebas live aisladas. No desplegar ni crear recursos hasta una aprobación separada.

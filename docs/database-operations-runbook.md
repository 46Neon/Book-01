# Runbook operativo de base de datos — Book-01 (borrador)

> **Estado: propuesta para revisión del propietario; no es una autorización operativa ni una declaración de preparación para producción.** No se han ejecutado copias, restauraciones, migraciones, SQL, despliegues ni cambios en Supabase al preparar este documento.

## 1. Alcance y estado conocido

Este runbook cubre exclusivamente la base PostgreSQL/Supabase de Book-01 Parte 1: preparación de migraciones, copia y restauración, y recuperación ante errores. No cubre la API, Android, Groq, MCP ni la operación de Parte 2.

A la fecha de esta revisión, la referencia de GitHub `main` inspeccionada fue `8a3c8a1d4482a32406dc4d4c2d6bc6b41ba72f2f` (13 de octubre de 2026). El proyecto Supabase alojado actualmente es **Book-01-dev**, ref `qjbduqwwpbnyxwyxfdsj`; no existe un proyecto de producción separado. Según el encargo y el estado documentado en `supabase/README.md`, las cuatro migraciones abajo están aplicadas en este orden. No hay datos contables reales ni usuarios Auth de prueba en el proyecto. Este estado no acredita una prueba de restauración ni preparación para producción.

| Orden | Migración | Alcance resumido |
| --- | --- | --- |
| 1 | `202610120001_initial_core.sql` | Núcleo multi-libro, tablas contables USD, restricciones, auditoría, triggers y RLS. |
| 2 | `202610120002_read_only_rest_access.sql` | Retira DML directo de roles de aplicación y deja lectura directa para `authenticated`. |
| 3 | `202610130001_draft_proposals.sql` | Tabla/RPC estrechas para propuestas y confirmación de borradores; no habilita contabilización ni reversas y las rutas aún no están expuestas. |
| 4 | `202610130002_account_hierarchy_counterparty.sql` | Añade padre de cuenta y contraparte opcionales; los valores históricos permanecen `NULL`; no añade grants DML de cliente. |

Las migraciones están versionadas en `supabase/migrations/`. El workflow `.github/workflows/supabase-db.yml` aplica las migraciones en PostgreSQL 16 de CI con bootstrap Auth solo de prueba y ejecuta las pruebas SQL del repositorio. Esto comprueba el conjunto de pruebas local/CI, **no** el proyecto alojado, su historial, permisos, restauración, Auth/RLS con identidades reales ni la correspondencia entre despliegue y commit. El Edge Function desplegado en Book-01-dev tampoco tiene commit remoto confirmado y el README informa cero invocaciones. No usar estos hechos como evidencia de una restauración ensayada.

No hay actualmente una política aprobada de copias, retención, PITR, RPO/RTO, recuperación de producción, hosting, dominio o proveedor de hosting. El plan/tipo de backup efectivo de Book-01-dev tampoco se da por verificado aquí. No asumir cobertura, frecuencia, retención, descarga, restauración o precio específicos.

## 2. Límites de seguridad y alcance de las copias

- Tratar toda copia, exportación lógica, archivo de roles, registro de recuperación y credencial de conexión como material sensible. Acceso mínimo, almacenamiento protegido y separado del proyecto de origen; no incluir copias, tokens, contraseñas, cadenas de conexión ni secretos en Git, incidencias públicas, chat o logs.
- No poner `service_role` en Android, navegador, solicitudes de usuario, ni en tráfico de usuario de la API. La arquitectura actual de lectura reenvía JWT del usuario y clave publicable, y depende de RLS. No convertir un problema de recuperación en acceso privilegiado de aplicación.
- Las copias de base de datos documentadas por Supabase no incluyen los objetos almacenados mediante Storage API; si Book-01 llegara a usar objetos, definir y ensayar una protección/restauración separada. No se conoce que haya objetos de usuario que deban recuperarse hoy.
- Una copia de seguridad no sustituye las migraciones versionadas, las pruebas ni la copia local cifrada de respaldo de Android. La copia Android Room v1 es una función offline distinta y deliberadamente no restaura sobre una base ya personalizada o con historial; no es una copia del PostgreSQL alojado.
- No restaurar encima de un entorno con escrituras sin aprobación explícita del propietario y sin haber protegido primero el estado actual. Una restauración a un punto anterior puede descartar cambios válidos posteriores.

## 3. Decisiones que bloquean una política operativa

Antes de afirmar que existe una protección de producción, el propietario debe decidir y documentar:

1. Qué proyectos y ambientes existirán, quién es dueño de cada uno y cómo se confirmará el destino antes de cada operación. Crear/provisionar un proyecto de producción está pendiente.
2. Método aprobado de backup: función/backup disponible para el proyecto y plan concretos, exportación lógica manual, u otra opción que el propietario verifique. Las capacidades de panel, backups físicos/lógicos, descarga, restauración y PITR dependen del proyecto/configuración/plan y deben comprobarse directamente; los enlaces oficiales al final explican opciones generales, no la cobertura de Book-01-dev.
3. Frecuencia y retención, copia fuera del proyecto, cifrado, custodios, control de acceso, ubicación y procedimiento de borrado seguro. Todo está **TBD**; no se infiere de valores publicados por el proveedor.
4. Si se habilitaría PITR, su periodo/configuración y la aceptación de costos/riesgos. Está **TBD**, no verificado ni aprobado.
5. RPO/RTO, ventanas de cambio, tiempo máximo de indisponibilidad y quién puede aprobar una restauración que sobrescriba datos.
6. Responsable/segundo revisor de migraciones, autorizadores de cambios de producción y método operativo aprobado para aplicarlas. No hay proceso de producción acordado.
7. Cadencia, ambiente aislado, criterios de éxito y registro de simulacros de restauración.
8. Si luego se aprueba hosting web, dominio y proveedor, decisión separada y aún pendiente; no afecta a las capacidades de backup de base de datos.

## 4. Preparación antes de una migración

Realizar estas comprobaciones en una futura ventana autorizada; este runbook no autoriza a ejecutarlas ahora:

1. **Identidad del destino:** el responsable confirma por el canal de cambio la etiqueta del proyecto y su ref exacto en el panel; dos personas revisan que el destino sea el ambiente previsto. Nunca deducir el destino de una variable local, nombre parecido o valor de CI. No apuntar una tarea de desarrollo a producción.
2. **Revisión del cambio:** PR separado, migración nueva con timestamp/nombre único, revisión SQL por una segunda persona y descripción de efectos, objetos alterados, datos que transforma, bloqueos/duración esperados, compatibilidad con la versión desplegada y plan de avance correctivo. No editar, renumerar ni eliminar un archivo que ya se aplicó en un entorno compartido.
3. **Pruebas:** ejecutar el workflow de base de datos y revisar su resultado en el commit candidato. Probar la secuencia completa de las cuatro migraciones más la nueva sobre PostgreSQL de CI/local y datos ficticios; añadir pruebas para nuevas invariantes/permisos. Las pruebas de CI no son una autorización ni una validación live.
4. **Estado remoto:** comparar el historial registrado de migraciones con la secuencia de archivos del commit candidato y comprobar el esquema/permisos relevantes. Investigar toda diferencia antes de continuar; no marcar/aplicar migraciones a ciegas. `migration repair`, si alguna vez se utiliza, solo corrige la tabla de historial y no ejecuta ni revierte SQL; su uso requiere confirmar por evidencia que el esquema ya coincide y aprobación del responsable.
5. **Protección previa:** verificar, no presumir, que hay una copia utilizable de ese destino; registrar método, fecha/hora, punto o artefacto, ubicación segura y responsable. Si el método de recuperación no está disponible o no fue probado, detener y elevarlo. La política/retención aún no está aprobada.
6. **Compatibilidad y operación:** revisar el comportamiento de cliente/API con esquema nuevo y antiguo, superficie RLS/grants/triggers/RPC y dependencias de función. En el esquema actual el API desplegado es de lectura; no habilitar escritura directa ni ampliar DML por conveniencia.
7. **Autorización y comunicación:** obtener aprobación explícita para destino/ventana/impacto. Anunciar inicio y criterio de detención. No operar si hay ambigüedad de proyecto, migraciones divergentes, resultados CI fallidos o una copia no verificable.

## 5. Despliegue y recuperación de una migración fallida

El estándar recomendado para el futuro es **forward-only**: conservar cada migración aplicada y reparar errores con una migración correctiva nueva, revisada y probada, que preserve datos e invariantes siempre que sea posible. Las migraciones actuales no traen scripts de rollback inverso; no prometer que un `down` genérico sea seguro. Particularmente, reversar cambios aditivos puede eliminar valores nuevos o romper dependencias, mientras que cambios de permisos/triggers pueden dejar una superficie insegura.

Si el proceso de migración falla o queda en estado incierto:

1. Detener nuevos cambios y solicitudes de despliegue. No reintentar ni continuar con archivos posteriores hasta conocer el estado.
2. Conservar salida/fecha/commit y quién operaba, sin guardar secretos. Inspeccionar esquema y tabla de historial en el destino, con personal autorizado, para establecer qué sentencias/objetos se aplicaron realmente; no inferir atomicidad a partir del fallo. El workflow de CI ejecuta SQL secuencialmente y las migraciones no están envueltas universalmente en una transacción explícita; una ejecución remota puede dejar cambios parciales.
3. Proteger el estado posterior al fallo antes de cualquier reparación, si la capacidad aprobada lo permite. No ejecutar sentencias destructivas o manuales improvisadas.
4. Si el cambio quedó completamente aplicado y el historial está desfasado, tratarlo como discrepancia de historial, no volver a ejecutar todo: confirmar el esquema y usar solo el procedimiento aprobado de reconciliación. Una reparación de historial no cambia datos/esquema.
5. Si quedó parcialmente aplicado, preparar una migración forward que haga explícito el estado actual, sea idempotente solo donde se haya probado y termine en el esquema/invariantes esperados. Revisarla y probarla antes de aplicarla. Si no se puede demostrar seguridad, detenerse y pedir revisión profesional/soporte antes de tocar datos.
6. Verificar después esquema, grants, RLS, funciones/triggers, historial, pruebas focalizadas y lecturas autorizadas; anotar commit, destino y resultado. Una aprobación CI sola no acredita el estado remoto.

**Rollback destructivo no es respuesta rutinaria.** Restaurar una copia anterior puede eliminar transacciones/usuarios/configuración posteriores, no solo deshacer el DDL. Solo considerar restaurar si la reparación forward no es segura o el dueño elige explícitamente esa pérdida: aprobación escrita del propietario, incidente y alcance documentados, destino confirmado por dos personas, copia del estado actual protegida, punto/fecha elegidos explícitamente, impactos explicados y plan de reconciliación. Preferir primero una restauración a un proyecto aislado/nuevo para inspección; no sobreescribir Book-01-dev ni un futuro proyecto de producción como prueba.

## 6. Copia de seguridad: procedimiento objetivo (cuando se apruebe)

1. Confirmar con el propietario el destino, el método, la fecha/hora requerida y el propósito (copia preventiva, antes de migración o exportación manual). No afirmar que una copia automática existe hasta verla disponible para ese proyecto.
2. En el panel/documentación vigente, confirmar el tipo de backup utilizable, la fecha/punto, plan y elegibilidad del proyecto, qué incluye, procedimiento de recuperación y cualquier limitación relevante. No asumir que backups descargables y restauración de proyecto usan el mismo formato/capacidad.
3. Si se aprueba exportación lógica manual, usar el proceso vigente de Supabase CLI/pg_dump documentado por Supabase y guardar las partes requeridas —por ejemplo esquema/datos/roles cuando proceda— en almacenamiento aprobado fuera del proyecto, cifrado y con acceso restringido. La página oficial indica que hay que proteger la cadena de conexión y distingue dumps de esquema, datos y roles. No copiar sus ejemplos con secretos en documentos o CI. Este borrador deliberadamente no incluye comandos ni credenciales.
4. Registrar un manifiesto no secreto: proyecto/ref, método, fecha/hora UTC, rango/punto, versiones de CLI/herramientas, nombres y tamaños de artefactos, resultado/errores, cifrado/ubicación lógica, responsable y revisión. No incluir connection strings, JWT, contraseñas o contenido financiero.
5. Comprobar integridad/disponibilidad del artefacto mediante el método aprobado, sin exponer datos; confirmar que una persona autorizada distinta puede localizarlo y que existe acceso al material necesario para descifrarlo. No almacenar clave de cifrado junto a la copia.
6. La frecuencia, retención, copia secundaria, rotación, prueba de restauración y alerta de fallos quedan **TBD** hasta decisión del propietario. Para Book-01-dev no hacer ahora una copia; no hay aprobación de operación en el encargo.

## 7. Simulacro seguro de restauración para Book-01-dev

Objetivo: comprobar procedimiento y artefactos sin arriesgar el proyecto alojado actual ni datos reales.

1. Esperar aprobación específica del propietario. Mantener Book-01-dev como origen intacto. No restaurar sobre él.
2. Elegir un proyecto efímero y aislado de desarrollo/restauración que el propietario haya autorizado y cuya ref se haya verificado por dos personas. Si no existe un destino aprobado, detener el simulacro; no crear ni modificar recursos durante este borrador.
3. Usar una copia aprobada del estado de desarrollo o un respaldo generado a partir de datos completamente ficticios. Actualmente no hay datos contables reales ni Auth users en Book-01-dev; si una copia futura contiene cuentas/configuración/datos de prueba, protegerla como sensible de todos modos. No importar datos reales para ensayar.
4. Restaurar mediante el método oficial elegido específicamente para el tipo de copia y proyecto de destino, siguiendo las instrucciones vigentes de Supabase. No mezclar restauración nativa/backup físico con restauración lógica/manual sin confirmar el procedimiento. Comprobar extensiones, configuración Auth, roles y dependencias que correspondan; las copias diarias documentadas no incluyen contraseñas de roles personalizados. No asumir que objetos de Storage se restauran con la base.
5. Comparar historial de migraciones y esquema restaurado con el manifiesto de origen y el commit correspondiente. No aplicar de nuevo las cuatro migraciones a una base ya restaurada sin determinar primero qué esquema e historial contiene; no reparar historial hasta validar que el esquema coincide.
6. Ejecutar en el destino aislado las pruebas SQL pertinentes del repo, revisar invariantes, triggers, grants y estado RLS. La prueba existente de CI es test-only y no sustituye pruebas Auth live; Book-01 carece de usuarios/tokens reales, así que no crear usuarios durante el simulacro sin aprobación separada y plan controlado.
7. Registrar duración, errores, datos de prueba, diferencias, barreras y si se alcanzaron RPO/RTO definidos (solo cuando el propietario los apruebe). Si no se logra completar la restauración, declarar el simulacro fallido y corregir el procedimiento, no tocar el origen.
8. Tras validación y aprobación, retirar el entorno desechable por el procedimiento autorizado y registrar la disposición. La eliminación de proyectos y de sus backups asociados puede ser irreversible; requiere aprobación explícita y verificación doble del destino.

Este simulacro no se ha ejecutado. No demuestra recuperación de un futuro entorno de producción.

## 8. Criterios mínimos de cierre de un incidente/restauración

No declarar recuperación hasta documentar: proyecto/ref y punto restaurado, autorización, copia preincidente protegida, historial de migraciones y commit reconciliados, salud de esquema/constraints/triggers, grants/RLS revisados, pruebas aprobadas, diferencias o pérdida de datos conocida, identidad/servicios que se habilitaron, validador independiente, hora de retorno, y acciones pendientes. Confirmar que ninguna clave privilegiada quedó en cliente, repositorio o logs. Notificar al propietario antes de reabrir escrituras.

## 9. Fuentes y documentos relacionados

- [Supabase: Database Backups](https://supabase.com/docs/guides/platform/backups) — tipos y disponibilidad general de backups, límites del contenido, acceso al panel y referencia a exportaciones/PITR. La elegibilidad concreta de Book-01-dev debe verificarse en su panel/plan.
- [Supabase: Backup and Restore using the CLI](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore) — flujo oficial de exportación/restauración lógica y dependencias; los ejemplos con cadenas de conexión/secret keys requieren manejo seguro y no se reproducen aquí.
- [Supabase: Database Migrations](https://supabase.com/docs/guides/deployment/database-migrations) — flujo de migraciones y reconciliación de tabla de historial. La reparación de historial no aplica ni revierte SQL.
- [`supabase/README.md`](../supabase/README.md) — arquitectura, migraciones, estado live observado y límites.
- [`docs/README.md`](README.md) — índice/estado documental actual.
- [`docs/supabase-live-rls-smoke-test.md`](supabase-live-rls-smoke-test.md) — smoke test opt-in de solo lectura, que requiere Auth users y JWTs reales autorizados; no se ha ejecutado.
- [`docs/adr-supabase-postgres-y-groq.md`](adr-supabase-postgres-y-groq.md) — decisiones y pendientes de arquitectura/secretos.

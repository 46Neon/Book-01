# Arquitectura base — Parte 1

## Diagrama

```text
APK Android
  Jetpack Compose → casos de uso → repositorios
                                  ├── Room / SQLite (fuente local para UI)
                                  └── sync HTTPS
                                         ↓
                                API contable → dominio → PostgreSQL
                                                ├── validación
                                                ├── autorización
                                                └── auditoría

Chat de otro mantenedor → MCP/API pública documentada → mismos casos de uso
```

El bloque de chatbot/MCP se muestra solo para definir el límite de integración: **no forma parte de la implementación de Parte 1**.

## Decisiones base

- **Android:** Kotlin + Jetpack Compose; navegación y componentes de tabla adaptativos.
- **Persistencia local:** Room/SQLite, con migraciones versionadas.
- **Trabajo diferido:** WorkManager para reintentos de sincronización; la UI muestra pendiente/sincronizado/error y no promete sincronización inmediata.
- **Servidor:** API HTTPS documentada con OpenAPI y PostgreSQL gestionado en Supabase. El framework backend queda por confirmar antes de iniciar ese módulo.
- **Límite arquitectónico:** APK, web y chatbot no acceden directamente a tablas ni ejecutan SQL; las operaciones pasan por la API y el dominio contable.
- **Aislamiento:** cada solicitud se autoriza por libro/empresa en servidor; las políticas RLS de PostgreSQL/Supabase serán una defensa adicional, no sustituyen las validaciones del dominio.
- **Secretos:** `GROQ_API_KEY`, credenciales de conexión y cualquier clave Supabase privilegiada viven solo en el servidor/secrets del despliegue; nunca en APK, navegador, Git ni logs.

## Datos contables iniciales

- `accounts`: identificador, código, nombre, tipo, cuenta padre y estado.
- `journal_entries`: identificador, fecha contable, descripción, estado, periodo, creador y referencia.
- `journal_lines`: identificador, asiento, cuenta, débito/crédito exactos, moneda y contraparte opcional.
- `attachments`: documento de soporte y metadatos de almacenamiento.
- `periods`: ejercicio, periodo y estado (abierto/cerrado).
- `audit_events`: actor, acción, objeto, momento, resultado y referencia de corrección.
- `sync_outbox`: operaciones pendientes con clave idempotente y estado de sincronización.

Usar identificadores estables. Representar el importe mediante decimal exacto en la API y una representación exacta en la base de datos. En Android se debe definir precisión por moneda; nunca usar coma flotante binaria para cálculos monetarios.

## Sincronización

1. Crear borrador local y evento de salida en una misma transacción Room.
2. Subir por HTTPS usando una clave de idempotencia.
3. El servidor valida propiedad, periodo, cuentas, moneda e invariantes y confirma atómicamente.
4. Descargar cambios confirmados y actualizar Room en una transacción.
5. Mostrar estados de cola y errores recuperables; conservar datos si falla la red.
6. No aplicar last-write-wins a un asiento contabilizado.

## Seguridad y operación

- Autenticación y autorización en servidor; permisos por libro/empresa y por operación.
- Identidad del usuario/tenant derivada de credenciales verificadas, no de campos que el cliente pueda manipular.
- HTTPS, secretos por entorno, logs sin credenciales ni datos financieros innecesarios.
- Backups y restauración probados; exportación elegida por el usuario.
- Pruebas de migraciones, idempotencia, concurrencia, pérdida de red y restauración.

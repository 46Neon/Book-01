# Arquitectura base — Parte 1

## Diagrama

```text
Android (Room / SQLite) ─┐
Web (Vite) ──────────────┼── HTTPS + sesión Supabase Auth ──> Supabase Edge Functions (Deno/TypeScript)
                         │                                      ├── accounting-api → PostgREST (JWT usuario + clave pública)
Conversational client ───┘                                      └── conversational function (pendiente)
                                                                    ↓
                                                         PostgreSQL + RLS
                                                         ├── tenant/book authorization
                                                         ├── accounting invariants
                                                         └── audit / guarded RPCs

FastAPI accounting + conversational services: permanecerán en el repo durante la revisión de paridad;
no son el destino de despliegue aprobado ni se eliminan en esta fase.
```

El destino de migración aprobado para ambos servicios del servidor es Supabase Edge Functions. La API contable ya tiene una implementación Edge de solo lectura en curso; la función conversacional todavía no está portada. No se afirma despliegue ni paridad live.

## Decisiones base

- **Android:** Kotlin + Jetpack Compose; navegación y componentes de tabla adaptativos.
- **Persistencia local:** Room/SQLite, con migraciones versionadas.
- **Trabajo diferido:** WorkManager para reintentos de sincronización; la UI muestra pendiente/sincronizado/error y no promete sincronización inmediata.
- **Servidor/destino:** ambos servicios FastAPI se portan a Supabase Edge Functions (Deno/TypeScript), con PostgreSQL gestionado por Supabase; Vite y Android siguen siendo clientes. FastAPI queda durante revisión de paridad.
- **Límite arquitectónico:** APK, web y conversación no acceden directamente a tablas ni ejecutan SQL; las lecturas pasan por el accounting Edge API y cualquier escritura futura por RPCs autorizados y confirmados.
- **Aislamiento:** la Edge Function verifica el bearer con Supabase Auth y reenvía el mismo JWT con la publishable/anon key a PostgREST; RLS es la autorización final, además de la comprobación explícita de libro en API.
- **Secretos:** ninguna clave `service_role` va en tráfico de usuario. `GROQ_API_KEY` y cualquier clave privilegiada viven solo como secretos del servidor, nunca en APK, navegador, Git ni logs; Groq requiere una aprobación de privacidad antes de habilitarse.

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

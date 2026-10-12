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
- **Servidor:** API HTTPS con OpenAPI y PostgreSQL. El framework backend queda por confirmar antes de iniciar ese módulo.
- **Límite arquitectónico:** interfaz y chatbot no acceden directo a tablas; todas las escrituras pasan por el dominio contable y se validan del lado servidor.

## Datos contables iniciales

- `accounts`: identificador, código, nombre, tipo, cuenta padre y estado.
- `journal_entries`: identificador, fecha contable, descripción, estado, periodo, creador y referencia.
- `journal_lines`: identificador, asiento, cuenta, débito/crédito exactos, moneda y contraparte opcional.
- `attachments`: documento de soporte y metadatos de almacenamiento.
- `periods`: ejercicio, periodo y estado (abierto/cerrado).
- `audit_events`: actor, acción, objeto, momento, resultado y referencia de corrección.
- `sync_outbox`: operaciones pendientes con clave idempotente y estado de sincronización.

Usar identificadores estables. En Android, los importes USD se guardan como centavos enteros (`Long`) y el texto se analiza con semántica decimal exacta (`BigDecimal`), sin coma flotante binaria. La presentación usa la etiqueta explícita `US$` y dos decimales; las formas numéricas ambiguas se rechazan, no se interpretan por inferencia. En la API futura se transmitirán valores decimales exactos junto con el código ISO de moneda. La versión inicial del libro admite únicamente USD: no convierte ni mezcla monedas. Agregar otra moneda requerirá precisión, reglas de cambio y migraciones explícitas.

La base local Room está en versión 3. La migración 1→2 crea `book_settings` con el libro inicial en USD; la migración 2→3 agrega `cashOrBank` a las cuentas y marca los códigos iniciales `1000` (Caja) y `1010` (Banco). Ambas son aditivas y conservan las cuentas, asientos y líneas ya almacenados. El catálogo actual permite agregar cuentas y las expone también cuando están inactivas para resolver el historial; no permite editar ni borrar cuentas. Las pantallas de captura solo ofrecen categorías activas y cuentas activas marcadas Caja/Banco. Si quedan registros heredados con otro código de moneda, la UI los muestra con el código original y los excluye de los totales USD; no los convierte. Solo se admiten nuevos asientos en USD.

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

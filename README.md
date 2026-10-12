# Book-01

Libro contable Android de uso familiar para contadores: una interfaz de tabla inspirada en hojas de cálculo, con controles contables y trazabilidad.

## Alcance

Este repositorio corresponde a la **Parte 1**: núcleo del libro contable, backend contable y APK Android. La interfaz conversacional, Groq y el servidor MCP serán responsabilidad de otro mantenedor y no se implementan en esta parte.

La Parte 1 expondrá una API documentada para que el chatbot consulte y proponga operaciones sin acceder directamente a la base de datos. Ver [`docs/contrato-integracion-chat.md`](docs/contrato-integracion-chat.md).

## Experiencia

- Pestañas de Diario, Mayor, Cuentas, Balance y Resultados.
- Filas, columnas, encabezados, totales y navegación inspirados en hojas de cálculo.
- Plan de cuentas tipo hoja de cálculo con alta de cuentas; los códigos y tipos se mantienen para proteger el histórico.
- Balance de comprobación local por cuenta en USD; confirma que débitos y créditos totales cuadren.
- Presentación adaptada a teléfono y tablet.
- Captura local y, más adelante, sincronización segura.
- Borradores editables; asientos contabilizados trazables y corregibles mediante reversas o ajustes.

## Stack

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización futura: WorkManager.
- Backend planeado: API HTTPS documentada con OpenAPI y PostgreSQL.
- Importes exactos; no usar punto flotante para dinero.

## Estado actual

La APK permite registrar localmente ingresos y gastos sencillos, agregar cuentas al plan y consultar el balance de comprobación. El libro inicial trabaja en USD: los importes se almacenan como centavos enteros (`Long`), se analizan con `BigDecimal` (sin `Double`) y se muestran como `US$` con dos decimales. Acepta formatos estadounidense y español inequívocos y rechaza entradas ambiguas. No convierte ni mezcla monedas. Las migraciones Room v1→v2→v3 conservan asientos y cuentas y añaden la configuración USD y la clasificación de Caja/Banco. Sigue siendo un prototipo: faltan Mayor completo, edición/reversas y auditoría, backend/API, sincronización, reportes completos y validación profesional; no usar todavía para libros reales.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

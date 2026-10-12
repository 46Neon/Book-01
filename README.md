# Book-01

Libro contable Android de uso familiar para contadores: una interfaz de tabla inspirada en hojas de cálculo, con controles contables y trazabilidad.

## Alcance

Este repositorio corresponde a la **Parte 1**: núcleo del libro contable, backend contable y APK Android. La interfaz conversacional, Groq y el servidor MCP serán responsabilidad de otro mantenedor y no se implementan en esta parte.

La Parte 1 expondrá una API documentada para que el chatbot consulte y proponga operaciones sin acceder directamente a la base de datos. Ver [`docs/contrato-integracion-chat.md`](docs/contrato-integracion-chat.md).

## Experiencia

- Pestañas de Diario, Mayor, Cuentas, Balance y Resultados.
- Filas, columnas, encabezados, totales y navegación inspirados en hojas de cálculo.
- Presentación adaptada a teléfono y tablet.
- Captura local y, más adelante, sincronización segura.
- Borradores editables; asientos contabilizados trazables y corregibles mediante reversas o ajustes.

## Stack

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización futura: WorkManager.
- Backend planeado: API HTTPS documentada con OpenAPI y PostgreSQL.
- Importes exactos; no usar punto flotante para dinero.

## Estado actual

La APK de depuración muestra el Diario y permite guardar localmente ingresos y gastos sencillos, generando sus dos líneas balanceadas. El libro inicial trabaja únicamente en USD: los importes se almacenan como centavos enteros (`Long`), el análisis decimal es exacto con `BigDecimal` (sin `Double`), y los importes se presentan con la etiqueta explícita `US$` y dos decimales. Acepta formatos estadounidense y español inequívocos; rechaza, en vez de adivinar, entradas ambiguas como `1.234`. No hay conversión de cambio ni soporte multi-moneda. La actualización de Room de v1 a v2 crea la configuración USD conservando los asientos y líneas existentes; si hay registros previos con otro código, se muestran con su código original y se excluyen de los totales USD, sin conversión. Solo se pueden contabilizar movimientos nuevos en USD. Es un prototipo, no un sistema contable de producción: faltan configuración fiscal, edición/reversiones y auditoría completa, backend, sincronización, reportes completos y validación profesional.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

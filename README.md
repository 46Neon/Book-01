# Book-01

Libro contable Android de uso familiar para contadores: una interfaz de tabla inspirada en hojas de cálculo, con controles contables y trazabilidad.

## Alcance

Este repositorio corresponde a la **Parte 1**: núcleo del libro contable, backend contable y APK Android. La interfaz conversacional, Groq y el servidor MCP serán responsabilidad de otro mantenedor y no se implementan en esta parte.

La Parte 1 expondrá una API documentada para que el chatbot consulte y proponga operaciones sin acceder directamente a la base de datos. Ver [`docs/contrato-integracion-chat.md`](docs/contrato-integracion-chat.md).

## Experiencia

- Pestañas de Diario, Mayor, Cuentas, Balance y Resultados.
- Filas, columnas, encabezados, totales y navegación inspirados en hojas de cálculo.
- Balance de comprobación local por cuenta en USD; confirma que débitos y créditos totales cuadren.
- Presentación adaptada a teléfono y tablet.
- Captura local y, más adelante, sincronización segura.
- Los asientos contabilizados se conservan intactos y se corrigen mediante reversas locales trazables; el flujo de borradores aún no está implementado.
- La sección de Trazabilidad desde «Más» permite consultar contabilizaciones/reversas y solicitar una reversa con fecha y motivo.

## Stack

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización futura: WorkManager.
- Backend planeado: API HTTPS documentada con OpenAPI y PostgreSQL.
- Importes exactos; no usar punto flotante para dinero.

## Estado actual

La aplicación incluye Diario, Plan de cuentas, Mayor, Balance de comprobación y, desde «Más», estados financieros y cierre de periodo. Este hito añade cierres inclusivos que crean un asiento USD nuevo, balanceado y no editable: salda actividad de ingresos/gastos publicada durante las fechas y transfiere la utilidad o pérdida a una cuenta activa de patrimonio; registra metadatos append-only y auditoría en una transacción; rechaza intervalos superpuestos; y bloquea registros o fechas de reversa dentro de periodos cerrados. Los cierres no se pueden reabrir ni reversar desde la función ordinaria. Los estados de resultados conservan la actividad previa al cierre y el balance general no vuelve a sumar los resultados ya transferidos; toda diferencia de conciliación se expone sin ajuste automático. Las reversiones se procesan en sus propias fechas. Room migra de forma aditiva v1→v6 y conserva los datos previos. Los importes son centavos USD enteros (`Long`), con aritmética exacta y sin conversión monetaria. El prototipo no afirma cumplimiento contable, fiscal ni legal en Venezuela ni en otra jurisdicción y requiere revisión profesional antes de cualquier uso oficial. No se debe considerar completa la Parte 1 por este hito: siguen pendientes API/backend de producción, sincronización, copias de seguridad/restauración, importación/exportación, borradores, hardening y revisión profesional. La compilación, las pruebas y el runtime instrumentado de esta revisión aún deben verificarse en CI; el emulador ha presentado problemas de arranque en la ejecución anterior.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

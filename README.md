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

La APK de depuración muestra el Diario y permite guardar localmente ingresos y gastos sencillos, generando sus dos líneas balanceadas. El libro inicial está configurado en USD: los importes se almacenan en centavos enteros, se formatean explícitamente como US$, se aceptan formatos numéricos estadounidense y español sin usar coma flotante y se rechazan entradas ambiguas. No hace conversión de moneda. Todavía no es un sistema contable de producción: faltan configuración fiscal, edición/reversiones, backend, sincronización y reportes completos.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

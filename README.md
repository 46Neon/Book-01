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
- Borradores editables; asientos contabilizados trazables y corregibles mediante reversas o ajustes.

## Stack

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización futura: WorkManager.
- Backend planeado: API HTTPS documentada con OpenAPI y PostgreSQL.
- Importes exactos; no usar punto flotante para dinero.

## Estado actual

La APK de depuración muestra el Diario, el Plan de cuentas en cuadrícula y el Balance de comprobación local; permite guardar ingresos y gastos sencillos, generando dos líneas balanceadas. Se pueden agregar cuentas con código, nombre y clasificación. El libro inicial trabaja únicamente en USD: los importes se almacenan como centavos enteros (`Long`), el análisis decimal es exacto con `BigDecimal` (sin `Double`), y se presentan con la etiqueta `US$` y dos decimales. Acepta formatos estadounidense y español inequívocos; rechaza entradas ambiguas como `1.234`. No hay conversión ni soporte multi-moneda. Room migra v1→v2→v3 de forma aditiva: configura USD, agrega el indicador Caja/Banco y conserva las cuentas y los asientos; marca las cuentas iniciales 1000/1010 como Caja/Banco. Las cuentas son add-only por ahora: no se pueden renombrar, reclasificar, desactivar ni borrar, y el histórico no se sobrescribe. Los registros heredados con otro código de moneda permanecen en su moneda original, no se convierten y se excluyen de los totales USD; solo se pueden contabilizar movimientos nuevos en USD. Es un prototipo, no un sistema contable de producción: faltan configuración fiscal, borradores, reversas y auditoría completa, backend/API, sincronización, Mayor y estados financieros completos, controles de periodo, importación/exportación, copias de seguridad y validación profesional.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

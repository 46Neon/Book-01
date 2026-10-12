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

La APK de depuración incluye el Diario, el Plan de cuentas, el Mayor por cuenta y el Balance de comprobación local. Permite guardar ingresos y gastos sencillos, genera dos líneas balanceadas y agrega cuentas por código, nombre y clasificación. El Mayor muestra movimientos cronológicos, debe/haber, saldo corrido D/H y totales del rango seleccionado; su saldo inicial incorpora los movimientos anteriores a «Desde». El Balance de comprobación también permite filtrar por fecha. Ambos informes aceptan límites inclusivos Desde/Hasta en formato estricto AAAA-MM-DD, con cualquiera de los extremos vacío; rechazan fechas inválidas y rangos invertidos. El Mayor excluye borradores y otras monedas sin convertirlas; los totales débito/crédito solo incluyen las filas del rango. El libro inicial trabaja únicamente en USD: los importes se guardan como centavos enteros (`Long`), el análisis decimal es exacto con `BigDecimal` (sin `Double`), y se presentan como `US$` con dos decimales. Acepta formatos estadounidense y español inequívocos para importes y rechaza entradas ambiguas como `1.234`. Room migra v1→v2→v3 de forma aditiva, conserva cuentas y asientos, configura USD y marca Caja/Banco. Las cuentas son add-only por ahora: no se pueden renombrar, reclasificar, desactivar ni borrar. Los registros heredados en otra moneda se conservan, no se convierten y se excluyen de los totales USD; solo se pueden contabilizar movimientos nuevos en USD. Es un prototipo, no producción: faltan controles de cierre de periodo, borradores, reversas y auditoría completa, backend/API, sincronización, estados financieros completos, importación/exportación, copias de seguridad, configuración fiscal y validación profesional. La ejecución instrumentada de migración aún debe confirmarse en un emulador funcional.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

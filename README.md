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

La APK de depuración incluye el Diario, el Plan de cuentas, el Mayor por cuenta y el Balance de comprobación local. Permite guardar ingresos y gastos sencillos, genera dos líneas balanceadas y agrega cuentas por código, nombre y clasificación. El Mayor muestra movimientos cronológicos, debe/haber, saldo corrido D/H y totales del rango seleccionado; su saldo inicial incorpora los movimientos anteriores a «Desde». El Balance de comprobación también permite filtrar por fecha. Ambos informes aceptan límites inclusivos Desde/Hasta en formato estricto AAAA-MM-DD, con cualquiera de los extremos vacío; rechazan fechas inválidas y rangos invertidos. El Mayor excluye borradores y otras monedas sin convertirlas; los totales débito/crédito solo incluyen las filas del rango. El libro inicial trabaja únicamente en USD: los importes se guardan como centavos enteros (`Long`), el análisis decimal es exacto con `BigDecimal` (sin `Double`), y se presentan como `US$` con dos decimales. Acepta formatos estadounidense y español inequívocos para importes y rechaza entradas ambiguas como `1.234`. Room migra v1→v2→v3→v4→v5 de forma aditiva, preservando los datos existentes y agregando el enlace de reversa, la auditoría append-only y la instantánea del nombre de cuenta por línea. La migración v4→v5 rellena las instantáneas heredadas con el nombre actualmente guardado; los nombres anteriores a cambios históricos no se pueden reconstruir. Los asientos contabilizados no tienen controles de edición o borrado; se pueden corregir mediante una reversa con líneas débito/crédito exactas, fecha y motivo obligatorios. Se bloquea la segunda reversa y la reversa de una reversa. La sección Trazabilidad, accesible desde «Más», muestra los eventos de contabilización y reversa; los nuevos eventos se escriben junto con el asiento dentro de una transacción. Los reportes Mayor y Balance procesan las líneas de reversa como cualquier otro asiento, y los totales de ingresos/gastos reflejan su efecto. Los asientos anteriores a v4 se conservan y no reciben eventos retroactivos. En Plan de cuentas se puede editar el nombre visible y activar/desactivar la cuenta; código y tipo son inmutables y no se borran cuentas. Las inactivas siguen visibles en referencias históricas pero no aparecen en los selectores de asientos nuevos. Cada cambio efectivo de nombre/estado genera un registro append-only. Las líneas ya publicadas y sus reversas conservan su instantánea de nombre, mientras que las nuevas contabilizaciones usan el nombre actual. Los registros heredados en otra moneda se conservan, no se convierten y se excluyen de los totales USD; solo se pueden contabilizar movimientos nuevos en USD. Es un prototipo, no producción: faltan controles de cierre de periodo, borradores, backend/API, sincronización, estados financieros completos, importación/exportación, copias de seguridad, configuración fiscal y validación profesional. CI ejecutó las pruebas unitarias, compiló Android-test y generó el APK debug. La prueba instrumentada ahora cubre la cadena v1→v5 y comprueba los registros de diario preservados y las instantáneas de nombres. Su compilación/runtime en la nueva versión está pendiente de CI; la verificación de ejecución en emulador sigue sin confirmarse porque la ejecución anterior no arrancó el emulador.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

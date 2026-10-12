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

La APK de depuración incluye Diario, Plan de cuentas, Mayor por cuenta, Balance de comprobación y, desde «Más», estados financieros informativos. Permite guardar ingresos y gastos sencillos, genera dos líneas balanceadas y agregar/editar cuentas conservando códigos y tipos inmutables. Mayor ofrece movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior a «Desde»; Balance de comprobación ofrece filtros de fecha. Se incorporan un estado de resultados con Desde/Hasta opcionales e inclusivos y un balance general a fecha inclusiva. Ambos estados financieros usan líneas USD de asientos contabilizados únicamente, excluyen de manera visible borradores y monedas distintas sin conversión, y procesan las reversas como contralíneas en su propia fecha de contabilización. En el balance general, los saldos de activo, pasivo y patrimonio respetan los tipos de cuenta registrados; el resultado acumulado de ingresos menos gastos se muestra por separado mientras no exista flujo de cierre. La diferencia de conciliación se muestra sin ajuste automático; no se reclasifican cuentas. Las fechas exigen formato estricto AAAA-MM-DD y rechazan fechas inválidas/rangos invertidos. Los importes se conservan como centavos enteros (`Long`) con aritmética exacta, sin coma flotante ni conversión monetaria. Room migra v1→v5 de forma aditiva, preservando datos, auditoría append-only, vínculo de reversa e instantáneas de nombres. Los asientos contabilizados no se editan ni eliminan; las reversas conservan el original, tienen fecha/motivo y no pueden duplicarse. Trazabilidad e historial de cambios de cuentas están disponibles. Este prototipo no es producción ni acredita cumplimiento contable/fiscal local: no implementa cierre o apertura formal de periodos, arrastre de utilidades, impuestos, aprobación profesional, borradores, backend/API de producción, sincronización, respaldo o importación/exportación. CI aún debe verificar el nuevo commit; el runtime instrumentado puede seguir sin confirmar si el emulador no arranca. No usar para libros reales ni como sustituto de asesoría profesional.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

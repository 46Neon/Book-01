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

La aplicación incluye Diario, Plan de cuentas, Mayor, Balance de comprobación y, desde «Más», estados financieros y cierre de periodo. El cierre inclusivo calcula líneas equilibradas con centavos USD exactos, transfiere utilidad o pérdida a una cuenta activa de patrimonio y no altera los asientos originales. Los periodos sin líneas P&L se registran como metadatos, sin asiento ficticio; los cierres son transaccionales, append-only, rechazan solapamientos, bloquean contabilizaciones y reversas fechadas dentro del periodo y no se pueden reabrir ni reversar por el flujo ordinario. Room conserva migraciones aditivas v1→v6. GitHub Actions [run 38316535052](https://github.com/46Neon/Book-01/actions/runs/38316535052) pasó pruebas unitarias, compilación Android-test, ensamblado debug y las **13 pruebas instrumentadas en emulador**, incluida la migración v1→v6. El ZIP del APK debug ocupa 18.723.390 bytes. Este hito no completa la Parte 1: siguen pendientes API/backend de producción, sincronización, copias de seguridad/restauración, importación/exportación, borradores, hardening y revisión profesional. No se afirma cumplimiento contable, fiscal ni legal en Venezuela ni en otra jurisdicción; no usar para libros reales.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

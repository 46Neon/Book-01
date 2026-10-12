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

La aplicación incluye Diario, Plan de cuentas, Mayor, Balance de comprobación y, desde «Más», estados financieros y cierre de periodo. El flujo de cierre inclusivo calcula líneas equilibradas usando centavos USD exactos; transfiere utilidad o pérdida a una cuenta activa de patrimonio y excluye los asientos de cierre del estado de resultados. Un periodo sin líneas P&L se guarda como metadatos sin asiento ni líneas ficticias; la auditoría se crea junto al asiento de cierre cuando este existe. Los metadatos y asientos son append-only, el cierre se guarda transaccionalmente, se rechazan intervalos solapados y se bloquean nuevas contabilizaciones o fechas de reversa dentro de periodos cerrados. Una reversa de un asiento anterior puede contabilizarse en una fecha abierta. El cierre es irreversible en este prototipo: no hay reapertura ni reversa ordinaria de un cierre. El balance general omite resultados ya cerrados y presenta su conciliación; el balance de comprobación conserva los cierres como contabilizaciones ordinarias. Room tiene migraciones aditivas v1→v6. El código de cierre está publicado; GitHub Actions run [38310560161](https://github.com/46Neon/Book-01/actions/runs/38310560161) pasó las pruebas unitarias, la compilación de Android-test y el ensamblado del APK debug. Esto no verifica el runtime de migración: el intento instrumentado anterior falló al iniciar el emulador y ese runtime sigue sin verificar. El prototipo no afirma cumplimiento contable, fiscal ni legal en Venezuela ni en otra jurisdicción y requiere revisión profesional antes de cualquier uso oficial. No se debe considerar completa la Parte 1: siguen pendientes API/backend de producción, sincronización, copias de seguridad/restauración, importación/exportación, borradores, hardening y revisión profesional. No usar para libros reales ni como sustituto de asesoría profesional.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas.

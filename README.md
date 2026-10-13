# Book-01

Libro contable Android de uso familiar para contadores: una interfaz de tabla inspirada en hojas de cálculo, con controles contables y trazabilidad.

## Alcance

Este repositorio corresponde a la **Parte 1**: núcleo del libro contable, backend contable y APK Android. La interfaz conversacional, Groq y el servidor MCP serán responsabilidad de otro mantenedor y no se implementan en esta parte.

La Parte 1 expondrá una API documentada para que el chatbot consulte y proponga operaciones sin acceder directamente a la base de datos. Ver [`docs/contrato-integracion-chat.md`](docs/contrato-integracion-chat.md).

## Experiencia

- Pestañas de Diario, Mayor, Cuentas, Balance y Resultados.
- Filas, columnas, encabezados, totales y navegación inspirados en hojas de cálculo.
- El Diario filtra borradores y asientos contabilizados por fechas inclusivas, texto (descripción/cuenta/código), cuenta/categoría, tipo y estado; los asientos conservan juntas sus líneas de débito y crédito.
- Balance de comprobación local por cuenta en USD; confirma que débitos y créditos totales cuadren.
- Presentación adaptada a teléfono y tablet.
- Captura local y, más adelante, sincronización segura.
- Los asientos se pueden guardar como borradores editables; al publicarlos, la validación y los bloqueos de base de datos impiden modificaciones posteriores. Las correcciones de asientos contabilizados se registran mediante reversas locales trazables.
- La sección de Trazabilidad desde «Más» permite consultar contabilizaciones/reversas y solicitar una reversa con fecha y motivo.
- «Más» permite exportar y restaurar copias completas cifradas mediante el selector de documentos SAF; no se cargan ni comparten automáticamente. La restauración solo se admite en el libro predeterminado intacto, sin asientos, líneas, auditorías ni cierres.

## Stack

- Android: Kotlin, Jetpack Compose.
- Datos locales: Room/SQLite; sincronización futura: WorkManager.
- Backend planeado: API HTTPS documentada con OpenAPI y PostgreSQL.
- Importes exactos; no usar punto flotante para dinero.

## Estado actual

La aplicación incluye Diario, Plan de cuentas, Mayor, Balance de comprobación y, desde «Más», estados financieros, cierre de periodo, exportación y restauración local cifrada. El Diario aplica a borradores y contabilizados los filtros por fecha inclusiva, búsqueda sin distinguir mayúsculas/minúsculas en descripción/cuenta/código, cuenta/categoría, ingreso/gasto y estado; el filtrado se hace por asiento para no separar sus líneas ni alterar cálculos e informes. El filtro por contraparte aún no está disponible: el esquema Room actual no almacena ese dato. La implementación de filtros quedó verificada en [GitHub Actions run 38331145528](https://github.com/46Neon/Book-01/actions/runs/38331145528): pruebas unitarias, compilación Android-test, APK debug y pruebas instrumentadas en emulador pasaron. El archivo v1 conserva IDs, relaciones, valores `Long` en centavos, instantáneas de nombres de cuenta, auditorías y cierres; usa PBKDF2-HMAC-SHA-256 y AES-256-GCM. Al restaurar, primero se autentica y valida el archivo, se muestra una vista previa y se solicita una confirmación separada. La restauración se limita estrictamente a una base sin historial y con las cuentas/configuración predeterminadas intactas (o una base no inicializada); nunca reemplaza un libro con datos ni personalizado. Los writes se validan y ejecutan en una sola transacción Room sin borrar filas ni crear auditoría artificial. La frase no se almacena. **Esto no constituye una política de respaldo de producción**: la revisión de seguridad/retención, recuperación, amenazas y el flujo SAF en dispositivos reales siguen pendientes.

El cierre inclusivo calcula líneas equilibradas con centavos USD exactos, transfiere utilidad o pérdida a una cuenta activa de patrimonio y no altera los asientos originales. Los periodos sin líneas P&L se registran como metadatos, sin asiento ficticio; los cierres son transaccionales, append-only, rechazan solapamientos, bloquean contabilizaciones y reversas fechadas dentro del periodo y no se pueden reabrir ni reversar por el flujo ordinario. Room conserva migraciones aditivas v1→v8. Un bloqueo derivado e inmutable para cada asiento contabilizado protege también sus líneas contra inserciones posteriores, edición y borrado; v6→v7 rellena bloqueos existentes y v7→v8 añade guardas validadas para editar y publicar borradores sin permitir alterar asientos contabilizados. La restauración conserva borradores y reconstruye bloqueos tras insertar líneas. GitHub Actions [run 38327256848](https://github.com/46Neon/Book-01/actions/runs/38327256848) pasó compilación, pruebas unitarias y compilación Android-test; [run 38327263369](https://github.com/46Neon/Book-01/actions/runs/38327263369) pasó **23 pruebas instrumentadas en emulador** (0 fallos). Esto aún no verifica manualmente el selector SAF en un teléfono real; hay un [protocolo de prueba física](docs/prueba-manual-saf.md). La Parte 1 no está completa: siguen pendientes API/backend de producción, sincronización, usuarios/roles y aprobaciones, hardening de producción, revisión profesional y una política de respaldo adecuada. No se afirma cumplimiento contable, fiscal ni legal en Venezuela ni en otra jurisdicción; no usar para libros reales.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas. Las copias exportadas son información financiera sensible: protege tanto el archivo como la frase de cifrado y no las guardes juntas. No existe recuperación de una frase perdida. La política de retención, disponibilidad/recuperación, gestión de claves y revisión de seguridad para respaldo de producción siguen pendientes.

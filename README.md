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

La aplicación incluye Diario, Plan de cuentas, Mayor, Balance de comprobación y, desde «Más», estados financieros, cierre de periodo, exportación y restauración local cifrada. El Diario aplica a borradores y contabilizados los filtros por fecha inclusiva, búsqueda sin distinguir mayúsculas/minúsculas en descripción/cuenta/código, cuenta/categoría, ingreso/gasto y estado; el filtrado se hace por asiento para no separar sus líneas ni alterar cálculos e informes. Los asientos admiten una contraparte opcional, persistida en Room, visible en el Diario y cubierta por la búsqueda de texto; todavía no hay un filtro dedicado por contraparte. La implementación de filtros quedó verificada en [GitHub Actions run 38331145528](https://github.com/46Neon/Book-01/actions/runs/38331145528): pruebas unitarias, compilación Android-test, APK debug y pruebas instrumentadas en emulador pasaron. El archivo v1 conserva IDs, relaciones, valores `Long` en centavos, instantáneas de nombres de cuenta, auditorías y cierres; usa PBKDF2-HMAC-SHA-256 y AES-256-GCM. Al restaurar, primero se autentica y valida el archivo, se muestra una vista previa y se solicita una confirmación separada. La restauración se limita estrictamente a una base sin historial y con las cuentas/configuración predeterminadas intactas (o una base no inicializada); nunca reemplaza un libro con datos ni personalizado. Los writes se validan y ejecutan en una sola transacción Room sin borrar filas ni crear auditoría artificial. La frase no se almacena. **Esto no constituye una política de respaldo de producción**: la revisión de seguridad/retención, recuperación, amenazas y el flujo SAF en dispositivos reales siguen pendientes.

El cierre inclusivo calcula líneas equilibradas con centavos USD exactos, transfiere utilidad o pérdida a una cuenta activa de patrimonio y no altera los asientos originales. Los periodos sin líneas P&L se registran como metadatos, sin asiento ficticio; los cierres son transaccionales, append-only, rechazan solapamientos, bloquean contabilizaciones y reversas fechadas dentro del periodo y no se pueden reabrir ni reversar por el flujo ordinario. Room conserva migraciones aditivas v1→v9; v8→v9 añade la contraparte opcional del asiento. Un bloqueo derivado e inmutable para cada asiento contabilizado protege también sus líneas contra inserciones posteriores, edición y borrado; v6→v7 rellena bloqueos existentes y v7→v8 añade guardas validadas para editar y publicar borradores sin permitir alterar asientos contabilizados. La exportación/restauración local conserva la contraparte opcional. La restauración conserva borradores y reconstruye bloqueos tras insertar líneas. GitHub Actions [run 38327256848](https://github.com/46Neon/Book-01/actions/runs/38327256848) pasó compilación, pruebas unitarias y compilación Android-test; [run 38327263369](https://github.com/46Neon/Book-01/actions/runs/38327263369), sobre Room v8, pasó **23 pruebas instrumentadas en emulador** (0 fallos). El build de [run 38333738927](https://github.com/46Neon/Book-01/actions/runs/38333738927) pasó las pruebas unitarias, compilación de Android-test y ensamblado debug; [run 38333742905](https://github.com/46Neon/Book-01/actions/runs/38333742905) ejecutó `:app:connectedDebugAndroidTest` en emulador y pasó **24/24 pruebas** (0 fallos, 0 errores, 0 omitidas), incluida la migración v1→v9 y el almacenamiento/validación de contraparte opcional. El [artefacto XML](https://github.com/46Neon/Book-01/actions/runs/38333742905/artifacts/11783798652) conserva los resultados. Esto aún no verifica manualmente el selector SAF en un teléfono real; hay un [protocolo de prueba física](docs/prueba-manual-saf.md). La Parte 1 no está completa: siguen pendientes API/backend de producción, sincronización, usuarios/roles y aprobaciones, hardening de producción, revisión profesional y una política de respaldo adecuada. No se afirma cumplimiento contable, fiscal ni legal en Venezuela ni en otra jurisdicción; no usar para libros reales.

La validación de copias autenticadas se endureció en los commits [`dcee04e`](https://github.com/46Neon/Book-01/commit/dcee04e238e72c827da833a3a5e4e41f3c321f05) y [`3209fae`](https://github.com/46Neon/Book-01/commit/3209faebcde01f1f828ca903bac298265acaf825): las reversas requieren original contabilizado elegible y líneas multiconjunto exactamente invertidas; cada cierre vuelve a calcular su plan en centavos y compara resultado, entrada de cierre y líneas. En [run build 38336118120](https://github.com/46Neon/Book-01/actions/runs/38336118120) pasaron pruebas unitarias, compilación Android-test y APK debug; [run emulador 38336123295](https://github.com/46Neon/Book-01/actions/runs/38336123295) pasó **25/25 pruebas instrumentadas** (0 fallos, 0 errores, 0 omitidas). El [artefacto XML de emulador](https://github.com/46Neon/Book-01/actions/runs/38336123295/artifacts/11784513287) conserva el detalle y el [artefacto APK debug](https://github.com/46Neon/Book-01/actions/runs/38336118120/artifacts/11785550681) el binario. Esta verificación no es una revisión profesional externa ni cambia que el producto no está listo para producción.

## Seguridad

Nunca subir libros reales, credenciales, claves de Groq, tokens, archivos `.env`, keystores ni secretos de firma. Usar datos ficticios en desarrollo y pruebas. Las copias exportadas son información financiera sensible: protege tanto el archivo como la frase de cifrado y no las guardes juntas. No existe recuperación de una frase perdida. La política de retención, disponibilidad/recuperación, gestión de claves y revisión de seguridad para respaldo de producción siguen pendientes.

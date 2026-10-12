# Estado de implementación — Parte 1

## Implementado en el prototipo Android

- Diario local de asientos sencillos ingreso/gasto en doble partida; Room y las cinco pestañas existentes (Diario, Mayor, Cuentas, Balance y Más).
- Plan de cuentas editable en nombre/estado, con códigos y tipos inmutables, auditoría append-only e instantáneas del nombre contable.
- Mayor, balance de comprobación y estados financieros con filtros inclusivos estrictos `AAAA-MM-DD`.
- Estado de resultados preserva la actividad original del periodo cerrado: excluye el asiento técnico de cierre y calcula la actividad contabilizada en las fechas seleccionadas.
- Balance general a fecha inclusiva incluye el saldo de patrimonio generado por cierres y solo calcula resultado actual para actividad P&L no cubierta por cierres; conserva visible cualquier diferencia de conciliación, sin ajuste o reclasificación silenciosa.
- Cierre inclusivo transaccional USD: crea un asiento nuevo, balanceado y no editable que lleva a cero las cuentas de ingreso/gasto según actividad publicada en el rango y transfiere la utilidad/pérdida neta a una cuenta EQUITY activa seleccionada. Las reversiones se atribuyen a su fecha efectiva.
- Metadatos de cierre y evento de auditoría se guardan atómicamente con el asiento y líneas; los metadatos/auditoría son append-only. Cierres superpuestos o duplicados se rechazan; las contabilizaciones normales y las fechas de reversa dentro de un periodo cerrado se bloquean en repositorio y formulario. El cierre no se puede revertir con el flujo ordinario ni reabrir.
- Los importes son centavos USD en `Long`; cálculos exactos sin coma flotante ni conversión FX.
- Exportación offline bajo demanda de instantánea consistente de Room v6 a un destino SAF/CreateDocument escogido por la persona. Incluye configuración, cuentas, asientos, líneas y sus instantáneas de nombre, auditoría de asientos, auditoría de cuentas y cierres de periodo; conserva IDs/relaciones, valores `Long` y `closingEntryId` nullable. El archivo tiene formato versionado y autenticado con PBKDF2-HMAC-SHA-256 + AES-256-GCM, sal y nonce aleatorios nuevos por exportación. La frase se introduce y confirma para cada exportación y no se almacena; no hay carga ni compartición automática.
- Migración Room aditiva v1→v6 y pruebas instrumentadas de preservación de datos, totales, cierre con utilidad/pérdida, bloqueo de fechas y rechazo de solapamiento; pruebas unitarias de continuidad de estados y no duplicación de utilidades.

## Pendiente / límites conocidos

- Este hito no completa la Parte 1. La exportación está añadida, pero **importación/restauración sigue pendiente**; también faltan API/backend de producción, sincronización, borradores editables, usuarios/roles, aprobación y hardening de producción.
- La exportación por sí sola no define una política de respaldo de producción. Siguen pendientes revisión independiente de criptografía y seguridad, gestión de frase/archivo, amenazas, recuperación ante pérdida, retención, disponibilidad y pruebas en dispositivo.
- Se requiere revisión profesional de reglas contables, tributarias, seguridad y usabilidad antes de cualquier uso oficial. El producto no afirma ni garantiza cumplimiento contable, fiscal o legal venezolano ni de otra jurisdicción.
- La implementación es local, offline y USD únicamente; no convierte otras monedas. Un rango sin líneas P&L se cierra como metadatos inmutables sin asiento ni líneas ficticias; si hay actividad P&L pero el neto es cero, el asiento lleva las cuentas P&L a cero sin línea de transferencia de importe cero. El cierre es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.

## Verificación

El cierre de periodo y su pipeline quedaron verificados previamente. GitHub Actions [run 38316535052](https://github.com/46Neon/Book-01/actions/runs/38316535052) terminó correctamente: pasaron pruebas unitarias, compilación Android-test, `:app:assembleDebug` y `:app:connectedDebugAndroidTest` en emulador. El XML de resultados confirma **13 pruebas instrumentadas aprobadas y 0 fallidas**, incluida `LedgerMigrationTest.versionOneBookMigratesToUsdSettingsAndPreservesExistingJournal`, que valida la migración v1→v6 con datos previos preservados. El ZIP del [APK debug](https://github.com/46Neon/Book-01/actions/runs/38316535052/artifacts/11776896371) ocupa 18.723.390 bytes; el artefacto de resultados instrumentados ocupa 30.612 bytes. Esto verifica la cadena correspondiente a ese commit, no el cambio de exportación descrito aquí.

La implementación de exportación y sus nuevas pruebas aún requieren ejecución de pruebas unitarias, compilación de Android-test y ensamblado del APK, idealmente además una prueba en dispositivo. Hasta que esos controles pasen, **no se considera verificado este cambio**. Esta iteración no completa la Parte 1 ni acredita preparación para producción.

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
- Migración Room aditiva v1→v6 y pruebas instrumentadas de preservación de datos, totales, cierre con utilidad/pérdida, bloqueo de fechas y rechazo de solapamiento; pruebas unitarias de continuidad de estados y no duplicación de utilidades.

## Pendiente / límites conocidos

- Este hito no completa la Parte 1. No hay API/backend de producción, sincronización, copia de seguridad/restauración, importación/exportación, borradores editables, usuarios/roles, aprobación ni hardening de producción.
- Se requiere revisión profesional de reglas contables, tributarias, seguridad y usabilidad antes de cualquier uso oficial. El producto no afirma ni garantiza cumplimiento contable, fiscal o legal venezolano ni de otra jurisdicción.
- La implementación es local, offline y USD únicamente; no convierte otras monedas. Un rango sin líneas P&L se cierra como metadatos inmutables sin asiento ni líneas ficticias; si hay actividad P&L pero el neto es cero, el asiento lleva las cuentas P&L a cero sin línea de transferencia de importe cero. El cierre es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.
- No se afirma cumplimiento contable, tributario/fiscal ni legal en Venezuela ni en otra jurisdicción; se requiere revisión profesional antes de cualquier uso oficial.

## Verificación

La implementación local de cierre y el cálculo/validación de líneas se publicó en el commit [`ff35346d4b261a3dba5b04d65a036d5406370c9d`](https://github.com/46Neon/Book-01/commit/ff35346d4b261a3dba5b04d65a036d5406370c9d). GitHub Actions [run 38310560161](https://github.com/46Neon/Book-01/actions/runs/38310560161) terminó correctamente: pasaron las pruebas unitarias, `:app:compileDebugAndroidTestKotlin` y `:app:assembleDebug`. El artefacto [book-01-debug-apk](https://github.com/46Neon/Book-01/actions/runs/38310560161/artifacts/11773979503) ocupa 18.723.248 bytes (ZIP). La compilación Android-test no acredita runtime de migración. El intento workflow-dispatch [run 38307010660](https://github.com/46Neon/Book-01/actions/runs/38307010660) falló por timeout al iniciar el emulador; el runtime instrumentado de migración permanece **sin verificar**. No se debe afirmar la finalización de Parte 1 con este hito.

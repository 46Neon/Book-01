# Estado de implementación — Parte 1

## Implementado en el prototipo Android

- Diario local con asientos sencillos de ingreso/gasto en doble partida; persistencia Room y cinco pestañas conservadas (Diario, Mayor, Cuentas, Balance y Más).
- Plan de cuentas con altas y edición segura del nombre/estado. Código y clasificación son inmutables; cambios de nombre/estado dejan auditoría append-only. Las líneas guardan instantánea del nombre de cuenta.
- Mayor por cuenta y Balance de comprobación con filtros inclusivos estrictos `AAAA-MM-DD`; el Mayor muestra saldo inicial anterior a «Desde».
- Estado de resultados por periodo: suma exacta de ingresos y gastos por cuenta clasificada, con límites opcionales inclusivos Desde/Hasta.
- Balance general a fecha inclusiva: muestra activos, pasivos y patrimonio en sus clasificaciones existentes, más el resultado acumulado de ingresos menos gastos como componente separado en el libro no cerrado. La diferencia de conciliación queda visible; no se crean ajustes ni se reclasifican tipos de cuenta.
- Los dos estados financieros usan solo líneas USD de asientos `POSTED`; excluyen e indican borradores y líneas en otras monedas sin convertir. Las contralíneas de reversa se procesan por su fecha efectiva, reduciendo el efecto del asiento original cuando ambas fechas están dentro del corte.
- Centavos USD como `Long`, operaciones aritméticas comprobadas y formato decimal exacto con `BigDecimal`; sin punto flotante ni conversión de moneda.
- Reversas exactas no destructivas, vínculo al original, fecha/motivo y auditoría local. Migraciones Room aditivas v1→v5; la migración v4→v5 completa instantáneas heredadas con el nombre actual de cuenta, no reconstruye nombres históricos.
- Pruebas unitarias de dominio existentes para reglas de cuenta, diario, reversas, USD, Mayor y Balance de comprobación. Se añadieron pruebas de estados financieros para límites, importes, reversas, exclusiones, rangos inválidos y conciliación.

## Pendiente / límites conocidos

- Este hito no implementa cierre o apertura de periodo, cierre formal de ingresos/gastos, arrastre formal de utilidades, ajustes de cierre ni control de periodos bloqueados. El resultado acumulado reportado es ingreso menos gasto publicado hasta la fecha, únicamente para el libro sin cerrar; no representa historial de utilidades retenidas derivado de periodos cerrados.
- No hay borradores editables, aprobación, usuarios/roles autenticados, API/backend de producción, sincronización, respaldo/restauración, adjuntos ni importación/exportación.
- No hay configuración fiscal ni validación profesional de normas contables locales. Los informes son informativos y no declaran cumplimiento contable, fiscal o legal de Venezuela ni de otra jurisdicción; requieren revisión de un profesional para cualquier uso oficial.
- Sincronización, seguridad de cuentas reales, pruebas de usabilidad de teléfono/tablet, recuperación, cierre y demás trabajo de producto quedan abiertos. No afirmar que Parte 1 completa está terminada.

## Verificación

GitHub Actions verificó el código Android del commit `015ff957f55f6ab7977ed5847c8d4c1f5732c921` en [run 38305329066](https://github.com/46Neon/Book-01/actions/runs/38305329066): terminaron con éxito las pruebas unitarias, `:app:compileDebugAndroidTestKotlin` y `:app:assembleDebug`; se subieron los artefactos. El artefacto [book-01-debug-apk](https://github.com/46Neon/Book-01/actions/runs/38305329066/artifacts/11772781096) ocupa 18.682.631 bytes (ZIP); contiene `app-debug.apk` de 19.225.073 bytes. El job de migración instrumentada quedó omitido en este evento `push`; la compilación Android-test no acredita runtime de migración. El runtime sigue sin verificar porque una ejecución previa no logró arrancar el emulador. No se afirma que la migración se haya ejecutado exitosamente en un dispositivo.

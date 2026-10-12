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

Los resultados de CI correspondientes a este cambio deben anotarse después de que GitHub Actions finalice: pruebas unitarias, compilación de fuentes Android-test, APK debug y tamaño/enlace de artefacto. La compilación de las pruebas instrumentadas no demuestra runtime de migración; este último solo se considera verificado si el job de emulador arranca y termina exitosamente. La última ejecución de emulador conocida falló al iniciar, por lo que no se debe afirmar que la migración se ejecutó en runtime hasta tener una corrida exitosa.

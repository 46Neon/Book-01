# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La aplicación conserva sus cinco pestañas (Diario, Mayor, Cuentas, Balance y Más); desde «Más» se accede a configuración, trazabilidad y estados financieros.

## Funciones del prototipo

- Diario local con asientos simples de ingreso/gasto, guardados como líneas de doble partida balanceadas.
- Plan de cuentas inicial y altas de cuentas. Nombre y estado se pueden editar; código y tipo son inmutables. Las cuentas inactivas no se ofrecen para asientos nuevos.
- Mayor por cuenta con movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior al límite «Desde».
- Balance de comprobación por cuenta y periodo.
- Estados financieros informativos: estado de resultados con límites inclusivos opcionales Desde/Hasta y balance general a una fecha inclusiva. Los importes son centavos USD enteros (`Long`); solo se consideran líneas USD de asientos contabilizados. Los borradores y otras monedas se excluyen y se informan, sin conversión.
- En el balance general, el resultado acumulado de ingresos menos gastos hasta la fecha aparece separado de las cuentas de patrimonio. Esta solución refleja el resultado mientras el libro permanezca sin cerrar; no cambia ni reclasifica tipos de cuenta. Se presenta la diferencia de conciliación y no se ajustan automáticamente saldos desbalanceados.
- Reversas como nuevos asientos con débitos/créditos exactamente opuestos, fecha y motivo, vínculo al asiento original y auditoría local append-only; no se permite reversar dos veces ni reversar una reversa.
- Filtros de fecha estrictos `AAAA-MM-DD`, límites inclusivos y rechazo de fechas inválidas o rangos invertidos. Los movimientos de reversa afectan las cifras en su fecha efectiva de contabilización.
- Migraciones Room aditivas v1→v5, incluida la instantánea de nombre de cuenta en cada línea histórica.

## Límites y advertencia

Es un prototipo local, no un sistema listo para producción ni una herramienta de asesoría o declaración fiscal. Los estados son cálculos básicos sobre las clasificaciones existentes; no implementan cierres de periodo, arrastre formal de utilidades, impuestos, normas contables locales, revisión o aprobación profesional. No debe afirmarse cumplimiento contable/fiscal venezolano ni de otra jurisdicción. Faltan cierre y apertura de periodos, borradores, usuarios/roles, backend/API de producción, sincronización, respaldo/restauración, importación/exportación, pruebas de usabilidad y revisión profesional. El runtime instrumentado de migración puede quedar pendiente si el emulador de CI no arranca; el resultado se debe distinguir de la compilación de Android-test.

USD se conserva como centavos enteros y se maneja con aritmética exacta, sin coma flotante ni conversión de monedas. Use datos ficticios en desarrollo; no guarde libros reales ni secretos en el repositorio.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La acción de GitHub genera un APK debug como artefacto cuando el job de compilación finaliza correctamente.

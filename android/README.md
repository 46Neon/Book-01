# Android app

Módulo Android de Ariño, construido con Kotlin + Jetpack Compose. La navegación prioriza los libros auxiliares de Ventas y Compras; Diario, Mayor, Cuentas, Balance y Más mantienen el acceso al trabajo contable general, informes, auditoría y respaldo cifrado.

## Funciones del prototipo

- Diario local con asientos simples de ingreso/gasto, guardados como líneas de doble partida balanceadas.
- Libros auxiliares de ventas y compras con resumen derivado por período: cantidad de facturas y suma exacta de sus totales brutos (no equivale a cobros/pagos ni al saldo pendiente); se usan centavos `Long` y la suma rechaza desbordamientos.
- Plan de cuentas inicial y altas de cuentas. Nombre y estado se pueden editar; código y tipo son inmutables. Las cuentas inactivas no se ofrecen para asientos nuevos.
- Mayor por cuenta con movimientos cronológicos, debe/haber, saldo corrido y saldo inicial anterior al límite «Desde».
- Balance de comprobación por cuenta y periodo.
- Estados financieros informativos: estado de resultados con límites inclusivos opcionales Desde/Hasta y balance general a una fecha inclusiva. Los importes son centavos USD enteros (`Long`); solo se consideran líneas USD de asientos contabilizados. Los borradores y otras monedas se excluyen y se informan, sin conversión.
- En el balance general, el resultado acumulado se presenta separado de las cuentas de patrimonio solo para actividad que no esté cubierta por un cierre. Un cierre transfiere el neto de ingresos/gastos a patrimonio y las líneas técnicas de cierre no alteran la actividad que muestra el estado de resultados del periodo.
- Reversas como nuevos asientos con débitos/créditos exactamente opuestos, fecha y motivo, vínculo al asiento original y auditoría local append-only; no se permite reversar dos veces ni reversar una reversa. Bloqueos derivados inmutables y triggers SQLite impiden actualizar o borrar líneas contabilizadas, o insertar líneas adicionales después de contabilizar.
- Filtros de fecha estrictos `AAAA-MM-DD`, límites inclusivos y rechazo de fechas inválidas o rangos invertidos. Los movimientos de reversa afectan las cifras en su fecha efectiva de contabilización.
- Room usa la versión 12. Las migraciones preservan los libros existentes y añaden, en v11→v12, terceros, facturas y liquidaciones relacionadas; los asientos `POSTED` conservan sus bloqueos inmutables y las facturas/cobros tienen guardas append-only. La migración y los respaldos deben validarse en el emulador sobre el commit final antes de distribuir una APK. Un rango sin líneas P&L se guarda como cierre de metadatos sin asiento ficticio; el cierre inclusivo bloquea fechas y es irreversible en este prototipo: no hay reapertura ni reversa ordinaria del asiento de cierre.
- Exportación y restauración cifrada offline de una instantánea Room versionada. El formato autenticado conserva configuración, cuentas, borradores, asientos y líneas, auditorías, cierres, terceros, facturas y liquidaciones; valida relaciones e importes antes de restaurar. La restauración sigue limitada a una base vacía o al libro inicial intacto y requiere confirmación. El selector SAF necesita verificación física en dispositivos reales.

## Límites y advertencia

Es un prototipo local, no un sistema listo para producción ni una herramienta de asesoría o declaración fiscal. El cierre no habilita reapertura y es una implementación informativa básica, pendiente de revisión profesional. No se afirma cumplimiento contable, fiscal o legal en Venezuela ni en otra jurisdicción. Faltan backend/API de producción, sincronización, usuarios/roles, aprobaciones, hardening y revisión profesional. Importación/restauración v1 está limitada al libro predeterminado sin personalizaciones ni historial y no reemplaza libros existentes. Exportación/restauración aún no forman una política de respaldo de producción: seguridad criptográfica, gestión de claves, recuperación, retención, amenazas y tratamiento de datos deben revisarse antes de cualquier uso real. El selector SAF debe probarse manualmente en dispositivos reales; sigue pendiente la [lista de comprobación](../docs/prueba-manual-saf.md). Las frases perdidas no se pueden recuperar; guarda la frase aparte del archivo. La aplicación no sube ni comparte el archivo automáticamente; la ubicación la elige la persona.

La carpeta de esquemas Room versionados termina en v12. La documentación histórica de CI registra builds e instrumentadas correctos en commits anteriores de la aplicación, pero **no valida** esta integración de navegación ni el esquema v12 del commit propuesto por PR #10. En la revisión actual los jobs de GitHub Actions fueron rechazados antes de arrancar por la configuración de facturación/límite de gasto de la cuenta propietaria; por eso no hay un APK ni resultados de pruebas de esta versión. Antes de distribuirla hay que ejecutar `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug` y `:app:connectedDebugAndroidTest` sobre el mismo commit final, y completar la prueba física del selector SAF.

USD se conserva como centavos enteros y se maneja con aritmética exacta, sin coma flotante ni conversión de monedas. Use datos ficticios en desarrollo; no guarde libros reales ni secretos en el repositorio.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La acción de GitHub genera un APK debug como artefacto cuando el job de compilación finaliza correctamente.


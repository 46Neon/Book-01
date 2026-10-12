# Android app

Módulo Android de Book-01 en Kotlin + Jetpack Compose. La interfaz conserva una organización familiar de hoja de cálculo —pestañas, tablas, encabezados, filtros y totales— adaptada a pantallas móviles.

## Ya funciona en el prototipo

- Diario con líneas de asiento separadas y alta de ingresos/gastos simples.
- Plan de cuentas tipo tabla y creación de cuentas con código, nombre, tipo y marca opcional para Caja/Banco.
- Validación de datos y generación de débitos/créditos balanceados.
- Persistencia local en Room/SQLite.
- Balance de comprobación por cuenta, con débitos, créditos, naturaleza D/H y verificación de equilibrio en USD.
- Totales de ingresos, gastos y neto, calculados en centavos.

## Manejo de USD y límites

El libro inicial usa USD como moneda funcional. Los importes se almacenan en centavos enteros (`Long`) y se analizan con `BigDecimal`, nunca con coma flotante binaria. Se muestran como `US$` con dos decimales. Se aceptan formatos inequívocos estadounidense y español; los valores ambiguos o con más de dos decimales se rechazan. No se convierten ni se mezclan monedas.

Las migraciones Room 1→2→3 conservan los asientos y las cuentas existentes y marcan Caja/Banco para registros nuevos. Las cuentas existentes no se pueden borrar ni cambiar de código/tipo; todavía faltan edición controlada y desactivación.

Aún faltan libro mayor completo, reversas e historial de auditoría, configuración fiscal, cierres, conciliación, exportación, sincronización, backend/API y validación profesional. No usar esta versión para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK API 36 y Gradle 8.13. Desde la raíz:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions publica el APK de depuración como artefacto. La prueba de migración en emulador se ejecuta manualmente.

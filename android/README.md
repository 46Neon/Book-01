# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La interfaz usa pestañas, tablas, encabezados, filtros visuales y totales para conservar la familiaridad de una hoja de cálculo, adaptándola a pantallas móviles.

## Ya funciona en el prototipo

- Pantalla Diario con filas de asiento en dos líneas contables.
- Alta de ingreso o gasto sencillo mediante formulario.
- Validación de descripción, fecha, importe positivo y precisión de dos decimales.
- Generación automática de débito/crédito balanceados para ingreso o gasto pagado/cobrado en Caja o Banco.
- Persistencia local inicial en Room/SQLite.
- Totales locales de ingresos, gastos y neto.

## Todavía pendiente

El prototipo usa un plan de cuentas de demostración y USD como moneda funcional. Los importes se guardan como centavos enteros (`Long`), se parsean con `BigDecimal` y se muestran como US$ con dos decimales. Acepta `1,234.56` y `1.234,56`; rechaza `1.234` por ambiguo. No mezcla ni convierte monedas. Aún faltan configuración fiscal, edición o reversión, cierres, conciliación, exportación, sincronización, backend y reportes completos. No usar para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

La compilación de GitHub Actions genera el APK de depuración como artefacto.

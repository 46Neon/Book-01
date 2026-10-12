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

El prototipo usa un plan de cuentas de demostración y admite únicamente USD como moneda funcional. Los importes se guardan como centavos enteros (`Long`) y se analizan con semántica decimal exacta mediante `BigDecimal`, nunca con coma flotante binaria. La salida siempre se etiqueta `US$` y muestra dos decimales. Acepta `1,234.56`, `1.234,56`, `28,50` y `USD 28.50`; rechaza formatos ambiguos como `1.234` en vez de adivinar el valor. No hay conversión de cambio, cuentas ni asientos en otras monedas, ni mezcla de monedas. La migración Room 1→2 conserva los datos existentes y agrega la configuración predeterminada USD. Si existen asientos v1 con otro código de moneda, siguen visibles con su código original y se totalizan aparte; quedan excluidos de los resúmenes USD. No se convierten y no se pueden crear asientos nuevos en otra moneda. Aún faltan configuración fiscal, edición/reversión y auditoría completa, cierres, conciliación, exportación, sincronización, backend, reportes completos, localización profesional y validación de especialistas. No usar para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

La compilación de GitHub Actions genera el APK de depuración como artefacto.

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

El prototipo usa un plan de cuentas de demostración y moneda USD fija. Aún no hay configuración de empresa/moneda, edición o reversión, cierres, conciliación, exportación, sincronización, backend ni reportes contables completos. No usar como sistema contable de producción ni para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:assembleDebug
```

La compilación de GitHub Actions genera el APK de depuración como artefacto.

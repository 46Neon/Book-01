# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La interfaz usa pestañas, tablas, encabezados, filtros visuales y totales para conservar la familiaridad de una hoja de cálculo, adaptándola a pantallas móviles.

## Ya funciona en el prototipo

- Pantalla Diario con filas de asiento en dos líneas contables y totales locales.
- Alta de ingreso o gasto sencillo mediante formulario.
- Validación de descripción, fecha, importe positivo y precisión de dos decimales.
- Generación automática de débito/crédito balanceados para ingreso o gasto pagado/cobrado en cuentas activas marcadas Caja/Banco.
- Persistencia local en Room/SQLite, con migraciones aditivas hasta versión 3.
- Plan de cuentas en tabla: muestra todas las cuentas para conservar referencias históricas y permite agregar cuentas con código único, nombre, tipo y clasificación opcional como Caja/Banco.
- Mayor por cuenta con filas cronológicas, debe/haber, saldo corrido D/H y totales en USD; borradores y otras monedas quedan excluidos.
- Balance de comprobación por cuenta, con totales de débito/crédito, naturaleza D/H y verificación de equilibrio en USD.

## Todavía pendiente

El prototipo usa un plan de cuentas ampliable y admite únicamente USD como moneda funcional. Los importes se guardan como centavos enteros (`Long`) y se analizan con `BigDecimal`, nunca con coma flotante binaria. Se muestran como `US$` con dos decimales; se aceptan formatos inequívocos y se rechazan los ambiguos, como `1.234`. No se convierten ni mezclan monedas. Las migraciones Room 1→2→3 agregan configuración USD y clasificación Caja/Banco, preservando los datos existentes. Las cuentas nuevas son aditivas; nombres y tipos no se editan y aún no hay baja de cuentas. El Mayor y el balance abarcan el periodo completo, sin filtros de fechas; faltan borradores, reversas, auditoría completa, cierres, conciliación, exportación, sincronización, backend/API, validación local y revisión profesional. La prueba de migración en emulador sigue sin ejecución confirmada porque el emulador no arrancó. No usar para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

La compilación de GitHub Actions genera el APK de depuración como artefacto.

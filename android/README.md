# Android app

Módulo Android de Book-01, construido con Kotlin + Jetpack Compose. La interfaz usa pestañas, tablas, encabezados, filtros visuales y totales para conservar la familiaridad de una hoja de cálculo, adaptándola a pantallas móviles.

## Ya funciona en el prototipo

- Pantalla Diario con filas de asiento en dos líneas contables y totales locales.
- Alta de ingreso o gasto sencillo mediante formulario.
- Validación de descripción, fecha, importe positivo y precisión de dos decimales.
- Generación automática de débito/crédito balanceados para ingreso o gasto pagado/cobrado en cuentas activas marcadas Caja/Banco.
- Persistencia local en Room/SQLite, con migraciones aditivas hasta versión 3.
- Plan de cuentas en tabla: muestra todas las cuentas para conservar referencias históricas y permite agregar cuentas con código único, nombre, tipo y clasificación opcional como Caja/Banco.
- Balance de comprobación por cuenta, con totales de débito/crédito, naturaleza D/H y verificación de equilibrio en USD.

## Todavía pendiente

El prototipo usa un plan de cuentas inicial ampliable y admite únicamente USD como moneda funcional. Los importes se guardan como centavos enteros (`Long`) y se analizan con semántica decimal exacta mediante `BigDecimal`, nunca con coma flotante binaria. La salida siempre se etiqueta `US$` y muestra dos decimales. Acepta `1,234.56`, `1.234,56`, `28,50` y `USD 28.50`; rechaza formatos ambiguos como `1.234` en vez de adivinar el valor. No hay conversión de cambio ni mezcla de monedas. Las migraciones Room 1→2→3 agregan la configuración USD y la clasificación Caja/Banco, preservando los datos existentes y marcando los códigos iniciales 1000 y 1010. Las cuentas nuevas son aditivas; nombres y tipos no se editan y no hay baja de cuentas todavía. Los asientos v1 con otra moneda conservan su código, no se convierten y se excluyen de los totales USD; no se pueden crear asientos nuevos en otra moneda. Aún faltan configuración fiscal, flujo de borrador, reversas y auditoría completa, cierres, conciliación, exportación, sincronización, backend/API, Mayor y estados financieros completos, localización profesional y validación de especialistas. No usar para libros reales.

## Compilar

Requisitos: JDK 17, Android SDK con API 36 y Gradle 8.13. Desde la raíz del repositorio:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

La compilación de GitHub Actions genera el APK de depuración como artefacto.

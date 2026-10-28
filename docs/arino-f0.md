# Ariño — cimientos y arquitectura (F0)

## Alcance de esta entrega

Ariño es la nueva identidad visible de la aplicación móvil del repositorio. Esta fase conserva Kotlin, Jetpack Compose, Room/SQLite, el `applicationId` `com.neon.book01` y la paleta verde petróleo, azul oscuro y fondos claros. El cambio de nombre no altera datos guardados ni las reglas contables existentes.

La aplicación continúa siendo un prototipo local en USD. F0 no implementa facturación, cartera de clientes/proveedores, cálculo fiscal, pagos posteriores, bancos, reconocimiento de facturas ni exportaciones. Las secciones planeadas se describen como futuras; no se presentan como funciones disponibles.

## Arquitectura observada

- **Interfaz:** `MainActivity.kt` crea la actividad y el árbol de Compose. `BookHomeScreen` coordina estado y navegación; los composables locales dibujan pestañas, formularios, tablas, filtros, informes y diálogos. El código de pantallas está concentrado actualmente en un archivo grande, un punto claro para refactor gradual cuando se amplíe la interfaz.
- **Dominio contable:** `android/app/src/main/java/com/neon/book01/domain/` contiene reglas y cálculos sin depender de pantallas: partida doble, importes USD exactos, Diario/Mayor, balance de comprobación, estados financieros, cierres, reversas y filtros.
- **Persistencia:** `data/` contiene entidades, DAO, base Room, migraciones y `LedgerRepository`. El repositorio coordina transacciones; las restricciones y triggers SQLite ayudan a proteger asientos publicados y bitácoras.
- **Pruebas:** `src/test` cubre reglas y cálculos de dominio; `src/androidTest` usa Room/Android para migraciones, transacciones, triggers e integración. GitHub Actions compila el APK y prueba esas capas.

## Navegación: disponible frente a planeada

La navegación que existe en esta rama tiene cinco pestañas inferiores:

1. **Diario:** movimientos locales simples de ingreso/gasto, filtros y borradores.
2. **Mayor:** movimientos y saldo corrido por cuenta.
3. **Cuentas:** plan de cuentas actual.
4. **Balance:** balance de comprobación.
5. **Más:** acceso a estados financieros, cierres, trazabilidad y copia local cifrada.

El plan de Ariño propone para etapas posteriores Inicio, Ventas, Compras, Clientes, Proveedores, Plan de cuentas, Diario, Mayor, Impuestos y Reportes. Estas secciones no se agregan como pestañas ficticias en F0. Se habilitarán al implementar cada flujo y sus datos.

## Componentes visuales existentes para reutilizar

- `LedgerTable` y `TableCell`: filas contables, encabezados, desplazamiento horizontal y alineación de importes.
- `DateRangeControls`, `JournalFilterDialog` y chips: fechas inclusivas y filtros.
- `AccountPicker`, `AccountTypePicker` y `AccountParentPicker`: elección de cuentas actuales.
- `SummaryCard`: resúmenes y totales.
- `AlertDialog`, estados de error y `SnackbarHost`: confirmación, errores y feedback de operaciones.
- Estados vacíos explícitos en Diario, Mayor, Balance y bitácora.

Para las fases siguientes conviene extraer tablas, campos monetarios, selectores de personas, etiquetas de estado, avisos y mensajes de error a componentes reutilizables, manteniendo la lógica del dominio fuera de Compose.

## CI y almacenamiento de artefactos

El workflow de Android debe seguir ejecutando pruebas unitarias, compilación de Android-test y `assembleDebug` para push, pull request y ejecución manual. Los esquemas Room ya están versionados bajo `android/app/schemas`, por lo que no hace falta subir una copia con cada build. Los APK de builds no-PR pueden conservarse por un plazo corto para descarga; los informes de emulador se conservan brevemente y solo en ejecuciones manuales. Esto reduce acumulación, aunque los builds siguen usando minutos de Actions.

## Decisiones que requieren al contador

Este cambio no valida plan de cuentas, tasas ni tratamientos fiscales. Antes de implementar facturas e impuestos, el contador debe aprobar el plan de cuentas, la naturaleza/aumento de cada cuenta, reglas de IVA, comprobantes y formatos requeridos, tratamiento de inventario y fechas/períodos fiscales. El repositorio no debe presentarse como herramienta fiscal aprobada.

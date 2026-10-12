# Estado de implementación — Parte 1

## Hecho

- Repositorio privado y documentación de alcance, arquitectura, guía visual y contrato de integración.
- Primera pantalla Android Kotlin/Compose con estilo tipo hoja de cálculo.
- Base Room local versionada con cuentas, asientos y líneas contables.
- Catálogo inicial de demostración: Caja, Banco, Cuentas por pagar, Capital, Ingresos, Papelería, Servicios y Otros gastos.
- Hoja Plan de cuentas en formato de cuadrícula y alta de nuevas cuentas por código, nombre, tipo y uso opcional como Caja/Banco; no se permiten borrados ni cambios de código/tipo que alteren el histórico.
- Formulario local para ingreso o gasto simple: descripción, importe, fecha, categoría y cuenta de efectivo/banco.
- La capa de dominio genera las dos líneas contables, valida el tipo de cuenta y comprueba débito = crédito.
- La escritura del asiento y sus líneas se realiza en una transacción local.
- Reglas contables y de altas de cuentas separadas en el dominio, con pruebas unitarias para partida doble, tipos/códigos de cuenta, balance y precisión/formato USD.
- Moneda funcional del libro persistida como USD en Room, con migraciones 1→2→3 que conservan los asientos y cuentas existentes.
- Importes USD representados como centavos enteros (`Long`), analizados con `BigDecimal` exacto y sin coma flotante binaria; salida rotulada `US$` con dos decimales. Admite formas inequívocas estadounidense y española; rechaza más de dos decimales y entradas ambiguas en vez de adivinarlas.
- Migraciones Room 1→2→3 agregan configuración USD y el indicador de cuentas de Caja/Banco sin borrar asientos ni líneas; las cuentas 1000/1010 existentes quedan marcadas para cobros/pagos.
- La tabla muestra cada línea del asiento y calcula saldo neto, ingresos/gastos y totales desde los registros locales.
- Balance de comprobación local por cuenta: débitos/créditos exactos, naturaleza de saldo D/H y comprobación de que el total cuadra; rechaza mezclar monedas en el reporte USD.
- GitHub Actions compila el APK de depuración.

## Limitaciones conocidas

- Moneda del prototipo fija en USD, dos decimales; sin conversión de cambio ni soporte multi-moneda.
- El plan permite agregar cuentas nuevas, pero aún no editar nombres/tipos ni desactivar cuentas; la plantilla sigue siendo genérica y no configurable por empresa.
- Los asientos sencillos se marcan contabilizados al guardar; falta flujo de borrador, aprobación, reversa y auditoría completa.
- Aún sin API/backend, usuarios, roles, sincronización, adjuntos, importación/exportación o restauración.
- Mayor y estados financieros completos siguen en navegación visual; el Balance de comprobación sí está implementado, pero solo para el periodo completo local y USD.
- No se declara cumplimiento contable/fiscal local.

## Siguiente hito

Completar la separación formal entre dominio, almacenamiento y presentación; validar migraciones con una base de prueba real; añadir borradores, reversas y auditoría antes de ampliar reportes. Cualquier futuro soporte multi-moneda debe diseñarse explícitamente antes de implementar conversiones. El backend compartido queda fuera de este hito.

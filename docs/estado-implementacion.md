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
- Reglas contables y de altas de cuentas separadas en el dominio, con pruebas unitarias para asientos, códigos/tipos, balance, Mayor y precisión/formato USD.
- Moneda funcional del libro persistida como USD en Room, con migraciones 1→2→3 que conservan los asientos y cuentas existentes.
- Importes USD representados como centavos enteros (`Long`), analizados con `BigDecimal` exacto y sin coma flotante binaria; salida rotulada `US$` con dos decimales. Admite formas inequívocas estadounidense y española; rechaza más de dos decimales y entradas ambiguas en vez de adivinarlas.
- Migraciones Room 1→2→3 agregan configuración USD y el indicador de cuentas de Caja/Banco sin borrar asientos ni líneas; las cuentas 1000/1010 existentes quedan marcadas para cobros/pagos. Hay una prueba instrumentada de migración v1→v3 que comprueba la conservación de registros y ambas marcas, pero todavía requiere una ejecución exitosa en emulador.
- La tabla muestra cada línea del asiento y calcula saldo neto, ingresos/gastos y totales desde los registros locales.
- Balance de comprobación local por cuenta: débitos/créditos exactos, naturaleza de saldo D/H y comprobación de que el total cuadra; rechaza mezclar monedas en el reporte USD.
- Mayor por cuenta con filas cronológicas, débitos/créditos, saldo corrido D/H y totales exactos; incluye saldo inicial anterior a «Desde», excluye borradores y separa otras monedas sin conversión.
- Filtros opcionales inclusivos Desde/Hasta para el Mayor y el Balance de comprobación, en formato estricto AAAA-MM-DD. El saldo inicial del Mayor incorpora los movimientos anteriores a «Desde»; los totales débito/crédito solo incluyen las filas del periodo.
- GitHub Actions compila el APK de depuración.

## Limitaciones conocidas

- Moneda del prototipo fija en USD, dos decimales; sin conversión de cambio ni soporte multi-moneda.
- El plan permite agregar cuentas nuevas, pero aún no editar nombres/tipos ni desactivar cuentas; la plantilla sigue siendo genérica y no configurable por empresa.
- Los asientos sencillos se marcan contabilizados al guardar; falta flujo de borrador, aprobación, reversa y auditoría completa.
- Aún sin API/backend, usuarios, roles, sincronización, adjuntos, importación/exportación o restauración.
- El Mayor y el Balance de comprobación permiten seleccionar un rango de fechas local en USD; faltan controles de cierre de periodos y estados financieros completos.
- No se declara cumplimiento contable/fiscal local.

## Siguiente hito

Siguiente: ciclo seguro de edición/desactivación de cuentas sin cambiar clasificaciones históricas, reversas trazables e historial de auditoría, controles de cierre/periodos y estados financieros más completos; repetir la prueba instrumentada de migración en un emulador que arranque. Después siguen API/backend, sincronización y respaldo dentro de Parte 1. El chat conversacional continúa fuera de mi alcance; cualquier futuro soporte multi-moneda debe diseñarse expresamente antes de implementarse.

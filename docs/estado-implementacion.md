# Estado de implementación — Parte 1

## Hecho

- Repositorio privado y documentación de alcance, arquitectura, guía visual y contrato de integración.
- Primera pantalla Android Kotlin/Compose con estilo tipo hoja de cálculo.
- Base Room local versionada con cuentas, asientos y líneas contables.
- Catálogo inicial de demostración: Caja, Banco, Ingresos, Papelería, Servicios y Otros gastos.
- Formulario local para ingreso o gasto simple: descripción, importe, fecha, categoría y cuenta de efectivo/banco.
- La capa de dominio genera las dos líneas contables, valida el tipo de cuenta y comprueba débito = crédito.
- La escritura del asiento y sus líneas se realiza en una transacción local.
- Reglas contables separadas en el dominio y pruebas unitarias para ingreso, gasto, cuenta incompatible, balance y parseo/formato USD.
- Moneda funcional del libro persistida como USD en Room, con migración 1→2 que conserva los asientos existentes.
- Importes USD representados como centavos enteros (`Long`), analizados con `BigDecimal` exacto y sin coma flotante binaria; salida rotulada `US$` con dos decimales. Admite formas inequívocas estadounidense y española; rechaza más de dos decimales y entradas ambiguas en vez de adivinarlas.
- La migración Room 1→2 crea la configuración USD sin borrar asientos ni líneas anteriores; conserva los importes existentes tal cual y no convierte monedas.
- La tabla muestra cada línea del asiento y calcula saldo neto, ingresos/gastos y totales desde los registros locales.
- GitHub Actions compila el APK y ejecuta una prueba instrumentada en emulador que crea una base v1, la migra a v2 y comprueba que sobrevive un asiento balanceado junto con sus líneas y que se crea la configuración USD. El flujo publica el APK y el esquema Room generado como artefactos; la ejecución queda pendiente del siguiente Actions run.

## Limitaciones conocidas

- Moneda funcional fija en USD, dos decimales; sin conversión de cambio ni soporte multi-moneda.
- Plan de cuentas fijo para demostración; aún no editable ni configurable por empresa.
- Los asientos sencillos se marcan contabilizados al guardar; falta flujo de borrador, aprobación, reversa y auditoría completa.
- Aún sin API/backend, usuarios, roles, sincronización, adjuntos, importación/exportación o restauración.
- Mayor, balance y estados financieros están en navegación visual, no implementados funcionalmente.
- No se declara cumplimiento contable/fiscal local.

## Siguiente hito

Verificar en Actions la prueba de migración instrumentada y revisar e incorporar al control de versiones el esquema Room exportado. Después, añadir borradores, reversas y auditoría antes de ampliar reportes; implementar API/backend, seguridad y sincronización en un hito propio. La interfaz conversacional, Groq y MCP corresponden a Parte 2 y quedan fuera de este hito.

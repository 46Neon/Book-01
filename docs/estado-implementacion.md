# Estado de implementación — Parte 1

## Hecho

- Repositorio privado y documentación de alcance, arquitectura, guía visual y contrato de integración.
- Primera pantalla Android Kotlin/Compose con estilo tipo hoja de cálculo.
- Base Room local versionada con cuentas, asientos y líneas contables.
- Catálogo inicial de demostración: Caja, Banco, Ingresos, Papelería, Servicios y Otros gastos.
- Formulario local para ingreso o gasto simple: descripción, importe, fecha, categoría y cuenta de efectivo/banco.
- La capa de dominio genera las dos líneas contables, valida el tipo de cuenta y comprueba débito = crédito.
- La escritura del asiento y sus líneas se realiza en una transacción local.
- Reglas contables separadas en el dominio y pruebas unitarias para ingreso, gasto, cuenta incompatible, balance e importes con coma/punto.
- La tabla muestra cada línea del asiento y calcula saldo neto, ingresos/gastos y totales desde los registros locales.
- GitHub Actions compila el APK de depuración.

## Limitaciones conocidas

- Moneda del prototipo fija en USD, dos decimales; sin tasa ni multi-moneda.
- Plan de cuentas fijo para demostración; aún no editable ni configurable por empresa.
- Los asientos sencillos se marcan contabilizados al guardar; falta flujo de borrador, aprobación, reversa y auditoría completa.
- Aún sin API/backend, usuarios, roles, sincronización, adjuntos, importación/exportación o restauración.
- Mayor, balance y estados financieros están en navegación visual, no implementados funcionalmente.
- No se declara cumplimiento contable/fiscal local.

## Siguiente hito

Separar formalmente dominio, almacenamiento y presentación; añadir pruebas automatizadas del equilibrio y clasificación; luego diseñar configuración del libro/moneda y reversas antes de ampliar reportes o crear el backend compartido.

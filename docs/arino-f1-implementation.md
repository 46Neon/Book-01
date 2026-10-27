# Ariño — implementación sencilla de F1

Este documento acompaña el trabajo de F1 sobre la rama `work/arino-f1-account-nature`, dependiente de F0. No declara implementadas funciones que todavía no existen.

## Paso 1 — Fundamentos del plan de cuentas

- La pantalla Plan de cuentas muestra código, nombre, tipo, naturaleza normal (aumenta por Debe/Haber), estado y uso.
- Etiquetas explicativas: Activo, Costo, Gasto, Pasivo, Patrimonio e Ingreso.
- Tipos y reglas fiscales deben ser aprobados por un contador antes de agregarlos al catálogo operativo; la etiqueta informativa no cambia cálculos existentes.
- La primera mejora implementada por este PR presenta el lado de aumento para los tipos de cuenta ya reconocidos y lo prueba como regla pura.

## Paso 2 — Clientes y proveedores

Maestro compartido mínimo en Room con tipo explícito (cliente/proveedor), nombre, identificación fiscal opcional, contacto opcional, plazo de crédito expresado en días y estado activo/inactivo. Los campos son simples, editables y sin cálculos de saldo hasta que existan facturas/cobros/pagos.

Para proveedores se podrá guardar categoría sugerida y cuenta por defecto, pero la cuenta sugerida nunca contabiliza por sí sola ni impone una regla tributaria. Los valores iniciales de prueba, si se agregan, se identificarán como demostrativos y ficticios.

Reglas mínimas: nombre requerido, tipo requerido, identificación normalizada sin asumir que su formato es legalmente válido, no borrar físicamente registros referenciados; desactivar en lugar de borrar. Duplicidad sugerida por tipo + identificación no vacía, sujeta a aprobación del contador.

## Paso 3 — Compatibilidad antes de habilitar captura

La migración debe ser aditiva desde Room v11; comprobar una base vieja con datos y conservar asientos, auditorías, cierres, cuentas y bloqueos. Las tablas de terceros deben incluirse en la instantánea cifrada y restaurarse bajo el mismo flujo de validación/transacción que las demás tablas. No lanzar la captura en UI hasta que migración y respaldo estén cubiertos.

## Paso 4 — Pruebas de aceptación de terceros

- Migrar de v11 a la nueva versión conservando todos los datos previos.
- Crear cliente y proveedor, validar campos requeridos y valores opcionales.
- Modificar y desactivar un tercero, manteniendo su registro e historial.
- Rechazar duplicado cuando se haya adoptado el criterio acordado.
- Exportar/restaurar una copia cifrada y confirmar que los terceros vuelven completos.
- Verificar que crear maestros no genere asientos, saldos ni impuestos.

## Paso 5 — Facturas (siguiente bloque F1, todavía no implementado)

Después de validar terceros se implementan formularios de venta y compra con factura, documento, fechas, base, tasa configurable, pagos, saldo, vista previa del asiento y cobros/pagos posteriores. La tasa por defecto y el tratamiento de IVA son parámetros; antes de afirmar conformidad fiscal hay que obtener validación profesional y formatos oficiales vigentes.

## Decisiones para aprobación del contador

- Campos obligatorios de RIF/identificación y formato esperado.
- Regla de duplicidad de terceros y documentos.
- Catálogo de cuentas y tipos para costos, inventario, IVA y retenciones.
- Si compras de mercancía afectan inventario o costo directamente.
- Tratamiento de crédito fiscal, devoluciones y redondeo por documento.

## Restricciones de entrega

No introducir datos reales de clientes/proveedores en pruebas. No cambiar el `applicationId`. No guardar secretos ni modificar `main`. Toda la funcionalidad de terceros requiere migración, pruebas, respaldo y aprobación antes de considerarse lista.

# F1 — facturas, cobros y pagos (alcance de este PR)

Añade documentos locales de venta y compra sobre el libro Room existente, con asientos automáticos, operaciones atómicas y auditoría. No declara cumplimiento SENIAT ni sustituye el visto bueno de un contador venezolano.

## Interpretaciones pendientes de aprobación profesional

- La tasa se captura por factura; `1.600` puntos base corresponde a 16 %. La UI no la preselecciona y guarda la tasa histórica en centésimas de punto.
- IVA débito de ventas y el tratamiento de IVA compras se eligen/configuran como hipótesis contable. La condición de crédito fiscal recuperable **no** se da por aprobada. El documento lo señala en la vista previa.
- El impuesto se calcula sobre centavos por factura usando redondeo HALF_UP. El contador debe confirmar el criterio de redondeo aplicable.
- Si el proveedor tiene cuenta de destino por defecto, se propone como costo/gasto/activo. El tratamiento para compras a activos requiere revisión.
- Duplicado: (tipo de documento, contraparte registrada, número de factura) luego de normalizar número a mayúsculas/sin espacios extremos.
- Se exige número de control para factura de venta en esta propuesta; validar la obligatoriedad y formato contra normativa vigente.
- Quedan fuera: 1 %, retenciones, impuesto municipal, calendario/frecuencia de declaración, banco/importación, OCR e inventario.

## Asientos de prueba, USD

- Venta base 100, tasa parámetro 16 %: Debe Banco/Caja 116; Haber Ingresos 100 e IVA débito provisional 16.
- Compra base 50, 16 % y criterio provisional de IVA separado: Debe Costo/Gasto 50 e IVA crédito provisional 8; Haber Banco/Caja 58.
- Venta de 116, cobro inicial 50: Debe Banco 50 y Cuentas por cobrar 66; Haber Ingresos 100 e IVA débito 16. Cobro posterior: Debe Banco 66 / Haber Cuentas por cobrar 66.
- Compra base 30 a tasa 0 en fixture (el plan no especifica tasa para este caso), pago de 10: Debe Costo/Gasto 30 / Haber Banco 10 y Cuentas por pagar 20. Pago posterior: Debe Cuentas por pagar 20 / Haber Banco 20.
- Préstamo 5.000: Debe Banco 5.000 / Haber Préstamo por pagar 5.000; nunca se registra como venta.
- Pago superior al saldo contable de caja/banco disponible se rechaza.

Las pruebas de dominio verifican estos montos, partida doble, pagos posteriores, tasa configurable, documento contado/crédito, venta controlada, préstamos, duplicados y saldo insuficiente. El guardado de factura, asiento, líneas, liquidación inicial y auditoría se hace en una misma transacción Room.

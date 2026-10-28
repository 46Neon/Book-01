# F1 usability milestone: auxiliary books first

This scoped milestone improves the mobile workflow without changing the ledger schema or accounting records. The Android app remains Kotlin, Jetpack Compose, and Room. Sales and purchases are shown as distinct auxiliary books at the start of navigation; each list filters to its own document kind, offers a matching invoice action and counterparty shortcut, and seeds the invoice form with that book's kind. The generated Journal and General Ledger remain separate, explicitly book-oriented destinations.

## Integrity and scope

- Existing invoice records remain invoice records; no income/expense journal entries are relabeled or inferred as invoices.
- Invoice accounting, configurable tax inputs, posting, settlements, and immutable/auditable accounting data remain backed by the existing F1 domain and repository. This UI change adds no Room schema or migration.
- Generic journal-entry capture remains available in the Journal, but is no longer the first task presented on app launch.
- Rates and purchase-tax treatment are parameters/hypotheses, not legal determinations. No country-specific compliance or tax-rate claim is made.

## Remaining roadmap (not implemented here)

1. Complete F1: configurable chart of accounts and master data, opening balances, invoice correction histories, robust customer/vendor statements beyond the current balance summary, and printable/exportable period books. Verify all accounting rules with an accountant before release.
2. F2: spreadsheet-like responsive table UX, search/filter/sort/totals, verify trial balance and financial statements, XLSX/PDF exports, and one company/year book boundary.
3. F3: balanced manual journal entries and bank import/reconciliation.
4. F4: reviewed invoice recognition, configurable tax/withholding profiles and filing calendar, only after expert validation.
5. F5: accountant acceptance, release checks and signed release. Do not claim tax/legal conformity without this review.

The existing GitHub Actions workflow already limits the debug APK artifact to two-day retention and only uploads it for main/manual builds. No workflows, releases, or artifacts were deleted or otherwise changed in this milestone.

## Invoice balance status (accounting-facing slice)

Invoice cards now display a derived balance status alongside the settled and outstanding amounts: Paid, Partial, Open, or Overdue. The status is calculated from the invoice total, persisted settlements, and the due date only when one was explicitly entered. A missing or malformed due date does not imply overdue; a fully settled invoice remains Paid, and any remaining balance after a passed due date is Overdue even when partially paid. This is an informational view only: it creates no accounting entries and changes no persisted invoice or settlement data. The current settlement UI and ledger continue to use the existing USD storage model; broader currency support remains future work.

## Filter by collection/payment state

The sales and purchase auxiliary books now expose display-only filters for all invoices, open balances, partial settlements, overdue balances, and paid invoices. Each invoice's filter state is derived from the same persisted settlement flow and explicit due date used for its status label. The filter does not alter, post, or hide records from the ledger; changing tabs or filters only changes which cards are currently visible. Unit tests cover that each filter maps exclusively to its corresponding derived state.

## Customer and supplier balance overview (derived, no new accounting records)

Each auxiliary book now provides a per-customer or per-supplier balance summary sourced from saved invoice totals and their saved initial/later settlements. It shows remaining and overdue amounts as of the current device date; past-due amounts require an explicitly stored due date and an outstanding balance. Fully settled invoices contribute zero, missing or malformed dates are not treated as overdue, and aggregation uses exact integer minor units with overflow/over-settlement rejection. The overview is derived only: no ledger postings or database schema changes are introduced. The summary covers the currently selected auxiliary book kind and is independent of the invoice issue-period filter.


## Antigüedad de saldos vencidos

El resumen por cliente/proveedor también desglosa los saldos vencidos pendientes en 1–30, 31–60 y más de 60 días, calculados por días transcurridos desde la fecha de vencimiento hasta la fecha local de consulta. El día de vencimiento todavía no se considera vencido; la suma de los tres tramos coincide con el saldo vencido. Cada tramo parte del saldo restante tras los abonos guardados, usa centavos enteros y trata una fecha ausente o inválida como antigüedad desconocida (no vencida). Es una vista informativa, no una modificación de facturas ni asientos.


## Prioridad de gestión de cobros y pagos

En el resumen auxiliar, las contrapartes con mayor saldo vencido aparecen primero; después se ordena por saldo pendiente, nombre y un identificador estable para resolver empates. Esto solo organiza la vista derivada para priorizar revisión y seguimiento: no altera facturas, vencimientos, abonos ni asientos.


## Estado de cuenta por contraparte (movimientos reales, vista de solo lectura)

Ventas y compras permiten seleccionar un cliente/proveedor y consultar su estado de cuenta cronológico. La vista reúne únicamente facturas guardadas para esa contraparte y cobros/pagos registrados y vinculados a ellas; cada renglón presenta fecha, referencia, cambio en unidades monetarias menores exactas y saldo acumulado. Si factura y abono tienen la misma fecha, la factura se muestra primero. El saldo final representa el pendiente de las facturas incluidas en el libro; la vista no crea asientos, no modifica registros y no calcula impuestos. Se mantienen las restricciones existentes de facturas inmutables y movimientos de liquidación separados/auditables. La prueba unitaria cubre secuencia, abonos parciales, fechas iguales, referencias y límites de integridad/overflow.


## Acceso al estado de cuenta en los libros auxiliares

El estado de cuenta de solo lectura está disponible directamente en cada libro auxiliar mediante un selector de cliente/proveedor. Muestra las facturas y cobros/pagos guardados que corresponden a la contraparte, con referencia, cambio y saldo corrido; la vista no agrega ni altera registros. La prueba de interfaz comprueba el acceso y el estado inicial sin contraparte seleccionada.


## Período inclusivo del estado de cuenta

Se pueden indicar fechas inicial y final (AAAA-MM-DD) para consultar un tramo inclusivo del estado de cuenta. El saldo inicial se deriva de todos los movimientos guardados anteriores a la fecha inicial; los saldos corridos del tramo parten de ese importe y el saldo final coincide con el último saldo calculado (o con el saldo inicial si el tramo no tiene movimientos). Fechas omitidas dejan abierto el extremo correspondiente. Se rechaza el formato de fecha no válido y un rango invertido. La función es de solo lectura y opera en unidades monetarias menores enteras; no crea, altera ni elimina facturas, liquidaciones ni asientos. Las pruebas de dominio cubren límites inclusivos, saldo inicial, períodos vacíos y fechas inválidas.

## Compartir estado de cuenta como CSV de texto

Desde el estado de cuenta filtrado se puede abrir el menú de compartir Android con una representación CSV del tramo visible. Incluye encabezados, contraparte, fecha/tipo/referencia, cambio y saldo corrido, código de moneda, saldo inicial y saldo final; conserva los importes almacenados como unidades menores enteras exactas y escapa comas/comillas. Los campos textuales que podrían interpretarse como fórmulas de hoja de cálculo reciben una marca preventiva. Se comparte como texto MIME `text/csv`; no se escribe un archivo ni se persiste ningún dato, y la consulta sigue siendo de solo lectura. Pruebas puras cubren valores exactos, extremos del período, escape, prefijos de fórmula y rechazo de código de moneda inválido. No se afirma compatibilidad exhaustiva con todos los lectores CSV ni se implementa PDF/XLSX.

## Validación visible de vencimiento de factura

En la captura de una factura a crédito, una fecha de vencimiento opcional se valida en el formulario: debe ser una fecha real en formato `AAAA-MM-DD` y no puede preceder la fecha de factura. El mensaje aparece junto al campo antes del guardado; se admite dejarlo vacío y la validación del dominio/repository sigue protegiendo el guardado. No se derivan vencimientos ni se altera el cálculo contable. Pruebas de dominio cubren fecha ausente, igual, posterior, anterior, inválida y formato estricto.

## Aviso preventivo de referencia de factura duplicada

Al capturar el número, la factura de venta/compra compara la referencia normalizada (recorte y mayúsculas) con los documentos ya cargados del mismo tipo y contraparte. Si detecta una coincidencia, informa antes del guardado y deshabilita la acción para reducir duplicados accidentales. La comprobación es una ayuda de interfaz basada en la lista visible; la validación transaccional existente del repositorio sigue siendo la autoridad final ante carreras o datos desactualizados. No crea ni modifica asientos, facturas, saldos ni esquema Room. Las pruebas de dominio cubren normalización, alcance por contraparte/tipo y entradas vacías.

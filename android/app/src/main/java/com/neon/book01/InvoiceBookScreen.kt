package com.neon.book01

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.book01.data.AccountEntity
import com.neon.book01.data.CounterpartyEntity
import com.neon.book01.data.InvoiceEntity
import com.neon.book01.data.LedgerRepository
import com.neon.book01.domain.InvoiceAccounts
import com.neon.book01.domain.InvoiceAccounting
import com.neon.book01.domain.InvoiceInput
import com.neon.book01.domain.InvoiceKind
import com.neon.book01.domain.PaymentCondition
import com.neon.book01.domain.PurchaseVatTreatment
import com.neon.book01.domain.UsdMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun InvoiceBookScreen(repository: LedgerRepository, accounts: List<AccountEntity>, currencyCode: String, showForm: Boolean, setShowForm: (Boolean) -> Unit, snackbarHostState: SnackbarHostState, initialKind: InvoiceKind) {
    var invoices by remember { mutableStateOf<List<InvoiceEntity>>(emptyList()) }
    var clients by remember { mutableStateOf<List<CounterpartyEntity>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<CounterpartyEntity>>(emptyList()) }
    var showPartyDialog by remember { mutableStateOf(false) }
    var partyKind by remember { mutableStateOf("CLIENT") }
    LaunchedEffect(repository) {
        launch { repository.observeInvoices().collect { invoices = it } }
        launch { repository.observeCounterparties("CLIENT").collect { clients = it } }
        launch { repository.observeCounterparties("SUPPLIER").collect { suppliers = it } }
    }
    val selectedKind = if (initialKind == InvoiceKind.SALE) "SALE" else "PURCHASE"
    val bookInvoices = invoices.filter { it.kind == selectedKind }
    val bookTitle = if (initialKind == InvoiceKind.SALE) "Libro auxiliar · Ventas" else "Libro auxiliar · Compras"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(bookTitle, color = Color(0xFF172B4D), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(if (initialKind == InvoiceKind.SALE) "Facturas emitidas · clientes · cobros y saldos por cobrar" else "Facturas recibidas · proveedores · pagos y saldos por pagar", color = Color(0xFF6B778C), fontSize = 12.sp)
        Text("Los asientos se generan automáticamente desde cada factura. Las tasas y tratamientos son parámetros contables configurables; confirma los criterios fiscales aplicables con un profesional.", color = Color(0xFF6B778C), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { setShowForm(true) }) { Text(if (initialKind == InvoiceKind.SALE) "Nueva factura de venta" else "Nueva factura de compra") }
            OutlinedButton(onClick = { partyKind = if (initialKind == InvoiceKind.SALE) "CLIENT" else "SUPPLIER"; showPartyDialog = true }) { Text(if (initialKind == InvoiceKind.SALE) "Registrar cliente" else "Registrar proveedor") }
        }
        if (bookInvoices.isEmpty()) Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Text(if (initialKind == InvoiceKind.SALE) "Aún no hay facturas de venta. Registra clientes y captura los documentos emitidos." else "Aún no hay facturas de compra. Registra proveedores y captura los documentos recibidos.", Modifier.padding(16.dp), color = Color(0xFF6B778C))
        }
        bookInvoices.forEach { InvoiceCard(it, repository, accounts) }
    }
    if (showForm) InvoiceEntryDialog(accounts, clients, suppliers, repository, { setShowForm(false) }, snackbarHostState, initialKind)
    if (showPartyDialog) CounterpartyEntryDialog(partyKind, repository, { showPartyDialog = false }, accounts)
}

@Composable
private fun InvoiceCard(invoice: InvoiceEntity, repository: LedgerRepository, accounts: List<AccountEntity>) {
    var settlements by remember(invoice.id) { mutableStateOf(emptyList<com.neon.book01.data.InvoiceSettlementEntity>()) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(invoice.id) { repository.observeInvoiceSettlements(invoice.id).collect { settlements = it } }
    val settled = settlements.sumOf { it.amountMinor }
    val pending = (invoice.totalMinor - settled).coerceAtLeast(0L)
    val partyFlow = remember(invoice.registeredPartyId) { repository.observeCounterparties(if (invoice.kind == "SALE") "CLIENT" else "SUPPLIER") }
    var partyName by remember(invoice.registeredPartyId) { mutableStateOf(invoice.registeredPartyId) }
    LaunchedEffect(partyFlow) { partyFlow.collect { rows -> partyName = rows.firstOrNull { it.id == invoice.registeredPartyId }?.displayName ?: invoice.registeredPartyId } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${if (invoice.kind == "SALE") "Venta" else "Compra"} · ${invoice.invoiceNumber}", color = Color(0xFF172B4D), fontWeight = FontWeight.SemiBold)
            Text("${invoice.invoiceDate} · $partyName · ${invoice.description}", color = Color(0xFF6B778C), fontSize = 12.sp)
            Text("Base ${money(invoice.taxableBaseMinor)} · IVA ${money(invoice.vatMinor)} · Total ${money(invoice.totalMinor)}", color = Color(0xFF172B4D), fontSize = 12.sp)
            Text("Abonado ${money(settled)} · pendiente ${money(pending)}", color = Color(0xFF6B778C), fontSize = 12.sp)
            if (invoice.dueDate != null) Text("Vence ${invoice.dueDate}", color = Color(0xFF6B778C), fontSize = 11.sp)
            if (pending > 0) TextButton(onClick = { showDialog = true }) { Text(if (invoice.kind == "SALE") "Registrar cobro" else "Registrar pago") }
        }
    }
    if (showDialog) SettlementDialog(invoice, pending, accounts, repository, { showDialog = false })
}

@Composable
private fun SettlementDialog(invoice: InvoiceEntity, pending: Long, accounts: List<AccountEntity>, repository: LedgerRepository, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var cash by remember { mutableStateOf(accounts.firstOrNull { it.active && it.cashOrBank }?.code ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (invoice.kind == "SALE") "Cobro de factura ${invoice.invoiceNumber}" else "Pago de factura ${invoice.invoiceNumber}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pendiente: ${money(pending)}")
            OutlinedTextField(date, { date = it; error = null }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
            OutlinedTextField(amountText, { amountText = it; error = null }, label = { Text("Importe (USD)") }, singleLine = true)
            OutlinedButton(onClick = { expanded = true }) { Text(accounts.firstOrNull { it.code == cash }?.name ?: "Elegir caja/banco") }
            DropdownMenu(expanded, { expanded = false }) { accounts.filter { it.active && it.cashOrBank }.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { cash = a.code; expanded = false }) } }
            error?.let { Text(it, color = Color(0xFFB3261E)) }
        } },
        confirmButton = { Button(onClick = { scope.launch { try {
            val amount = UsdMoney.parseMinorUnits(amountText)
            require(amount <= pending) { "El importe supera el saldo pendiente." }
            val selectedCash = accounts.firstOrNull { it.code == cash && it.active && it.cashOrBank } ?: throw IllegalArgumentException("Selecciona una cuenta activa de caja o banco.")
            val config = defaultInvoiceAccounts(accounts, selectedCash.code, PurchaseVatTreatment.INPUT_TAX_ASSET)
            withContext(Dispatchers.IO) {
                if (invoice.kind == "PURCHASE" && !repository.hasAvailableCash(cash, amount)) throw IllegalArgumentException("Saldo insuficiente: el pago dejaría caja o banco en negativo.")
                repository.settleInvoice(invoice.id, amount, date, cash, config)
            }
            onDismiss()
        } catch (e: Exception) { error = e.message ?: "No se pudo registrar el movimiento." } } }) { Text("Guardar movimiento") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun CounterpartyEntryDialog(kind: String, repository: LedgerRepository, onDismiss: () -> Unit, accounts: List<AccountEntity>) {
    var name by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var defaultCode by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val choices = accounts.filter { a -> a.active && if (kind == "CLIENT") a.type == "ASSET" else a.type == "EXPENSE" || a.type == "ASSET" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (kind == "CLIENT") "Registrar cliente" else "Registrar proveedor") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre registrado") }, singleLine = true)
            OutlinedTextField(taxId, { taxId = it; error = null }, label = { Text("RIF / identificación (opcional)") }, singleLine = true)
            if (kind == "SUPPLIER") {
                OutlinedButton(onClick = { expanded = true }) { Text(choices.firstOrNull { it.code == defaultCode }?.let { "Cuenta predeterminada: ${it.name}" } ?: "Clasificación/cuenta por defecto") }
                DropdownMenu(expanded, { expanded = false }) { choices.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { defaultCode = a.code; expanded = false }) } }
            }
            error?.let { Text(it, color = Color(0xFFB3261E)) }
        } },
        confirmButton = { Button(onClick = { scope.launch { try {
            require(kind != "SUPPLIER" || defaultCode.isNotBlank()) { "Elige la clasificación del proveedor." }
            withContext(Dispatchers.IO) { repository.createCounterparty(UUID.randomUUID().toString(), kind, name, taxId, defaultCode.takeIf { it.isNotBlank() }) }
            onDismiss()
        } catch (e: Exception) { error = e.message ?: "No se pudo guardar el tercero." } } }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun InvoiceEntryDialog(accounts: List<AccountEntity>, clients: List<CounterpartyEntity>, suppliers: List<CounterpartyEntity>, repository: LedgerRepository, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, initialKind: InvoiceKind) {
    var kind by remember { mutableStateOf(initialKind) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var dueDate by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var control by remember { mutableStateOf("") }
    var partyId by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var baseText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var purchaseVatTreatment by remember { mutableStateOf<PurchaseVatTreatment?>(null) }
    var paidText by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf(PaymentCondition.CREDIT) }
    var cashCode by remember { mutableStateOf(accounts.firstOrNull { it.active && it.cashOrBank }?.code ?: "") }
    var partyExpanded by remember { mutableStateOf(false) }
    var cashExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parties = if (kind == InvoiceKind.SALE) clients else suppliers
    val supplierAccount = suppliers.firstOrNull { it.id == partyId }?.defaultAccountCode
    val accountsConfig = defaultInvoiceAccounts(accounts, cashCode, purchaseVatTreatment ?: PurchaseVatTreatment.INPUT_TAX_ASSET).let { baseAccounts ->
        if (kind == InvoiceKind.PURCHASE && supplierAccount != null) baseAccounts.copy(purchaseCostOrExpense = supplierAccount) else baseAccounts
    }
    val input = runCatching {
        val base = UsdMoney.parseMinorUnits(baseText)
        val rate = if (rateText.isBlank()) throw IllegalArgumentException("Indica una tasa configurable (usa 0 si la operación es exenta).") else BigDecimal(rateText.trim()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
        val paid = if (paidText.isBlank()) 0L else UsdMoney.parseMinorUnits(paidText)
        InvoiceInput(kind, date, number, control.takeIf { kind == InvoiceKind.SALE }, partyId, description, base, rate, condition, paid, cashCode, dueDate.takeIf { condition == PaymentCondition.CREDIT && it.isNotBlank() })
    }.getOrNull()
    val preview = input?.let { runCatching { InvoiceAccounting.preview(it, accountsConfig) }.getOrNull() }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (kind == InvoiceKind.SALE) "Nueva factura de venta" else "Nueva factura de compra") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { kind = InvoiceKind.SALE; partyId = ""; error = null }) { Text("Venta") }; OutlinedButton(onClick = { kind = InvoiceKind.PURCHASE; partyId = ""; error = null }) { Text("Compra") } }
            OutlinedTextField(date, { date = it; error = null }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { condition = PaymentCondition.CASH; if (paidText.isBlank()) paidText = baseText }, modifier = Modifier.fillMaxWidth()) { Text("Condición seleccionada: ${if (condition == PaymentCondition.CASH) "Contado" else "Crédito"}") }
            OutlinedTextField(number, { number = it; error = null }, label = { Text("Número de factura") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (condition == PaymentCondition.CREDIT) OutlinedTextField(dueDate, { dueDate = it; error = null }, label = { Text("Vencimiento (opcional, AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (kind == InvoiceKind.SALE) OutlinedTextField(control, { control = it; error = null }, label = { Text("Número de control") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(if (kind == InvoiceKind.SALE) "Cliente registrado" else "Proveedor registrado", fontSize = 12.sp, color = Color(0xFF6B778C))
            OutlinedButton(onClick = { partyExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(parties.firstOrNull { it.id == partyId }?.displayName ?: "Seleccionar tercero") }
            DropdownMenu(partyExpanded, { partyExpanded = false }) { parties.filter { it.active }.forEach { p -> DropdownMenuItem(text = { Text(p.displayName) }, onClick = { partyId = p.id; partyExpanded = false }) } }
            OutlinedTextField(description, { description = it; error = null }, label = { Text("Descripción") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(baseText, { baseText = it; error = null }, label = { Text("Base imponible (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rateText, { rateText = it; error = null }, label = { Text("Tasa de IVA (%) configurable; 0 si exenta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (kind == InvoiceKind.PURCHASE) {
                Text("Tratamiento del IVA de compra (requiere aprobación contable)", fontSize = 12.sp, color = Color(0xFF6B778C))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { purchaseVatTreatment = PurchaseVatTreatment.INPUT_TAX_ASSET }) { Text(if (purchaseVatTreatment == PurchaseVatTreatment.INPUT_TAX_ASSET) "✓ IVA separado" else "IVA separado") }
                    OutlinedButton(onClick = { purchaseVatTreatment = PurchaseVatTreatment.INCLUDED_IN_PURCHASE_COST }) { Text(if (purchaseVatTreatment == PurchaseVatTreatment.INCLUDED_IN_PURCHASE_COST) "✓ Incluido en costo" else "Incluido en costo") }
                }
                if (purchaseVatTreatment == null) Text("Elige un criterio provisional; no es una determinación legal.", color = Color(0xFFB3261E), fontSize = 11.sp)
            }
            OutlinedTextField(paidText, { paidText = it; error = null }, label = { Text("Cobrado/pagado hoy (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { condition = PaymentCondition.CASH }) { Text(if (condition == PaymentCondition.CASH) "✓ Contado" else "Contado") }; OutlinedButton(onClick = { condition = PaymentCondition.CREDIT }) { Text(if (condition == PaymentCondition.CREDIT) "✓ Crédito" else "Crédito") } }
            OutlinedButton(onClick = { cashExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(accounts.firstOrNull { it.code == cashCode }?.name ?: "Elegir caja o banco") }
            DropdownMenu(cashExpanded, { cashExpanded = false }) { accounts.filter { it.active && it.cashOrBank }.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { cashCode = a.code; cashExpanded = false }) } }
            preview?.let { p -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EF))) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vista previa · solo lectura", fontWeight = FontWeight.SemiBold)
                Text("Base ${money(p.baseMinor)} · IVA ${money(p.vatMinor)} · Total ${money(p.totalMinor)}")
                p.lines.forEach { Text("${it.accountCode} · Debe ${money(it.debitMinor)} · Haber ${money(it.creditMinor)}", fontSize = 11.sp) }
                Text("Saldo pendiente ${money(p.outstandingMinor)}", fontWeight = FontWeight.SemiBold)
                Text("IVA compras se trata provisionalmente como activo recuperable; aprobación del contador requerida.", fontSize = 10.sp, color = Color(0xFF6B778C))
            } } }
            error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
        } },
        confirmButton = { Button(enabled = !saving && input != null && preview != null && partyId.isNotBlank() && (kind != InvoiceKind.PURCHASE || purchaseVatTreatment != null), onClick = { val value = input ?: return@Button; scope.launch { saving = true; try {
            withContext(Dispatchers.IO) {
                val party = parties.firstOrNull { it.id == value.registeredPartyId && it.active } ?: throw IllegalArgumentException("Registra o selecciona un tercero activo.")
                require(party.kind == if (kind == InvoiceKind.SALE) "CLIENT" else "SUPPLIER") { "El tercero no corresponde al tipo de factura." }
                repository.createInvoice(value, accountsConfig.copy(purchaseVatTreatment = purchaseVatTreatment ?: accountsConfig.purchaseVatTreatment))
            }
            onDismiss(); snackbarHostState.showSnackbar("Factura guardada con asiento automático y auditoría.")
        } catch (e: Exception) { error = e.message ?: "No se pudo guardar la factura." } finally { saving = false } } }) { Text(if (saving) "Guardando…" else "Guardar factura") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } })
}

private fun defaultInvoiceAccounts(accounts: List<AccountEntity>, cashCode: String, purchaseVatTreatment: PurchaseVatTreatment = PurchaseVatTreatment.INPUT_TAX_ASSET) = InvoiceAccounts(
    cashOrBank = cashCode,
    receivable = accounts.firstOrNull { it.code == "1100" }?.code ?: "1100",
    payable = accounts.firstOrNull { it.code == "2000" }?.code ?: "2000",
    revenue = accounts.firstOrNull { it.type == "INCOME" && it.active }?.code ?: "4000",
    purchaseCostOrExpense = accounts.firstOrNull { it.type == "EXPENSE" && it.active }?.code ?: "5100",
    outputVat = accounts.firstOrNull { it.code == "2103" }?.code ?: "2103",
    inputVat = accounts.firstOrNull { it.code == "1105" }?.code ?: "1105",
    bankLoanLiability = accounts.firstOrNull { it.code == "2102" }?.code ?: "2102",
    purchaseVatTreatment = purchaseVatTreatment,
)

private fun money(value: Long): String = UsdMoney.format(value)

package com.neon.book01

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.neon.book01.data.AccountEntity
import com.neon.book01.data.EntryKind
import com.neon.book01.data.EntryWithLines
import com.neon.book01.data.LedgerDatabase
import com.neon.book01.data.LedgerRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BookGreen = Color(0xFF147D64)
private val BookGreenSoft = Color(0xFFE7F3EF)
private val BookInk = Color(0xFF172B4D)
private val BookMuted = Color(0xFF6B778C)
private val BookCanvas = Color(0xFFF6F8F9)

private data class BookTab(val title: String, val icon: ImageVector)

private data class EntryDraft(
    val dateIso: String,
    val description: String,
    val kind: EntryKind,
    val categoryAccountCode: String,
    val paymentAccountCode: String,
    val amountMinor: Long,
)

private val tabs = listOf(
    BookTab("Diario", Icons.Outlined.MenuBook),
    BookTab("Mayor", Icons.Outlined.AccountBalance),
    BookTab("Cuentas", Icons.Outlined.ReceiptLong),
    BookTab("Balance", Icons.Outlined.BarChart),
    BookTab("Más", Icons.Outlined.MoreHoriz),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LedgerRepository(LedgerDatabase.getInstance(applicationContext))
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BookGreen,
                    onPrimary = Color.White,
                    secondary = BookInk,
                    background = BookCanvas,
                    surface = Color.White,
                    onSurface = BookInk,
                ),
            ) {
                BookHomeScreen(repository)
            }
        }
    }
}

@Composable
private fun BookHomeScreen(repository: LedgerRepository) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showEntryDialog by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<EntryWithLines>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        repository.initializeBook()
        launch { repository.observeEntries().collect { entries = it } }
        launch { repository.observeAccounts().collect { accounts = it } }
    }

    val incomeMinor = entries.filter { it.entry.kind == EntryKind.INCOME.name }.sumOf { it.entry.amountMinor }
    val expenseMinor = entries.filter { it.entry.kind == EntryKind.EXPENSE.name }.sumOf { it.entry.amountMinor }
    val rowItems = entries.flatMap { entry -> entry.toJournalRows(accounts) }

    Scaffold(
        containerColor = BookCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEntryDialog = true },
                containerColor = BookGreen,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nuevo asiento", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            BookHeader()
            Spacer(Modifier.height(16.dp))
            SummaryCards(
                netMinor = incomeMinor - expenseMinor,
                incomeMinor = incomeMinor,
                expenseMinor = expenseMinor,
            )
            Spacer(Modifier.height(22.dp))

            if (selectedTab == 0) {
                JournalScreen(
                    rows = rowItems,
                    onFilterClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Los filtros se incorporarán en la siguiente iteración.")
                        }
                    },
                )
            } else {
                ComingSoonScreen(tabs[selectedTab].title)
            }
            Spacer(Modifier.height(92.dp))
        }
    }

    if (showEntryDialog) {
        NewEntryDialog(
            accounts = accounts,
            onDismiss = { showEntryDialog = false },
            onSave = { draft ->
                withContext(Dispatchers.IO) {
                    repository.recordSimpleEntry(
                        dateIso = draft.dateIso,
                        description = draft.description,
                        kind = draft.kind,
                        categoryAccountCode = draft.categoryAccountCode,
                        paymentAccountCode = draft.paymentAccountCode,
                        amountMinor = draft.amountMinor,
                    )
                }
                showEntryDialog = false
                snackbarHostState.showSnackbar("Asiento guardado en este dispositivo.")
            },
        )
    }
}

@Composable
private fun BookHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = BookGreenSoft) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = BookGreen, modifier = Modifier.size(23.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text("Book-01", color = BookInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Libro de prueba", color = BookMuted, fontSize = 12.sp)
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Cambiar libro",
                    tint = BookMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        IconButton(onClick = { }) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "Notificaciones", tint = BookInk)
        }
        Surface(shape = RoundedCornerShape(50), color = BookGreenSoft) {
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Text("C", color = BookGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SummaryCards(netMinor: Long, incomeMinor: Long, expenseMinor: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCard("Saldo", money(netMinor), modifier = Modifier.weight(1.15f), emphasize = true)
        SummaryCard("Ingresos", money(incomeMinor), modifier = Modifier.weight(1f))
        SummaryCard("Gastos", money(expenseMinor), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(title: String, amount: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = if (emphasize) BookGreen else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 13.dp)) {
            Text(title, color = if (emphasize) Color.White.copy(alpha = 0.82f) else BookMuted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(
                amount,
                color = if (emphasize) Color.White else BookInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JournalScreen(rows: List<JournalRow>, onFilterClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Diario", color = BookInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Movimientos del libro", color = BookMuted, fontSize = 12.sp)
        }
        Text("USD · LOCAL", color = BookGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, onClick = { }) {
            Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Todos los periodos", color = BookInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Elegir periodo", tint = BookMuted, modifier = Modifier.size(18.dp))
            }
        }
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, onClick = onFilterClick) {
            Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, tint = BookGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Filtrar", color = BookGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LedgerTable(rows)
}

@Composable
private fun LedgerTable(rows: List<JournalRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Todavía no hay asientos", color = BookInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(5.dp))
                Text("Usa “Nuevo asiento” para registrar el primero.", color = BookMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row(modifier = Modifier.background(Color(0xFFF0F4F5)), verticalAlignment = Alignment.CenterVertically) {
                    TableCell("#", 34.dp, isHeader = true, align = TextAlign.Center)
                    TableCell("Fecha", 66.dp, isHeader = true)
                    TableCell("Concepto", 132.dp, isHeader = true)
                    TableCell("Cuenta", 126.dp, isHeader = true)
                    TableCell("Débito", 92.dp, isHeader = true, align = TextAlign.End)
                    TableCell("Crédito", 92.dp, isHeader = true, align = TextAlign.End)
                }
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.background(if (index == 0) Color(0xFFF7FBF9) else Color.White),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TableCell((index + 1).toString(), 34.dp, align = TextAlign.Center, color = BookMuted)
                        TableCell(row.date, 66.dp, color = BookMuted)
                        TableCell(row.concept, 132.dp, fontWeight = FontWeight.Medium)
                        TableCell(row.account, 126.dp, color = BookMuted)
                        TableCell(row.debit, 92.dp, align = TextAlign.End, color = if (row.debit == "—") BookMuted else BookInk)
                        TableCell(row.credit, 92.dp, align = TextAlign.End, color = if (row.credit == "—") BookMuted else BookInk)
                    }
                }
                Row(modifier = Modifier.background(BookGreenSoft), verticalAlignment = Alignment.CenterVertically) {
                    TableCell("", 34.dp, isHeader = true)
                    TableCell("Total", 66.dp, isHeader = true)
                    TableCell("", 132.dp, isHeader = true)
                    TableCell("", 126.dp, isHeader = true)
                    TableCell(money(rows.sumOf { it.debitMinor }), 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                    TableCell(money(rows.sumOf { it.creditMinor }), 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                }
            }
        }
    }
    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Desliza la tabla hacia los lados para ver más columnas",
            modifier = Modifier.fillMaxWidth(),
            color = BookMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    isHeader: Boolean = false,
    align: TextAlign = TextAlign.Start,
    color: Color = BookInk,
    fontWeight: FontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
) {
    Box(
        modifier = Modifier.width(width).height(45.dp).padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = when (align) {
            TextAlign.End -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        Text(
            text = text,
            color = color,
            fontSize = if (isHeader) 10.sp else 11.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntryDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: suspend (EntryDraft) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(EntryKind.EXPENSE) }
    var description by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
    var categoryCode by rememberSaveable { mutableStateOf("") }
    var paymentCode by rememberSaveable { mutableStateOf("1000") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val paymentAccounts = accounts.filter { it.type == "ASSET" }
    val categoryAccounts = accounts.filter { it.type == if (kind == EntryKind.EXPENSE) "EXPENSE" else "INCOME" }
    LaunchedEffect(categoryAccounts) {
        if (categoryAccounts.none { it.code == categoryCode }) {
            categoryCode = categoryAccounts.firstOrNull()?.code.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Nuevo asiento", color = BookInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == EntryKind.EXPENSE,
                        onClick = { kind = EntryKind.EXPENSE; errorText = null },
                        label = { Text("Gasto") },
                    )
                    FilterChip(
                        selected = kind == EntryKind.INCOME,
                        onClick = { kind = EntryKind.INCOME; errorText = null },
                        label = { Text("Ingreso") },
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; errorText = null },
                    label = { Text("Concepto") },
                    placeholder = { Text("Ej. Compra de papelería") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorText = null },
                    label = { Text("Importe en USD") },
                    placeholder = { Text("Ej. 28,50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; errorText = null },
                    label = { Text("Fecha · dd/mm/aaaa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountPicker(
                    label = if (kind == EntryKind.EXPENSE) "Categoría del gasto" else "Cuenta de ingreso",
                    options = categoryAccounts,
                    selectedCode = categoryCode,
                    onSelected = { categoryCode = it },
                )
                AccountPicker(
                    label = "Recibido/pagado en",
                    options = paymentAccounts,
                    selectedCode = paymentCode,
                    onSelected = { paymentCode = it },
                )
                Text("El sistema generará las dos líneas del asiento y comprobará que cuadren.", color = BookMuted, fontSize = 11.sp)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && accounts.isNotEmpty(),
                onClick = {
                    val draft = parseDraft(kind, description, amountText, dateText, categoryCode, paymentCode)
                    if (draft == null) {
                        errorText = "Revisa concepto, importe positivo y fecha válida. Usa hasta dos decimales."
                    } else {
                        scope.launch {
                            saving = true
                            errorText = null
                            try {
                                onSave(draft)
                            } catch (error: Exception) {
                                errorText = error.message ?: "No se pudo guardar el asiento."
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            ) { Text(if (saving) "Guardando…" else "Guardar asiento") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AccountPicker(
    label: String,
    options: List<AccountEntity>,
    selectedCode: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code == selectedCode }
    Column {
        Text(label, color = BookMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFBFC8D0)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selected?.let { "${it.code} · ${it.name}" } ?: "Selecciona una cuenta",
                        modifier = Modifier.weight(1f),
                        color = BookInk,
                        fontSize = 13.sp,
                    )
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = BookMuted)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { account ->
                    DropdownMenuItem(
                        text = { Text("${account.code} · ${account.name}") },
                        onClick = { onSelected(account.code); expanded = false },
                    )
                }
            }
        }
    }
}

private fun parseDraft(
    kind: EntryKind,
    description: String,
    amountText: String,
    dateText: String,
    categoryCode: String,
    paymentCode: String,
): EntryDraft? {
    if (description.isBlank() || categoryCode.isBlank() || paymentCode.isBlank()) return null
    val date = runCatching {
        LocalDate.parse(dateText.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrNull() ?: return null
    val amount = runCatching {
        BigDecimal(amountText.trim().replace(',', '.'))
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()?.takeIf { it > 0 } ?: return null
    return EntryDraft(date.toString(), description.trim(), kind, categoryCode, paymentCode, amount)
}

@Composable
private fun ComingSoonScreen(section: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = BookGreen, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text(section, color = BookInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Esta hoja se habilitará en una siguiente etapa de Book-01.",
                color = BookMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun EntryWithLines.toJournalRows(accounts: List<AccountEntity>): List<JournalRow> {
    val dateLabel = runCatching {
        LocalDate.parse(entry.entryDate).format(DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es-VE")))
            .replace(".", "")
    }.getOrDefault(entry.entryDate)
    return lines.map { line ->
        val accountName = accounts.firstOrNull { it.code == line.accountCode }?.name ?: line.accountCode
        JournalRow(
            date = dateLabel,
            concept = entry.description,
            account = accountName,
            debit = if (line.debitMinor == 0L) "—" else money(line.debitMinor),
            credit = if (line.creditMinor == 0L) "—" else money(line.creditMinor),
            debitMinor = line.debitMinor,
            creditMinor = line.creditMinor,
        )
    }
}

private data class JournalRow(
    val date: String,
    val concept: String,
    val account: String,
    val debit: String,
    val credit: String,
    val debitMinor: Long = 0,
    val creditMinor: Long = 0,
)

private fun money(amountMinor: Long): String =
    "$ " + BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString().replace('.', ',')

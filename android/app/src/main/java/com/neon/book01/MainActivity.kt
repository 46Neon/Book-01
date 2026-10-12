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
import androidx.compose.material3.Checkbox
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
import com.neon.book01.data.AccountAuditEventEntity
import com.neon.book01.data.AuditEventEntity
import com.neon.book01.data.BookSettingsEntity
import com.neon.book01.data.EntryWithLines
import com.neon.book01.data.PeriodCloseEntity
import com.neon.book01.data.LedgerDatabase
import com.neon.book01.data.LedgerRepository
import com.neon.book01.domain.AccountRules
import com.neon.book01.domain.EntryKind
import com.neon.book01.domain.GeneralLedgerCalculator
import com.neon.book01.domain.IncomeStatementReport
import com.neon.book01.domain.BalanceSheetReport
import com.neon.book01.domain.FinancialStatementsCalculator
import com.neon.book01.domain.ClosedPeriodRange
import com.neon.book01.domain.OperatingSummaryCalculator
import com.neon.book01.domain.PeriodCloseAccount
import com.neon.book01.domain.PeriodCloseCalculator
import com.neon.book01.domain.PeriodClosePlan
import com.neon.book01.domain.PeriodClosePosting
import com.neon.book01.domain.PeriodClosePostingLine
import com.neon.book01.domain.ReportAccount
import com.neon.book01.domain.ReportPostingLine
import com.neon.book01.domain.StatementPostingLine
import com.neon.book01.domain.TrialBalanceCalculator
import com.neon.book01.domain.TrialBalanceReport
import com.neon.book01.domain.UsdMoney
import java.math.BigDecimal
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
    val currencyCode: String,
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
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var accountBeingEdited by remember { mutableStateOf<AccountEntity?>(null) }
    var showReversalDialog by rememberSaveable { mutableStateOf(false) }
    var showAuditHistory by rememberSaveable { mutableStateOf(false) }
    var showFinancialStatements by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<EntryWithLines>>(emptyList()) }
    var auditEvents by remember { mutableStateOf<List<AuditEventEntity>>(emptyList()) }
    var accountAuditEvents by remember { mutableStateOf<List<AccountAuditEventEntity>>(emptyList()) }
    var periodCloses by remember { mutableStateOf<List<PeriodCloseEntity>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var bookSettings by remember { mutableStateOf<BookSettingsEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        repository.initializeBook()
        launch { repository.observeEntries().collect { entries = it } }
        launch { repository.observeAuditEvents().collect { auditEvents = it } }
        launch { repository.observeAccountAuditEvents().collect { accountAuditEvents = it } }
        launch { repository.observePeriodCloses().collect { periodCloses = it } }
        launch { repository.observeAccounts().collect { accounts = it } }
        launch { repository.observeBookSettings().collect { bookSettings = it } }
    }

    val bookName = bookSettings?.bookName ?: "Libro de prueba"
    val currencyCode = bookSettings?.baseCurrency ?: UsdMoney.CURRENCY_CODE
    val operatingSummary = remember(accounts, entries, currencyCode) {
        OperatingSummaryCalculator.calculate(
            accounts = accounts.map { ReportAccount(it.code, it.name, it.type) },
            postings = entries.filter { it.entry.status == "POSTED" }
                .flatMap { entry ->
                    entry.lines.map { line ->
                        ReportPostingLine(
                            line.accountCode,
                            line.debitMinor,
                            line.creditMinor,
                            line.currencyCode,
                            entry.entry.entryDate,
                        )
                    }
                },
            currencyCode = currencyCode,
        )
    }
    val incomeMinor = operatingSummary.incomeMinor
    val expenseMinor = operatingSummary.expenseMinor
    val netMinor = UsdMoney.subtractMinorUnits(incomeMinor, expenseMinor)
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
            if (selectedTab == 0 || selectedTab == 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) showEntryDialog = true else showAccountDialog = true
                    },
                    containerColor = BookGreen,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(if (selectedTab == 0) "Nuevo asiento" else "Nueva cuenta", fontWeight = FontWeight.SemiBold) },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            BookHeader(bookName, currencyCode)
            Spacer(Modifier.height(16.dp))
            SummaryCards(
                netMinor = netMinor,
                incomeMinor = incomeMinor,
                expenseMinor = expenseMinor,
                currencyCode = currencyCode,
            )
            Spacer(Modifier.height(22.dp))

            if (selectedTab == 0) {
                JournalScreen(
                    rows = rowItems,
                    currencyCode = currencyCode,
                    onFilterClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Los filtros se incorporarán en la siguiente iteración.")
                        }
                    },
                )
            } else if (selectedTab == 1) {
                GeneralLedgerScreen(accounts, entries, currencyCode)
            } else if (selectedTab == 2) {
                AccountsScreen(accounts, accountAuditEvents, onEditAccount = { accountBeingEdited = it })
            } else if (selectedTab == 3) {
                TrialBalanceScreen(accounts, entries, currencyCode)
            } else if (selectedTab == 4) {
                if (showAuditHistory) {
                    AuditHistoryScreen(
                        events = auditEvents,
                        entries = entries,
                        onReverse = { showReversalDialog = true },
                        onBack = { showAuditHistory = false },
                    )
                } else if (showFinancialStatements) {
                    FinancialStatementsScreen(
                        accounts = accounts,
                        entries = entries,
                        periodCloses = periodCloses,
                        onClosePeriod = { start, end, equity -> withContext(Dispatchers.IO) { repository.closePeriod(start, end, equity) } },
                        onBack = { showFinancialStatements = false },
                    )
                } else {
                    BookSettingsScreen(
                        bookName = bookName,
                        currencyCode = currencyCode,
                        onSaveName = { newName ->
                            withContext(Dispatchers.IO) { repository.renameBook(newName) }
                            snackbarHostState.showSnackbar("Nombre del libro actualizado.")
                        },
                        onOpenAudit = { showAuditHistory = true },
                        onOpenStatements = { showFinancialStatements = true },
                    )
                }
            } else {
                ComingSoonScreen(tabs[selectedTab].title)
            }
            Spacer(Modifier.height(92.dp))
        }
    }

    if (showEntryDialog) {
        NewEntryDialog(
            accounts = accounts,
            closedPeriods = periodCloses.map { ClosedPeriodRange(it.startDate, it.endDate) },
            currencyCode = currencyCode,
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
                        currencyCode = draft.currencyCode,
                    )
                }
                showEntryDialog = false
                snackbarHostState.showSnackbar("Asiento guardado en este dispositivo.")
            },
        )
    }
    if (showAccountDialog) {
        NewAccountDialog(
            onDismiss = { showAccountDialog = false },
            onSave = { code, name, type, cashOrBank ->
                withContext(Dispatchers.IO) { repository.createAccount(code, name, type, cashOrBank) }
                showAccountDialog = false
                snackbarHostState.showSnackbar("Cuenta agregada al plan de cuentas.")
            },
        )
    }
    if (accountBeingEdited != null) {
        EditAccountDialog(
            account = accountBeingEdited!!,
            onDismiss = { accountBeingEdited = null },
            onSave = { name, active ->
                val account = accountBeingEdited!!
                withContext(Dispatchers.IO) { repository.updateAccountSettings(account.code, name, active) }
                accountBeingEdited = null
                snackbarHostState.showSnackbar("Cuenta actualizada; el código y la clasificación no cambian.")
            },
        )
    }
    if (showReversalDialog) {
        val reversedEntryIds = entries.mapNotNull { it.entry.reversalOfEntryId }.toSet()
        val eligibleEntries = entries.filter {
            it.entry.status == "POSTED" && it.entry.kind != "PERIOD_CLOSE" && it.entry.reversalOfEntryId == null && it.entry.id !in reversedEntryIds
        }
        ReversalDialog(
            entries = eligibleEntries,
            closedPeriods = periodCloses.map { ClosedPeriodRange(it.startDate, it.endDate) },
            onDismiss = { showReversalDialog = false },
            onSave = { entryId, reversalDate, reason ->
                withContext(Dispatchers.IO) { repository.reversePostedEntry(entryId, reversalDate, reason) }
                showReversalDialog = false
                snackbarHostState.showSnackbar("Reversa contabilizada; el asiento original permanece intacto.")
            },
        )
    }
}

@Composable
private fun BookHeader(bookName: String, currencyCode: String) {
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
                Text("$bookName · $currencyCode", color = BookMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SummaryCards(netMinor: Long, incomeMinor: Long, expenseMinor: Long, currencyCode: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCard("Saldo", money(netMinor, currencyCode), modifier = Modifier.weight(1.15f), emphasize = true)
        SummaryCard("Ingresos", money(incomeMinor, currencyCode), modifier = Modifier.weight(1f))
        SummaryCard("Gastos", money(expenseMinor, currencyCode), modifier = Modifier.weight(1f))
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
private fun JournalScreen(rows: List<JournalRow>, currencyCode: String, onFilterClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Diario", color = BookInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Movimientos del libro", color = BookMuted, fontSize = 12.sp)
        }
        Text("$currencyCode · LOCAL", color = BookGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    LedgerTable(rows, currencyCode)
}

@Composable
private fun LedgerTable(rows: List<JournalRow>, currencyCode: String) {
    val baseCurrencyRows = rows.filter { it.currencyCode == currencyCode }
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
                    TableCell(money(UsdMoney.sumMinorUnits(baseCurrencyRows.map { it.debitMinor }), currencyCode), 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                    TableCell(money(UsdMoney.sumMinorUnits(baseCurrencyRows.map { it.creditMinor }), currencyCode), 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
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
        if (rows.any { it.currencyCode != currencyCode }) {
            Text(
                "Los totales resaltados solo incluyen $currencyCode; las otras monedas se muestran aparte y no se convierten.",
                modifier = Modifier.fillMaxWidth(),
                color = BookMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
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
    closedPeriods: List<ClosedPeriodRange>,
    currencyCode: String,
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

    val selectableAccounts = accounts.map { AccountRules.SelectableAccount(it.code, it.type, it.active, it.cashOrBank) }
    val paymentCodes = AccountRules.activePaymentAccounts(selectableAccounts).map { it.code }.toSet()
    val paymentAccounts = accounts.filter { it.code in paymentCodes }
    val categoryType = if (kind == EntryKind.EXPENSE) "EXPENSE" else "INCOME"
    val categoryCodes = AccountRules.activeCategoryAccounts(selectableAccounts, categoryType).map { it.code }.toSet()
    val categoryAccounts = accounts.filter { it.code in categoryCodes }
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
                    label = { Text("Importe en $currencyCode") },
                    placeholder = { Text("Ej. 28,50 o 1.234,56") },
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
                Text("Se registrará en $currencyCode. El sistema genera las dos líneas y comprueba que cuadren; no convierte moneda.", color = BookMuted, fontSize = 11.sp)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && accounts.isNotEmpty(),
                onClick = {
                    val draft = parseDraft(kind, description, amountText, dateText, categoryCode, paymentCode, currencyCode)
                    if (draft == null) {
                        errorText = "Revisa concepto y fecha; para USD usa, por ejemplo, 1234.56, 1,234.56 o 1.234,56 (máximo 2 decimales)."
                    } else if (closedPeriods.any { draft.dateIso >= it.startDate && draft.dateIso <= it.endDate }) {
                        errorText = "La fecha elegida pertenece a un periodo cerrado. Selecciona una fecha abierta."
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
    currencyCode: String,
): EntryDraft? {
    if (description.isBlank() || categoryCode.isBlank() || paymentCode.isBlank()) return null
    val date = runCatching {
        LocalDate.parse(dateText.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrNull() ?: return null
    val amount = runCatching {
        UsdMoney.parseMinorUnits(amountText)
    }.getOrNull() ?: return null
    return EntryDraft(date.toString(), description.trim(), kind, categoryCode, paymentCode, amount, currencyCode)
}

private val accountTypeOptions = listOf(
    "ASSET" to "Activo",
    "LIABILITY" to "Pasivo",
    "EQUITY" to "Patrimonio",
    "INCOME" to "Ingreso",
    "EXPENSE" to "Gasto",
)

private fun accountTypeLabel(type: String): String =
    accountTypeOptions.firstOrNull { it.first == type }?.second ?: type

@Composable
private fun AccountsScreen(
    accounts: List<AccountEntity>,
    accountAuditEvents: List<AccountAuditEventEntity>,
    onEditAccount: (AccountEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Plan de cuentas", color = BookInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Código, clasificación y uso en el libro", color = BookMuted, fontSize = 12.sp)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row(modifier = Modifier.background(Color(0xFFF0F4F5)), verticalAlignment = Alignment.CenterVertically) {
                    TableCell("Código", 78.dp, isHeader = true)
                    TableCell("Nombre", 170.dp, isHeader = true)
                    TableCell("Tipo", 120.dp, isHeader = true)
                    TableCell("Estado", 88.dp, isHeader = true)
                    TableCell("Uso", 110.dp, isHeader = true)
                    TableCell("Acciones", 100.dp, isHeader = true)
                }
                accounts.forEach { account ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TableCell(account.code, 78.dp, color = BookMuted)
                        TableCell(account.name, 170.dp, fontWeight = FontWeight.Medium)
                        TableCell(accountTypeLabel(account.type), 120.dp, color = BookMuted)
                        TableCell(if (account.active) "Activa" else "Inactiva", 88.dp, color = if (account.active) BookGreen else BookMuted)
                        TableCell(if (account.cashOrBank) "Caja/Banco" else "General", 110.dp, color = BookMuted)
                        Box(Modifier.width(100.dp).height(45.dp), contentAlignment = Alignment.CenterStart) {
                            TextButton(onClick = { onEditAccount(account) }) { Text("Editar", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        Text(
            "Las cuentas inactivas siguen visibles en la historia. Código y tipo son inmutables; solo puedes cambiar el nombre y la disponibilidad para nuevos asientos.",
            color = BookMuted,
            fontSize = 11.sp,
        )
        if (accountAuditEvents.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cambios recientes", color = BookInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                accountAuditEvents.take(5).forEach { event ->
                    val changed = buildList {
                        if (event.oldName != event.newName) add("nombre: ${event.oldName} → ${event.newName}")
                        if (event.oldActive != event.newActive) add(if (event.newActive) "reactivada" else "desactivada")
                    }.joinToString(" · ")
                    Text("${event.occurredAt.take(10)} · ${event.accountCode} · $changed", color = BookMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: suspend (String, Boolean) -> Unit,
) {
    var name by rememberSaveable(account.code) { mutableStateOf(account.name) }
    var active by rememberSaveable(account.code) { mutableStateOf(account.active) }
    var errorText by rememberSaveable(account.code) { mutableStateOf<String?>(null) }
    var saving by rememberSaveable(account.code) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar cuenta ${account.code}", color = BookInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${account.code} · ${accountTypeLabel(account.type)} (clasificación fija)", color = BookMuted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it; errorText = null }, label = { Text("Nombre visible") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Activa para nuevos asientos", color = BookInk, fontSize = 12.sp)
                }
                Text("Al desactivarla seguirá apareciendo en el diario y los informes históricos.", color = BookMuted, fontSize = 11.sp)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                if (name.trim().length !in 2..80) {
                    errorText = "El nombre debe tener entre 2 y 80 caracteres."
                } else scope.launch {
                    saving = true
                    errorText = null
                    try { onSave(name, active) } catch (error: Exception) {
                        errorText = error.message ?: "No se pudo actualizar la cuenta."
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Guardando…" else "Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NewAccountDialog(
    onDismiss: () -> Unit,
    onSave: suspend (String, String, String, Boolean) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var cashOrBank by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva cuenta", color = BookInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { digit -> digit in '0'..'9' }.take(8); errorText = null },
                    label = { Text("Código (3–8 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Nombre de la cuenta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountTypePicker(type = type, onSelected = { type = it; if (it != "ASSET") cashOrBank = false })
                if (type == "ASSET") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cashOrBank, onCheckedChange = { cashOrBank = it })
                        Text("Usar como Caja/Banco para movimientos simples", color = BookInk, fontSize = 12.sp)
                    }
                }
                Text("Las cuentas nuevas se guardan en el dispositivo y aparecen en los informes.", color = BookMuted, fontSize = 11.sp)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (!code.matches(Regex("[0-9]{3,8}")) || name.trim().length !in 2..80) {
                        errorText = "Revisa el código (3–8 dígitos) y el nombre (2–80 caracteres)."
                    } else {
                        scope.launch {
                            saving = true
                            errorText = null
                            try {
                                onSave(code, name, type, cashOrBank)
                            } catch (error: Exception) {
                                errorText = error.message ?: "No se pudo guardar la cuenta."
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            ) { Text(if (saving) "Guardando…" else "Agregar cuenta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AccountTypePicker(type: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Tipo de cuenta", color = BookMuted, fontSize = 11.sp)
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
                    Text(accountTypeLabel(type), modifier = Modifier.weight(1f), color = BookInk, fontSize = 13.sp)
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = BookMuted)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accountTypeOptions.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(code); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun GeneralLedgerScreen(
    accounts: List<AccountEntity>,
    entries: List<EntryWithLines>,
    currencyCode: String,
) {
    var selectedAccountCode by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(accounts) {
        if (accounts.none { it.code == selectedAccountCode }) {
            selectedAccountCode = accounts.firstOrNull()?.code.orEmpty()
        }
    }
    val selectedAccount = accounts.firstOrNull { it.code == selectedAccountCode }
    val reportResult = remember(selectedAccountCode, entries, currencyCode, startDate, endDate) {
        if (selectedAccountCode.isBlank()) null else runCatching {
            GeneralLedgerCalculator.calculate(
                accountCode = selectedAccountCode,
                entries = entries,
                currencyCode = currencyCode,
                startDate = startDate,
                endDate = endDate,
            )
        }
    }
    val report = reportResult?.getOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Mayor", color = BookInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Movimientos y saldo corrido · ${dateRangeLabel(startDate, endDate)} · $currencyCode", color = BookMuted, fontSize = 12.sp)
        }
        if (accounts.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("Aún no hay cuentas en el libro.", modifier = Modifier.padding(16.dp), color = BookMuted)
            }
        } else {
            AccountPicker(
                label = "Cuenta",
                options = accounts,
                selectedCode = selectedAccountCode,
                onSelected = { selectedAccountCode = it },
            )
            selectedAccount?.let { account ->
                Text(
                    "${accountTypeLabel(account.type)}${if (account.active) "" else " · Inactiva"}",
                    color = BookMuted,
                    fontSize = 11.sp,
                )
            }
            DateRangeControls(startDate, endDate, onStartDateChange = { startDate = it }, onEndDateChange = { endDate = it })
            reportResult?.exceptionOrNull()?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(
                        error.message ?: "No se pudo calcular el Mayor.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }
            report?.let { data ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard("Total débitos", money(data.totalDebitMinor, currencyCode), modifier = Modifier.weight(1f))
                    SummaryCard("Total créditos", money(data.totalCreditMinor, currencyCode), modifier = Modifier.weight(1f))
                }
                SummaryCard(
                    "Saldo final (D/H)",
                    formatSignedLedgerBalance(data.closingBalanceMinor, currencyCode),
                    modifier = Modifier.fillMaxWidth(),
                    emphasize = true,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Row(modifier = Modifier.background(Color(0xFFF0F4F5)), verticalAlignment = Alignment.CenterVertically) {
                            TableCell("Fecha", 88.dp, isHeader = true)
                            TableCell("Asiento", 84.dp, isHeader = true)
                            TableCell("Concepto", 180.dp, isHeader = true)
                            TableCell("Debe", 116.dp, isHeader = true, align = TextAlign.End)
                            TableCell("Haber", 116.dp, isHeader = true, align = TextAlign.End)
                            TableCell("Saldo D/H", 132.dp, isHeader = true, align = TextAlign.End)
                        }
                        Row(modifier = Modifier.background(Color(0xFFE7F3EF)), verticalAlignment = Alignment.CenterVertically) {
                            TableCell("", 88.dp)
                            TableCell("", 84.dp)
                            TableCell("Saldo inicial", 180.dp, fontWeight = FontWeight.SemiBold)
                            TableCell("—", 116.dp, align = TextAlign.End)
                            TableCell("—", 116.dp, align = TextAlign.End)
                            TableCell(formatSignedLedgerBalance(data.openingBalanceMinor, currencyCode), 132.dp, align = TextAlign.End, fontWeight = FontWeight.SemiBold)
                        }
                        if (data.rows.isEmpty()) {
                            Text(
                                "No hay movimientos $currencyCode en esta cuenta.",
                                modifier = Modifier.padding(16.dp),
                                color = BookMuted,
                                fontSize = 12.sp,
                            )
                        } else {
                            data.rows.forEachIndexed { index, row ->
                                Row(
                                    modifier = Modifier.background(if (index % 2 == 0) Color.White else Color(0xFFFAFCFC)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TableCell(formatLedgerDate(row.entryDate), 88.dp, color = BookMuted)
                                    TableCell(row.entryId.take(8), 84.dp, color = BookMuted)
                                    TableCell(row.description, 180.dp)
                                    TableCell(if (row.debitMinor == 0L) "—" else money(row.debitMinor, currencyCode), 116.dp, align = TextAlign.End)
                                    TableCell(if (row.creditMinor == 0L) "—" else money(row.creditMinor, currencyCode), 116.dp, align = TextAlign.End)
                                    TableCell(formatSignedLedgerBalance(row.runningBalanceMinor, currencyCode), 132.dp, align = TextAlign.End, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Row(modifier = Modifier.background(Color(0xFFF0F4F5)), verticalAlignment = Alignment.CenterVertically) {
                            TableCell("Totales", 88.dp, isHeader = true)
                            TableCell("", 84.dp, isHeader = true)
                            TableCell("", 180.dp, isHeader = true)
                            TableCell(money(data.totalDebitMinor, currencyCode), 116.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                            TableCell(money(data.totalCreditMinor, currencyCode), 116.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                            TableCell(formatSignedLedgerBalance(data.closingBalanceMinor, currencyCode), 132.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                        }
                    }
                }
                if (data.excludedOtherCurrencyLineCount > 0) {
                    Text(
                        "Se excluyeron ${data.excludedOtherCurrencyLineCount} línea(s) en ${data.excludedCurrencyCodes.joinToString()}; no se convierten ni se mezclan con $currencyCode.",
                        color = BookMuted,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    "Desliza la tabla para ver todas las columnas. El saldo inicial incluye los movimientos anteriores al rango; los asientos no se modifican.",
                    color = BookMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DateRangeControls(
    startDate: String,
    endDate: String,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startDate,
                onValueChange = onStartDateChange,
                label = { Text("Desde") },
                placeholder = { Text("AAAA-MM-DD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = onEndDateChange,
                label = { Text("Hasta") },
                placeholder = { Text("AAAA-MM-DD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
        }
        Text("Fechas inclusivas; deja vacío un extremo sin límite. Usa AAAA-MM-DD.", color = BookMuted, fontSize = 11.sp)
    }
}

private fun dateRangeLabel(startDate: String, endDate: String): String {
    val start = startDate.trim().ifEmpty { null }
    val end = endDate.trim().ifEmpty { null }
    return when {
        start == null && end == null -> "Completo"
        start == null -> "Hasta $end"
        end == null -> "Desde $start"
        else -> "$start a $end"
    }
}

private fun formatSignedLedgerBalance(amountMinor: Long, currencyCode: String): String =
    if (amountMinor < 0) "${money(Math.negateExact(amountMinor), currencyCode)} H" else "${money(amountMinor, currencyCode)} D"

private fun formatLedgerDate(isoDate: String): String = runCatching {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.forLanguageTag("es-VE")))
}.getOrDefault(isoDate)

@Composable
private fun TrialBalanceScreen(
    accounts: List<AccountEntity>,
    entries: List<EntryWithLines>,
    currencyCode: String,
) {
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    val reportResult = remember(accounts, entries, currencyCode, startDate, endDate) {
        runCatching {
            TrialBalanceCalculator.calculate(
                accounts = accounts.map { ReportAccount(it.code, it.name, it.type) },
                postings = entries
                    .filter { it.entry.status == "POSTED" }
                    .flatMap { entry ->
                        entry.lines.map { line ->
                            ReportPostingLine(
                                line.accountCode,
                                line.debitMinor,
                                line.creditMinor,
                                line.currencyCode,
                                entry.entry.entryDate,
                            )
                        }
                    },
                currencyCode = currencyCode,
                startDate = startDate,
                endDate = endDate,
            )
        }
    }
    val report = reportResult.getOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Balance de comprobación", color = BookInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Periodo: ${dateRangeLabel(startDate, endDate)} · $currencyCode", color = BookMuted, fontSize = 12.sp)
            }
            Text(
                if (report?.isBalanced == true) "CUADRA" else if (report == null) "REVISAR" else "DIFERENCIA",
                color = if (report?.isBalanced == true) BookGreen else MaterialTheme.colorScheme.error,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        DateRangeControls(startDate, endDate, onStartDateChange = { startDate = it }, onEndDateChange = { endDate = it })

        reportResult.exceptionOrNull()?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text(
                    error.message ?: "No se pudo calcular el balance.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }

        report?.let { data ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("Total débitos", money(data.totalDebitMinor, currencyCode), modifier = Modifier.weight(1f))
                SummaryCard("Total créditos", money(data.totalCreditMinor, currencyCode), modifier = Modifier.weight(1f))
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.background(Color(0xFFF0F4F5)), verticalAlignment = Alignment.CenterVertically) {
                        TableCell("Código", 68.dp, isHeader = true)
                        TableCell("Cuenta", 145.dp, isHeader = true)
                        TableCell("Débitos", 104.dp, isHeader = true, align = TextAlign.End)
                        TableCell("Créditos", 104.dp, isHeader = true, align = TextAlign.End)
                        TableCell("Saldo", 110.dp, isHeader = true, align = TextAlign.End)
                        TableCell("Naturaleza", 85.dp, isHeader = true, align = TextAlign.Center)
                    }
                    data.lines.forEach { line ->
                        val debitNature = line.accountType == "ASSET" || line.accountType == "EXPENSE"
                        val side = when {
                            line.signedBalanceMinor == 0L -> "—"
                            debitNature && line.signedBalanceMinor > 0 -> "D"
                            !debitNature && line.signedBalanceMinor > 0 -> "H"
                            debitNature -> "H"
                            else -> "D"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TableCell(line.accountCode, 68.dp, color = BookMuted)
                            TableCell(line.accountName, 145.dp, fontWeight = FontWeight.Medium)
                            TableCell(money(line.totalDebitMinor, currencyCode), 104.dp, align = TextAlign.End)
                            TableCell(money(line.totalCreditMinor, currencyCode), 104.dp, align = TextAlign.End)
                            TableCell(money(line.signedBalanceMinor, currencyCode), 110.dp, align = TextAlign.End)
                            TableCell(side, 85.dp, align = TextAlign.Center, color = BookGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Text(
                "D = saldo deudor · H = saldo acreedor. Los importes están expresados en USD; no se convierten otras monedas.",
                color = BookMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun FinancialStatementsScreen(
    accounts: List<AccountEntity>,
    entries: List<EntryWithLines>,
    periodCloses: List<PeriodCloseEntity>,
    onClosePeriod: suspend (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var asOfDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val reportAccounts = remember(accounts) { accounts.map { ReportAccount(it.code, it.name, it.type) } }
    val statementPostings = remember(entries) {
        entries.flatMap { entry ->
            entry.lines.map { line ->
                StatementPostingLine(
                    accountCode = line.accountCode,
                    debitMinor = line.debitMinor,
                    creditMinor = line.creditMinor,
                    currencyCode = line.currencyCode,
                    entryDate = entry.entry.entryDate,
                    entryStatus = entry.entry.status,
                    entryKind = entry.entry.kind,
                )
            }
        }
    }
    val closedRanges = remember(periodCloses) { periodCloses.map { ClosedPeriodRange(it.startDate, it.endDate) } }
    val incomeResult = remember(reportAccounts, statementPostings, startDate, endDate) {
        runCatching {
            FinancialStatementsCalculator.incomeStatement(reportAccounts, statementPostings, startDate, endDate)
        }
    }
    val balanceResult = remember(reportAccounts, statementPostings, asOfDate, closedRanges) {
        runCatching { FinancialStatementsCalculator.balanceSheet(reportAccounts, statementPostings, asOfDate, closedRanges) }
    }
    val income = incomeResult.getOrNull()
    val balance = balanceResult.getOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Estados financieros", color = BookInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Informes informativos en USD · no sustituyen revisión profesional", color = BookMuted, fontSize = 11.sp)
            }
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado de resultados", color = BookInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Ingresos y gastos contabilizados dentro del periodo inclusivo.", color = BookMuted, fontSize = 12.sp)
                DateRangeControls(startDate, endDate, onStartDateChange = { startDate = it }, onEndDateChange = { endDate = it })
                incomeResult.exceptionOrNull()?.let { error ->
                    Text(error.message ?: "No se pudo calcular el estado de resultados.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                income?.let { data -> IncomeStatementContent(data) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Balance general", color = BookInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Saldos acumulados hasta la fecha inclusive.", color = BookMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = asOfDate,
                    onValueChange = { asOfDate = it },
                    label = { Text("Al día (AAAA-MM-DD)") },
                    placeholder = { Text("AAAA-MM-DD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                balanceResult.exceptionOrNull()?.let { error ->
                    Text(error.message ?: "No se pudo calcular el balance general.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                balance?.let { data -> BalanceSheetContent(data) }
            }
        }
        PeriodCloseSection(accounts, entries, periodCloses, onClosePeriod)
        Text(
            "Las líneas de reversa afectan los informes en su fecha de contabilización. Se incluyen únicamente asientos contabilizados y líneas USD; no se convierten monedas ni se reclasifican cuentas. En el balance general, los periodos cerrados se transfieren a patrimonio y solo queda el resultado de periodos no cerrados.",
            color = BookMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PeriodCloseSection(
    accounts: List<AccountEntity>,
    entries: List<EntryWithLines>,
    closes: List<PeriodCloseEntity>,
    onClose: suspend (String, String, String) -> Unit,
) {
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var retainedCode by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val equityAccounts = accounts.filter { it.type == "EQUITY" && it.active }
    LaunchedEffect(equityAccounts) {
        if (equityAccounts.none { it.code == retainedCode }) {
            retainedCode = equityAccounts.firstOrNull { it.code == "3100" }?.code
                ?: equityAccounts.firstOrNull()?.code.orEmpty()
        }
    }
    val normalized = runCatching {
        val s = LocalDate.parse(start.trim()).toString()
        val e = LocalDate.parse(end.trim()).toString()
        require(s <= e)
        s to e
    }.getOrNull()
    val endAfterToday = normalized?.second?.let { it > LocalDate.now().toString() } == true
    val overlap = normalized?.let { pair -> closes.any { it.startDate <= pair.second && it.endDate >= pair.first } } == true
    val retained = equityAccounts.firstOrNull { it.code == retainedCode }
    val closePostings = remember(entries) {
        entries.map { entry ->
            PeriodClosePosting(
                entryDate = entry.entry.entryDate,
                status = entry.entry.status,
                currencyCode = entry.entry.currencyCode,
                kind = entry.entry.kind,
                lines = entry.lines.map { line ->
                    PeriodClosePostingLine(line.accountCode, line.debitMinor, line.creditMinor, line.currencyCode)
                },
            )
        }
    }
    val previewResult = remember(accounts, closePostings, normalized, retainedCode) {
        if (normalized == null || retained == null) null else runCatching {
            PeriodCloseCalculator.calculate(
                accounts = accounts.map { PeriodCloseAccount(it.code, it.type, it.active) },
                postings = closePostings,
                startDate = normalized.first,
                endDate = normalized.second,
                retainedEarningsAccountCode = retained.code,
            )
        }
    }
    val preview = previewResult?.getOrNull()

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Cierre de periodo", color = BookInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Cierra fechas inclusivas transfiriendo ingresos y gastos netos a patrimonio. Si no hay líneas P&L, guarda solo los metadatos, sin inventar importes. El cierre es irreversible en este prototipo: no se puede reabrir ni reversar desde la reversa ordinaria.", color = BookMuted, fontSize = 12.sp)
            OutlinedTextField(start, { start = it; error = null }, label = { Text("Desde (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it; error = null }, label = { Text("Hasta inclusivo (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AccountPicker("Ganancias retenidas · patrimonio", equityAccounts, retainedCode) { retainedCode = it }
            when {
                start.isNotBlank() && end.isNotBlank() && normalized == null -> Text("Indica fechas ISO válidas y un intervalo en orden.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                endAfterToday -> Text("No se puede cerrar un periodo que todavía no ha terminado.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                overlap -> Text("El intervalo se superpone con un periodo ya cerrado.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                retained == null -> Text("Crea o activa una cuenta de patrimonio para utilidades retenidas.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                previewResult?.isFailure == true -> Text(previewResult.exceptionOrNull()?.message ?: "No se puede cerrar este intervalo.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                preview != null -> Text("Resultado neto a transferir: ${money(preview.netResultMinor)} · líneas de cierre: ${preview.lines.size}", color = BookInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            TextButton(enabled = normalized != null && !endAfterToday && !overlap && retained != null && preview != null && !saving, onClick = { showConfirm = true }) {
                Text(if (saving) "Contabilizando…" else "Revisar y cerrar periodo")
            }
            Text("Periodos cerrados (no reabribles):", color = BookInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (closes.isEmpty()) Text("Ningún periodo cerrado.", color = BookMuted, fontSize = 12.sp)
            closes.forEach { close ->
                Text("${close.startDate} a ${close.endDate} · ${money(close.netResultMinor)} · patrimonio ${close.retainedEarningsAccountCode}" + (if (close.closingEntryId == null) " · solo metadatos" else " · asiento ${close.closingEntryId.take(8)}"), color = BookMuted, fontSize = 11.sp)
            }
        }
    }
    if (showConfirm && normalized != null && retained != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) showConfirm = false },
            title = { Text("Confirmar cierre") },
            text = { Text("Se cerrará el periodo inclusivo ${normalized.first} a ${normalized.second}. Resultado neto ${money(preview?.netResultMinor ?: 0L)}; patrimonio ${retained.code} · ${retained.name}. ${if (preview?.lines.isNullOrEmpty()) "No hay saldos P&L que transferir: solo se guardará el cierre, sin asiento ficticio." else "Se registrará un asiento equilibrado con ${preview?.lines?.size ?: 0} líneas."} Esta acción es permanente e irreversible en este prototipo.") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        try {
                            onClose(normalized.first, normalized.second, retained.code)
                            showConfirm = false
                        } catch (failure: Exception) {
                            error = failure.message ?: "No se pudo cerrar el periodo."
                        } finally { saving = false }
                    }
                }) { Text(if (saving) "Cerrando…" else "Contabilizar cierre") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { showConfirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun IncomeStatementContent(report: IncomeStatementReport) {
    if (report.incomeLines.none { it.balanceMinor != 0L } && report.expenseLines.none { it.balanceMinor != 0L }) {
        Text("No hay movimientos USD contabilizados en este periodo.", color = BookMuted, fontSize = 12.sp)
    }
    StatementLines("Ingresos", report.incomeLines)
    StatementLines("Gastos", report.expenseLines)
    StatementValueRow("Total ingresos", report.totalIncomeMinor, strong = true)
    StatementValueRow("Total gastos", report.totalExpensesMinor, strong = true)
    StatementValueRow("Utilidad (pérdida) neta", report.netIncomeMinor, strong = true)
    StatementExclusions(report.excludedDraftLineCount, report.excludedOtherCurrencyLineCount)
}

@Composable
private fun BalanceSheetContent(report: BalanceSheetReport) {
    val hasActivity = report.assetLines.any { it.balanceMinor != 0L } ||
        report.liabilityLines.any { it.balanceMinor != 0L } ||
        report.equityLines.any { it.balanceMinor != 0L } || report.accumulatedResultMinor != 0L
    if (!hasActivity) Text("No hay movimientos USD contabilizados hasta esta fecha.", color = BookMuted, fontSize = 12.sp)
    StatementLines("Activos", report.assetLines)
    StatementLines("Pasivos", report.liabilityLines)
    StatementLines("Patrimonio (cuentas clasificadas como patrimonio)", report.equityLines)
    StatementValueRow("Total activos", report.totalAssetsMinor, strong = true)
    StatementValueRow("Total pasivos", report.totalLiabilitiesMinor, strong = true)
    StatementValueRow("Patrimonio", report.totalEquityMinor, strong = true)
    StatementValueRow("Resultado acumulado / actual sin cierre", report.accumulatedResultMinor, strong = true)
    StatementValueRow("Pasivos + patrimonio + resultado", Math.addExact(Math.addExact(report.totalLiabilitiesMinor, report.totalEquityMinor), report.accumulatedResultMinor), strong = true)
    StatementValueRow("Diferencia de conciliación", report.reconciliationDifferenceMinor, strong = true)
    Text(
        if (report.isReconciled) "Conciliado: activos = pasivos + patrimonio + resultado acumulado."
        else "No conciliado: revisa integridad y clasificación de los asientos; no se ajustaron saldos automáticamente.",
        color = if (report.isReconciled) BookGreen else MaterialTheme.colorScheme.error,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    StatementExclusions(report.excludedDraftLineCount, report.excludedOtherCurrencyLineCount)
}

@Composable
private fun StatementLines(title: String, lines: List<com.neon.book01.domain.StatementAccountBalance>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = BookInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        lines.filter { it.balanceMinor != 0L }.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${line.accountCode} · ${line.accountName}", color = BookMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(money(line.balanceMinor), color = BookInk, fontSize = 12.sp)
            }
        }
        if (lines.none { it.balanceMinor != 0L }) Text("—", color = BookMuted, fontSize = 12.sp)
    }
}

@Composable
private fun StatementValueRow(label: String, amount: Long, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BookInk, fontSize = 12.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(money(amount), color = BookInk, fontSize = 12.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun StatementExclusions(drafts: Int, otherCurrency: Int) {
    if (drafts > 0 || otherCurrency > 0) {
        Text("Excluidos: $drafts líneas de borrador · $otherCurrency líneas en otra moneda (sin conversión).", color = BookMuted, fontSize = 10.sp)
    }
}

@Composable
private fun AuditHistoryScreen(
    events: List<AuditEventEntity>,
    entries: List<EntryWithLines>,
    onReverse: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Trazabilidad", color = BookInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Historial de contabilizaciones y reversas", color = BookMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Corrección segura", color = BookInk, fontWeight = FontWeight.SemiBold)
                Text("Un asiento contabilizado no se edita ni se elimina. La corrección crea una reversa con líneas opuestas y conserva ambos registros.", color = BookMuted, fontSize = 12.sp)
                TextButton(onClick = onReverse) { Text("Reversar asiento contabilizado") }
            }
        }
        Text("Historial de auditoría", color = BookInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) {
            Text("Aún no hay eventos de auditoría.", color = BookMuted, fontSize = 13.sp)
        } else {
            events.forEach { event ->
                val entry = entries.firstOrNull { it.entry.id == event.entryId }?.entry
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (event.eventType) {
                                "POSTED" -> "Asiento contabilizado"
                                "REVERSAL_POSTED" -> "Reversa contabilizada"
                                "REVERSED" -> "Asiento reversado"
                                "PERIOD_CLOSED" -> "Cierre de periodo contabilizado"
                                else -> event.eventType
                            },
                            color = BookInk,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Fecha efectiva: ${event.eventDate} · ID: ${event.entryId.take(8)}", color = BookMuted, fontSize = 11.sp)
                        Text("Registrado: ${event.occurredAt}", color = BookMuted, fontSize = 10.sp)
                        if (entry != null) Text(entry.description, color = BookInk, fontSize = 12.sp)
                        event.relatedEntryId?.let { Text("Relacionado con: ${it.take(8)}", color = BookMuted, fontSize = 11.sp) }
                        if (event.reason.isNotBlank()) Text("Motivo: ${event.reason}", color = BookMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text("Los eventos nuevos se guardan en una tabla append-only local. Los datos anteriores a la actualización se conservan, pero no tienen eventos retroactivos.", color = BookMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReversalDialog(
    entries: List<EntryWithLines>,
    closedPeriods: List<ClosedPeriodRange>,
    onDismiss: () -> Unit,
    onSave: suspend (String, String, String) -> Unit,
) {
    var selectedEntryId by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var reversalDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = entries.firstOrNull { it.entry.id == selectedEntryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear reversa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entries.isEmpty()) {
                    Text("No hay asientos contabilizados elegibles. Las reversas existentes no se pueden volver a reversar.", color = BookMuted, fontSize = 13.sp)
                } else {
                    Box {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = BookCanvas,
                            onClick = { menuExpanded = true },
                        ) {
                            Text(
                                selected?.let { "${it.entry.entryDate} · ${it.entry.description}" } ?: "Elegir asiento original",
                                modifier = Modifier.padding(12.dp),
                                color = BookInk,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.entry.entryDate} · ${item.entry.description}", maxLines = 2) },
                                    onClick = { selectedEntryId = item.entry.id; menuExpanded = false; errorMessage = null },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reversalDate,
                        onValueChange = { reversalDate = it; errorMessage = null },
                        label = { Text("Fecha de reversa (AAAA-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; errorMessage = null },
                        label = { Text("Motivo obligatorio") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = entries.isNotEmpty() && selected != null && !saving,
                onClick = {
                    val validDate = runCatching { LocalDate.parse(reversalDate.trim()).toString() }.getOrNull()
                    if (validDate == null) {
                        errorMessage = "Indica una fecha válida en formato AAAA-MM-DD."
                    } else if (closedPeriods.any { validDate >= it.startDate && validDate <= it.endDate }) {
                        errorMessage = "La fecha de reversa debe pertenecer a un periodo abierto."
                    } else scope.launch {
                        saving = true
                        try {
                            onSave(selectedEntryId, validDate, reason)
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "No se pudo crear la reversa."
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Guardando…" else "Contabilizar reversa") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
private fun BookSettingsScreen(
    bookName: String,
    currencyCode: String,
    onSaveName: suspend (String) -> Unit,
    onOpenAudit: () -> Unit,
    onOpenStatements: () -> Unit,
) {
    var nameInput by rememberSaveable(bookName) { mutableStateOf(bookName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configuración del libro", color = BookInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nombre", color = BookMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; errorMessage = null },
                    label = { Text("Nombre del libro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                TextButton(
                    enabled = !saving && nameInput.trim() != bookName,
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                onSaveName(nameInput)
                                errorMessage = null
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "No se pudo guardar el nombre."
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text(if (saving) "Guardando…" else "Guardar nombre") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Moneda funcional", color = BookMuted, fontSize = 12.sp)
                Text("$currencyCode · Dólar estadounidense", color = BookInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Esta versión trabaja únicamente en USD. Cada importe se guarda como centavos enteros; no convierte ni mezcla monedas.",
                    color = BookMuted,
                    fontSize = 12.sp,
                )
                Text(
                    "Para evitar confusiones, 1,234.56 y 1.234,56 se aceptan. Un número como 1.234 se considera ambiguo y se pedirá escribirlo de otra forma.",
                    color = BookMuted,
                    fontSize = 12.sp,
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Estados financieros", color = BookInk, fontWeight = FontWeight.SemiBold)
                Text("Consulta el estado de resultados por periodo y el balance general a una fecha.", color = BookMuted, fontSize = 12.sp)
                TextButton(onClick = onOpenStatements) { Text("Abrir estados financieros") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Trazabilidad", color = BookInk, fontWeight = FontWeight.SemiBold)
                Text("Consulta contabilizaciones y reversas; los asientos contabilizados se corrigen sin editar el original.", color = BookMuted, fontSize = 12.sp)
                TextButton(onClick = onOpenAudit) { Text("Abrir historial de auditoría") }
            }
        }
    }
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
        val accountName = line.accountNameSnapshot.ifBlank { accounts.firstOrNull { it.code == line.accountCode }?.name ?: line.accountCode }
        JournalRow(
            date = dateLabel,
            concept = entry.description,
            account = accountName,
            debit = if (line.debitMinor == 0L) "—" else money(line.debitMinor, line.currencyCode),
            credit = if (line.creditMinor == 0L) "—" else money(line.creditMinor, line.currencyCode),
            currencyCode = line.currencyCode,
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
    val currencyCode: String,
    val debitMinor: Long = 0,
    val creditMinor: Long = 0,
)

private fun money(amountMinor: Long, currencyCode: String = UsdMoney.CURRENCY_CODE): String {
    if (currencyCode == UsdMoney.CURRENCY_CODE) return UsdMoney.format(amountMinor)
    val legacyAmount = BigDecimal.valueOf(amountMinor, 2).toPlainString().replace('.', ',')
    return "$currencyCode $legacyAmount"
}

package com.neon.book01

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BookGreen = Color(0xFF147D64)
private val BookGreenSoft = Color(0xFFE7F3EF)
private val BookInk = Color(0xFF172B4D)
private val BookMuted = Color(0xFF6B778C)
private val BookGrid = Color(0xFFE4E9ED)
private val BookCanvas = Color(0xFFF6F8F9)

private data class BookTab(val title: String, val icon: ImageVector)

private data class JournalRow(
    val date: String,
    val concept: String,
    val account: String,
    val debit: String,
    val credit: String,
)

private val tabs = listOf(
    BookTab("Diario", Icons.Outlined.MenuBook),
    BookTab("Mayor", Icons.Outlined.AccountBalance),
    BookTab("Cuentas", Icons.Outlined.ReceiptLong),
    BookTab("Balance", Icons.Outlined.BarChart),
    BookTab("Más", Icons.Outlined.MoreHoriz),
)

private val sampleRows = listOf(
    JournalRow("12 oct", "Papelería", "Gastos oficina", "$ 28,00", "—"),
    JournalRow("11 oct", "Pago cliente", "Banco", "—", "$ 150,00"),
    JournalRow("10 oct", "Internet", "Servicios", "$ 45,00", "—"),
    JournalRow("09 oct", "Venta mostrador", "Ingresos", "—", "$ 320,00"),
    JournalRow("08 oct", "Transporte", "Movilidad", "$ 18,50", "—"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                BookHomeScreen()
            }
        }
    }
}

@Composable
private fun BookHomeScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("El formulario de asiento se incorporará en el siguiente paso.")
                    }
                },
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
            SummaryCards()
            Spacer(Modifier.height(22.dp))

            if (selectedTab == 0) {
                JournalScreen(
                    onFilterClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Filtros de Diario: próximos a implementar.")
                        }
                    },
                )
            } else {
                ComingSoonScreen(tabs[selectedTab].title)
            }
            Spacer(Modifier.height(92.dp))
        }
    }
}

@Composable
private fun BookHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(13.dp),
            color = BookGreenSoft,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = BookGreen,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text("Book-01", color = BookInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Libro de ejemplo", color = BookMuted, fontSize = 12.sp)
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Cambiar libro",
                    tint = BookMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        IconButton(onClick = { }) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = "Notificaciones",
                tint = BookInk,
            )
        }
        Surface(shape = RoundedCornerShape(50), color = BookGreenSoft) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("C", color = BookGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SummaryCards() {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCard(
            title = "Saldo",
            amount = "$ 8.450,00",
            modifier = Modifier.weight(1.15f),
            emphasize = true,
        )
        SummaryCard(title = "Ingresos", amount = "$ 4.200,00", modifier = Modifier.weight(1f))
        SummaryCard(title = "Gastos", amount = "$ 1.750,00", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = if (emphasize) BookGreen else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 13.dp)) {
            Text(
                title,
                color = if (emphasize) Color.White.copy(alpha = 0.82f) else BookMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
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
private fun JournalScreen(onFilterClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Diario", color = BookInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Movimientos del libro", color = BookMuted, fontSize = 12.sp)
        }
        Text("USD · DEMO", color = BookGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            onClick = { },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Octubre 2026", color = BookInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Elegir periodo",
                    tint = BookMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            onClick = onFilterClick,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, tint = BookGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Filtrar", color = BookGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LedgerTable()
}

@Composable
private fun LedgerTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.background(Color(0xFFF0F4F5)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TableCell("#", 34.dp, isHeader = true, align = TextAlign.Center)
                TableCell("Fecha", 66.dp, isHeader = true)
                TableCell("Concepto", 132.dp, isHeader = true)
                TableCell("Cuenta", 126.dp, isHeader = true)
                TableCell("Débito", 92.dp, isHeader = true, align = TextAlign.End)
                TableCell("Crédito", 92.dp, isHeader = true, align = TextAlign.End)
            }
            sampleRows.forEachIndexed { index, row ->
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
            Row(
                modifier = Modifier.background(BookGreenSoft),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TableCell("", 34.dp, isHeader = true)
                TableCell("Total", 66.dp, isHeader = true)
                TableCell("", 132.dp, isHeader = true)
                TableCell("", 126.dp, isHeader = true)
                TableCell("$ 91,50", 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
                TableCell("$ 470,00", 92.dp, isHeader = true, align = TextAlign.End, color = BookGreen)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Desliza la tabla hacia los lados para ver más columnas",
        modifier = Modifier.fillMaxWidth(),
        color = BookMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    align: TextAlign = TextAlign.Start,
    color: Color = BookInk,
    fontWeight: FontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(45.dp)
            .background(Color.Transparent)
            .padding(horizontal = 7.dp, vertical = 5.dp),
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

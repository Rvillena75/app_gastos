package com.villegol.appgastos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.villegol.appgastos.data.AppContainer
import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.PaymentCard
import com.villegol.appgastos.data.PaymentCardType
import com.villegol.appgastos.data.formatMoney
import com.villegol.appgastos.domain.SpendingAnalysis
import com.villegol.appgastos.domain.SpendingAnalyzer
import com.villegol.appgastos.domain.SpendingBreakdown
import com.villegol.appgastos.domain.WeeklySpend
import com.villegol.appgastos.export.ExpenseXlsxExporter
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private val analysisPalette = listOf(
    Color(0xFF8CE8C8),
    Color(0xFF6FA8FF),
    Color(0xFFB58CFF),
    Color(0xFFFFC96B),
    Color(0xFFFF8D80),
    Color(0xFF5BD3D5)
)

private data class ExportSnapshot(
    val expenses: List<Expense>,
    val cards: List<PaymentCard>,
    val analysis: SpendingAnalysis,
    val budgetMinor: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val expenses by container.expenseRepository.observeActive().collectAsState(emptyList())
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val budgetFlow = remember(selectedMonth) {
        container.budgetRepository.observeMonthlyBudget(selectedMonth)
    }
    val budgetMinor by budgetFlow.collectAsState(initial = null)
    val analysis = remember(expenses, cards, selectedMonth) {
        SpendingAnalyzer().analyze(expenses, cards, selectedMonth)
    }
    val cardsByLast4 = remember(cards) { cards.associateBy { it.last4 } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showBudgetEditor by remember { mutableStateOf(false) }
    var budgetInput by remember(selectedMonth, budgetMinor) { mutableStateOf(budgetMinor?.toString().orEmpty()) }
    var budgetError by remember { mutableStateOf<String?>(null) }
    val latestExportSnapshot by rememberUpdatedState(
        ExportSnapshot(expenses, cards, analysis, budgetMinor)
    )
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        if (uri != null) {
            val snapshot = latestExportSnapshot
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Android no pudo abrir el archivo seleccionado.")
                        ExpenseXlsxExporter().write(
                            output = output,
                            expenses = snapshot.expenses,
                            cards = snapshot.cards,
                            analysis = snapshot.analysis,
                            monthlyBudgetMinor = snapshot.budgetMinor
                        )
                    }
                }
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "Archivo Excel exportado correctamente." },
                        onFailure = { it.message ?: "No se pudo exportar el archivo." }
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Análisis") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MonthSelector(
                    month = selectedMonth,
                    canGoForward = selectedMonth < YearMonth.now(),
                    onPrevious = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNext = { if (selectedMonth < YearMonth.now()) selectedMonth = selectedMonth.plusMonths(1) }
                )
            }
            item {
                BudgetCard(
                    analysis = analysis,
                    budgetMinor = budgetMinor,
                    onEdit = {
                        budgetInput = budgetMinor?.toString().orEmpty()
                        budgetError = null
                        showBudgetEditor = true
                    }
                )
            }
            item { MetricCards(analysis) }
            item {
                WeeklyChartCard(analysis.weeklySpend)
            }
            item {
                CategoryChartCard(
                    breakdown = analysis.categoryBreakdown,
                    totalMinor = analysis.totalMinor
                )
            }
            item {
                CardBreakdownCard(
                    breakdown = analysis.cardBreakdown,
                    cardsByLast4 = cardsByLast4,
                    totalMinor = analysis.totalMinor
                )
            }
            if (analysis.uncountedMovementCount > 0) {
                item {
                    Text(
                        "${analysis.uncountedMovementCount} movimiento(s) sin monto CLP no se incluyen en los gráficos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                ExportCard(
                    month = selectedMonth,
                    onExport = { exportLauncher.launch("gastos-${selectedMonth}.xlsx") }
                )
            }
        }
    }

    if (showBudgetEditor) {
        AlertDialog(
            onDismissRequest = { showBudgetEditor = false },
            title = { Text("Presupuesto de ${selectedMonth.monthLabel()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = {
                            budgetInput = it.filter(Char::isDigit).take(12)
                            budgetError = null
                        },
                        label = { Text("Monto mensual en CLP") },
                        prefix = { Text("$") },
                        supportingText = { Text("Solo informa y alerta; nunca bloquea un gasto.") },
                        singleLine = true
                    )
                    budgetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = budgetInput.toLongOrNull()
                    if (value == null || value <= 0L) {
                        budgetError = "Ingresa un monto mayor que cero."
                    } else {
                        scope.launch {
                            container.budgetRepository.setMonthlyBudget(selectedMonth, value)
                            showBudgetEditor = false
                            snackbarHostState.showSnackbar("Presupuesto guardado para ${selectedMonth.monthLabel()}.")
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (budgetMinor != null) {
                        TextButton(onClick = {
                            scope.launch {
                                container.budgetRepository.setMonthlyBudget(selectedMonth, null)
                                showBudgetEditor = false
                                snackbarHostState.showSnackbar("Presupuesto eliminado.")
                            }
                        }) { Text("Quitar") }
                    }
                    TextButton(onClick = { showBudgetEditor = false }) { Text("Cancelar") }
                }
            }
        )
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
            }
            Text(
                month.monthLabel(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
            }
        }
    }
}

@Composable
private fun BudgetCard(
    analysis: SpendingAnalysis,
    budgetMinor: Long?,
    onEdit: () -> Unit
) {
    val spent = analysis.totalMinor
    val progress = if (budgetMinor == null || budgetMinor <= 0L) 0f else spent.toFloat() / budgetMinor.toFloat()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Presupuesto mensual", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (budgetMinor == null) "Aún sin definir" else formatMoney(budgetMinor),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (budgetMinor == null) "Definir" else "Editar")
                }
            }
            if (budgetMinor == null) {
                Text(
                    "Define un límite de referencia para seguir el mes sin bloquear ninguna compra.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = if (progress > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Gastado", style = MaterialTheme.typography.labelMedium)
                        Text(formatMoney(spent), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val remaining = budgetMinor - spent
                        Text(if (remaining >= 0) "Disponible" else "Exceso", style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatMoney(kotlin.math.abs(remaining)),
                            fontWeight = FontWeight.Bold,
                            color = if (remaining >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    "${(progress * 100).toInt()}% del presupuesto utilizado",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MetricCards(analysis: SpendingAnalysis) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(
            label = "Total del mes",
            value = formatMoney(analysis.totalMinor),
            supporting = "${analysis.movementCount} movimientos",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Promedio",
            value = formatMoney(analysis.averageMovementMinor),
            supporting = "por movimiento",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, supporting: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyChartCard(weeks: List<WeeklySpend>) {
    AnalysisSectionCard(title = "Ritmo del mes", subtitle = "Gasto CLP por semana") {
        val maxAmount = weeks.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
        val primary = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics {
                    contentDescription = weeks.joinToString(", ") { "Días ${it.label}: ${formatMoney(it.amountMinor)}" }
                }
        ) {
            val baseline = size.height - 22.dp.toPx()
            val chartHeight = baseline - 8.dp.toPx()
            val slot = size.width / weeks.size.coerceAtLeast(1)
            val barWidth = slot * 0.48f
            weeks.forEachIndexed { index, week ->
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, 8.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barWidth, chartHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
                val barHeight = chartHeight * (week.amountMinor.toFloat() / maxAmount.toFloat())
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(left, baseline - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weeks.forEach { week ->
                Text(
                    week.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CategoryChartCard(breakdown: List<SpendingBreakdown>, totalMinor: Long) {
    AnalysisSectionCard(title = "Por categoría", subtitle = "Distribución del gasto del mes") {
        if (breakdown.isEmpty()) {
            AnalysisEmptyState("Categoriza tus movimientos para ver la distribución.")
        } else {
            val visible = remember(breakdown) {
                val top = breakdown.take(5)
                if (breakdown.size <= 5) top else top + SpendingBreakdown(
                    key = "__other__",
                    label = "Otras",
                    amountMinor = breakdown.drop(5).sumOf { it.amountMinor },
                    movementCount = breakdown.drop(5).sumOf { it.movementCount }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = visible.joinToString(", ") {
                                    "${it.label}: ${formatMoney(it.amountMinor)}"
                                }
                            }
                    ) {
                        val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt)
                        var startAngle = -90f
                        visible.forEachIndexed { index, item ->
                            val sweep = if (totalMinor == 0L) 0f else item.amountMinor.toFloat() / totalMinor * 360f
                            drawArc(
                                color = analysisPalette[index % analysisPalette.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = stroke
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            compactMoney(totalMinor),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    visible.forEachIndexed { index, item ->
                        BreakdownLegendRow(
                            color = analysisPalette[index % analysisPalette.size],
                            item = item,
                            totalMinor = totalMinor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownLegendRow(color: Color, item: SpendingBreakdown, totalMinor: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (totalMinor == 0L) "0%" else "${(item.amountMinor * 100 / totalMinor)}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CardBreakdownCard(
    breakdown: List<SpendingBreakdown>,
    cardsByLast4: Map<String, PaymentCard>,
    totalMinor: Long
) {
    AnalysisSectionCard(title = "Por tarjeta", subtitle = "Compara tus medios de pago") {
        if (breakdown.isEmpty()) {
            AnalysisEmptyState("Los movimientos del mes aparecerán aquí por tarjeta.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                breakdown.forEach { item ->
                    val card = cardsByLast4[item.key]
                    val color = parseCardColor(card?.colorHex)
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = color.copy(alpha = 0.18f), shape = CircleShape) {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.padding(9.dp).size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    card?.type?.displayName() ?: "Sin clasificar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatMoney(item.amountMinor), fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = {
                                if (totalMinor == 0L) 0f else (item.amountMinor.toFloat() / totalMinor).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCard(month: YearMonth, onExport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Exportar a Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Incluye todos los movimientos, tarjetas y el resumen de ${month.monthLabel()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Crear archivo .xlsx")
            }
        }
    }
}

@Composable
private fun AnalysisSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun AnalysisEmptyState(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

private fun YearMonth.monthLabel(): String =
    format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "CL")))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CL")) else it.toString() }

private fun PaymentCardType.displayName(): String = when (this) {
    PaymentCardType.CREDIT -> "Crédito"
    PaymentCardType.DEBIT -> "Débito"
    PaymentCardType.PREPAID -> "Prepago"
    PaymentCardType.OTHER -> "Otra"
}

private fun parseCardColor(hex: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex ?: "#5E8CFF"))
}.getOrDefault(Color(0xFF5E8CFF))

private fun compactMoney(amountMinor: Long): String = when {
    amountMinor >= 1_000_000L -> "$${String.format(Locale("es", "CL"), "%.1f", amountMinor / 1_000_000.0)}M"
    amountMinor >= 1_000L -> "$${amountMinor / 1_000} mil"
    else -> formatMoney(amountMinor)
}

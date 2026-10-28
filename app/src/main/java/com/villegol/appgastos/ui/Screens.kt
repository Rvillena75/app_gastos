package com.villegol.appgastos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.villegol.appgastos.data.AppContainer
import com.villegol.appgastos.data.Category
import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.ExpenseProduct
import com.villegol.appgastos.data.ExpenseStatus
import com.villegol.appgastos.data.Necessity
import com.villegol.appgastos.data.PaymentCard
import com.villegol.appgastos.data.PaymentCardType
import com.villegol.appgastos.data.ReceiptProcessingStatus
import com.villegol.appgastos.data.formatMoney
import com.villegol.appgastos.data.requiresExpenseReview
import com.villegol.appgastos.source.wallet.ReceiptOcrWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import org.json.JSONObject

private const val FILTER_WITHOUT_CARD = "__without_card__"
private const val FILTER_WITHOUT_CATEGORY = "__without_category__"

@Composable
fun HomeScreen(container: AppContainer, navController: NavHostController) {
    val expenses by container.expenseRepository.observeActive().collectAsState(emptyList())
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val pendingCount by container.expenseRepository.observePendingCount().collectAsState(0)
    val month = YearMonth.now()
    val previousMonth = month.minusMonths(1)
    val monthExpenses = expenses.filter { YearMonth.from(it.occurredAt.atZone(ZoneId.systemDefault())) == month }
    val previousMonthExpenses = expenses.filter {
        YearMonth.from(it.occurredAt.atZone(ZoneId.systemDefault())) == previousMonth
    }
    val monthName = monthNames[month.monthValue - 1]
    val cardNames = cards.associate { it.last4 to it.name }
    var selectedSummaryPage by remember { mutableStateOf<CardSummaryPage?>(null) }
    val recentExpenses = when (selectedSummaryPage?.key) {
        null, "month" -> expenses
        "without-card" -> expenses.filter { it.cardLast4.isNullOrBlank() }
        else -> expenses.filter { it.cardLast4 == selectedSummaryPage?.last4 }
    }
    ScreenColumn(
        title = "Mis gastos de $monthName",
        onSettings = { navController.navigate("settings") { launchSingleTop = true } }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CardSummaryPager(
                    expenses = monthExpenses,
                    previousMonthExpenses = previousMonthExpenses,
                    cards = cards,
                    onPageSelected = { selectedSummaryPage = it }
                )
            }
            if (pendingCount > 0) {
                item {
                    PendingBanner(pendingCount) { navController.navigate("pending") }
                }
            }
            item {
                SectionHeader(
                    title = selectedSummaryPage?.let { page ->
                        when (page.key) {
                            "month" -> "Movimientos recientes"
                            "without-card" -> "Movimientos sin tarjeta"
                            else -> "Movimientos de ${page.title}"
                        }
                    } ?: "Movimientos recientes",
                    action = "Ver todos",
                    onAction = { navController.navigate("expenses") }
                )
            }
            if (recentExpenses.isEmpty()) {
                item {
                    EmptyState(
                        title = if (selectedSummaryPage?.key == "month") {
                            "Todavía no hay movimientos"
                        } else {
                            "No hay movimientos en esta tarjeta"
                        },
                        body = if (selectedSummaryPage?.key == "month") {
                            "Las compras detectadas desde Wallet aparecerán aquí automáticamente."
                        } else {
                            "Cuando registres una compra con ella, aparecerá aquí."
                        }
                    )
                }
            } else {
                items(recentExpenses.take(5), key = { it.id }) { expense ->
                    ExpenseRow(expense, navController, cardNames)
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
fun ExpensesScreen(container: AppContainer, navController: NavHostController) {
    val expenses by container.expenseRepository.observeActive().collectAsState(emptyList())
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val categories by container.expenseRepository.observeCategories().collectAsState(emptyList())
    val cardNames = cards.associate { it.last4 to it.name }
    var query by remember { mutableStateOf("") }
    var pendingOnly by remember { mutableStateOf(false) }
    var selectedCardLast4 by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val cardOptions = remember(expenses, cards) {
        buildList {
            if (expenses.any { it.cardLast4.isNullOrBlank() }) {
                add(FILTER_WITHOUT_CARD to "Sin tarjeta asociada")
            }
            addAll((cards.map { it.last4 } + expenses.mapNotNull { it.cardLast4 }).distinct().map { last4 ->
            last4 to (cards.firstOrNull { it.last4 == last4 }?.name?.takeIf { it.isNotBlank() }
                ?.let { "$it · •••• $last4" } ?: "•••• $last4")
            })
        }
    }
    val categoryOptions = remember(expenses, categories) {
        buildList {
            if (expenses.any { it.categoryName.isNullOrBlank() }) {
                add(FILTER_WITHOUT_CATEGORY to "Sin categoría")
            }
            addAll(
                (categories.map { it.name } + expenses.mapNotNull { it.categoryName })
                    .distinct().sorted().map { it to it }
            )
        }
    }
    val matchingFilters = expenses.filter { expense ->
        val cardLabel = expense.cardLast4?.let { "${cardNames[it].orEmpty()} $it" }
        val haystack = listOfNotNull(expense.merchantName, expense.categoryName, expense.description, cardLabel).joinToString(" ")
        haystack.contains(query.trim(), ignoreCase = true) &&
            (selectedCardLast4 == null ||
                selectedCardLast4 == FILTER_WITHOUT_CARD && expense.cardLast4.isNullOrBlank() ||
                expense.cardLast4 == selectedCardLast4) &&
            (selectedCategory == null ||
                selectedCategory == FILTER_WITHOUT_CATEGORY && expense.categoryName.isNullOrBlank() ||
                expense.categoryName == selectedCategory)
    }
    val allCount = matchingFilters.size
    val reviewCount = matchingFilters.count { it.status == ExpenseStatus.PENDING_REVIEW }
    val filtered = matchingFilters.filter { !pendingOnly || it.status == ExpenseStatus.PENDING_REVIEW }
    ScreenColumn(
        title = "Movimientos",
        onSettings = { navController.navigate("settings") { launchSingleTop = true } }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar movimientos") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                    }
                }
            },
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpenseFilterMenu(
                allLabel = "Tarjetas",
                selectedLabel = cardOptions.firstOrNull { it.first == selectedCardLast4 }?.second,
                options = cardOptions,
                onSelected = { selectedCardLast4 = it },
                modifier = Modifier.weight(1f)
            )
            ExpenseFilterMenu(
                allLabel = "Categorías",
                selectedLabel = categoryOptions.firstOrNull { it.first == selectedCategory }?.second,
                options = categoryOptions,
                onSelected = { selectedCategory = it },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !pendingOnly,
                onClick = { pendingOnly = false },
                label = { Text("Todos $allCount") }
            )
            FilterChip(
                selected = pendingOnly,
                onClick = { pendingOnly = true },
                label = { Text("Por revisar $reviewCount") }
            )
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (query.isNotBlank()) "Sin resultados" else "Nada por aquí",
                body = if (pendingOnly) "No tienes movimientos pendientes de revisión."
                else "Prueba con otro comercio, categoría o tarjeta."
            )
        } else {
            ExpenseList(
                expenses = filtered,
                navController = navController,
                cardNames = cardNames,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExpenseFilterMenu(
    allLabel: String,
    selectedLabel: String?,
    options: List<Pair<String, String>>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                selectedLabel ?: allLabel,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = { onSelected(null); expanded = false }
            )
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(value); expanded = false }
                )
            }
        }
    }
}

@Composable
fun PendingScreen(container: AppContainer, navController: NavHostController) {
    val expenses by container.expenseRepository.observePending().collectAsState(emptyList())
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    ScreenColumn(
        title = "Pendientes",
        onSettings = { navController.navigate("settings") { launchSingleTop = true } },
        onBack = {
            if (!navController.popBackStack()) {
                navController.navigate("home") { launchSingleTop = true }
            }
        }
    ) {
        if (expenses.isEmpty()) {
            Text("No hay pendientes. La app está al día.")
        } else {
            ExpenseList(
                expenses = expenses,
                navController = navController,
                cardNames = cards.associate { it.last4 to it.name },
                onConfirm = { expense ->
                    scope.launch { container.expenseRepository.confirmPending(expense.id) }
                }
            )
        }
    }
}

@Composable
private fun ExpenseList(
    expenses: List<Expense>,
    navController: NavHostController,
    cardNames: Map<String, String?> = emptyMap(),
    onConfirm: ((Expense) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(expenses, key = { it.id }) { expense ->
            ExpenseRow(expense, navController, cardNames, onConfirm)
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    navController: NavHostController,
    cardNames: Map<String, String?>,
    onConfirm: ((Expense) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("detail/${expense.id}") },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        expense.merchantName ?: "Comercio por identificar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildList {
                            add(expense.categoryName ?: "Sin categoría")
                            expense.cardLast4?.let { last4 ->
                                add(cardNames[last4]?.takeIf(String::isNotBlank) ?: "••$last4")
                            }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatRelativeDate(expense),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(expense.amountMinor, expense.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (expense.status == ExpenseStatus.PENDING_REVIEW) {
                        Text(
                            "Por revisar",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            if (onConfirm != null && expense.status == ExpenseStatus.PENDING_REVIEW) {
                TextButton(onClick = { onConfirm(expense) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Marcar como revisado")
                }
            }
        }
    }
}

private data class CardSummaryPage(
    val key: String,
    val title: String,
    val last4: String?,
    val colorHex: String?,
    val type: PaymentCardType,
    val expenses: List<Expense>
)

private data class CardColorOption(val name: String, val hex: String)

private val cardColorOptions = listOf(
    CardColorOption("Verde", "#245B49"),
    CardColorOption("Azul", "#1859A9"),
    CardColorOption("Morado", "#624391"),
    CardColorOption("Burdeo", "#8A3D55"),
    CardColorOption("Cobre", "#95501E"),
    CardColorOption("Grafito", "#414A59")
)

private fun cardColor(hex: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex ?: cardColorOptions.first().hex))
}.getOrElse { Color(android.graphics.Color.parseColor(cardColorOptions.first().hex)) }

@Composable
private fun CardSummaryPager(
    expenses: List<Expense>,
    previousMonthExpenses: List<Expense>,
    cards: List<PaymentCard>,
    onPageSelected: (CardSummaryPage) -> Unit
) {
    val pages = remember(expenses, previousMonthExpenses, cards) {
        val knownCards = cards.associateBy { it.last4 }
        val last4Values = buildList {
            addAll(cards.map { it.last4 })
            addAll((expenses + previousMonthExpenses).mapNotNull { it.cardLast4 })
        }.distinct()
        buildList {
            last4Values.forEach { last4 ->
                val card = knownCards[last4]
                add(
                    CardSummaryPage(
                        key = last4,
                        title = card?.name?.takeIf { it.isNotBlank() }
                            ?: card?.type?.takeIf { it != PaymentCardType.OTHER }?.let(::paymentCardTypeLabel)
                            ?: "Tarjeta",
                        last4 = last4,
                        colorHex = card?.colorHex,
                        type = card?.type ?: PaymentCardType.OTHER,
                        expenses = expenses.filter { it.cardLast4 == last4 }
                    )
                )
            }
            val expensesWithoutCard = expenses.filter { it.cardLast4.isNullOrBlank() }
            val previousWithoutCard = previousMonthExpenses.filter { it.cardLast4.isNullOrBlank() }
            if (expensesWithoutCard.isNotEmpty() || previousWithoutCard.isNotEmpty()) {
                add(
                    CardSummaryPage(
                        key = "without-card",
                        title = "Sin tarjeta asociada",
                        last4 = null,
                        colorHex = null,
                        type = PaymentCardType.OTHER,
                        expenses = expensesWithoutCard
                    )
                )
            }
            if (isEmpty()) {
                add(
                    CardSummaryPage(
                        key = "month",
                        title = "Total del mes",
                        last4 = null,
                        colorHex = null,
                        type = PaymentCardType.OTHER,
                        expenses = expenses
                    )
                )
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    LaunchedEffect(pagerState.currentPage, pages) {
        pages.getOrNull(pagerState.currentPage)?.let(onPageSelected)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardHeight = (maxWidth - 34.dp) * (5.4f / 8.56f)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 17.dp),
                pageSpacing = 0.dp,
                snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Center,
                beyondViewportPageCount = 1,
                key = { index -> pages.getOrNull(index)?.key ?: "summary-page-$index" }
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                val pageDisplacement = (
                    (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                ).coerceIn(-1f, 1f)
                val pageOffset = pageDisplacement.absoluteValue
                MonthSummaryCard(
                    page = page,
                    pageIndex = pageIndex,
                    contentAlpha = ((1f - pageOffset) * 2f - 0.5f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .zIndex(1f - pageOffset)
                        .graphicsLayer {
                            scaleX = 1f - (pageOffset * 0.055f)
                            scaleY = 1f - (pageOffset * 0.055f)
                            translationX = pageDisplacement * 18.dp.toPx()
                            translationY = pageOffset * 10.dp.toPx()
                            alpha = 1f - (pageOffset * 0.12f)
                        }
                )
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .background(
                                color = if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    page: CardSummaryPage,
    pageIndex: Int,
    contentAlpha: Float,
    modifier: Modifier = Modifier
) {
    val expenses = page.expenses
    val total = expenses.sumOf { it.amountMinor ?: 0L }
    val containerColor = cardColor(page.colorHex ?: cardColorOptions[pageIndex % cardColorOptions.size].hex)
    val contentColor = if (containerColor.luminance() > 0.48f) Color(0xFF151515) else Color.White
    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        page.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    page.last4?.let {
                        Text(
                            buildString {
                                if (page.type != PaymentCardType.OTHER) {
                                    append(paymentCardTypeLabel(page.type)).append(" · ")
                                }
                                append("•••• ").append(it)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.76f)
                        )
                    }
                }
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                formatMoney(total),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 movimiento necesita atención" else "$count movimientos necesitan atención",
                    fontWeight = FontWeight.SemiBold
                )
                Text("Completa los datos que faltan", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Ver pendientes")
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 20.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    value: String,
    categories: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            label = { Text("Categoría (opcional)") },
            placeholder = { Text("Sin categoría") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin categoría") },
                onClick = {
                    onValueChange("")
                    expanded = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onValueChange(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

private data class ProductDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = ""
)

@Composable
private fun ProductsEditor(
    values: List<ProductDraft>,
    onValuesChange: (List<ProductDraft>) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Opcional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onValuesChange(values + ProductDraft()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Agregar")
        }
    }
    if (values.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Text(
                "Agrega los productos si quieres conservar el detalle de la compra.",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    values.forEachIndexed { index, product ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Producto ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onValuesChange(values.filterNot { it.id == product.id }) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar producto", tint = MaterialTheme.colorScheme.error)
                    }
                }
                OutlinedTextField(
                    value = product.name,
                    onValueChange = { name ->
                        onValuesChange(values.map { if (it.id == product.id) it.copy(name = name) else it })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = product.quantity,
                        onValueChange = { quantity ->
                            val filtered = quantity.filter { it.isDigit() || it == ',' || it == '.' }
                            onValuesChange(values.map { if (it.id == product.id) it.copy(quantity = filtered) else it })
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Cantidad") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = product.unitPrice,
                        onValueChange = { price ->
                            onValuesChange(values.map { if (it.id == product.id) it.copy(unitPrice = price.filter(Char::isDigit)) else it })
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Precio unitario") },
                        prefix = { Text("$ ") },
                        singleLine = true
                    )
                }
            }
        }
    }
}

private fun ProductDraft.toDomain(expenseId: String): ExpenseProduct? {
    val normalizedName = name.trim().takeIf { it.isNotEmpty() } ?: return null
    val normalizedQuantity = quantity.replace(',', '.').toDoubleOrNull()
    return ExpenseProduct(
        id = id,
        expenseId = expenseId,
        name = normalizedName,
        quantity = normalizedQuantity?.takeIf { it > 0 },
        unitPriceMinor = unitPrice.toLongOrNull()?.takeIf { it >= 0 }
    )
}

private fun ExpenseProduct.toDraft() = ProductDraft(
    id = id,
    name = name,
    quantity = quantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
    unitPrice = unitPriceMinor?.toString().orEmpty()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentCardPicker(
    selectedLast4: String,
    cards: List<PaymentCard>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.last4 == selectedLast4 }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.let { "${it.name?.takeIf(String::isNotBlank) ?: "Tarjeta"} · ••${it.last4}" }
                ?: if (selectedLast4.isBlank()) "Sin tarjeta" else "••$selectedLast4",
            onValueChange = {},
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            label = { Text("Tarjeta (opcional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin tarjeta") },
                onClick = {
                    onSelected("")
                    expanded = false
                }
            )
            cards.forEach { card ->
                DropdownMenuItem(
                    text = { Text("${card.name?.takeIf(String::isNotBlank) ?: "Tarjeta"} · ••${card.last4}") },
                    onClick = {
                        onSelected(card.last4)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentCardTypePicker(
    value: PaymentCardType,
    onValueChange: (PaymentCardType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = paymentCardTypeLabel(value),
            onValueChange = {},
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            label = { Text("Tipo de tarjeta") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentCardType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(paymentCardTypeLabel(type)) },
                    onClick = {
                        onValueChange(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun paymentCardTypeLabel(type: PaymentCardType): String = when (type) {
    PaymentCardType.CREDIT -> "Crédito"
    PaymentCardType.DEBIT -> "Débito"
    PaymentCardType.PREPAID -> "Prepago"
    PaymentCardType.OTHER -> "Otra"
}

private val monthNames = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenColumn(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, "Configuración")
                        }
                    }
                }
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(container: AppContainer, expenseId: String?, navController: NavHostController) {
    val id = expenseId ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showReceiptImage by remember { mutableStateOf(false) }
    var recentlyDeleted by remember { mutableStateOf(false) }
    val evidence by container.expenseRepository.observeEvidence(id).collectAsState(emptyList())
    val products by container.expenseRepository.observeProducts(id).collectAsState(emptyList())
    val receipt by container.expenseRepository.observeReceipt(id).collectAsState(initial = null)
    val currentReceipt = receipt
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val observedCurrent by container.expenseRepository.observeExpense(id).collectAsState(initial = null)
    val current = observedCurrent
    val cardName = current?.cardLast4?.let { last4 -> cards.firstOrNull { it.last4 == last4 }?.name }
    val location = evidence.firstOrNull { it.type == com.villegol.appgastos.data.EvidenceType.LOCATION }
        ?.let(::parseLocationEvidence)
    val receiptImage by produceState(
        initialValue = ReceiptImageState(),
        key1 = receipt?.imagePath,
        key2 = showReceiptImage
    ) {
        if (!showReceiptImage || receipt?.imagePath == null) {
            value = ReceiptImageState()
        } else {
            value = ReceiptImageState(isLoading = true)
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val prepared = container.receiptPipelines.preprocessor.prepare(receipt!!.imagePath)
                    loadReceiptBitmap(prepared.path)
                }.getOrNull()
            }
            value = ReceiptImageState(bitmap = bitmap)
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Detalle") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        if (current == null) {
            Text("Gasto no encontrado", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            current.merchantName ?: "Comercio por identificar",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatMoney(current.amountMinor, current.currency),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (current.status == ExpenseStatus.PENDING_REVIEW) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null)
                                    Text("Faltan datos por confirmar", fontWeight = FontWeight.SemiBold)
                                }
                                Text("Revisa el comercio y el monto para dejar este movimiento listo.")
                                Button(
                                    onClick = {
                                        if (!receipt?.structuredOcr.isNullOrBlank()) {
                                            navController.navigate("receipt-review/${current.id}")
                                        } else {
                                            navController.navigate("edit/${current.id}")
                                        }
                                    }
                                ) { Text("Completar movimiento") }
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Detalles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            DetailLine("Fecha", formatDate(current))
                            DetailLine("Categoría", current.categoryName ?: "Sin categoría")
                            current.cardLast4?.let { last4 ->
                                DetailLine("Tarjeta", cardName?.takeIf { it.isNotBlank() } ?: "•••• $last4")
                            }
                            current.description?.takeIf { it.isNotBlank() }?.let { DetailLine("Nota", it) }
                            if (location != null) {
                                TextButton(onClick = {
                                    val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(Compra)")
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                }) { Text("Ver lugar de la compra") }
                            }
                        }
                    }
                }
                item {
                    Text("Productos", style = MaterialTheme.typography.titleLarge)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (products.isEmpty()) {
                                Text(
                                    "Sin productos agregados",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                products.forEachIndexed { index, product ->
                                    if (index > 0) HorizontalDivider()
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(product.name, fontWeight = FontWeight.Medium)
                                            product.quantity?.let { quantity ->
                                                Text(
                                                    "Cantidad: ${if (quantity % 1.0 == 0.0) quantity.toLong() else quantity}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        product.unitPriceMinor?.let { price ->
                                            Text(formatMoney(price, current.currency), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Boleta", style = MaterialTheme.typography.titleLarge)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (currentReceipt == null) {
                                Text(
                                    "Adjunta una foto si quieres conservar el comprobante o completar los datos automáticamente.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { navController.navigate("receipt/${current.id}/manual") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Adjuntar boleta")
                                }
                            } else {
                                when (currentReceipt.processingStatus) {
                                    ReceiptProcessingStatus.PROCESSING -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                                        Text("Leyendo la boleta…")
                                    }
                                    ReceiptProcessingStatus.COMPLETE -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Boleta guardada", color = MaterialTheme.colorScheme.primary)
                                    }
                                    ReceiptProcessingStatus.ERROR -> {
                                        Text("No pudimos leer la boleta. La foto sigue guardada.", color = MaterialTheme.colorScheme.error)
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    container.expenseRepository.markReceiptProcessing(currentReceipt.id)
                                                    enqueueReceiptOcr(context, currentReceipt.id, currentReceipt.imagePath)
                                                }
                                            }
                                        ) { Text("Intentar nuevamente") }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showReceiptImage = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ver boleta")
                                    }
                                    if (!receipt?.structuredOcr.isNullOrBlank()) {
                                        Button(
                                            onClick = { navController.navigate("receipt-review/${current.id}") },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Revisar datos") }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    if (recentlyDeleted) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Movimiento eliminado")
                                TextButton(onClick = {
                                    scope.launch {
                                        container.expenseRepository.restore(current.id)
                                        recentlyDeleted = false
                                    }
                                }) { Text("Deshacer") }
                            }
                        }
                    } else {
                        Button(
                            onClick = { navController.navigate("edit/${current.id}") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Editar movimiento")
                        }
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar movimiento", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
    if (showReceiptImage && receipt != null) {
        Dialog(
            onDismissRequest = { showReceiptImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Foto de la boleta", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = { showReceiptImage = false }) {
                            Icon(Icons.Default.ArrowBack, "Cerrar")
                        }
                    }
                    val loadedReceiptBitmap = receiptImage.bitmap
                    when {
                        receiptImage.isLoading -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                        loadedReceiptBitmap != null -> ZoomableReceiptImage(
                            bitmap = loadedReceiptBitmap,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        else -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se pudo cargar la foto.")
                        }
                    }
                }
            }
        }
    }
    if (showDeleteConfirmation && current != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("¿Borrar este gasto?") },
            text = { Text("Podrás deshacerlo durante unos segundos.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    scope.launch {
                        container.expenseRepository.delete(current.id)
                        recentlyDeleted = true
                        delay(5_000)
                        if (recentlyDeleted) navController.popBackStack()
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ZoomableReceiptImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale == 1f) Offset.Zero else offset + panChange
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto de la boleta",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState),
            contentScale = ContentScale.Fit
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = CircleShape
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        scale = (scale - 0.5f).coerceAtLeast(1f)
                        if (scale == 1f) offset = Offset.Zero
                    },
                    enabled = scale > 1f
                ) { Icon(Icons.Default.ZoomOut, contentDescription = "Alejar") }
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                IconButton(
                    onClick = { scale = (scale + 0.5f).coerceAtMost(5f) },
                    enabled = scale < 5f
                ) { Icon(Icons.Default.ZoomIn, contentDescription = "Acercar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(container: AppContainer, expenseId: String?, navController: NavHostController) {
    val id = expenseId ?: return
    val scope = rememberCoroutineScope()
    val current by container.expenseRepository.observeExpense(id).collectAsState(initial = null)
    val receipt by container.expenseRepository.observeReceipt(id).collectAsState(initial = null)
    val categories by container.expenseRepository.observeCategories().collectAsState(emptyList())
    val summary = remember(receipt?.structuredOcr) {
        parseReceiptReviewSummary(receipt?.structuredOcr)
    }
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var productDrafts by remember { mutableStateOf<List<ProductDraft>>(emptyList()) }
    var initializedKey by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val stateKey = "${current?.id}:${current?.updatedAt}:${receipt?.id}:${receipt?.structuredOcr?.hashCode()}"

    LaunchedEffect(stateKey) {
        if (current != null && stateKey != initializedKey) {
            merchant = current?.merchantName?.takeIf { it.isNotBlank() } ?: summary?.merchantName.orEmpty()
            amount = (current?.amountMinor ?: summary?.totalMinor)?.toString().orEmpty()
            category = current?.categoryName?.takeIf { it.isNotBlank() } ?: summary?.category.orEmpty()
            description = current?.description.orEmpty()
            val storedProducts = container.expenseRepository.getProducts(id)
            productDrafts = if (storedProducts.isNotEmpty()) {
                storedProducts.map { it.toDraft() }
            } else {
                summary?.items.orEmpty().map { item ->
                    ProductDraft(
                        name = item.description,
                        quantity = item.quantity?.toString().orEmpty(),
                        unitPrice = item.unitPriceMinor?.toString().orEmpty()
                    )
                }
            }
            initializedKey = stateKey
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Revisar boleta") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            current == null -> Text("Cargando datos…", modifier = Modifier.padding(padding).padding(16.dp))
            summary == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("La boleta todavía no tiene datos estructurados para revisar.")
                TextButton(onClick = { navController.popBackStack() }) { Text("Volver al detalle") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Confirma los datos antes de guardarlos.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comercio") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit); errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monto ${summary.currency}") },
                    singleLine = true
                )
                CategoryPicker(
                    value = category,
                    categories = categories.map { it.name },
                    onValueChange = { category = it; errorMessage = null }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nota (opcional)") },
                    singleLine = true
                )
                ProductsEditor(productDrafts) { productDrafts = it; errorMessage = null }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val expense = current ?: return@Button
                        val amountMinor = amount.toLongOrNull()
                        val normalizedMerchant = merchant.trim().takeIf { it.isNotBlank() }
                        val normalizedCategory = category.trim().takeIf { it.isNotBlank() }
                        if (amountMinor == null || amountMinor <= 0L || normalizedMerchant == null) {
                            errorMessage = "Completa un comercio y un monto válido."
                            return@Button
                        }
                        if (productDrafts.any { it.name.isBlank() }) {
                            errorMessage = "Completa el nombre de cada producto o elimínalo."
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            errorMessage = null
                            try {
                                container.receiptPipelines.merchantCorrections.rememberCorrectionFromStructuredReceipt(
                                    structuredOcr = receipt?.structuredOcr,
                                    correctedMerchant = normalizedMerchant
                                )
                                container.receiptPipelines.merchantCorrections.rememberCorrection(
                                    observedMerchant = expense.merchantName,
                                    correctedMerchant = normalizedMerchant
                                )
                                normalizedCategory?.let {
                                    container.receiptPipelines.categoryResolver.rememberCorrection(normalizedMerchant, it)
                                }
                                container.expenseRepository.recordManualCorrection(
                                    expense.copy(
                                        amountMinor = amountMinor,
                                        merchantName = normalizedMerchant,
                                        categoryName = normalizedCategory,
                                        description = description.trim().takeIf { it.isNotBlank() },
                                        status = if (requiresExpenseReview(normalizedMerchant, amountMinor)) {
                                            ExpenseStatus.PENDING_REVIEW
                                        } else {
                                            ExpenseStatus.IDENTIFIED
                                        },
                                        confidence = 1f
                                    ),
                                    correction = "receipt_review;merchant=$normalizedMerchant;amount=$amountMinor;category=$normalizedCategory"
                                )
                                container.expenseRepository.replaceProducts(
                                    expense.id,
                                    productDrafts.mapNotNull { it.toDomain(expense.id) }
                                )
                                navController.popBackStack()
                            } catch (exception: Exception) {
                                errorMessage = exception.message ?: "No se pudo guardar la revisión."
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isSaving) "Guardando…" else "Guardar cambios") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(container: AppContainer, expenseId: String?, navController: NavHostController) {
    val isNew = expenseId == "new" || expenseId == null
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val categories by container.expenseRepository.observeCategories().collectAsState(emptyList())
    var existing by remember { mutableStateOf<Expense?>(null) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var productDrafts by remember { mutableStateOf<List<ProductDraft>>(emptyList()) }
    var cardLast4 by remember { mutableStateOf("") }
    var sourceReceipt by remember { mutableStateOf<com.villegol.appgastos.data.Receipt?>(null) }
    var loaded by remember { mutableStateOf(isNew) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expenseId) {
        if (!isNew) {
            existing = container.expenseRepository.get(expenseId!!)
            sourceReceipt = container.expenseRepository.getLatestReceipt(expenseId)
            productDrafts = container.expenseRepository.getProducts(expenseId).map { it.toDraft() }
            existing?.let {
                amount = it.amountMinor?.toString().orEmpty()
                merchant = it.merchantName.orEmpty()
                category = it.categoryName.orEmpty()
                description = it.description.orEmpty()
                cardLast4 = it.cardLast4.orEmpty()
            }
        }
        loaded = true
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isNew) "Nuevo gasto" else "Editar gasto") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        if (loaded) Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isNew) "Completa los datos principales del movimiento." else "Actualiza solo lo que necesites.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                amount,
                { amount = it.filter(Char::isDigit); errorMessage = null },
                Modifier.fillMaxWidth(),
                label = { Text("Monto") },
                prefix = { Text("$ ") },
                supportingText = { Text("Pesos chilenos") },
                singleLine = true
            )
            OutlinedTextField(
                merchant,
                { merchant = it; errorMessage = null },
                Modifier.fillMaxWidth(),
                label = { Text("Comercio") },
                singleLine = true
            )
            CategoryPicker(
                value = category,
                categories = categories.map { it.name },
                onValueChange = { category = it; errorMessage = null }
            )
            PaymentCardPicker(
                selectedLast4 = cardLast4,
                cards = cards,
                onSelected = { cardLast4 = it }
            )
            OutlinedTextField(
                description,
                { description = it },
                Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") },
                minLines = 2
            )
            ProductsEditor(productDrafts) { productDrafts = it; errorMessage = null }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        val normalizedCardLast4 = cardLast4.takeIf { it.length == 4 }
                        val normalizedAmount = amount.toLongOrNull()
                        if (normalizedAmount == null || normalizedAmount <= 0L || merchant.isBlank()) {
                            errorMessage = "Completa un comercio y un monto válido."
                            return@launch
                        }
                        if (productDrafts.any { it.name.isBlank() }) {
                            errorMessage = "Completa el nombre de cada producto o elimínalo."
                            return@launch
                        }
                        isSaving = true
                        errorMessage = null
                        try {
                            normalizedCardLast4?.let {
                                container.expenseRepository.ensurePaymentCard(it)
                            }
                        if (!isNew) {
                            container.receiptPipelines.merchantCorrections.rememberCorrectionFromStructuredReceipt(
                                structuredOcr = sourceReceipt?.structuredOcr,
                                correctedMerchant = merchant
                            )
                            container.receiptPipelines.merchantCorrections.rememberCorrection(
                                observedMerchant = existing?.merchantName,
                                correctedMerchant = merchant
                            )
                        }
                        if (merchant.isNotBlank() && category.isNotBlank()) {
                            container.receiptPipelines.categoryResolver.rememberCorrection(merchant, category)
                        }
                        if (isNew) {
                            container.expenseRepository.createManual(
                                amountMinor = normalizedAmount,
                                currency = "CLP",
                                merchantName = merchant,
                                categoryName = category,
                                description = description,
                                cardLast4 = normalizedCardLast4,
                                products = productDrafts.mapNotNull { it.toDomain("") }
                            )
                        } else {
                            existing?.let { current ->
                                container.expenseRepository.recordManualCorrection(
                                    current.copy(
                                        amountMinor = normalizedAmount,
                                        merchantName = merchant.ifBlank { null },
                                        categoryName = category.ifBlank { null },
                                        description = description.ifBlank { null },
                                        cardLast4 = normalizedCardLast4,
                                        status = if (requiresExpenseReview(merchant, normalizedAmount)) {
                                            ExpenseStatus.PENDING_REVIEW
                                        } else {
                                            ExpenseStatus.IDENTIFIED
                                        },
                                        confidence = 1f
                                    ),
                                    correction = "merchant=$merchant;category=$category;description=$description"
                                )
                                container.expenseRepository.replaceProducts(
                                    current.id,
                                    productDrafts.mapNotNull { it.toDomain(current.id) }
                                )
                            }
                        }
                        navController.popBackStack()
                        } catch (exception: Exception) {
                            errorMessage = exception.message ?: "No se pudo guardar el gasto."
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isSaving) "Guardando…" else "Guardar movimiento") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCaptureScreen(
    container: AppContainer,
    expenseId: String?,
    navController: NavHostController,
    autoOpenCamera: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun saveAndQueueReceipt(path: String) {
        val targetExpenseId = when {
            expenseId == "new" -> container.expenseRepository.createManual(
                amountMinor = null,
                currency = "CLP",
                merchantName = null,
                categoryName = null,
                description = null
            ).id
            !expenseId.isNullOrBlank() -> expenseId!!
            else -> error("No hay un gasto al que asociar la boleta")
        }
        val receiptId = container.expenseRepository.saveReceipt(targetExpenseId, path)
        enqueueReceiptOcr(context, receiptId, path)

        navController.popBackStack()
        if (expenseId == "new") {
            navController.navigate("detail/$targetExpenseId")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = imageFile
        if (success && file != null) {
            scope.launch {
                isSaving = true
                errorMessage = null
                try {
                    saveAndQueueReceipt(file.absolutePath)
                } catch (exception: Exception) {
                    errorMessage = exception.message ?: "No se pudo guardar la boleta."
                }
                isSaving = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                isSaving = true
                errorMessage = null
                try {
                    val path = copyReceiptUri(context, uri)
                    saveAndQueueReceipt(path)
                } catch (exception: Exception) {
                    errorMessage = exception.message ?: "No se pudo leer o guardar la imagen seleccionada."
                }
                isSaving = false
            }
        }
    }

    fun openCamera() {
        val directory = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File.createTempFile("receipt_", ".jpg", directory)
        imageFile = file
        cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    LaunchedEffect(expenseId, autoOpenCamera) {
        if (autoOpenCamera && expenseId != null) openCamera()
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Boleta") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (expenseId == "new") {
                    "Agrega una foto de boleta y la app creará el movimiento automáticamente."
                } else {
                    "La imagen se guarda localmente y se procesa con OCR para completar este gasto."
                }
            )
            if (isSaving) {
                Text("Guardando y preparando la boleta…")
                androidx.compose.material3.CircularProgressIndicator()
            } else {
                Button(onClick = { openCamera() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tomar foto")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Elegir foto del dispositivo") }
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cards by container.expenseRepository.observePaymentCards().collectAsState(emptyList())
    val categories by container.expenseRepository.observeCategories().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
    }
    var showAddCard by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<PaymentCard?>(null) }
    var cardToEdit by remember { mutableStateOf<PaymentCard?>(null) }
    var newCardLast4 by remember { mutableStateOf("") }
    var newCardName by remember { mutableStateOf("") }
    var newCardColor by remember { mutableStateOf(cardColorOptions.first().hex) }
    var newCardType by remember { mutableStateOf(PaymentCardType.OTHER) }
    var showCardColorPicker by remember { mutableStateOf(false) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var categoriesExpanded by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listenerEnabled = remember(permissionRefresh) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.isNotificationListenerAccessGranted(
            ComponentName(context, com.villegol.appgastos.source.wallet.WalletNotificationListenerService::class.java)
        )
    }
    val notificationsEnabled = remember(permissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationEnabled = remember(permissionRefresh) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuración") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { Text("Registro automático", style = MaterialTheme.typography.titleLarge) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Default.CreditCard,
                            title = "Compras desde Wallet",
                            subtitle = if (listenerEnabled) "Activo" else "Requiere acceso a notificaciones",
                            enabled = listenerEnabled,
                            action = "Administrar",
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        )
                        HorizontalDivider(Modifier.padding(start = 64.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Notifications,
                            title = "Avisos de la app",
                            subtitle = if (notificationsEnabled) "Activos" else "Desactivados",
                            enabled = notificationsEnabled,
                            action = if (notificationsEnabled) "Administrar" else "Activar",
                            onClick = {
                                if (notificationsEnabled) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                } else {
                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(start = 64.dp))
                        SettingsActionRow(
                            icon = Icons.Default.LocationOn,
                            title = "Ubicación de la compra",
                            subtitle = if (locationEnabled) "Permitida" else "Opcional y desactivada",
                            enabled = locationEnabled,
                            action = if (locationEnabled) "Administrar" else "Activar",
                            onClick = {
                                if (locationEnabled) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    )
                                } else {
                                    locationPermission.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
            item {
                Text(
                    "La ubicación solo se guarda junto a la compra; la app no aprende lugares ni sucursales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoriesExpanded = !categoriesExpanded }
                                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Categorías", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (categories.size == 1) "1 categoría" else "${categories.size} categorías",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                categoryToEdit = null
                                categoryName = ""
                                categoryError = null
                                showCategoryEditor = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar categoría")
                            }
                            IconButton(onClick = { categoriesExpanded = !categoriesExpanded }) {
                                Icon(
                                    if (categoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (categoriesExpanded) "Ocultar categorías" else "Mostrar categorías"
                                )
                            }
                        }
                        if (categoriesExpanded) {
                            HorizontalDivider()
                            if (categories.isEmpty()) {
                                Text(
                                    "Aún no hay categorías. Agrega una para organizar tus movimientos.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                            categories.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                                CategoryRow(
                                    category = category,
                                    onEdit = {
                                        categoryToEdit = category
                                        categoryName = category.name
                                        categoryError = null
                                        showCategoryEditor = true
                                    },
                                    onDelete = { categoryToDelete = category }
                                )
                            }
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tarjetas", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = {
                        cardToEdit = null
                        newCardLast4 = ""
                        newCardName = ""
                        newCardColor = cardColorOptions.first().hex
                        newCardType = PaymentCardType.OTHER
                        cardError = null
                        showCardColorPicker = false
                        showAddCard = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar")
                    }
                }
            }
            if (cards.isEmpty()) {
                item {
                    EmptyState(
                        title = "Sin tarjetas guardadas",
                        body = "Aparecerán automáticamente después de una compra con Wallet."
                    )
                }
            } else {
                items(cards, key = { it.last4 }) { card ->
                    PaymentCardRow(
                        card = card,
                        onEdit = {
                            cardToEdit = card
                            newCardLast4 = card.last4
                            newCardName = card.name.orEmpty()
                            newCardColor = card.colorHex ?: cardColorOptions.first().hex
                            newCardType = card.type
                            cardError = null
                            showCardColorPicker = false
                            showAddCard = true
                        },
                        onDelete = { cardToDelete = card }
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    if (showCategoryEditor) {
        AlertDialog(
            onDismissRequest = { showCategoryEditor = false },
            title = { Text(if (categoryToEdit == null) "Agregar categoría" else "Editar categoría") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it; categoryError = null },
                        label = { Text("Nombre") },
                        singleLine = true
                    )
                    categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            categoryToEdit?.let {
                                container.expenseRepository.renameCategory(it, categoryName)
                            } ?: container.expenseRepository.addCategory(categoryName)
                            showCategoryEditor = false
                        } catch (exception: Exception) {
                            categoryError = exception.message ?: "No se pudo guardar la categoría."
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showCategoryEditor = false }) { Text("Cancelar") } }
        )
    }
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("¿Eliminar ${category.name}?") },
            text = {
                Text("Dejará de aparecer al categorizar movimientos nuevos. Los movimientos anteriores conservarán este nombre.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.expenseRepository.deleteCategory(category)
                        categoryToDelete = null
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { categoryToDelete = null }) { Text("Cancelar") } }
        )
    }
    if (showAddCard && !showCardColorPicker) {
        AlertDialog(
            onDismissRequest = { showAddCard = false },
            title = { Text(if (cardToEdit == null) "Agregar tarjeta" else "Editar tarjeta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCardLast4,
                        onValueChange = { newCardLast4 = it.filter(Char::isDigit).take(4); cardError = null },
                        label = { Text("Últimos 4 dígitos") },
                        readOnly = cardToEdit != null,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newCardName,
                        onValueChange = { newCardName = it; cardError = null },
                        label = { Text("Nombre para identificarla") },
                        supportingText = { Text("Ejemplo: Visa personal, débito Banco Estado") },
                        singleLine = true
                    )
                    PaymentCardTypePicker(
                        value = newCardType,
                        onValueChange = { newCardType = it }
                    )
                    Text("Color de la tarjeta", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { showCardColorPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(cardColor(newCardColor), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("Elegir color", fontWeight = FontWeight.SemiBold)
                            Text(
                                newCardColor.uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                    cardError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val digits = newCardLast4.filter(Char::isDigit)
                    if (digits.length != 4) {
                        cardError = "Ingresa exactamente los últimos 4 dígitos."
                    } else {
                        scope.launch {
                            try {
                                if (cardToEdit == null) {
                                    container.expenseRepository.savePaymentCard(
                                        digits,
                                        newCardName,
                                        newCardColor,
                                        newCardType
                                    )
                                } else {
                                    container.expenseRepository.updatePaymentCard(
                                        digits,
                                        newCardName,
                                        newCardColor,
                                        newCardType
                                    )
                                }
                                showAddCard = false
                            } catch (exception: Exception) {
                                cardError = exception.message ?: "No se pudo guardar la tarjeta."
                            }
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAddCard = false }) { Text("Cancelar") } }
        )
    }
    if (showCardColorPicker) {
        CardColorPickerDialog(
            initialHex = newCardColor,
            recentColors = cards.mapNotNull { it.colorHex },
            onDismiss = { showCardColorPicker = false },
            onConfirm = { selected ->
                newCardColor = selected
                showCardColorPicker = false
            }
        )
    }
    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("¿Eliminar tarjeta?") },
            text = {
                Text(
                    "Se eliminará el nombre guardado para •••• ${card.last4}. Los movimientos conservarán sus últimos 4 dígitos, pero quedarán sin ese nombre."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.expenseRepository.deletePaymentCard(card.last4)
                        cardToDelete = null
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CardColorPickerDialog(
    initialHex: String,
    recentColors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val startingHex = remember(initialHex) { normalizeColorHex(initialHex) }
    val startingHsv = remember(startingHex) { colorHexToHsv(startingHex) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedHex by remember(startingHex) { mutableStateOf(startingHex) }
    var hue by remember(startingHex) { mutableFloatStateOf(startingHsv[0]) }
    var saturation by remember(startingHex) { mutableFloatStateOf(startingHsv[1]) }
    var brightness by remember(startingHex) { mutableFloatStateOf(startingHsv[2]) }

    fun selectHex(raw: String) {
        val normalized = normalizeColorHexOrNull(raw) ?: return
        val hsv = colorHexToHsv(normalized)
        selectedHex = normalized
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    fun selectHsv(nextHue: Float = hue, nextSaturation: Float = saturation, nextBrightness: Float = brightness) {
        hue = nextHue
        saturation = nextSaturation
        brightness = nextBrightness
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        selectedHex = String.format(Locale.US, "#%06X", colorInt and 0xFFFFFF)
    }

    val colorInt = android.graphics.Color.parseColor(selectedHex)
    val recent = remember(recentColors, startingHex) {
        (listOf(startingHex) + recentColors.mapNotNull(::normalizeColorHexOrNull) + cardColorOptions.map { it.hex })
            .distinct()
            .take(6)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Color de la tarjeta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(4.dp)) {
                            listOf("Muestras", "Espectro").forEachIndexed { index, title ->
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { selectedTab = index },
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(
                                        title,
                                        modifier = Modifier.padding(vertical = 13.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) MaterialTheme.colorScheme.onSurface else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTab == 0) {
                        ColorSampleGrid(selectedHex = selectedHex, onSelected = ::selectHex)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                                color = cardColor(selectedHex),
                                shape = RoundedCornerShape(16.dp)
                            ) {}
                            Text("Matiz · ${hue.toInt()}°", style = MaterialTheme.typography.labelLarge)
                            Slider(value = hue, onValueChange = { selectHsv(nextHue = it) }, valueRange = 0f..360f)
                            Text("Saturación · ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                            Slider(value = saturation, onValueChange = { selectHsv(nextSaturation = it) }, valueRange = 0f..1f)
                            Text("Brillo · ${(brightness * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                            Slider(value = brightness, onValueChange = { selectHsv(nextBrightness = it) }, valueRange = 0.08f..1f)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(cardColor(selectedHex), RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        )
                        ColorHexValue(selectedHex, Modifier.weight(1.4f))
                        ColorChannelValue("Rojo", android.graphics.Color.red(colorInt), Modifier.weight(0.7f))
                        ColorChannelValue("Verde", android.graphics.Color.green(colorInt), Modifier.weight(0.7f))
                        ColorChannelValue("Azul", android.graphics.Color.blue(colorInt), Modifier.weight(0.7f))
                    }

                    Text("Colores recientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        recent.forEach { hex ->
                            ColorCircle(
                                hex = hex,
                                selected = hex.equals(selectedHex, ignoreCase = true),
                                onClick = { selectHex(hex) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onConfirm(selectedHex) }
                        ) { Text("Aplicar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSampleGrid(selectedHex: String, onSelected: (String) -> Unit) {
    val hues = listOf<Float?>(null, 0f, 24f, 42f, 58f, 92f, 135f, 170f, 205f, 225f, 265f, 300f, 335f)
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        repeat(8) { row ->
            Row(Modifier.fillMaxWidth()) {
                hues.forEach { hue ->
                    val hex = if (hue == null) {
                        colorHsvToHex(0f, 0f, (1f - row * 0.12f).coerceAtLeast(0.08f))
                    } else {
                        colorHsvToHex(
                            hue,
                            (0.12f + row * 0.12f).coerceAtMost(0.95f),
                            (1f - (row - 3).coerceAtLeast(0) * 0.14f).coerceAtLeast(0.26f)
                        )
                    }
                    val selected = selectedHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(cardColor(hex))
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface)
                                else Modifier
                            )
                            .semantics { contentDescription = "Color $hex${if (selected) ", seleccionado" else ""}" }
                            .clickable { onSelected(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (cardColor(hex).luminance() > 0.48f) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorHexValue(hex: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hex", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hex, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ColorChannelValue(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColorCircle(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(cardColor(hex), CircleShape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                CircleShape
            )
            .semantics { contentDescription = "Color reciente $hex${if (selected) ", seleccionado" else ""}" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (cardColor(hex).luminance() > 0.48f) Color.Black else Color.White
            )
        }
    }
}

private fun normalizeColorHex(raw: String): String =
    normalizeColorHexOrNull(raw) ?: cardColorOptions.first().hex

private fun normalizeColorHexOrNull(raw: String): String? {
    val normalized = raw.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
    return normalized.takeIf { it.matches(Regex("#[0-9A-F]{6}")) }
}

private fun colorHexToHsv(hex: String): FloatArray {
    val value = android.graphics.Color.parseColor(normalizeColorHex(hex))
    return FloatArray(3).also {
        android.graphics.Color.RGBToHSV(
            android.graphics.Color.red(value),
            android.graphics.Color.green(value),
            android.graphics.Color.blue(value),
            it
        )
    }
}

private fun colorHsvToHex(hue: Float, saturation: Float, brightness: Float): String {
    val value = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    return String.format(Locale.US, "#%06X", value and 0xFFFFFF)
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(category.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar categoría") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar categoría", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    if (enabled) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp).height(16.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun PaymentCardRow(
    card: PaymentCard,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = cardColor(card.colorHex))
            Column(Modifier.weight(1f)) {
                Text(card.name?.takeIf { it.isNotBlank() } ?: "Tarjeta sin nombre", fontWeight = FontWeight.SemiBold)
                Text(
                    "${paymentCardTypeLabel(card.type)} · •••• ${card.last4}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar tarjeta") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar tarjeta", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private data class LocationDetails(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

private fun parseLocationEvidence(evidence: com.villegol.appgastos.data.Evidence): LocationDetails? {
    val raw = evidence.rawPayload
    val latitude = Regex("lat=([-+0-9.]+)").find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
    val longitude = Regex("lon=([-+0-9.]+)").find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
    val accuracy = Regex("accuracy=([-+0-9.]+)").find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
    return LocationDetails(latitude, longitude, accuracy)
}

private data class ReceiptImageState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null
)

private fun loadReceiptBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}

private suspend fun copyReceiptUri(context: android.content.Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "receipts").apply { mkdirs() }
        val target = File(directory, "receipt_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir la imagen seleccionada")
        target.absolutePath
    }

private fun enqueueReceiptOcr(context: android.content.Context, receiptId: String, imagePath: String) {
    val request = OneTimeWorkRequestBuilder<ReceiptOcrWorker>()
        .setInputData(
            workDataOf(
                ReceiptOcrWorker.RECEIPT_ID to receiptId,
                ReceiptOcrWorker.IMAGE_PATH to imagePath
            )
        )
        .addTag("receipt-ocr:$receiptId")
        .build()
    WorkManager.getInstance(context).enqueue(request)
}

private data class ReceiptReviewItem(
    val description: String,
    val quantity: Int?,
    val unitPriceMinor: Long?,
    val lineTotalMinor: Long?
)

private data class ReceiptReviewSummary(
    val merchantName: String?,
    val totalMinor: Long?,
    val currency: String,
    val category: String?,
    val date: String?,
    val receiptNumber: String?,
    val items: List<ReceiptReviewItem>
)

private fun parseReceiptReviewSummary(raw: String?): ReceiptReviewSummary? {
    val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val itemsJson = json.optJSONArray("items")
    val items = buildList {
        if (itemsJson != null) {
            for (index in 0 until itemsJson.length()) {
                val item = itemsJson.optJSONObject(index) ?: continue
                val description = item.optionalText("description") ?: continue
                add(
                    ReceiptReviewItem(
                        description = description,
                        quantity = item.optionalLong("quantity")?.toInt(),
                        unitPriceMinor = item.optionalLong("unitPriceMinor"),
                        lineTotalMinor = item.optionalLong("lineTotalMinor")
                    )
                )
            }
        }
    }
    val category = json.optJSONObject("category")?.optionalText("category")
    return ReceiptReviewSummary(
        merchantName = json.optionalText("merchantName"),
        totalMinor = json.optionalLong("totalMinor"),
        currency = json.optionalText("currency") ?: "CLP",
        category = category,
        date = json.optionalText("date"),
        receiptNumber = json.optionalText("receiptNumber"),
        items = items
    )
}

private fun JSONObject.optionalText(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optionalLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key).takeIf { it != 0L || optString(key) == "0" }

private fun formatDate(expense: Expense): String = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "CL"))
    .format(expense.occurredAt.atZone(ZoneId.systemDefault()))

private fun formatRelativeDate(expense: Expense): String {
    val dateTime = expense.occurredAt.atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now(ZoneId.systemDefault())
    val prefix = when (dateTime.toLocalDate()) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> DateTimeFormatter.ofPattern("d MMM", Locale("es", "CL")).format(dateTime)
    }
    return "$prefix, ${DateTimeFormatter.ofPattern("HH:mm").format(dateTime)}"
}

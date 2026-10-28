package com.villegol.appgastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.villegol.appgastos.data.AppContainer
import com.villegol.appgastos.ui.ExpenseDetailScreen
import com.villegol.appgastos.ui.ExpenseEditorScreen
import com.villegol.appgastos.ui.ExpensesScreen
import com.villegol.appgastos.ui.HomeScreen
import com.villegol.appgastos.ui.AnalysisScreen
import com.villegol.appgastos.ui.PendingScreen
import com.villegol.appgastos.ui.ReceiptCaptureScreen
import com.villegol.appgastos.ui.ReceiptReviewScreen
import com.villegol.appgastos.ui.SettingsScreen
import com.villegol.appgastos.ui.theme.AppGastosTheme

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = intent.toNavigationRequest()
        val container = (application as GastosApplication).container
        setContent {
            AppGastosTheme {
                GastosApp(
                    container = container,
                    initialExpenseId = navigationRequest?.expenseId,
                    initialAction = navigationRequest?.action,
                    navigationToken = navigationRequest?.token ?: 0L
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationRequest = intent.toNavigationRequest()
    }

    private fun android.content.Intent.toNavigationRequest(): NavigationRequest? {
        val expenseId = getStringExtra(EXTRA_EXPENSE_ID) ?: return null
        return NavigationRequest(
            expenseId = expenseId,
            action = getStringExtra(EXTRA_ACTION),
            token = System.nanoTime()
        )
    }

    private data class NavigationRequest(
        val expenseId: String,
        val action: String?,
        val token: Long
    )

    companion object {
        const val EXTRA_EXPENSE_ID = "expense_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_EDIT = "edit"
        const val ACTION_RECEIPT = "receipt"
    }
}

@Composable
fun GastosApp(
    container: AppContainer,
    initialExpenseId: String? = null,
    initialAction: String? = null,
    navigationToken: Long = 0L
) {
    val navController = rememberNavController()
    LaunchedEffect(initialExpenseId, initialAction, navigationToken) {
        if (initialExpenseId != null) {
            navController.navigate(
                if (initialAction == MainActivity.ACTION_RECEIPT) "receipt/$initialExpenseId/auto"
                else "edit/$initialExpenseId"
            ) { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) { container.expenseRepository.seedCategories() }

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            when (initialState.destination.route to targetState.destination.route) {
                "home" to "expenses" -> slideInHorizontally(tween(280)) { it }
                "expenses" to "home" -> slideInHorizontally(tween(280)) { -it }
                else -> EnterTransition.None
            }
        },
        exitTransition = {
            when (initialState.destination.route to targetState.destination.route) {
                "home" to "expenses" -> slideOutHorizontally(tween(280)) { -it }
                "expenses" to "home" -> slideOutHorizontally(tween(280)) { it }
                else -> ExitTransition.None
            }
        },
        popEnterTransition = {
            when (initialState.destination.route to targetState.destination.route) {
                "home" to "expenses" -> slideInHorizontally(tween(280)) { it }
                "expenses" to "home" -> slideInHorizontally(tween(280)) { -it }
                else -> EnterTransition.None
            }
        },
        popExitTransition = {
            when (initialState.destination.route to targetState.destination.route) {
                "home" to "expenses" -> slideOutHorizontally(tween(280)) { -it }
                "expenses" to "home" -> slideOutHorizontally(tween(280)) { it }
                else -> ExitTransition.None
            }
        }
    ) {
        composable("home") { MainTabScaffold(navController) { HomeScreen(container, navController) } }
        composable("expenses") { MainTabScaffold(navController) { ExpensesScreen(container, navController) } }
        composable("analysis") { MainTabScaffold(navController) { AnalysisScreen(container, navController) } }
        composable("pending") { MainTabScaffold(navController) { PendingScreen(container, navController) } }
        composable("settings") { SettingsScreen(container, navController) }
        composable("detail/{expenseId}") { entry ->
            ExpenseDetailScreen(container, entry.arguments?.getString("expenseId"), navController)
        }
        composable("edit/{expenseId}") { entry ->
            ExpenseEditorScreen(container, entry.arguments?.getString("expenseId"), navController)
        }
        composable("receipt/{expenseId}/{mode}") { entry ->
            ReceiptCaptureScreen(
                container = container,
                expenseId = entry.arguments?.getString("expenseId"),
                navController = navController,
                autoOpenCamera = entry.arguments?.getString("mode") == "auto"
            )
        }
        composable("receipt-review/{expenseId}") { entry ->
            ReceiptReviewScreen(
                container = container,
                expenseId = entry.arguments?.getString("expenseId"),
                navController = navController
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTabScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"
    var showAddOptions by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(32.dp)
                    )
                    .height(80.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = current == "home",
                    onClick = { navController.popBackStack("home", inclusive = false) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Resumen") },
                    label = { Text("Resumen") },
                    colors = modernNavigationColors()
                )
                NavigationBarItem(
                    selected = current == "expenses",
                    onClick = {
                        navController.navigate("expenses") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = "Movimientos") },
                    label = { Text("Movimientos") },
                    colors = modernNavigationColors()
                )
                NavigationBarItem(
                    selected = current == "analysis",
                    onClick = {
                        navController.navigate("analysis") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Análisis") },
                    label = { Text("Análisis") },
                    colors = modernNavigationColors()
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { showAddOptions = true },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Agregar movimiento") },
                    label = { Text("Agregar") },
                    colors = modernNavigationColors()
                )
            }
        }
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content(padding) } }

    if (showAddOptions) {
        ModalBottomSheet(onDismissRequest = { showAddOptions = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Agregar movimiento", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text(
                    "Elige cómo quieres registrarlo.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddMovementOption(
                    title = "Ingresar datos",
                    subtitle = "Crea un movimiento manualmente",
                    icon = Icons.Default.Edit,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        showAddOptions = false
                        navController.navigate("edit/new")
                    }
                )
                AddMovementOption(
                    title = "Escanear una boleta",
                    subtitle = "Usa una foto para completar los datos",
                    icon = Icons.Default.ReceiptLong,
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = {
                        showAddOptions = false
                        navController.navigate("receipt/new/manual")
                    }
                )
            }
        }
    }
}

@Composable
private fun modernNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledIconColor = Color.Transparent,
    disabledTextColor = Color.Transparent
)

@Composable
private fun AddMovementOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

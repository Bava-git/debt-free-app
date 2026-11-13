package com.debtfree.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigation(vm: LoanViewModel, notificationLoanId: Long, onNotificationHandled: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dashboard"
    val isDashboard = route == "dashboard"
    val title = when {
        isDashboard -> "Debt Free"
        route?.startsWith("loan-form") == true -> "Loan details"
        route?.startsWith("payment") == true -> "Add payment"
        route?.startsWith("closure") == true -> "Closure calculator"
        route?.startsWith("settings") == true -> "Settings"
        else -> "Loan"
    }
    LaunchedEffect(notificationLoanId) {
        if (notificationLoanId > 0) {
            navController.navigate("loan/$notificationLoanId") { launchSingleTop = true }
            onNotificationHandled()
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isDashboard) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isDashboard) {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (isDashboard) {
                FloatingActionButton(
                    onClick = { navController.navigate("loan-form") },
                    modifier = Modifier.semantics { contentDescription = "Add a loan" },
                ) { Text("+") }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding),
        ) {
            composable("dashboard") {
                DashboardScreen(
                    vm = vm,
                    onLoanClick = { navController.navigate("loan/$it") },
                    onAddLoan = { navController.navigate("loan-form") },
                )
            }
            composable("loan/{loanId}", arguments = listOf(navArgument("loanId") { type = NavType.LongType })) {
                LoanDetailScreen(
                    vm,
                    it.arguments?.getLong("loanId") ?: return@composable,
                    onEdit = { id -> navController.navigate("loan-form/$id") },
                    onPayment = { id -> navController.navigate("payment/$id") },
                    onClosure = { id -> navController.navigate("closure/$id") },
                    onDone = { navController.popBackStack() },
                )
            }
            composable("loan-form") {
                LoanFormScreen(vm, loanId = null, onSaved = { navController.popBackStack() })
            }
            composable("loan-form/{loanId}", arguments = listOf(navArgument("loanId") { type = NavType.LongType })) {
                LoanFormScreen(vm, it.arguments?.getLong("loanId"), onSaved = { navController.popBackStack() })
            }
            composable("payment/{loanId}", arguments = listOf(navArgument("loanId") { type = NavType.LongType })) {
                PaymentFormScreen(vm, it.arguments?.getLong("loanId") ?: return@composable) {
                    navController.popBackStack()
                }
            }
            composable("closure/{loanId}", arguments = listOf(navArgument("loanId") { type = NavType.LongType })) {
                ClosureScreen(vm, it.arguments?.getLong("loanId") ?: return@composable)
            }
            composable("settings") {
                SettingsScreen(vm)
            }
        }
    }
}

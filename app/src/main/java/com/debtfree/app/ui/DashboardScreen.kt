package com.debtfree.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.debtfree.app.data.LoanWithDetails
import com.debtfree.app.domain.LoanMetrics
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun DashboardScreen(
    vm: LoanViewModel,
    onLoanClick: (Long) -> Unit,
    onAddLoan: () -> Unit,
) {
    val loans by vm.activeLoans.collectAsState()
    val allLoans by vm.loans.collectAsState()
    val settings by vm.settings.collectAsState()
    val metrics = loans.mapNotNull { loan ->
        loan.loan.id to loan.metrics(settings)
    }.toMap()
    val totals = metrics.values
    val principal = totals.fold(BigDecimal.ZERO) { total, value -> total + value.closure.outstandingPrincipal }
    val interest = totals.fold(BigDecimal.ZERO) { total, value -> total + value.closure.unpaidInterest }
    val payable = totals.fold(BigDecimal.ZERO) { total, value -> total + value.closure.totalPayable }
    val attention = loans.filter { loan ->
        val metric = metrics[loan.loan.id] ?: return@filter false
        metric.isOverdue || metric.daysToMaturity in 0..7 ||
            (metric.currentLtvPercent?.let { it > settings.warnLtvAbovePercent.toBigDecimal() } == true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Track it. Repay it. Be Debt Free.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                MetricCard("Outstanding principal", money(principal), Modifier.weight(1f))
                MetricCard("Accrued interest", money(interest), Modifier.weight(1f))
            }
            MetricCard("Total payable today", money(payable), Modifier.fillMaxWidth().padding(top = 8.dp))
            EstimateDisclaimer()
            SectionTitle("Needs attention")
            if (attention.isEmpty()) {
                Text("Nothing needs attention right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                attention.forEach { loan ->
                    val metric = metrics[loan.loan.id] ?: return@forEach
                    AttentionCard(loan, metric, settings.warnLtvAbovePercent) { onLoanClick(loan.loan.id) }
                }
            }
            SectionTitle("Active loans")
        }
        if (loans.isEmpty()) {
            item {
                EmptyState(
                    "No active loans yet",
                    "Add your gold loan details to track estimated interest, maturity and loan-to-value.",
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(onClick = onAddLoan) { Text("Add your first loan") }
            }
        } else {
            items(loans, key = { it.loan.id }) { loan ->
                val metric = metrics[loan.loan.id] ?: return@items
                LoanCard(loan, metric, settings.warnLtvAbovePercent) { onLoanClick(loan.loan.id) }
            }
        }
        val closedLoans = allLoans.filter { it.status == com.debtfree.app.domain.LoanStatus.CLOSED }
        if (closedLoans.isNotEmpty()) {
            item { SectionTitle("Closed loans") }
            items(closedLoans, key = { "closed-${it.id}" }) { loan ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onLoanClick(loan.id) },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(loan.nickname, style = MaterialTheme.typography.titleMedium)
                        Text("${loan.bankName} · Closed${loan.closedDate?.let { " on ${it.displayDate()}" } ?: ""}")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LoanCard(loan: LoanWithDetails, metric: LoanMetrics, warnPercent: Int, onClick: () -> Unit) {
    val ltv = metric.currentLtvPercent
    val tint = when {
        ltv == null -> MaterialTheme.colorScheme.surfaceVariant
        ltv > warnPercent.toBigDecimal() -> Color(0xFFFFDAD6)
        ltv >= BigDecimal("65") -> Color(0xFFFFEFC2)
        else -> Color(0xFFD8F2E0)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .semantics { contentDescription = "${loan.loan.nickname}, ${money(metric.closure.totalPayable)} payable, LTV ${percentage(ltv)}" },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(loan.loan.nickname, style = MaterialTheme.typography.titleMedium)
                Surface(color = tint, shape = RoundedCornerShape(50)) {
                    Text("LTV ${percentage(ltv)}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text(loan.loan.bankName, style = MaterialTheme.typography.bodyMedium)
            Text("${money(metric.closure.totalPayable)} payable today", style = MaterialTheme.typography.bodyLarge)
            Text(maturityText(metric), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttentionCard(loan: LoanWithDetails, metric: LoanMetrics, warnPercent: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(loan.loan.nickname, style = MaterialTheme.typography.titleSmall)
            Text(buildList {
                if (metric.isOverdue) add("Overdue")
                else if (metric.daysToMaturity in 0..7) add("Due in ${metric.daysToMaturity} days")
                metric.currentLtvPercent?.let { ltv ->
                    if (ltv > warnPercent.toBigDecimal()) add("High LTV: ${percentage(ltv)}")
                }
            }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun maturityText(metric: LoanMetrics): String = when {
    metric.isOverdue -> "Overdue · maturity ${metric.closure.asOf.plusDays(metric.daysToMaturity).displayDate()}"
    metric.daysToMaturity == 0L -> "Matures today"
    else -> "${metric.daysToMaturity} days to maturity"
}

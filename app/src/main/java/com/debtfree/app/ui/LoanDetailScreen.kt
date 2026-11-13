package com.debtfree.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.debtfree.app.data.PaymentEntity
import com.debtfree.app.domain.LoanCalculator
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun LoanDetailScreen(
    vm: LoanViewModel,
    loanId: Long,
    onEdit: (Long) -> Unit,
    onPayment: (Long) -> Unit,
    onClosure: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val relation by vm.details(loanId).collectAsState(initial = null)
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var closeConfirmation by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    if (relation == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Loan not found or was deleted.") }
        return
    }
    val loan = relation!!.loan
    val metrics = relation!!.metrics(settings)
    val paymentTotal = relation!!.payments.fold(java.math.BigDecimal.ZERO) { total, payment -> total + payment.amount }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(loan.nickname, style = MaterialTheme.typography.headlineSmall)
                Text("${loan.bankName}${loan.branch.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}")
                Text("Account ${loan.loanAccountNumber.ifBlank { "Not entered" }}")
                Text("Status: ${loan.status.name.lowercase().replaceFirstChar(Char::uppercase)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Outstanding principal", money(metrics.closure.outstandingPrincipal), Modifier.weight(1f))
                    MetricCard("Unpaid interest", money(metrics.closure.unpaidInterest), Modifier.weight(1f))
                }
                MetricCard("Total payable today", money(metrics.closure.totalPayable), Modifier.fillMaxWidth())
                Text("Pending fees: ${money(metrics.closure.pendingFees)}")
                Text("Interest paid so far: ${money(metrics.interestPaidSoFar)}")
                Text("Total cost to date: ${money(metrics.costToDate)}")
                Text("Current gold value: ${money(metrics.currentGoldValue)}")
                Text("LTV at current gold value: ${percentage(metrics.currentLtvPercent)}")
                Text("LTV at bank valuation: ${percentage(metrics.bankValuationLtvPercent)}")
                metrics.maturityLtvPercent?.let { Text("Estimated LTV at maturity: ${percentage(it)}") }
                Text("Interest method: ${loan.interestMethod.name.replace('_', ' ')} · ${loan.annualInterestRate}% p.a.")
                Text(maturityText(metrics))
                EstimateDisclaimer()
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { onPayment(loanId) }, modifier = Modifier.fillMaxWidth(), enabled = loan.status.name == "ACTIVE") {
                    Text("Add payment")
                }
                OutlinedButton(onClick = { onClosure(loanId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Calculate closure amount")
                }
                OutlinedButton(onClick = {
                    val start = loan.maturityDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val end = start + 60 * 60 * 1000
                    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, "Debt Free · ${loan.nickname} maturity")
                        .putExtra(CalendarContract.Events.DESCRIPTION, "Loan maturity reminder. Estimate only – the bank's figure is final.")
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        message = "No calendar app is available on this device."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Add maturity to calendar") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onEdit(loanId) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                    if (loan.status.name == "ACTIVE") {
                        OutlinedButton(onClick = { closeConfirmation = true }, modifier = Modifier.weight(1f)) {
                            Text("Close loan")
                        }
                    }
                }
                TextButton(onClick = { deleteConfirmation = true }) { Text("Delete loan") }
                SectionTitle("Interest accrual breakdown")
                if (metrics.interestBreakdown.isEmpty()) Text("No interest has accrued yet.")
                SectionTitle("Gold items")
            }
        }
        items(metrics.interestBreakdown, key = { "${it.fromDate}-${it.toDate}-${it.principalAtStart}" }) { segment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${segment.fromDate.displayDate()} – ${segment.toDate.displayDate()}", style = MaterialTheme.typography.titleSmall)
                    Text("Principal at start: ${money(segment.principalAtStart)}")
                    if (segment.method == com.debtfree.app.domain.InterestMethod.COMPOUND_MONTHLY) {
                        Text("Unpaid interest at start: ${money(segment.unpaidInterestAtStart)}")
                    }
                    Text("Interest accrued: ${money(segment.accruedInterest)}")
                }
            }
        }
        if (relation!!.goldItems.isEmpty()) {
            item { Text("No gold items added.") }
        } else {
            items(relation!!.goldItems, key = { "gold-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.description.ifBlank { "Gold item" }, style = MaterialTheme.typography.titleSmall)
                        Text("${item.weightGrams.stripTrailingZeros().toPlainString()} g · ${item.purityCarat}K")
                        Text("Bank valuation: ${money(item.bankValuation)}")
                    }
                }
            }
        }
        item { SectionTitle("Payment history") }
        if (relation!!.payments.isEmpty()) {
            item { Text("No payments recorded.") }
        } else {
            items(relation!!.payments.sortedWith(compareByDescending<PaymentEntity> { it.date }.thenByDescending { it.id }), key = { "pay-${it.id}" }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${payment.type.name.replace('_', ' ')} · ${money(payment.amount)}", style = MaterialTheme.typography.titleSmall)
                        Text("${payment.date.displayDate()}${payment.note.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}")
                    }
                }
            }
        }
        item {
            if (loan.notes?.isNotBlank() == true) {
                SectionTitle("Notes")
                Text(loan.notes)
            }
            Text("Total payments recorded: ${money(paymentTotal)}")
            Spacer(Modifier.height(24.dp))
        }
    }

    if (closeConfirmation) {
        AlertDialog(
            onDismissRequest = { closeConfirmation = false },
            title = { Text("Close this loan?") },
            text = { Text("This marks the loan closed today. It does not contact the bank or make a payment.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        vm.repository.closeLoan(loanId, java.time.LocalDate.now())
                        closeConfirmation = false
                        onDone()
                    }
                }) { Text("Mark closed") }
            },
            dismissButton = { TextButton(onClick = { closeConfirmation = false }) { Text("Cancel") } },
        )
    }
    if (deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = false },
            title = { Text("Delete ${loan.nickname}?") },
            text = { Text("This permanently removes the loan, its gold items and payment history from this device.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        vm.repository.deleteLoan(loanId)
                        deleteConfirmation = false
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ClosureScreen(vm: LoanViewModel, loanId: Long) {
    val relation by vm.details(loanId).collectAsState(initial = null)
    val settings by vm.settings.collectAsState()
    var dateText by remember { mutableStateOf(java.time.LocalDate.now().displayDate()) }
    val date = parseDisplayDate(dateText)
    val metrics = if (relation != null && date != null && !date.isBefore(relation!!.loan.startDate)) {
        LoanCalculator.metrics(
            relation!!.loan.toTerms(),
            relation!!.domainPayments(),
            relation!!.goldItems.map { com.debtfree.app.domain.GoldItemValue(it.weightGrams, it.purityCarat, it.bankValuation) },
            com.debtfree.app.domain.GoldRates(settings.rate22K, settings.rate24K),
            date,
        )
    } else null
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Estimate your closure amount for any date.", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("As of date (DD-MM-YYYY)") },
            modifier = Modifier.fillMaxWidth(),
            isError = date == null,
        )
        if (date == null) Text("Enter a valid date in DD-MM-YYYY format.", color = MaterialTheme.colorScheme.error)
        if (date != null && relation?.loan?.startDate?.isAfter(date) == true) {
            Text("Calculation date cannot be before the loan start date.", color = MaterialTheme.colorScheme.error)
        }
        metrics?.let {
            MetricCard("Outstanding principal", money(it.closure.outstandingPrincipal), Modifier.fillMaxWidth())
            MetricCard("Unpaid accrued interest", money(it.closure.unpaidInterest), Modifier.fillMaxWidth())
            MetricCard("Pending fees", money(it.closure.pendingFees), Modifier.fillMaxWidth())
            MetricCard("Estimated closure amount", money(it.closure.totalPayable), Modifier.fillMaxWidth())
            EstimateDisclaimer()
        }
    }
}

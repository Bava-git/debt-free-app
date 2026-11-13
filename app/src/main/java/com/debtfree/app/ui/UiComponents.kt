package com.debtfree.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.debtfree.app.data.AppSettings
import com.debtfree.app.data.GoldItemEntity
import com.debtfree.app.data.LoanEntity
import com.debtfree.app.data.LoanWithDetails
import com.debtfree.app.data.PaymentEntity
import com.debtfree.app.domain.GoldItemValue
import com.debtfree.app.domain.GoldRates
import com.debtfree.app.domain.LoanCalculator
import com.debtfree.app.domain.LoanMetrics
import com.debtfree.app.domain.LoanPayment
import com.debtfree.app.domain.LoanTerms
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val inrFormatter = NumberFormat.getCurrencyInstance(
    Locale.Builder().setLanguage("en").setRegion("IN").build(),
)

fun LocalDate.displayDate(): String = format(dateFormatter)
fun parseDisplayDate(value: String): LocalDate? = runCatching {
    LocalDate.parse(value, dateFormatter)
}.getOrNull()
fun money(value: BigDecimal): String = inrFormatter.format(value)
fun percentage(value: BigDecimal?): String = value?.let { "${it.setScale(1, java.math.RoundingMode.HALF_UP)}%" } ?: "—"

fun LoanEntity.toTerms() = LoanTerms(
    principal = principal,
    annualInterestRatePercent = annualInterestRate,
    interestMethod = interestMethod,
    repaymentType = repaymentType,
    startDate = startDate,
    maturityDate = maturityDate,
    processingFee = processingFee ?: BigDecimal.ZERO,
)

fun LoanWithDetails.metrics(settings: AppSettings, date: LocalDate = LocalDate.now()): LoanMetrics =
    LoanCalculator.metrics(
        loan.toTerms(),
        payments.map { LoanPayment(it.date, it.amount, it.type) },
        goldItems.map { GoldItemValue(it.weightGrams, it.purityCarat, it.bankValuation) },
        GoldRates(settings.rate22K, settings.rate24K),
        date,
    )

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics { contentDescription = "$title: $value" },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun EstimateDisclaimer() {
    Text(
        "Estimate only – the bank's figure is final.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FieldActions(actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (label, action) ->
            OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
}

fun LoanWithDetails.domainPayments(): List<LoanPayment> = payments.map {
    LoanPayment(it.date, it.amount, it.type)
}

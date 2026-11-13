package com.debtfree.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.debtfree.app.data.GoldItemEntity
import com.debtfree.app.data.LoanEntity
import com.debtfree.app.domain.InterestMethod
import com.debtfree.app.domain.LoanStatus
import com.debtfree.app.domain.PaymentType
import com.debtfree.app.domain.RepaymentType
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class GoldItemInput(
    val description: String = "",
    val weight: String = "",
    val purity: String = "22",
    val valuation: String = "",
)

@Composable
fun LoanFormScreen(vm: LoanViewModel, loanId: Long?, onSaved: () -> Unit) {
    val detailsFlow = remember(loanId) { loanId?.let(vm::details) ?: flowOf(null) }
    val relation by detailsFlow.collectAsState(initial = null)
    val isLoaded = loanId == null || relation != null
    val scope = rememberCoroutineScope()
    var loaded by remember(loanId) { mutableStateOf(loanId == null) }
    var nickname by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(LocalDate.now().displayDate()) }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var interestMethod by remember { mutableStateOf(InterestMethod.SIMPLE_DAILY) }
    var repaymentType by remember { mutableStateOf(RepaymentType.BULLET) }
    var tenure by remember { mutableStateOf("12") }
    var maturityText by remember { mutableStateOf(LocalDate.now().plusMonths(12).displayDate()) }
    var maturityWasEdited by remember { mutableStateOf(false) }
    var emi by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf(GoldItemInput()) }

    LaunchedEffect(relation) {
        val loan = relation?.loan
        if (loan != null && !loaded) {
            nickname = loan.nickname
            bankName = loan.bankName
            branch = loan.branch
            account = loan.loanAccountNumber
            startText = loan.startDate.displayDate()
            principal = loan.principal.toPlainString()
            rate = loan.annualInterestRate.toPlainString()
            interestMethod = loan.interestMethod
            repaymentType = loan.repaymentType
            tenure = loan.tenureMonths.toString()
            maturityText = loan.maturityDate.displayDate()
            maturityWasEdited = loan.maturityDate != loan.startDate.plusMonths(loan.tenureMonths.toLong())
            emi = loan.emiAmount?.toPlainString().orEmpty()
            fee = loan.processingFee?.toPlainString().orEmpty()
            notes = loan.notes.orEmpty()
            items.clear()
            relation!!.goldItems.forEach {
                items += GoldItemInput(it.description, it.weightGrams.toPlainString(), it.purityCarat.toString(), it.bankValuation.toPlainString())
            }
            if (items.isEmpty()) items += GoldItemInput()
            loaded = true
        }
    }

    val startDate = parseDisplayDate(startText)
    val tenureMonths = tenure.toIntOrNull()
    LaunchedEffect(startDate, tenure, maturityWasEdited) {
        if (!maturityWasEdited && startDate != null && tenureMonths != null && tenureMonths >= 0) {
            maturityText = startDate.plusMonths(tenureMonths.toLong()).displayDate()
        }
    }

    if (!isLoaded || !loaded) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (loanId == null) "Enter your loan details" else "Update your loan details", style = MaterialTheme.typography.titleLarge)
                TextField("Nickname *", nickname, { nickname = it })
                TextField("Bank name *", bankName, { bankName = it })
                TextField("Branch", branch, { branch = it })
                TextField("Loan account number", account, { account = it })
                TextField("Start date (DD-MM-YYYY) *", startText, { startText = it })
                TextField("Principal borrowed (INR) *", principal, { principal = it }, numeric = true)
                TextField("Annual interest rate (%) *", rate, { rate = it }, numeric = true)
                ChoiceField("Interest method", interestMethod, InterestMethod.entries) { interestMethod = it }
                ChoiceField("Repayment type", repaymentType, RepaymentType.entries) { repaymentType = it }
                TextField("Tenure (months) *", tenure, { tenure = it }, numeric = true)
                TextField("Maturity date (DD-MM-YYYY) *", maturityText, {
                    maturityText = it
                    maturityWasEdited = true
                })
                OutlinedButton(onClick = {
                    maturityWasEdited = false
                    if (startDate != null && tenureMonths != null && tenureMonths > 0) {
                        maturityText = startDate.plusMonths(tenureMonths.toLong()).displayDate()
                    }
                }) { Text("Use start date + tenure") }
                if (repaymentType == RepaymentType.EMI) {
                    TextField("EMI amount (INR) *", emi, { emi = it }, numeric = true)
                }
                TextField("Processing fee (INR)", fee, { fee = it }, numeric = true)
                TextField("Notes", notes, { notes = it }, singleLine = false)
                if (repaymentType == RepaymentType.BULLET && startDate?.isAfter(LocalDate.of(2026, 4, 1)) == true &&
                    tenureMonths != null && tenureMonths > 12
                ) {
                    Text(
                        "Reminder: bullet loans taken after 01-04-2026 have a 12-month maximum tenure under the rule described in this app specification. Check your loan terms.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SectionTitle("Gold items")
            }
        }
        items.forEachIndexed { index, item ->
            item(key = "gold-input-$index") {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gold item ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        TextField("Description", item.description, { items[index] = item.copy(description = it) })
                        TextField("Weight (grams)", item.weight, { items[index] = item.copy(weight = it) }, numeric = true)
                        ChoiceField("Purity (carat)", item.purity.toIntOrNull() ?: 22, (1..24).toList()) {
                            items[index] = item.copy(purity = it.toString())
                        }
                        TextField("Bank valuation (INR)", item.valuation, { items[index] = item.copy(valuation = it) }, numeric = true)
                        if (items.size > 1) {
                            OutlinedButton(onClick = { items.removeAt(index) }) { Text("Remove item") }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { items += GoldItemInput() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add another gold item")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val parsedPrincipal = principal.toBigDecimalOrNull()
                        val parsedRate = rate.toBigDecimalOrNull()
                        val parsedFee = fee.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
                        val parsedEmi = emi.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
                        val maturity = parseDisplayDate(maturityText)
                        val parsedTenure = tenure.toIntOrNull()
                        val goldRows = items.mapNotNull { input ->
                            if (input.weight.isBlank() && input.valuation.isBlank() && input.description.isBlank()) null
                            else {
                                val weight = input.weight.toBigDecimalOrNull()
                                val valuation = input.valuation.toBigDecimalOrNull()
                                val purity = input.purity.toIntOrNull()
                                if (weight == null || valuation == null || purity == null || weight < BigDecimal.ZERO ||
                                    valuation < BigDecimal.ZERO || purity !in 1..24
                                ) null
                                else GoldItemEntity(
                                    loanId = loanId ?: 0,
                                    description = input.description.trim(),
                                    weightGrams = weight,
                                    purityCarat = purity,
                                    bankValuation = valuation,
                                )
                            }
                        }
                        val hasPartialGoldRow = items.any {
                            (it.weight.isNotBlank() || it.valuation.isNotBlank() || it.description.isNotBlank()) &&
                                (it.weight.toBigDecimalOrNull() == null || it.valuation.toBigDecimalOrNull() == null ||
                                    (it.purity.toIntOrNull() ?: -1) !in 1..24 ||
                                    (it.weight.toBigDecimalOrNull() ?: BigDecimal("-1")) < BigDecimal.ZERO ||
                                    (it.valuation.toBigDecimalOrNull() ?: BigDecimal("-1")) < BigDecimal.ZERO)
                        }
                        val problem = when {
                            nickname.isBlank() -> "Enter a loan nickname."
                            bankName.isBlank() -> "Enter the bank name."
                            startDate == null -> "Enter a valid start date in DD-MM-YYYY format."
                            startDate.isAfter(LocalDate.now()) -> "Loan start date cannot be in the future."
                            parsedPrincipal == null || parsedPrincipal <= BigDecimal.ZERO -> "Principal must be greater than zero."
                            parsedRate == null || parsedRate < BigDecimal.ZERO -> "Enter a valid non-negative annual interest rate."
                            parsedTenure == null || parsedTenure <= 0 -> "Tenure must be a positive number of months."
                            maturity == null || maturity.isBefore(startDate) -> "Maturity date must be valid and on or after the start date."
                            repaymentType == RepaymentType.EMI && (parsedEmi == null || parsedEmi <= BigDecimal.ZERO) -> "Enter a positive EMI amount."
                            fee.isNotBlank() && (parsedFee == null || parsedFee < BigDecimal.ZERO) -> "Processing fee cannot be negative."
                            hasPartialGoldRow -> "Complete each gold item and use non-negative weight and valuation."
                            else -> null
                        }
                        if (problem != null) {
                            error = problem
                        } else {
                            error = null
                            val old = relation?.loan
                            val loan = LoanEntity(
                                id = old?.id ?: 0,
                                nickname = nickname.trim(),
                                bankName = bankName.trim(),
                                branch = branch.trim(),
                                loanAccountNumber = account.trim(),
                                startDate = startDate!!,
                                principal = parsedPrincipal!!,
                                annualInterestRate = parsedRate!!,
                                interestMethod = interestMethod,
                                repaymentType = repaymentType,
                                tenureMonths = parsedTenure!!,
                                maturityDate = maturity!!,
                                emiAmount = parsedEmi,
                                processingFee = parsedFee,
                                status = old?.status ?: LoanStatus.ACTIVE,
                                closedDate = old?.closedDate,
                                notes = notes.trim().takeIf(String::isNotBlank),
                            )
                            scope.launch {
                                try {
                                    vm.saveLoan(loan, goldRows)
                                    onSaved()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = "Could not save this loan: ${failure.localizedMessage ?: "unknown error"}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save loan") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PaymentFormScreen(vm: LoanViewModel, loanId: Long, onSaved: () -> Unit) {
    val relation by vm.details(loanId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var dateText by remember { mutableStateOf(LocalDate.now().displayDate()) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PaymentType.INTEREST) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(relation?.loan?.nickname ?: "Loan payment", style = MaterialTheme.typography.titleLarge)
        TextField("Payment date (DD-MM-YYYY) *", dateText, { dateText = it })
        TextField("Amount (INR) *", amount, { amount = it }, numeric = true)
        ChoiceField("Payment type", type, PaymentType.entries) { type = it }
        TextField("Note", note, { note = it }, singleLine = false)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val date = parseDisplayDate(dateText)
            val value = amount.toBigDecimalOrNull()
            val loanStart = relation?.loan?.startDate
            error = when {
                relation == null -> "Loan could not be loaded."
                date == null -> "Enter a valid date in DD-MM-YYYY format."
                loanStart != null && date.isBefore(loanStart) -> "Payment date cannot be before the loan start date."
                value == null || value <= BigDecimal.ZERO -> "Payment amount must be greater than zero."
                else -> null
            }
            if (error == null && date != null && value != null) {
                scope.launch {
                    try {
                        vm.savePayment(loanId, date, value, type, note.trim())
                        onSaved()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = "Could not save payment: ${failure.localizedMessage ?: "unknown error"}"
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save payment") }
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
private fun <T> ChoiceField(label: String, selected: T, options: List<T>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected.toString().replace('_', ' ')}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString().replace('_', ' ')) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

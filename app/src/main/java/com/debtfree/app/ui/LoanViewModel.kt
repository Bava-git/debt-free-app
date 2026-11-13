package com.debtfree.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.debtfree.app.data.GoldItemEntity
import com.debtfree.app.data.LoanEntity
import com.debtfree.app.data.LoanRepository
import com.debtfree.app.data.LoanWithDetails
import com.debtfree.app.data.PaymentEntity
import com.debtfree.app.data.AppSettings
import com.debtfree.app.domain.InterestMethod
import com.debtfree.app.domain.LoanStatus
import com.debtfree.app.domain.PaymentType
import com.debtfree.app.domain.RepaymentType
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LoanViewModel(val repository: LoanRepository) : ViewModel() {
    val loans = repository.loans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeLoans = repository.activeLoanDetails.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repository.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val settingsLoaded = repository.settings.settings.map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun details(id: Long) = repository.loanWithDetails(id)

    suspend fun saveLoan(loan: LoanEntity, items: List<GoldItemEntity>): Long {
        return if (loan.id == 0L) repository.addLoan(loan, items) else {
            repository.updateLoan(loan, items)
            loan.id
        }
    }

    suspend fun savePayment(loanId: Long, date: LocalDate, amount: BigDecimal, type: PaymentType, note: String) {
        repository.addPayment(PaymentEntity(loanId = loanId, date = date, amount = amount, type = type, note = note))
    }

    suspend fun addDemoData() {
        val existingNames = repository.allLoansWithDetails().map { it.loan.nickname }.toSet()
        val today = LocalDate.now()
        if ("Demo · SBI Gold Loan" !in existingNames) repository.addLoan(
            LoanEntity(
                nickname = "Demo · SBI Gold Loan",
                bankName = "Example Bank",
                branch = "Demo branch",
                loanAccountNumber = "DEMO-001",
                startDate = today.minusMonths(5),
                principal = BigDecimal("150000"),
                annualInterestRate = BigDecimal("10.5"),
                interestMethod = InterestMethod.SIMPLE_MONTHLY,
                repaymentType = RepaymentType.BULLET,
                tenureMonths = 12,
                maturityDate = today.plusMonths(7),
                emiAmount = null,
                processingFee = BigDecimal("500"),
                notes = "Fictional demo data. Replace with your own loan details.",
            ),
            listOf(
                GoldItemEntity(
                    loanId = 0,
                    description = "Demo bangles",
                    weightGrams = BigDecimal("35"),
                    purityCarat = 22,
                    bankValuation = BigDecimal("245000"),
                ),
            ),
        )
        if ("Demo · Co-operative Bank" !in existingNames) repository.addLoan(
            LoanEntity(
                nickname = "Demo · Co-operative Bank",
                bankName = "Example Co-operative Bank",
                branch = "Demo branch",
                loanAccountNumber = "DEMO-002",
                startDate = today.minusMonths(2),
                principal = BigDecimal("80000"),
                annualInterestRate = BigDecimal("11"),
                interestMethod = InterestMethod.SIMPLE_DAILY,
                repaymentType = RepaymentType.INTEREST_ONLY,
                tenureMonths = 6,
                maturityDate = today.plusMonths(4),
                emiAmount = null,
                processingFee = null,
                notes = "Fictional demo data. Replace with your own loan details.",
            ),
            listOf(
                GoldItemEntity(
                    loanId = 0,
                    description = "Demo necklace",
                    weightGrams = BigDecimal("18"),
                    purityCarat = 22,
                    bankValuation = BigDecimal("126000"),
                ),
            ),
        )
    }
}

class LoanViewModelFactory(private val repository: LoanRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LoanViewModel::class.java))
        return LoanViewModel(repository) as T
    }
}

package com.debtfree.app.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class InterestMethod { SIMPLE_DAILY, SIMPLE_MONTHLY, COMPOUND_MONTHLY }
enum class RepaymentType { BULLET, INTEREST_ONLY, EMI }
enum class PaymentType { INTEREST, PRINCIPAL, EMI, FEE, OTHER }
enum class LoanStatus { ACTIVE, CLOSED }

data class LoanTerms(
    val principal: BigDecimal,
    val annualInterestRatePercent: BigDecimal,
    val interestMethod: InterestMethod,
    val repaymentType: RepaymentType,
    val startDate: LocalDate,
    val maturityDate: LocalDate,
    val processingFee: BigDecimal = BigDecimal.ZERO,
)

data class LoanPayment(
    val date: LocalDate,
    val amount: BigDecimal,
    val type: PaymentType,
)

data class GoldItemValue(
    val weightGrams: BigDecimal,
    val purityCarat: Int,
    val bankValuation: BigDecimal,
)

data class GoldRates(
    val rate22KPerGram: BigDecimal,
    val rate24KPerGram: BigDecimal,
)

data class ClosureBreakdown(
    val asOf: LocalDate,
    val outstandingPrincipal: BigDecimal,
    val unpaidInterest: BigDecimal,
    val pendingFees: BigDecimal,
) {
    val totalPayable: BigDecimal
        get() = outstandingPrincipal + unpaidInterest + pendingFees
}

data class LoanMetrics(
    val closure: ClosureBreakdown,
    val interestBreakdown: List<InterestAccrualSegment>,
    val interestPaidSoFar: BigDecimal,
    val costToDate: BigDecimal,
    val currentGoldValue: BigDecimal,
    val currentLtvPercent: BigDecimal?,
    val bankValuationLtvPercent: BigDecimal?,
    val maturityLtvPercent: BigDecimal?,
    val daysToMaturity: Long,
    val isOverdue: Boolean,
)

data class InterestAccrualSegment(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val principalAtStart: BigDecimal,
    val unpaidInterestAtStart: BigDecimal,
    val accruedInterest: BigDecimal,
    val method: InterestMethod,
)

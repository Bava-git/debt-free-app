package com.debtfree.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object LoanCalculator {
    private val mc = MathContext.DECIMAL128
    private val hundred = BigDecimal("100")
    private val twelve = BigDecimal("12")
    private val thirty = BigDecimal("30")
    private val daysPerYear = BigDecimal("365")
    private val zero = BigDecimal.ZERO

    private data class CalculationState(
        val closure: ClosureBreakdown,
        val interestPaid: BigDecimal,
        val interestBreakdown: List<InterestAccrualSegment>,
    )

    fun closure(loan: LoanTerms, payments: List<LoanPayment>, asOf: LocalDate): ClosureBreakdown =
        calculate(loan, payments, asOf).closure

    private fun calculate(loan: LoanTerms, payments: List<LoanPayment>, asOf: LocalDate): CalculationState {
        require(loan.principal >= zero) { "Principal cannot be negative" }
        require(loan.annualInterestRatePercent >= zero) { "Interest rate cannot be negative" }
        require(loan.processingFee >= zero) { "Processing fee cannot be negative" }
        require(!loan.maturityDate.isBefore(loan.startDate)) { "Maturity date cannot precede start date" }
        require(!asOf.isBefore(loan.startDate)) { "Calculation date cannot precede loan start date" }

        var principal = loan.principal
        var unpaidInterest = zero
        var pendingFees = loan.processingFee
        var interestPaid = zero
        var cursor = loan.startDate
        val breakdown = mutableListOf<InterestAccrualSegment>()

        payments.asSequence()
            .filter { !it.date.isAfter(asOf) }
            .sortedBy { it.date }
            .forEach { payment ->
                require(!payment.date.isBefore(loan.startDate)) { "Payment date cannot precede loan start date" }
                require(payment.amount >= zero) { "Payment cannot be negative" }
                val accrualDate = if (payment.date.isAfter(cursor)) payment.date else cursor
                val interestBefore = unpaidInterest
                val accrued = accrue(loan, principal, unpaidInterest, cursor, accrualDate)
                unpaidInterest += accrued
                if (accrualDate.isAfter(cursor)) {
                    breakdown += InterestAccrualSegment(
                        fromDate = cursor,
                        toDate = accrualDate,
                        principalAtStart = principal,
                        unpaidInterestAtStart = interestBefore,
                        accruedInterest = accrued,
                        method = loan.interestMethod,
                    )
                }
                cursor = accrualDate

                when (payment.type) {
                    PaymentType.INTEREST -> {
                        val applied = payment.amount.min(unpaidInterest)
                        unpaidInterest -= applied
                        interestPaid += applied
                    }
                    PaymentType.PRINCIPAL -> principal = (principal - payment.amount).max(zero)
                    PaymentType.EMI -> {
                        val interestPart = payment.amount.min(unpaidInterest)
                        unpaidInterest -= interestPart
                        interestPaid += interestPart
                        principal = (principal - (payment.amount - interestPart)).max(zero)
                    }
                    PaymentType.FEE -> pendingFees = (pendingFees - payment.amount).max(zero)
                    PaymentType.OTHER -> Unit
                }
            }

        val interestBefore = unpaidInterest
        val accrued = accrue(loan, principal, unpaidInterest, cursor, asOf)
        unpaidInterest += accrued
        if (asOf.isAfter(cursor)) {
            breakdown += InterestAccrualSegment(
                fromDate = cursor,
                toDate = asOf,
                principalAtStart = principal,
                unpaidInterestAtStart = interestBefore,
                accruedInterest = accrued,
                method = loan.interestMethod,
            )
        }
        return CalculationState(
            closure = ClosureBreakdown(
                asOf = asOf,
                outstandingPrincipal = principal,
                unpaidInterest = unpaidInterest,
                pendingFees = pendingFees,
            ),
            interestPaid = interestPaid,
            interestBreakdown = breakdown,
        )
    }

    fun metrics(
        loan: LoanTerms,
        payments: List<LoanPayment>,
        goldItems: List<GoldItemValue>,
        rates: GoldRates,
        asOf: LocalDate,
    ): LoanMetrics {
        val currentState = calculate(loan, payments, asOf)
        val current = currentState.closure
        val bankValue = goldItems.fold(zero) { total, item -> total + item.bankValuation }
        val currentGold = goldValue(goldItems, rates)
        val maturity = calculate(loan, payments, loan.maturityDate).closure
        val days = ChronoUnit.DAYS.between(asOf, loan.maturityDate)

        return LoanMetrics(
            closure = current,
            interestBreakdown = currentState.interestBreakdown,
            interestPaidSoFar = currentState.interestPaid,
            costToDate = currentState.interestPaid + loan.processingFee,
            currentGoldValue = currentGold,
            currentLtvPercent = percentage(current.totalPayable, currentGold),
            bankValuationLtvPercent = percentage(current.totalPayable, bankValue),
            maturityLtvPercent = if (loan.repaymentType == RepaymentType.BULLET) {
                percentage(maturity.outstandingPrincipal + maturity.unpaidInterest, currentGold)
            } else null,
            daysToMaturity = days,
            isOverdue = asOf.isAfter(loan.maturityDate),
        )
    }

    fun goldValue(items: List<GoldItemValue>, rates: GoldRates): BigDecimal =
        items.fold(zero) { total, item ->
            require(item.weightGrams >= zero) { "Gold weight cannot be negative" }
            require(item.purityCarat in 1..24) { "Gold purity must be between 1 and 24 carats" }
            val perGram = when (item.purityCarat) {
                22 -> rates.rate22KPerGram
                24 -> rates.rate24KPerGram
                else -> rates.rate24KPerGram.multiply(
                    BigDecimal(item.purityCarat).divide(BigDecimal("24"), mc),
                )
            }
            total + item.weightGrams.multiply(perGram, mc)
        }

    private fun percentage(amount: BigDecimal, base: BigDecimal): BigDecimal? =
        if (base <= zero) null else amount.multiply(hundred, mc).divide(base, mc)

    private fun accrue(
        loan: LoanTerms,
        principal: BigDecimal,
        unpaidInterest: BigDecimal,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal {
        if (!to.isAfter(from) || principal <= zero) return zero
        val annualFraction = loan.annualInterestRatePercent.divide(hundred, mc)
        return when (loan.interestMethod) {
            InterestMethod.SIMPLE_DAILY -> principal
                .multiply(annualFraction, mc)
                .multiply(BigDecimal(ChronoUnit.DAYS.between(from, to)), mc)
                .divide(daysPerYear, mc)
            InterestMethod.SIMPLE_MONTHLY -> monthlyAccrual(
                base = principal,
                annualFraction = annualFraction,
                from = from,
                to = to,
                compound = false,
                initialInterest = zero,
            )
            InterestMethod.COMPOUND_MONTHLY -> monthlyAccrual(
                base = principal,
                annualFraction = annualFraction,
                from = from,
                to = to,
                compound = true,
                initialInterest = unpaidInterest,
            )
        }
    }

    private fun monthlyAccrual(
        base: BigDecimal,
        annualFraction: BigDecimal,
        from: LocalDate,
        to: LocalDate,
        compound: Boolean,
        initialInterest: BigDecimal,
    ): BigDecimal {
        var fullMonths = 0L
        while (!from.plusMonths(fullMonths + 1).isAfter(to)) fullMonths++

        var interest = if (compound) initialInterest else zero
        repeat(fullMonths.toInt()) {
            val monthlyBase = if (compound) base + interest else base
            interest += monthlyBase.multiply(annualFraction, mc).divide(twelve, mc)
        }

        val completedDate = from.plusMonths(fullMonths)
        val partialDays = ChronoUnit.DAYS.between(completedDate, to)
        if (partialDays > 0) {
            val partialBase = if (compound) base + interest else base
            val partial = partialBase
                .multiply(annualFraction, mc)
                .divide(twelve, mc)
                .multiply(BigDecimal(partialDays), mc)
                .divide(thirty, mc)
            interest += partial
        }
        return if (compound) interest - initialInterest else interest
    }
}

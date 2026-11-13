package com.debtfree.app.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanCalculatorTest {
    private val start = LocalDate.of(2024, 1, 1)
    private fun loan(method: InterestMethod, principal: String = "120000") = LoanTerms(
        principal = principal.bd(),
        annualInterestRatePercent = "12".bd(),
        interestMethod = method,
        repaymentType = RepaymentType.BULLET,
        startDate = start,
        maturityDate = start.plusMonths(12),
    )

    @Test fun zeroPaymentsAccrueInterest() {
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_DAILY), emptyList(), start.plusDays(365))
        assertEquals(14400.0, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun principalPartPaymentChangesLaterAccrual() {
        val payment = LoanPayment(start.plusMonths(6), "30000".bd(), PaymentType.PRINCIPAL)
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_MONTHLY), listOf(payment), start.plusMonths(12))
        assertEquals(90000.0, result.outstandingPrincipal.toDouble(), 0.01)
        assertEquals(12600.0, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun interestOnlyPaymentReducesUnpaidInterest() {
        val payment = LoanPayment(start.plusMonths(6), "7200".bd(), PaymentType.INTEREST)
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_MONTHLY), listOf(payment), start.plusMonths(12))
        assertEquals(7200.0, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun emiPaysInterestBeforeReducingPrincipal() {
        val date = start.plusMonths(1)
        val payment = LoanPayment(date, "1500".bd(), PaymentType.EMI)
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_MONTHLY), listOf(payment), date)
        assertEquals(119700.0, result.outstandingPrincipal.toDouble(), 0.01)
        assertEquals(0.0, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun earlyClosureUsesChosenDate() {
        val asOf = start.plusMonths(3)
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_MONTHLY), emptyList(), asOf)
        assertEquals(3600.0, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun overdueLoansAreFlagged() {
        val result = LoanCalculator.metrics(
            loan(InterestMethod.SIMPLE_DAILY),
            emptyList(),
            emptyList(),
            GoldRates("0".bd(), "0".bd()),
            start.plusMonths(13),
        )
        assertTrue(result.isOverdue)
        assertTrue(result.daysToMaturity < 0)
    }

    @Test fun leapYearDailyAccrualUsesElapsedDaysOver365() {
        val terms = loan(InterestMethod.SIMPLE_DAILY).copy(
            startDate = LocalDate.of(2024, 1, 1),
            maturityDate = LocalDate.of(2025, 1, 1),
        )
        val result = LoanCalculator.closure(terms, emptyList(), terms.maturityDate)
        assertEquals(14439.4521, result.unpaidInterest.toDouble(), 0.01)
    }

    @Test fun simpleMonthlyAndCompoundMonthlyAreDifferent() {
        val asOf = start.plusMonths(12)
        val simple = LoanCalculator.closure(loan(InterestMethod.SIMPLE_MONTHLY), emptyList(), asOf)
        val compound = LoanCalculator.closure(loan(InterestMethod.COMPOUND_MONTHLY), emptyList(), asOf)
        assertEquals(14400.0, simple.unpaidInterest.toDouble(), 0.01)
        assertTrue(compound.unpaidInterest > simple.unpaidInterest)
    }

    @Test fun sameDayPaymentDoesNotAccrueAnExtraDay() {
        val payment = LoanPayment(start.plusDays(10), "100".bd(), PaymentType.INTEREST)
        val result = LoanCalculator.closure(loan(InterestMethod.SIMPLE_DAILY), listOf(payment), payment.date)
        assertFalse(result.unpaidInterest < BigDecimal.ZERO)
        assertEquals(294.52, result.unpaidInterest.toDouble(), 0.02)
    }

    private fun String.bd() = BigDecimal(this)
}

package com.debtfree.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.app.Application
import com.debtfree.app.domain.InterestMethod
import com.debtfree.app.domain.PaymentType
import com.debtfree.app.domain.RepaymentType
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class DatabaseTestApplication : Application()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DatabaseTestApplication::class)
class LoanRelationsTest {
    private lateinit var database: DebtFreeDatabase
    private lateinit var dao: LoanDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DebtFreeDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.loanDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun loanRelationsLoadAndChildrenCascadeOnDelete() = runBlocking {
        val start = LocalDate.of(2026, 1, 15)
        val loanId = dao.insertLoan(
            LoanEntity(
                nickname = "Test gold loan",
                bankName = "Test bank",
                branch = "Main",
                loanAccountNumber = "1234",
                startDate = start,
                principal = BigDecimal("100000"),
                annualInterestRate = BigDecimal("10.5"),
                interestMethod = InterestMethod.SIMPLE_MONTHLY,
                repaymentType = RepaymentType.BULLET,
                tenureMonths = 12,
                maturityDate = start.plusMonths(12),
                emiAmount = null,
                processingFee = BigDecimal("100"),
            ),
        )
        dao.insertGoldItem(
            GoldItemEntity(
                loanId = loanId,
                description = "Necklace",
                weightGrams = BigDecimal("22.5"),
                purityCarat = 22,
                bankValuation = BigDecimal("150000"),
            ),
        )
        dao.insertPayment(PaymentEntity(
            loanId = loanId,
            date = start.plusMonths(1),
            amount = BigDecimal("900"),
            type = PaymentType.INTEREST,
            note = "First month",
        ))

        val loaded = dao.observeLoanWithDetails(loanId).first()
        assertEquals("Test gold loan", loaded?.loan?.nickname)
        assertEquals(1, loaded?.goldItems?.size)
        assertEquals(BigDecimal("22.5"), loaded?.goldItems?.single()?.weightGrams)
        assertEquals(PaymentType.INTEREST, loaded?.payments?.single()?.type)

        dao.deleteLoan(loanId)
        assertNull(dao.observeLoanWithDetails(loanId).first())
        assertEquals(0, dao.countGoldItems(loanId))
        assertEquals(0, dao.countPayments(loanId))
    }
}

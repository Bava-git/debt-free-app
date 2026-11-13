package com.debtfree.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class LoanRepository(
    private val database: DebtFreeDatabase,
    val settings: SettingsRepository,
) {
    private val dao = database.loanDao()

    val loans: Flow<List<LoanEntity>> = dao.observeLoans()
    val activeLoans: Flow<List<LoanEntity>> = dao.observeActiveLoans()
    val activeLoanDetails: Flow<List<LoanWithDetails>> = dao.observeActiveWithDetails()

    fun loanWithDetails(id: Long): Flow<LoanWithDetails?> = dao.observeLoanWithDetails(id)
    suspend fun getLoan(id: Long): LoanEntity? = dao.getLoan(id)
    suspend fun allLoansWithDetails(): List<LoanWithDetails> = dao.getAllWithDetails()

    suspend fun addLoan(loan: LoanEntity, items: List<GoldItemEntity>): Long =
        database.withTransaction {
            val id = dao.insertLoan(loan)
            if (items.isNotEmpty()) dao.insertGoldItems(items.map { it.copy(loanId = id, id = 0) })
            id
        }

    suspend fun updateLoan(loan: LoanEntity, items: List<GoldItemEntity>) {
        database.withTransaction {
            dao.updateLoan(loan)
            dao.deleteGoldItems(loan.id)
            if (items.isNotEmpty()) dao.insertGoldItems(items.map { it.copy(loanId = loan.id, id = 0) })
        }
    }

    suspend fun addPayment(payment: PaymentEntity) {
        dao.insertPayment(payment)
    }

    suspend fun closeLoan(id: Long, date: java.time.LocalDate) {
        dao.getLoan(id)?.let { dao.updateLoan(it.copy(status = com.debtfree.app.domain.LoanStatus.CLOSED, closedDate = date)) }
    }

    suspend fun deleteLoan(id: Long) = dao.deleteLoan(id)

    suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAllPayments()
            dao.deleteAllGoldItems()
            dao.deleteAllLoans()
        }
    }

    suspend fun restoreLoans(loans: List<LoanEntity>, items: List<GoldItemEntity>, payments: List<PaymentEntity>) {
        database.withTransaction {
            clearAll()
            loans.forEach { loan ->
                val oldId = loan.id
                val newId = dao.insertLoan(loan.copy(id = 0))
                items.filter { it.loanId == oldId }.forEach { dao.insertGoldItem(it.copy(id = 0, loanId = newId)) }
                payments.filter { it.loanId == oldId }.forEach { dao.insertPayment(it.copy(id = 0, loanId = newId)) }
            }
        }
    }
}

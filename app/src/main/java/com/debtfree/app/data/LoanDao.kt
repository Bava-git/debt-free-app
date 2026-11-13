package com.debtfree.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY status, maturityDate")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY maturityDate")
    fun observeActiveLoans(): Flow<List<LoanEntity>>

    @Transaction
    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY maturityDate")
    fun observeActiveWithDetails(): Flow<List<LoanWithDetails>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): LoanEntity?

    @Transaction
    @Query("SELECT * FROM loans WHERE id = :id")
    fun observeLoanWithDetails(id: Long): Flow<LoanWithDetails?>

    @Transaction
    @Query("SELECT * FROM loans ORDER BY id")
    suspend fun getAllWithDetails(): List<LoanWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Insert
    suspend fun insertGoldItems(items: List<GoldItemEntity>)

    @Insert
    suspend fun insertGoldItem(item: GoldItemEntity): Long

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("DELETE FROM gold_items WHERE loanId = :loanId")
    suspend fun deleteGoldItems(loanId: Long)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM gold_items")
    suspend fun deleteAllGoldItems()

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()

    @Query("SELECT * FROM payments ORDER BY date, id")
    suspend fun getAllPayments(): List<PaymentEntity>

    @Query("SELECT COUNT(*) FROM gold_items WHERE loanId = :loanId")
    suspend fun countGoldItems(loanId: Long): Int

    @Query("SELECT COUNT(*) FROM payments WHERE loanId = :loanId")
    suspend fun countPayments(loanId: Long): Int
}

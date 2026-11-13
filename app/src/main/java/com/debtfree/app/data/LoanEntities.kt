package com.debtfree.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.debtfree.app.domain.InterestMethod
import com.debtfree.app.domain.LoanStatus
import com.debtfree.app.domain.PaymentType
import com.debtfree.app.domain.RepaymentType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val bankName: String,
    val branch: String,
    val loanAccountNumber: String,
    val startDate: LocalDate,
    val principal: BigDecimal,
    val annualInterestRate: BigDecimal,
    val interestMethod: InterestMethod,
    val repaymentType: RepaymentType,
    val tenureMonths: Int,
    val maturityDate: LocalDate,
    val emiAmount: BigDecimal?,
    val processingFee: BigDecimal?,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val closedDate: LocalDate? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "gold_items",
    foreignKeys = [ForeignKey(
        entity = LoanEntity::class,
        parentColumns = ["id"],
        childColumns = ["loanId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("loanId")],
)
data class GoldItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val description: String,
    val weightGrams: BigDecimal,
    val purityCarat: Int,
    val bankValuation: BigDecimal,
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = LoanEntity::class,
        parentColumns = ["id"],
        childColumns = ["loanId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("loanId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val date: LocalDate,
    val amount: BigDecimal,
    val type: PaymentType,
    val note: String = "",
)

data class LoanWithDetails(
    @Embedded val loan: LoanEntity,
    @Relation(parentColumn = "id", entityColumn = "loanId") val goldItems: List<GoldItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "loanId") val payments: List<PaymentEntity>,
)

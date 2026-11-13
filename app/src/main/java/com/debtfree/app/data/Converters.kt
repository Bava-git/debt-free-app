package com.debtfree.app.data

import androidx.room.TypeConverter
import com.debtfree.app.domain.InterestMethod
import com.debtfree.app.domain.LoanStatus
import com.debtfree.app.domain.PaymentType
import com.debtfree.app.domain.RepaymentType
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun decimalToString(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun stringToDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
    @TypeConverter fun interestToString(value: InterestMethod?): String? = value?.name
    @TypeConverter fun stringToInterest(value: String?): InterestMethod? = value?.let(InterestMethod::valueOf)
    @TypeConverter fun repaymentToString(value: RepaymentType?): String? = value?.name
    @TypeConverter fun stringToRepayment(value: String?): RepaymentType? = value?.let(RepaymentType::valueOf)
    @TypeConverter fun paymentToString(value: PaymentType?): String? = value?.name
    @TypeConverter fun stringToPayment(value: String?): PaymentType? = value?.let(PaymentType::valueOf)
    @TypeConverter fun statusToString(value: LoanStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): LoanStatus? = value?.let(LoanStatus::valueOf)
}

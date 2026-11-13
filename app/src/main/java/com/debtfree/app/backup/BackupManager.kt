package com.debtfree.app.backup

import android.content.ContentResolver
import android.net.Uri
import com.debtfree.app.data.AppSettings
import com.debtfree.app.data.GoldItemEntity
import com.debtfree.app.data.LoanEntity
import com.debtfree.app.data.LoanRepository
import com.debtfree.app.data.PaymentEntity
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val resolver: ContentResolver,
    private val repository: LoanRepository,
) {
    suspend fun export(uri: Uri, settings: AppSettings) {
        val loans = repository.allLoansWithDetails()
        val root = JSONObject()
            .put("formatVersion", 1)
            .put("exportedAt", LocalDate.now().toString())
            .put("settings", settingsJson(settings.copy(lastBackupExport = LocalDate.now())))
        val loanArray = JSONArray()
        loans.forEach { relation ->
            val loan = relation.loan
            loanArray.put(JSONObject()
                .put("id", loan.id)
                .put("nickname", loan.nickname)
                .put("bankName", loan.bankName)
                .put("branch", loan.branch)
                .put("loanAccountNumber", loan.loanAccountNumber)
                .put("startDate", loan.startDate.toString())
                .put("principal", loan.principal.toPlainString())
                .put("annualInterestRate", loan.annualInterestRate.toPlainString())
                .put("interestMethod", loan.interestMethod.name)
                .put("repaymentType", loan.repaymentType.name)
                .put("tenureMonths", loan.tenureMonths)
                .put("maturityDate", loan.maturityDate.toString())
                .put("emiAmount", loan.emiAmount?.toPlainString())
                .put("processingFee", loan.processingFee?.toPlainString())
                .put("status", loan.status.name)
                .put("closedDate", loan.closedDate?.toString())
                .put("notes", loan.notes)
                .put("goldItems", JSONArray().apply {
                    relation.goldItems.forEach { item ->
                        put(JSONObject()
                            .put("description", item.description)
                            .put("weightGrams", item.weightGrams.toPlainString())
                            .put("purityCarat", item.purityCarat)
                            .put("bankValuation", item.bankValuation.toPlainString()))
                    }
                })
                .put("payments", JSONArray().apply {
                    relation.payments.forEach { payment ->
                        put(JSONObject()
                            .put("date", payment.date.toString())
                            .put("amount", payment.amount.toPlainString())
                            .put("type", payment.type.name)
                            .put("note", payment.note))
                    }
                }))
        }
        root.put("loans", loanArray)
        val stream = resolver.openOutputStream(uri) ?: throw IOException("Could not open the selected backup file")
        stream.bufferedWriter().use { it.write(root.toString(2)) }
    }

    suspend fun import(uri: Uri, onSettings: suspend (AppSettings) -> Unit) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the selected backup file")
        val root = input.bufferedReader().use { JSONObject(it.readText()) }
        require(root.optInt("formatVersion") == 1) { "Unsupported backup file version" }
        val restoredLoans = mutableListOf<LoanEntity>()
        val restoredItems = mutableListOf<GoldItemEntity>()
        val restoredPayments = mutableListOf<PaymentEntity>()
        val loanJson = root.getJSONArray("loans")
        for (i in 0 until loanJson.length()) {
            val entry = loanJson.getJSONObject(i)
            val id = entry.getLong("id")
            restoredLoans += LoanEntity(
                id = id,
                nickname = entry.getString("nickname"),
                bankName = entry.getString("bankName"),
                branch = entry.getString("branch"),
                loanAccountNumber = entry.getString("loanAccountNumber"),
                startDate = LocalDate.parse(entry.getString("startDate")),
                principal = entry.decimal("principal"),
                annualInterestRate = entry.decimal("annualInterestRate"),
                interestMethod = com.debtfree.app.domain.InterestMethod.valueOf(entry.getString("interestMethod")),
                repaymentType = com.debtfree.app.domain.RepaymentType.valueOf(entry.getString("repaymentType")),
                tenureMonths = entry.getInt("tenureMonths"),
                maturityDate = LocalDate.parse(entry.getString("maturityDate")),
                emiAmount = entry.optString("emiAmount").takeIf(String::isNotBlank)?.let(::BigDecimal),
                processingFee = entry.optString("processingFee").takeIf(String::isNotBlank)?.let(::BigDecimal),
                status = com.debtfree.app.domain.LoanStatus.valueOf(entry.getString("status")),
                closedDate = entry.optString("closedDate").takeIf(String::isNotBlank)?.let(LocalDate::parse),
                notes = entry.optString("notes").takeIf(String::isNotBlank),
            )
            val itemArray = entry.getJSONArray("goldItems")
            for (j in 0 until itemArray.length()) {
                val item = itemArray.getJSONObject(j)
                restoredItems += GoldItemEntity(
                    loanId = id,
                    description = item.getString("description"),
                    weightGrams = item.decimal("weightGrams"),
                    purityCarat = item.getInt("purityCarat"),
                    bankValuation = item.decimal("bankValuation"),
                )
            }
            val paymentArray = entry.getJSONArray("payments")
            for (j in 0 until paymentArray.length()) {
                val payment = paymentArray.getJSONObject(j)
                restoredPayments += PaymentEntity(
                    loanId = id,
                    date = LocalDate.parse(payment.getString("date")),
                    amount = payment.decimal("amount"),
                    type = com.debtfree.app.domain.PaymentType.valueOf(payment.getString("type")),
                    note = payment.optString("note"),
                )
            }
        }
        val restoredSettings = parseSettings(root.getJSONObject("settings"))
        validateBackup(restoredLoans, restoredItems, restoredPayments, restoredSettings)
        repository.restoreLoans(restoredLoans, restoredItems, restoredPayments)
        onSettings(restoredSettings)
    }

    private fun settingsJson(settings: AppSettings) = JSONObject()
        .put("rate22K", settings.rate22K.toPlainString())
        .put("rate24K", settings.rate24K.toPlainString())
        .put("goldRateUpdatedOn", settings.goldRateUpdatedOn?.toString())
        .put("reminderDaysBefore", JSONArray(settings.reminderDaysBefore))
        .put("reminderTime", settings.reminderTime)
        .put("warnLtvAbovePercent", settings.warnLtvAbovePercent)
        .put("appLockEnabled", settings.appLockEnabled)
        .put("screenCaptureProtection", settings.screenCaptureProtection)
        .put("lastBackupExport", settings.lastBackupExport?.toString())

    private fun parseSettings(json: JSONObject): AppSettings = AppSettings(
        rate22K = json.decimal("rate22K"),
        rate24K = json.decimal("rate24K"),
        goldRateUpdatedOn = json.optString("goldRateUpdatedOn").takeIf(String::isNotBlank)?.let(LocalDate::parse),
        reminderDaysBefore = json.getJSONArray("reminderDaysBefore").let { days ->
            (0 until days.length()).map(days::getInt)
        },
        reminderTime = json.getString("reminderTime"),
        warnLtvAbovePercent = json.getInt("warnLtvAbovePercent"),
        appLockEnabled = json.getBoolean("appLockEnabled"),
        screenCaptureProtection = json.getBoolean("screenCaptureProtection"),
        lastBackupExport = json.optString("lastBackupExport").takeIf(String::isNotBlank)?.let(LocalDate::parse),
    ).also { settings ->
        require(settings.rate22K >= BigDecimal.ZERO && settings.rate24K >= BigDecimal.ZERO) { "Backup has a negative gold rate" }
        require(settings.reminderDaysBefore.all { it >= 0 }) { "Backup has an invalid reminder day" }
        require(runCatching { LocalTime.parse(settings.reminderTime) }.isSuccess) { "Backup has an invalid reminder time" }
        require(settings.warnLtvAbovePercent in 1..100) { "Backup has an invalid LTV warning level" }
    }

    private fun validateBackup(
        loans: List<LoanEntity>,
        items: List<GoldItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettings,
    ) {
        require(loans.map { it.id }.distinct().size == loans.size) { "Backup contains duplicate loan IDs" }
        loans.forEach { loan ->
            require(loan.nickname.isNotBlank() && loan.bankName.isNotBlank()) { "Backup contains a loan without required names" }
            require(loan.principal > BigDecimal.ZERO && loan.annualInterestRate >= BigDecimal.ZERO) {
                "Backup contains an invalid principal or interest rate"
            }
            require(loan.tenureMonths > 0 && !loan.maturityDate.isBefore(loan.startDate)) {
                "Backup contains invalid loan dates or tenure"
            }
            require(!loan.startDate.isAfter(LocalDate.now())) { "Backup contains a future loan start date" }
            require(loan.processingFee == null || loan.processingFee >= BigDecimal.ZERO) { "Backup contains a negative fee" }
            require(loan.emiAmount == null || loan.emiAmount > BigDecimal.ZERO) { "Backup contains an invalid EMI" }
        }
        items.forEach { item ->
            require(item.loanId in loans.map { it.id }) { "Backup contains a gold item with no loan" }
            require(item.weightGrams >= BigDecimal.ZERO && item.bankValuation >= BigDecimal.ZERO && item.purityCarat in 1..24) {
                "Backup contains invalid gold item values"
            }
        }
        payments.forEach { payment ->
            val loan = loans.firstOrNull { it.id == payment.loanId }
            require(loan != null && !payment.date.isBefore(loan.startDate) && payment.amount >= BigDecimal.ZERO) {
                "Backup contains an invalid payment"
            }
        }
        require(settings.reminderDaysBefore.distinct().size == settings.reminderDaysBefore.size) {
            "Backup contains duplicate reminder days"
        }
    }

    private fun JSONObject.decimal(key: String) = BigDecimal(getString(key))
}

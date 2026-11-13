package com.debtfree.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.debtfree.app.DebtFreeApplication
import com.debtfree.app.MainActivity
import com.debtfree.app.R
import com.debtfree.app.data.LoanEntity
import com.debtfree.app.data.LoanWithDetails
import com.debtfree.app.domain.GoldItemValue
import com.debtfree.app.domain.GoldRates
import com.debtfree.app.domain.LoanCalculator
import com.debtfree.app.domain.LoanTerms
import com.debtfree.app.domain.LoanPayment
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class LoanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DebtFreeApplication
        val settings = app.settingsRepository.settings.first()
        val loans = app.loanRepository.allLoansWithDetails()
        val today = LocalDate.now()
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        loans.filter { it.loan.status == com.debtfree.app.domain.LoanStatus.ACTIVE }.forEach { relation ->
            val loan = relation.loan
            val dueDays = ChronoUnit.DAYS.between(today, loan.maturityDate).toInt()
            val messages = mutableListOf<String>()
            if (dueDays in settings.reminderDaysBefore || dueDays == 0) {
                messages += if (dueDays == 0) "Matures today" else "Matures in $dueDays day${if (dueDays == 1) "" else "s"}"
            }
            if (dueDays < 0) messages += "Loan is overdue"
            val terms = loan.toTerms()
            val items = relation.goldItems.map {
                GoldItemValue(it.weightGrams, it.purityCarat, it.bankValuation)
            }
            val payments = relation.payments.map { LoanPayment(it.date, it.amount, it.type) }
            val metrics = LoanCalculator.metrics(
                terms, payments, items, GoldRates(settings.rate22K, settings.rate24K), today,
            )
            if (metrics.currentLtvPercent?.let { it > settings.warnLtvAbovePercent.toBigDecimal() } == true) {
                messages += "Loan-to-value is above your ${settings.warnLtvAbovePercent}% warning level"
            }
            if (messages.isNotEmpty()) {
                val bulletMessage = if (loan.repaymentType == com.debtfree.app.domain.RepaymentType.BULLET) {
                    " Renewal needs interest paid first; start early."
                } else ""
                notifyLoan(loan, messages.joinToString(". ") + "." + bulletMessage)
            }
        }
        val rateDate = settings.goldRateUpdatedOn
        if (rateDate == null || settings.rate22K <= java.math.BigDecimal.ZERO ||
            settings.rate24K <= java.math.BigDecimal.ZERO || ChronoUnit.DAYS.between(rateDate, today) > 7
        ) {
            notifyGeneral("Gold rates may be out of date", "Update your 22K and 24K rates in Debt Free.")
        }
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Loan reminders", NotificationManager.IMPORTANCE_DEFAULT)
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notifyLoan(loan: LoanEntity, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_LOAN_ID, loan.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext, loan.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Debt Free · ${loan.nickname}")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message Estimate only – the bank's figure is final."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(loan.id.toInt(), notification)
    }

    private fun notifyGeneral(title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Debt Free · $title")
            .setContentText(message)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(GENERAL_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "loan_reminders"
        private const val GENERAL_ID = Int.MAX_VALUE - 3

        fun schedule(context: Context, time: String = "09:00") {
            val pieces = time.split(":").mapNotNull(String::toIntOrNull)
            val reminderTime = if (pieces.size == 2) {
                runCatching { LocalTime.of(pieces[0], pieces[1]) }.getOrNull()
            } else null
            val now = LocalTime.now()
            var delay = if (reminderTime == null) 0L else ChronoUnit.SECONDS.between(now, reminderTime)
            if (delay < 0) delay += TimeUnit.DAYS.toSeconds(1)
            val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_loan_reminders", ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }
    }
}

private fun LoanEntity.toTerms() = LoanTerms(
    principal = principal,
    annualInterestRatePercent = annualInterestRate,
    interestMethod = interestMethod,
    repaymentType = repaymentType,
    startDate = startDate,
    maturityDate = maturityDate,
    processingFee = processingFee ?: java.math.BigDecimal.ZERO,
)

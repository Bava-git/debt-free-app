package com.debtfree.app

import android.app.Application
import com.debtfree.app.data.DebtFreeDatabase
import com.debtfree.app.data.LoanRepository
import com.debtfree.app.data.SettingsRepository
import com.debtfree.app.reminders.LoanReminderWorker

class DebtFreeApplication : Application() {
    val database by lazy { DebtFreeDatabase.create(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val loanRepository by lazy { LoanRepository(database, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
        LoanReminderWorker.schedule(this)
    }
}

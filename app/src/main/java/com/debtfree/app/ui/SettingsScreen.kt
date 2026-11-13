package com.debtfree.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.debtfree.app.backup.BackupManager
import com.debtfree.app.reminders.LoanReminderWorker
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: LoanViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rate22 by remember { mutableStateOf("") }
    var rate24 by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf("7, 1") }
    var reminderTime by remember { mutableStateOf("09:00") }
    var ltvWarning by remember { mutableStateOf("75") }
    var appLock by remember { mutableStateOf(false) }
    var secureScreens by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!loaded) {
            rate22 = settings.rate22K.takeIf { it > BigDecimal.ZERO }?.toPlainString().orEmpty()
            rate24 = settings.rate24K.takeIf { it > BigDecimal.ZERO }?.toPlainString().orEmpty()
            reminderDays = settings.reminderDaysBefore.joinToString(", ")
            reminderTime = settings.reminderTime
            ltvWarning = settings.warnLtvAbovePercent.toString()
            appLock = settings.appLockEnabled
            secureScreens = settings.screenCaptureProtection
            loaded = true
        }
    }

    val backupManager = remember { BackupManager(context.contentResolver, vm.repository) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    backupManager.export(uri, settings)
                    vm.repository.settings.markExported()
                    info = "Backup exported successfully."
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = "Could not export backup: ${failure.localizedMessage ?: "unknown error"}"
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    var importedSettings = settings
                    backupManager.import(uri) { restored ->
                        importedSettings = restored
                        vm.repository.settings.restore(restored)
                        rate22 = restored.rate22K.takeIf { it > BigDecimal.ZERO }?.toPlainString().orEmpty()
                        rate24 = restored.rate24K.takeIf { it > BigDecimal.ZERO }?.toPlainString().orEmpty()
                        reminderDays = restored.reminderDaysBefore.joinToString(", ")
                        reminderTime = restored.reminderTime
                        ltvWarning = restored.warnLtvAbovePercent.toString()
                        appLock = restored.appLockEnabled
                        secureScreens = restored.screenCaptureProtection
                    }
                    LoanReminderWorker.schedule(context, importedSettings.reminderTime)
                    info = "Backup restored successfully."
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = "Could not restore backup: ${failure.localizedMessage ?: "unknown error"}"
                }
            }
        }
    }
    val backupIsDue = settings.lastBackupExport == null ||
        ChronoUnit.DAYS.between(settings.lastBackupExport, LocalDate.now()) >= 30

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Gold rates")
                Text("Rates are entered manually and stay on this device.")
                SettingsField("22K rate per gram (INR)", rate22, { rate22 = it }, numeric = true)
                SettingsField("24K rate per gram (INR)", rate24, { rate24 = it }, numeric = true)
                Text("Last updated: ${settings.goldRateUpdatedOn?.displayDate() ?: "Not set"}")
                TextButton(onClick = {
                    val parsed22 = rate22.toBigDecimalOrNull()
                    val parsed24 = rate24.toBigDecimalOrNull()
                    if (parsed22 == null || parsed24 == null || parsed22 < BigDecimal.ZERO || parsed24 < BigDecimal.ZERO) {
                        error = "Enter valid non-negative 22K and 24K rates before marking them updated."
                    } else {
                        scope.launch {
                            try {
                                vm.repository.settings.saveGoldRates(parsed22, parsed24)
                                info = "Gold rates saved and marked updated today."
                                error = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = "Could not save gold rates: ${failure.localizedMessage ?: "unknown error"}"
                            }
                        }
                    }
                }) { Text("Mark rates updated today") }
                SectionTitle("Reminders")
                SettingsField("Days before maturity (comma-separated)", reminderDays, { reminderDays = it })
                SettingsField("Reminder time (HH:MM, 24-hour)", reminderTime, { reminderTime = it })
                SettingsField("LTV warning level (%)", ltvWarning, { ltvWarning = it }, numeric = true)
                SectionTitle("Privacy")
                SettingSwitch("Require biometric or device credential on open/resume", appLock) { appLock = it }
                SettingSwitch("Block screenshots and screen recording on sensitive screens", secureScreens) { secureScreens = it }
                Text("Screen-capture protection is enabled by default.")
                SectionTitle("Backup and restore")
                if (backupIsDue) {
                    Text(
                        "Backup reminder: no export has been made in the last 30 days.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("Exports and imports use the system file picker. No storage permission or cloud account is used.")
                OutlinedButton(onClick = { exportLauncher.launch("debt-free-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export data to JSON")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore data from JSON")
                }
                SectionTitle("Data")
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                vm.addDemoData()
                                info = "Fictional demo loans are ready."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = "Could not add demo data: ${failure.localizedMessage ?: "unknown error"}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Load demo data") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear all data")
                }
                SectionTitle("About Debt Free")
                Text("Debt Free · Track it. Repay it. Be Debt Free.")
                Text("Personal loan monitoring only. No lending, payments, bank connection, accounts, SMS reading, analytics, ads or cloud sync.")
                Text("No bank passwords or OTPs are requested. Your bank's statement and figure are authoritative.")
                SectionTitle("Calculation assumptions")
                Text(
                    "Interest accrues chronologically from the loan start date. Payments apply on their recorded date; interest payments reduce unpaid interest, principal payments reduce principal, and EMI payments pay interest first then principal. Simple monthly and compound monthly use calendar-month periods; partial months are prorated by days/30. Monthly compounding adds unpaid interest to the interest-bearing balance. Simple daily uses elapsed days/365. Displayed values are estimates and are not bank quotes.",
                )
                Text("Gold value uses the saved 22K or 24K rate, or scales the 24K rate by carat/24 for other purities.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                OutlinedButton(
                    onClick = {
                        val parsed22 = rate22.toBigDecimalOrNull()
                        val parsed24 = rate24.toBigDecimalOrNull()
                        val days = reminderDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                        val warning = ltvWarning.toIntOrNull()
                        val timeValid = runCatching { java.time.LocalTime.parse(reminderTime) }.isSuccess
                        error = when {
                            parsed22 == null || parsed22 < BigDecimal.ZERO -> "22K gold rate must be a non-negative number."
                            parsed24 == null || parsed24 < BigDecimal.ZERO -> "24K gold rate must be a non-negative number."
                            days.size != reminderDays.split(",").size || days.any { it < 0 } -> "Enter reminder days as non-negative numbers separated by commas."
                            !timeValid -> "Enter reminder time in HH:MM 24-hour format."
                            warning == null || warning !in 1..100 -> "LTV warning must be between 1 and 100."
                            else -> null
                        }
                        if (error == null) {
                            scope.launch {
                                try {
                                    vm.repository.settings.update(
                                        parsed22!!, parsed24!!, days.distinct(), reminderTime,
                                        warning!!, appLock, secureScreens,
                                    )
                                    LoanReminderWorker.schedule(context, reminderTime)
                                    info = "Settings saved."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = "Could not save settings: ${failure.localizedMessage ?: "unknown error"}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save settings") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("This permanently removes all loans, gold items, payments and settings from this device. Export a backup first if you may need it.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            vm.repository.clearAll()
                            vm.repository.settings.clear()
                            rate22 = ""
                            rate24 = ""
                            reminderDays = "7, 1"
                            reminderTime = "09:00"
                            ltvWarning = "75"
                            appLock = false
                            secureScreens = true
                            info = "All local data and settings were cleared."
                            confirmClear = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = "Could not clear data: ${failure.localizedMessage ?: "unknown error"}"
                        }
                    }
                }) { Text("Clear everything") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

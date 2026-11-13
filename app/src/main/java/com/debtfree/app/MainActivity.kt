package com.debtfree.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.debtfree.app.reminders.LoanReminderWorker
import com.debtfree.app.ui.DebtFreeTheme
import com.debtfree.app.ui.LoanViewModel
import com.debtfree.app.ui.LoanViewModelFactory
import com.debtfree.app.ui.RootNavigation

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DebtFreeApplication
        setContent {
            val vm: LoanViewModel = viewModel(factory = LoanViewModelFactory(app.loanRepository))
            val settings by vm.settings.collectAsState()
            val settingsReady by vm.settingsLoaded.collectAsState()
            val context = LocalContext.current
            val view = LocalView.current
            SideEffect {
                if (!view.isInEditMode) {
                    if (settings.screenCaptureProtection) {
                        window.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
            LaunchedEffect(settings.reminderTime) {
                LoanReminderWorker.schedule(context, settings.reminderTime)
            }
            DebtFreeTheme {
                AppPermissionGate {
                    AppLockGate(enabled = settings.appLockEnabled, ready = settingsReady) {
                        RootNavigation(vm, intent.getLongExtra(EXTRA_LOAN_ID, -1L)) {
                            intent.removeExtra(EXTRA_LOAN_ID)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOAN_ID = "com.debtfree.app.extra.LOAN_ID"
    }
}

@Composable
private fun AppPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var showExplanation by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    content()
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Allow loan reminders?") },
            text = { Text("Debt Free can remind you about upcoming or overdue maturities and high loan-to-value estimates. Notifications stay on this device.") },
            confirmButton = {
                Button(onClick = {
                    showExplanation = false
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun AppLockGate(enabled: Boolean, ready: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var unlocked by remember { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun authenticate() {
        if (!enabled || authInProgress) return
        authInProgress = true
        error = null
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    error = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    error = "Authentication was not recognized. Try again."
                }
            },
        )
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val availability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(activity).canAuthenticate(authenticators)
        } else {
            BiometricManager.BIOMETRIC_SUCCESS
        }
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            authInProgress = false
            error = "Set up a device credential or strong biometric in Android settings to unlock Debt Free."
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Debt Free")
            .setSubtitle("Authenticate to view your loan information")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(
            promptInfo,
        )
    }

    LaunchedEffect(enabled, ready) {
        if (!ready) {
            unlocked = false
        } else if (enabled) {
            unlocked = false
            authenticate()
        } else {
            unlocked = true
        }
    }
    androidx.compose.runtime.DisposableEffect(lifecycle, enabled, ready) {
        val observer = LifecycleEventObserver { _, event ->
            if (ready && enabled && event == Lifecycle.Event.ON_STOP) unlocked = false
            if (ready && enabled && event == Lifecycle.Event.ON_START && !unlocked) authenticate()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (ready && (!enabled || unlocked)) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ready) "Debt Free is locked" else "Loading secure settings", style = MaterialTheme.typography.headlineSmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (authInProgress) CircularProgressIndicator()
                if (ready) Button(onClick = { authenticate() }, enabled = !authInProgress) { Text("Unlock") }
            }
        }
    }
}

# Debt Free

**Track it. Repay it. Be Debt Free.**

Debt Free is a personal, single-user Android app for manually tracking gold loans from Indian banks. It stores loan data locally and does not lend money, take payments, connect to banks, read SMS, request account credentials, or use a backend.

## Requirements

- Android Studio with Android SDK Platform 37 and Build Tools 36.0.0 (install through SDK Manager)
- JDK 21 (Android Studio's bundled JDK can be selected in Gradle settings)
- Android 8.0 (API 26) or newer for installation
- Gradle Wrapper is included; a separate Gradle installation is not needed.

The project has no `INTERNET` permission. Database cloud backup is disabled. Loan records, settings, and manual JSON backups remain under your control on the device.

## Open and run

1. Open the project root folder in Android Studio.
2. Ensure **Android SDK Platform 37** and **Android SDK Build-Tools 36.0.0** are installed in **Tools > SDK Manager**.
3. Select JDK 21 under **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**.
4. Allow Gradle sync to finish.
5. Connect an Android phone with USB debugging enabled, or create an API 26+ emulator.
6. Select the `app` run configuration and press **Run**. Grant notification permission if you want reminders.

No login or bank connection is required. Gold rates are entered manually in Settings. Use **Load demo data** to try two clearly fictional loans.

## Build and install APKs

From PowerShell in the project root:

```powershell
.\gradlew.bat test
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Install it on a connected device with:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

`.\gradlew.bat assembleRelease` creates an **unsigned** APK at `app\build\outputs\apk\release\app-release-unsigned.apk`, which cannot be installed directly. To create an installable release APK, use **Build > Generate Signed Bundle / APK > APK** in Android Studio. Create and keep a personal signing key in a safe place; do not commit it to source control. Android Studio places the signed APK under `app\release\`. A release signed with a different key cannot update an existing installation signed by another key.

This project intentionally does not store a release signing configuration or credentials. Do not store signing passwords or key files in this repository.

To sideload manually, transfer the APK to your phone, allow installation from the file manager you use when Android prompts, then open the APK and confirm installation. Keep your signing key and exported backups private.

## Features

- Dashboard with active loan totals, due/overdue attention, maturity countdown, and estimated LTV.
- Loan and gold-item management, payment history, close/delete actions, maturity calendar intent, and closure calculator.
- Manual 22K/24K gold rates, adjustable reminder days/time and LTV warning, notification reminders, app lock, and screenshot protection setting.
- JSON export/import through Android's system file picker; no storage permission is requested.
- Local Room database, DataStore settings, and daily WorkManager reminders.

## GitHub Actions

The workflow at `.github\workflows\android.yml` runs tests and builds debug and unsigned release APKs on every push and pull request. Successful runs upload the APKs as a downloadable `debt-free-apks` workflow artifact. It installs Android SDK Platform 37 and Build-Tools 36.0.0 and uses the included Gradle Wrapper.

Run the same checks locally on Windows:

```powershell
.\gradlew.bat --no-daemon test :app:assembleDebug :app:assembleRelease
```

On macOS or Linux, use `./gradlew` instead. If the copied file does not have its executable bit, run `chmod +x ./gradlew` once first.

## Calculation assumptions

All amounts are estimates. **Estimate only – the bank's figure is final.** Compare these assumptions and the editable interest method/rate against each bank statement:

1. Accrual is chronological from the loan start date. Payment dates are treated as the date the payment is applied; payments on the same date are processed in the recorded order.
2. `SIMPLE_DAILY` uses outstanding principal × annual rate / 100 × elapsed days / 365. A leap year still uses a 365-day denominator.
3. `SIMPLE_MONTHLY` uses outstanding principal × annual rate / 100 / 12 for each complete calendar month. A partial month is prorated by elapsed days / 30.
4. `COMPOUND_MONTHLY` compounds unpaid interest monthly. For a partial month, interest is prorated by elapsed days / 30 on the principal plus unpaid interest balance.
5. Interest payments reduce accrued unpaid interest. Principal payments reduce principal. EMI payments pay accrued interest first and then principal. A payment larger than the applicable balance cannot create a negative balance. `OTHER` payments are recorded but do not change balances. `FEE` payments reduce the recorded processing fee.
6. Only the processing fee is modeled as a fee charge; there is no separate schedule for other bank charges. Pending fees are included in closure amount; cost-to-date includes processing fee and interest actually paid.
7. Loan maturity defaults to start date plus tenure in calendar months and can be edited. The app warns when a bullet-loan start date is after 1 April 2026 and tenure exceeds 12 months; the warning is not legal or regulatory advice.
8. LTV uses payable today divided by current estimated gold value. Gold value uses the saved 22K/24K gram rates; other carats use 24K rate × carat / 24. If no current gold rate or valuation is available, the corresponding LTV is unavailable.
9. Bullet maturity LTV uses principal outstanding plus interest projected through maturity, divided by the current estimated gold value. Future payments are only included if they have already been recorded with a date on or before maturity.
10. Calendar date calculations use the device's local date and Java `java.time`. Displayed dates use `DD-MM-YYYY`; currency uses Indian rupee formatting.

## Privacy and limitations

There are no accounts, cloud sync, analytics, ads, third-party tracking, online bank access, payments, UPI support, SMS access, or internet permission. Biometric/device-credential availability depends on the phone's Android security configuration. Manual JSON exports are not encrypted by the app; store them securely.

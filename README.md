# AFEC - Automated Financial Expiry Countdown

Android app for automatically tracking financial expiry dates (Fixed Deposits, card renewals, premium accounts) by scanning inbound SMS messages.

## Project Structure

```
AFEC/
├── docs/                    # Project documentation
│   └── PRD.md               # Product Requirement Document
├── app/                     # Android Studio module
│   ├── build.gradle         # App-level build configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml  # App manifest with permissions
│   │   │   ├── java/
│   │   │   │   ├── com/afec/app/
│   │   │   │   │   ├── MainActivity.kt    # Main entry point (Compose)
│   │   │   │   │   ├── data/
│   │   │   │   │   │   ├── ExpiryItem.kt      # Room database entity
│   │   │   │   │   ├── AFECDatabase.kt      # Room database class
│   │   │   │   │   ├── ExpiryItemDao.kt     # DAO operations
│   │   │   │   │   ├── sms/
│   │   │   │   │   │   └── SmsReceiver.kt   # SMS broadcast receiver
│   │   │   │   │   └── ui/
│   │   │   │   │       ├── CountdownCard.kt   # Dashboard card UI
│   │   │   │   │       └── Dashboard.kt       # Main dashboard screen
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml    # String resources
│   │   │   │   │   ├── colors.xml     # Color resources
│   │   │   │   │   └── dimens.xml     # Dimension resources
│   │   │   │   └── (layout resources)
│   │   │   └── AndroidManifest.xml
│   └── gradle/
├── gradle.properties      # Gradle configuration
└── settings.gradle        # Project settings
```

## Key Features (Phase 1)

- **SMS Ingestion**: Automatic monitoring of incoming texts for financial keywords
- **Regex Date Engine**: Parse variable date strings (DD/MM/YYYY, DD-MMM-YYYY, etc.)
- **Visual Countdown Dashboard**: Color-coded cards showing remaining days
- **Notification Stacking**: Customizable pre-alerts (14d, 7d, 1d, day-of)
- **Manual Entry Fallback**: Form to manually input/override tracking dates
- **Local-First Architecture**: Room SQLite database, zero cloud sync

## Permissions Required

- `RECEIVE_SMS`: Process automated date capturing
- `READ_SMS`: Read incoming text messages
- `POST_NOTIFICATIONS`: Time-critical alarms on Android 13+

## Getting Started

1. Open the project in Android Studio
2. Sync Gradle files
3. Connect an Android device or create an emulator
4. Run the app

## Notes

- This app is designed for sideloading as a private developer build
- Google Play Store restricts apps with broad READ_SMS permissions
- Bank SMS formats vary - the parser supports manual updates
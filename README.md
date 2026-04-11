# NFC_Wallet

An advanced NFC research and wallet testing application for rooted Android 14 (Google Pixel 7 Pro) with Magisk and LSPosed.

## Features

### 🃏 Wallet Dashboard
- Scrollable list of all saved cards with color-coded card backgrounds
- NFC status indicator (available/disabled/error)
- Root/Magisk status indicator
- Quick-access navigation to all tools

### 📡 NFC Card Scanner
- Comprehensive scanning of **all NFC card types**:
  - MIFARE Classic (1K/4K/Plus/Pro) — reads all sectors with 12 default key dictionary
  - MIFARE Ultralight / Ultralight-C / NTAG 213/215/216
  - ISO 14443-4 (IsoDep) — probes 12+ known AIDs including payment AIDs
  - NFC-A, NFC-B, NFC-F (FeliCa), NFC-V (ISO 15693)
  - NDEF tags
- Auto-detection of card type, company (Visa, Mastercard, Amex, etc.), and suggested AID injection
- EMV payment card data extraction (GPO, READ RECORD, SFI)
- Real-time scan log
- Save as individual text file

### ✏️ Manual Card Entry
- Full card data form with all NFC fields
- Card type / company spinners with auto-detection from card number prefix
- Primary AID, additional AIDs, emulation UID override
- Custom APDU command/response mapping table
- Raw protocol data fields (NDEF, MIFARE, ISO-DEP)
- Physical card info (card number, cardholder, expiry)
- Card color picker
- View/edit saved cards

### 📲 Card Emulation (HCE)
- Select any saved card from list and start NFC emulation
- **UID Spoofing** — override the emulated UID with any custom value
- **AID Spoofing** — override the primary AID at emulation time
- Full ISO 7816-4 APDU processing:
  - SELECT AID with FCI response
  - GET UID (FF CA 00 00 00)
  - READ BINARY
  - READ RECORD (EMV)
  - GET PROCESSING OPTIONS
  - GENERATE AC
  - GET CHALLENGE
  - MIFARE READ block emulation
  - Custom APDU command → response map
- Foreground service with notification and stop button

### 🔬 Testing Tools
- **Custom APDU Transceiver** — send arbitrary APDU hex to any tapped card
- **AID Probe** — probe all known payment/access AIDs or a specific AID
- **GET UID** command
- **READ BINARY sweep** — sweep offsets 0x00–0x20
- Real-time test log

### 🔑 Root / Magisk / LSPosed Features
Requires root access via Magisk.
- NFC controller device info (sysfs, /dev/nfc*)
- Secure Element (eSE) status and device files
- NFC routing table dump (via `dumpsys nfc`)
- All NFC-related system settings
- Force enable NFC via root
- List all registered AIDs in NFC service
- Magisk module list
- LSPosed installation status
- NFC logcat dump
- Set preferred payment service (bypasses system UI requirement)
- Arbitrary root shell command execution

### 🪝 LSPosed Module
When activated in LSPosed Manager (scope: System Framework + com.android.nfc):
- Hooks `NfcAdapter.enableForegroundDispatch` — logs all app NFC listeners
- Hooks `NfcManager.getDefaultAdapter` — monitors NFC adapter access
- Hooks `CardEmulationManager` — monitors HCE activation/deactivation
- Hooks `RegisteredAidCache.resolveAid` — intercepts AID routing decisions
- Hooks `SEService` constructor — monitors Secure Element access
- All hooks log to LSPosed log viewer for real-time analysis

## Card Storage
Each card is saved as an individual **plain text `.txt` file** in:
```
/data/data/com.nfc.wallet/files/cards/
```
No encryption or additional security — designed for easy debugging and inspection.

Text file format:
```
=== NFC_Wallet Card ===
ID: 1234567890
Label: My Visa Card
Type: CREDIT_CARD
Company: VISA
...
UID: AABBCCDD
ATQA: 0004
SAK: 20
PrimaryAID: A0000000031010
...
=== End Card ===
```

## Building

1. Install Android Studio / Gradle
2. Open the project root (contains `settings.gradle`)
3. Sync Gradle
4. Build: `./gradlew :android:assembleDebug`
5. Install: `adb install android/build/outputs/apk/debug/android-debug.apk`

## Requirements
- Android 14 (API 34), minSdk 26
- NFC hardware + HCE support
- Magisk root (optional, for root features)
- LSPosed (optional, for hook features) — activate for `android` + `com.android.nfc` scope
- Bootloader unlocked (Pixel 7 Pro)

## Project Structure
```
android/
├── build.gradle                    # App module Gradle config
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   └── xposed_init             # LSPosed module entry point
    ├── java/com/nfc/wallet/
    │   ├── MainActivity.java       # Wallet dashboard
    │   ├── NFCScanActivity.java    # NFC card scanner
    │   ├── ManualCardActivity.java # Manual card entry/view
    │   ├── CardEmulationActivity.java # Emulation control
    │   ├── TestingToolsActivity.java  # NFC testing tools
    │   ├── RootFeaturesActivity.java  # Root/Magisk/LSPosed
    │   ├── NfcEmulatorService.java    # HCE service
    │   ├── EmulationControlReceiver.java
    │   ├── model/CardModel.java       # Card data model
    │   ├── util/
    │   │   ├── APDUUtils.java         # APDU helpers
    │   │   ├── CardStorageManager.java
    │   │   ├── CardTypeDetector.java
    │   │   └── RootUtils.java
    │   └── xposed/XposedHooks.java    # LSPosed hooks
    └── res/
        ├── layout/                 # Dark Material UI layouts
        ├── values/                 # Dark theme colors, strings, styles
        └── xml/                    # apduservice, nfc_tech_filter, etc.
```

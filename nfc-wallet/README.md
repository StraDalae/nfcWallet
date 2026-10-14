# NFC Wallet

An Android wallet for NFC tags and cards: scan, name, save, and copy to a blank tag later.
Kotlin, Jetpack Compose (Material 3, dark), Room, Android Keystore. Min SDK 30.

## Open it

1. Open this folder in Android Studio and let Gradle sync (it downloads Gradle 8.9 from `gradle-wrapper.properties`).
2. Run on a **physical** Android phone with NFC. Emulators have no NFC reader.
3. Dependency versions are pinned to a known-good set; accept Android Studio's upgrade suggestions if you like.
   Check Google Play's current target-SDK requirement before publishing.

## How the data is protected

| Layer | What it does |
|---|---|
| Keystore keys | Two AES-256-GCM keys, StrongBox when available, bound to biometric / screen lock, unusable while the device is locked |
| `META` key (5 min window) | Name, colour, icon, note, tag type, timestamps |
| `DUMP` key (30 s window) | UID, memory pages, sectors, keys. Decrypted only on **Reveal** or **Copy** |
| Database | Random ID + two ciphertext blobs per row. No plaintext names, UIDs or dates |
| Row binding | The row ID is GCM associated data, so blobs can't be swapped between rows |
| App behaviour | Locks on leaving the app, `FLAG_SECURE` (no screenshots/recents preview), backups disabled, no network, no INTERNET permission |

If Android deletes the keys (screen lock removed), the app detects it and offers a vault reset; the old data is unrecoverable by design.

## What each tag type can do

| Tag | Read & save | Copy to blank |
|---|---|---|
| NDEF tags (text, URL, etc.) | Yes | Yes, to any writable NDEF tag |
| NTAG / Ultralight | Yes | Yes, user memory only; new tag keeps its own UID |
| MIFARE Classic | Sectors on default keys, NXP-chip phones only | Data blocks to a blank card; UID needs a "magic" card (toggle in the copy sheet) |
| DESFire / payment / passport (ISO-DEP) | Identifying info only | No: protected by design |
| ISO 15693, FeliCa | UID only | No |

Never written, to avoid bricking targets: UID/lock/OTP/config pages, MIFARE Classic sector trailers.
Every copy is read back and compared; the result sheet says whether it verified.

## Layout

```
security/  VaultCrypto (keys + AES-GCM), AuthGate (BiometricPrompt)
data/      Models (encrypted JSON payloads), Room entity/DAO, CardRepository
nfc/       TagReader, TagWriter
ui/        WalletViewModel, screens, sheets, theme
```

## Not built yet

- Emulating a saved card from the phone (HCE). Only works for AID-based ISO-DEP cards, never fakes a UID.
- Passphrase-encrypted export/import (Keystore keys can't move between phones).
- Auto-lock timer setting, light theme.

## Responsible use

Only read and copy tags and cards you own or are authorized to copy.

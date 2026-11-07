# Health Connect

openScale writes supported measurements directly to Android Health Connect. It does not send health data to an openScale server. Bluetooth scale handlers, including FitTrack Dara, continue to save through the existing measurement flow.

## Setup

1. Open **Settings → Health Connect** in openScale.
2. Turn on synchronization and choose whether to synchronize the complete local history. The full-history option is selected the first time.
3. Grant the Health Connect write permissions you want openScale to use. Partial grants are supported; measurements requiring other permissions remain pending.
4. Use **Synchronize now** to retry work, or **Manage access in Health Connect** to review permissions.

New and edited measurements are queued as expedited WorkManager jobs. WorkManager retries temporary failures, and a periodic recovery job checks for outstanding changes. Stable Health Connect `clientRecordId` values and increasing `clientRecordVersion` values make retries idempotent. Deleting a measurement or user in openScale propagates deletions to Health Connect, and editing a measurement removes any metrics no longer present.

Disabling synchronization cancels openScale's queued jobs. It does not delete data already written to Health Connect.

## Data mapping

| openScale value | Health Connect record | Rule |
| --- | --- | --- |
| Weight | `WeightRecord` | Converted to kilograms; original timestamp and UTC offset are preserved. |
| Height | `HeightRecord` | User profile height is written at the measurement timestamp. |
| Body fat | `BodyFatRecord` | Percentage is written directly. A mass value is converted to a percentage only with weight from the same measurement. |
| Body water | `BodyWaterMassRecord` | Mass is written directly. A percentage is converted to mass only with weight from the same measurement. |
| Lean body mass | `LeanBodyMassRecord` | Converted to kilograms. |
| Bone mass | `BoneMassRecord` | Converted to kilograms. |
| Basal metabolic rate | `BasalMetabolicRateRecord` | openScale kcal/day is converted to Health Connect power in watts. |
| BMI and other values without a matching record | — | Kept in openScale; no substitute record is invented. |

The per-measurement status shown in the overview and settings distinguishes synchronized, pending, failed, invalid, excluded, and unsupported measurements.

## Build and tests

The Android project uses Java 21 and Android SDK 37. From `android_app`, run:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

The debug APK is produced under `app/build/outputs/apk/debug/`. Device validation is still needed for Bluetooth weighing, Health Connect permission flows, and record visibility in compatible apps.

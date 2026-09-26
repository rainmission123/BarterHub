# BarterHub

BarterHub is a native Android marketplace and barter application built with **Kotlin**. It combines item trading, real-time messaging, wallet/coin transactions, ratings, notifications, and secure Firebase-backed workflows in one mobile app.

> Current status: active development / internal testing.

## Highlights

- Native Android application written in Kotlin
- Firebase Authentication and user profiles
- Realtime Database and Firestore integrations
- Real-time chat with text, image, video, reactions, and system messages
- Trade request, acceptance, completion, and rating flows
- In-app wallet and coin transaction history
- Google Play Billing integration for coin purchases
- Firebase Cloud Messaging push notifications
- Firebase Cloud Functions for trusted server-side operations
- Firebase App Check / Play Integrity integration
- Firebase Realtime Database and Storage security rules
- Maps, location, CameraX, QR scanning, and media handling
- Release optimization with R8 / resource shrinking

## Tech Stack

| Area | Technologies |
| --- | --- |
| Language | Kotlin |
| UI | XML, Material Components, ViewBinding, RecyclerView |
| Architecture | ViewModel, Repository pattern, domain/data/UI separation |
| Backend | Firebase Authentication, Realtime Database, Firestore, Storage, Cloud Functions |
| Messaging | Firebase Cloud Messaging |
| Payments | Google Play Billing |
| Networking | Retrofit, OkHttp |
| Images | Glide, Coil, Cloudinary |
| Location & Maps | Google Maps, Google Play Services Location, Mapbox |
| Camera / Scanning | CameraX, ZXing |
| Server Functions | Node.js, Firebase Admin SDK |
| Build | Gradle Kotlin DSL, Java 17 |

## Architecture

The Android project is organized around feature and responsibility boundaries:

```text
app/
└── src/main/java/com/example/barterhub/
    ├── data/          # repositories and data models
    ├── domain/        # domain-level logic
    ├── ui/            # activities/fragments/viewmodels
    ├── billing/       # Play Billing and purchase verification
    ├── services/      # Android/Firebase services
    ├── network/       # network-facing components
    ├── managers/      # application managers
    ├── adapters/      # RecyclerView adapters
    ├── binders/       # chat/message presentation binders
    └── utils/         # shared utilities

functions/
└── Node.js Firebase Cloud Functions
```

The app uses repositories to isolate data access from UI logic and ViewModels to manage UI-facing state. Sensitive wallet, purchase, and trade operations are backed by server-side validation and Firebase security rules rather than trusting the Android client alone.

## Selected Engineering Work

### Secure trade and wallet flows

BarterHub includes trade request, acceptance, completion, rating, wallet, and transaction flows. Authorization and validation are enforced in Firebase security rules and Cloud Functions for operations that should not be trusted to the client.

### Google Play Billing

The app integrates Google Play Billing for coin products and includes server-side purchase verification and idempotency protections to reduce duplicate-credit risk.

### Real-time messaging

Chat supports real-time conversations, media messages, reactions, system-generated trade messages, read/unread states, archived conversations, and FCM notifications.

### Firebase security

The project includes:

- Realtime Database rules
- Storage rules
- Firebase App Check providers
- Cloud Function authorization and input validation

## Build Requirements

- Android Studio
- JDK 17
- Android SDK matching the project configuration
- A Firebase project
- Local configuration values in `local.properties`

Example local configuration:

```properties
MAPS_API_KEY=your_maps_key
CLOUDINARY_CLOUD_NAME=your_cloud_name
```

Do not commit private credentials or signing keys.

## Build

On macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

## Continuous Integration

GitHub Actions validates the Android project on pull requests and pushes by running unit tests, lint, and a debug build.

## Security Notes

BarterHub handles authentication, messaging, wallet transactions, purchases, and user-generated content. Security-sensitive logic should be reviewed carefully before production deployment.

If you discover a security issue, avoid posting exploitable details in a public issue.

## Roadmap

Near-term engineering work includes:

- Expanding unit and integration test coverage
- Adding more automated checks to CI
- Continuing architecture cleanup and modularization
- Improving production observability and failure handling
- Expanding modern Android UI coverage where appropriate

## Project Owner

**Rian Mission**  
Android Developer / Android Engineer  
GitHub: [@rainmission123](https://github.com/rainmission123)

---

This repository is being actively improved as both a real product and an Android engineering portfolio project.

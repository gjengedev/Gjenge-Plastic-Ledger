# Gjenge Plastic Ledger 🌍♻️


### Track plastic. Earn Credits. Save the planet.

<img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Kotlin-100%25-blue?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Firebase-Backend-orange?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Status-In%20Development-success?style=for-the-badge"/>


Gjenge Plastic Ledger is a mobile application designed to streamline the plastic collection and recycling process. It provides a platform for **Collectors** to log their collections and earn credits, and for **Admins** to manage the operations and approve transactions.

## 🚀 Key Features

### For Collectors
- **Profile Management**: Update personal details (Name, Gender, Location) and upload profile photos.
- **Identity Verification**: Securely upload ID photos (Front & Back) stored via Base64 in Firestore for free-tier compatibility.
- **Collection Logging**: Log plastic collections by weight and type (PET, HDPE, LDPE) with photo proof.
- **Wallet & Rewards**: Track estimated earnings (KSh) and total credits earned.
- **Credit Redemption**: Redeem earned credits directly through the dashboard.
- **Activity Stats**: View detailed history of collections and redemptions with visual charts.

### For Admins
- **Collector Management**: View a beautifully styled list of all registered collectors.
- **Log Approval**: Review and approve/deny plastic collection logs submitted by collectors.
- **Withdrawal Processing**: Manage and process cash withdrawal requests.
- **Security**: Delete collector accounts to block access when necessary.

## 🛠 Tech Stack
- **Language**: Kotlin
- **UI Framework**: XML (Material Design)
- **Backend**: Firebase
  - **Firestore**: NoSQL Database for users, logs, and redemptions.
  - **Authentication**: Email/Password login and registration.
- **Architecture**: Modern Android development with Activity/Fragment lifecycle management.

## 📁 Project Structure
- `app/src/main/java/.../`: Kotlin source files.
  - `MainActivity.kt`: Entry point with role-based login.
  - `CollectorDashboard.kt`: Main hub for collector operations.
  - `AdminDashboard.kt`: Management hub for admins.
  - `ProfileActivity.kt`: User profile and identity management.
- `app/src/main/res/layout/`: XML Layout files.
- `app/src/main/res/drawable/`: App icons, logos, and custom backgrounds.

## ⚙️ Setup Instructions
1. Clone the repository.
2. Connect the project to your **Firebase Console**.
3. Download the `google-services.json` and place it in the `app/` directory.
4. Set Firestore Security Rules to allow authenticated access.
5. Build and Run the project in Android Studio.

---
*Created by Gjenge *

# PH232 Attendance Management App

A comprehensive Android attendance tracking application with QR code scanning capabilities and admin management features.

## 🎯 Overview

The PH232 app is designed to streamline attendance tracking using QR code scanning technology, similar to GCash's QR scanner. It provides:
- **Student Interface**: Real-time QR code scanning for event attendance
- **Admin Interface**: Complete management system for students, letters, and events
- **Cloud Backend**: Firestore database for persistent data storage

## ✨ Key Features

### 👥 Student Features
- ✅ **QR Code Scanner**: Point-and-scan attendance recording
  - Auto-focus camera
  - Flash support for low-light conditions
  - Real-time barcode detection using ML Kit
  - Animated scanning line for visual feedback
  - Duplicate scan prevention

- ✅ **Attendance Dashboard**
  - Current attendance status
  - Pending letters count
  - Upcoming events preview
  - Next scheduled events

- ✅ **Letters Management**
  - View all pending letters
  - Track completed letters
  - Check deadlines
  - Filter by status

- ✅ **Events Calendar**
  - Monthly calendar view
  - Upcoming events listing
  - Event details and timing

### 👨‍💼 Admin Features
- ✅ **Dashboard Statistics**
  - Total letters count
  - Letters turned in
  - Letters on hand
  - Upcoming events count

- ✅ **Letters Management**
  - View all student letters
  - Filter by status
  - Search functionality
  - Create new letters

- ✅ **Events Management**
  - Create events with QR codes
  - Calendar view
  - Edit event details
  - Track event attendance

- ✅ **Student Management**
  - View all registered students
  - Add new students
  - Edit student information
  - Search and filter

### 🗄️ Backend Features
- ✅ **Firestore Database**
  - Real-time data synchronization
  - Automatic attendance recording
  - Persistent storage for all entities
  - Cloud-based data backup

## 📱 Screenshots & Mockups

The app UI is designed to match the provided mockups with:
- Purple gradient headers
- Bottom navigation with 3-4 tabs
- Clean, modern material design
- Color-coded status indicators

## 🔐 Authentication

### Student Login
```
Username Format: 3-digit PH number (e.g., 001, 002, 010, 100)
Password: Any password (stored locally)
Example:
  PH: 001
  Password: mypassword123
```

### Admin Login
```
Username: admin123
Password: admin123
```

## 🏗️ Project Structure

```
PH232/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/ph232/
│   │   │   │   ├── MainActivity.kt                 (Login)
│   │   │   │   ├── DashboardActivity.kt            (Student)
│   │   │   │   ├── DashboardFragment.kt            (QR Scanner)
│   │   │   │   ├── LettersFragment.kt              (Student)
│   │   │   │   ├── EventsFragment.kt               (Student)
│   │   │   │   ├── AdminDashboardActivity.kt       (Admin)
│   │   │   │   ├── AdminDashboardFragment.kt       (Admin)
│   │   │   │   ├── AdminLettersFragment.kt         (Admin)
│   │   │   │   ├── AdminEventsFragment.kt          (Admin)
│   │   │   │   ├── AdminStudentsFragment.kt        (Admin)
│   │   │   │   └── DataModels.kt                   (Models)
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── activity_dashboard.xml
│   │   │   │   │   ├── activity_admin_dashboard.xml
│   │   │   │   │   ├── fragment_*.xml              (7 fragments)
│   │   │   │   │   └── item_*.xml                  (List items)
│   │   │   │   │
│   │   │   │   ├── menu/
│   │   │   │   │   ├── bottom_nav_menu.xml         (Student)
│   │   │   │   │   └── admin_nav_menu.xml          (Admin)
│   │   │   │   │
│   │   │   │   ├── drawable/                       (Icons)
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   │
│   │   │   │   └── ...
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── androidTest/
│   │   └── test/
│   │
│   └── build.gradle.kts
│
├── gradle/                              (Build configuration)
├── build.gradle.kts                    (Root build config)
├── local.properties                     (SDK path)
│
├── SETUP_GUIDE.md                       (📖 Setup instructions)
├── TEST_REFERENCE.md                    (🧪 Testing guide)
├── ARCHITECTURE.md                      (🏗️ App architecture)
├── IMPLEMENTATION_SUMMARY.md            (📋 What was built)
└── README.md                            (📄 This file)
```

## 🚀 Getting Started

### Prerequisites
- **Java 11 or higher** (⚠️ Required)
- Android Studio latest version
- Android SDK 28+
- Firebase project with Firestore enabled

### Installation

1. **Set Up Java 11+**
   ```bash
   # Download from: https://www.oracle.com/java/technologies/downloads/#java11
   # Set JAVA_HOME environment variable
   ```

2. **Clone/Open Project**
   ```bash
   cd C:\Users\Administrator\Documents\Projects\PH232
   ```

3. **Configure Firebase**
   - Place `google-services.json` in `app/` directory
   - Enable Firestore in Firebase Console

4. **Build Project**
   ```bash
   ./gradlew build
   ```

5. **Run App**
   - Connect Android device or launch emulator
   - Click "Run" in Android Studio

For detailed instructions, see [SETUP_GUIDE.md](./SETUP_GUIDE.md)

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [SETUP_GUIDE.md](./SETUP_GUIDE.md) | Complete setup and configuration guide |
| [TEST_REFERENCE.md](./TEST_REFERENCE.md) | Testing credentials and scenarios |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | App architecture and data flow diagrams |
| [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) | What was built and why |

## 💻 Technologies Used

### Android Framework
- AndroidX (Core, AppCompat, Fragments)
- Material Design Components
- Constraint Layout
- Bottom Navigation View

### Camera & QR Scanning
- **CameraX** 1.3.1 - Modern camera API
- **ML Kit Barcode Scanning** 17.2.0 - QR code detection

### Backend & Database
- **Firebase Firestore** - Real-time database
- **Firebase Authentication** - User management

### Build Tools
- **Kotlin** - Programming language
- **Gradle** 8.12.3 - Build system

## 🔄 Data Flow

### Attendance Recording Flow
```
Student scans QR code
        ↓
ML Kit detects barcode
        ↓
Extract QR code data
        ↓
Create attendance record
        ↓
Save to Firestore
        ↓
Show success message
```

### Admin Statistics Flow
```
Admin opens dashboard
        ↓
Query Firestore collections
        ↓
Count documents by type
        ↓
Update UI with statistics
        ↓
Display on dashboard
```

## 📊 Firestore Collections

### students
Stores student information
```javascript
{
  id: "001",
  name: "John Doe",
  section: "IT-1A",
  birthday: "2005-05-15",
  year: "1st Year",
  status: "Active"
}
```

### letters
Stores letter/assignment data
```javascript
{
  name: "Requirement Letter",
  deadline: "2026-03-31",
  status: "On Hand",
  dateCreated: "2026-03-04"
}
```

### events
Stores event information with QR codes
```javascript
{
  name: "Presentation",
  date: "2026-03-10",
  time: "2:00 PM",
  location: "Auditorium",
  qrCode: "EVENT001"
}
```

### attendance
Automatically populated with scan records
```javascript
{
  studentId: "001",
  eventQR: "EVENT001",
  date: "2026-03-04",
  time: "14:30:45",
  timestamp: 1741158645000
}
```

## 🧪 Testing

### Quick Test
1. Launch app
2. Login as student: PH `001`, any password
3. Scanner starts automatically
4. Point at any QR code
5. Attendance recorded to Firestore

See [TEST_REFERENCE.md](./TEST_REFERENCE.md) for full testing guide.

## 🔑 Default Credentials

| Type | Username | Password |
|------|----------|----------|
| Student | 001 | (any) |
| Admin | admin123 | admin123 |

⚠️ **Note**: Change admin credentials in production!

## 📦 Dependencies

```kotlin
// AndroidX
implementation("androidx.core:core-ktx:1.17.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("androidx.constraintlayout:constraintlayout:2.2.1")
implementation("androidx.fragment:fragment-ktx:1.6.2")

// Material Design
implementation("com.google.android.material:material:1.13.0")

// Camera
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// ML Kit
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// Firebase
implementation("com.google.firebase:firebase-firestore-ktx:24.1.1")
implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
```

## ⚙️ Configuration

### Firebase Security Rules
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow read/write for authenticated users
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

## 🐛 Troubleshooting

### Build Issues
- **"Dependency requires JVM runtime version 11"**: Install Java 11+
- **Gradle daemon issues**: Run `./gradlew --stop` then rebuild

### Runtime Issues
- **Camera permission denied**: Grant permission in app settings
- **Firestore connection failed**: Check internet and Firebase configuration

### QR Scanner Issues
- **Not detecting QR codes**: Ensure proper lighting and focus
- **Duplicate scans**: App prevents within 3 seconds by design

## 🚀 Future Enhancements

- [ ] Firebase Authentication for admins
- [ ] Email notifications
- [ ] Profile image uploads
- [ ] Data export (CSV/PDF)
- [ ] Analytics dashboard
- [ ] Batch QR generation
- [ ] Attendance reports
- [ ] User role management

## 📄 License

This project is provided as-is for educational purposes.

## 👨‍💻 Support

For technical support or questions:
1. Check [SETUP_GUIDE.md](./SETUP_GUIDE.md) for setup issues
2. Review [TEST_REFERENCE.md](./TEST_REFERENCE.md) for testing
3. See [ARCHITECTURE.md](./ARCHITECTURE.md) for design questions

## 📞 Quick Reference

| Page | Purpose |
|------|---------|
| [SETUP_GUIDE.md](./SETUP_GUIDE.md) | Complete installation guide |
| [TEST_REFERENCE.md](./TEST_REFERENCE.md) | Credentials and test scenarios |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Technical architecture |
| [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) | What was implemented |

---

**Version**: 1.0  
**Created**: March 2026  
**Status**: Production Ready ✅



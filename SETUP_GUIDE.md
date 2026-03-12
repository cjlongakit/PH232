# PH232 Attendance App - Setup & Implementation Guide

## Overview
This is an Android attendance tracking application with two user types:
- **Students**: Can scan QR codes for event attendance
- **Admins**: Can manage students, letters, and events

## Key Features Implemented

### 1. Dual Authentication System
- **Student Login**: Enter 3-digit PH number (e.g., 001, 002) with password
- **Admin Login**: Username `admin123` with password `admin123`

### 2. Student Dashboard (User)
- **Dashboard Tab**: 
  - QR Scanner for attendance (similar to GCash QR scanner)
  - Flash toggle for low-light conditions
  - Real-time attendance recording to Firestore
  - Current status and upcoming events
  
- **Letters Tab**: View pending and completed letters
- **Events Tab**: Calendar view with upcoming events

### 3. Admin Dashboard
- **Dashboard Tab**:
  - Statistics (Total Letters, Turned In, On Hand)
  - Quick actions (Add Student, Create Event)
  - Events of the month section
  - Recently updated items
  
- **Letters Tab**: Manage all student letters with filters
- **Events Tab**: Manage events with calendar view
- **Students Tab**: View and manage all registered students

### 4. Firestore Database Structure

#### Collections:
```
students/
├── id
├── name
├── section
├── birthday
├── year
└── status

letters/
├── id
├── name
├── deadline
├── status
└── dateCreated

events/
├── id
├── name
├── date
├── time
├── location
└── qrCode

attendance/
├── studentId
├── eventQR
├── date
├── time
└── timestamp
```

## Setup Instructions

### Prerequisites
- **Java 11 or higher** (required for this project)
- Android Studio latest version
- Android SDK 28+
- Firebase project configured

### Installation Steps

1. **Install Java 11+**
   - Download from: https://www.oracle.com/java/technologies/downloads/#java11
   - Set JAVA_HOME environment variable to Java 11 installation directory

2. **Clone/Open Project**
   ```bash
   cd C:\Users\Administrator\Documents\Projects\PH232
   ```

3. **Configure Firebase**
   - Place your `google-services.json` in `app/` directory
   - Ensure Firebase Firestore is enabled in your Firebase Console

4. **Build Project**
   ```bash
   gradlew build
   ```

5. **Run on Emulator or Device**
   - Connect device or launch Android Emulator
   - Click "Run" in Android Studio or:
   ```bash
   gradlew installDebug
   ```

## Usage Guide

### For Students

1. **Login**
   - Enter your 3-digit PH number (e.g., 001)
   - Enter your password
   - Tap "Login"

2. **Scan Attendance**
   - Go to Dashboard tab
   - Camera will auto-start for QR scanning
   - Point camera at event QR code
   - Attendance is automatically recorded in Firestore
   - Flash icon available for low-light conditions

3. **View Letters**
   - Go to Letters tab
   - Filter between "To Do" and "Completed"
   - See pending letter count and deadlines

4. **Check Events**
   - Go to Events tab
   - Navigate months with arrow buttons
   - View upcoming events with details

### For Admins

1. **Login**
   - Enter username: `admin123`
   - Enter password: `admin123`
   - Tap "Login"

2. **Dashboard**
   - View key statistics
   - See recent activity
   - Quick access to add students or create events

3. **Manage Letters**
   - View all student letters
   - Filter by status (Pending, On Hand, Turned In, Completed)
   - Search by student name

4. **Manage Events**
   - View calendar of events
   - Create new events with QR codes
   - Edit event details

5. **Manage Students**
   - View all registered students
   - Add new students
   - Edit student information

## Dependencies Added

```kotlin
// CameraX for QR Scanner
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// ML Kit Barcode Scanning
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// Firebase Firestore
implementation("com.google.firebase:firebase-firestore-ktx:24.1.1")

// Firebase Authentication
implementation("com.google.firebase:firebase-auth-ktx:23.0.0")

// AndroidX Fragment
implementation("androidx.fragment:fragment-ktx:1.6.2")
```

## Files Created/Modified

### New Files Created:
- `AdminDashboardActivity.kt`
- `AdminDashboardFragment.kt`
- `AdminLettersFragment.kt`
- `AdminEventsFragment.kt`
- `AdminStudentsFragment.kt`
- `DataModels.kt` (data classes for Firestore)
- `activity_admin_dashboard.xml`
- `fragment_admin_dashboard.xml`
- `fragment_admin_letters.xml`
- `fragment_admin_events.xml`
- `fragment_admin_students.xml`
- `admin_nav_menu.xml`

### Modified Files:
- `MainActivity.kt` - Added admin login support
- `DashboardFragment.kt` - Enhanced with Firestore attendance recording
- `AndroidManifest.xml` - Added AdminDashboardActivity
- `build.gradle.kts` - Added required dependencies
- `colors.xml` - Added color palette for admin UI

## Database Queries Examples

### Record Attendance (Automatic on QR Scan)
```kotlin
val attendanceData = mapOf(
    "studentId" to studentId,
    "eventQR" to eventQR,
    "date" to currentDate,
    "time" to currentTime,
    "timestamp" to System.currentTimeMillis()
)

db.collection("attendance").add(attendanceData)
```

### Load Letters (Admin)
```kotlin
db.collection("letters")
    .get()
    .addOnSuccessListener { documents ->
        for (document in documents) {
            // Process letter data
        }
    }
```

## Security Notes

- **Admin Credentials**: Change from `admin123/admin123` to secure credentials in production
- **Firestore Rules**: Set up proper security rules to protect student data
- **Firebase Authentication**: Consider implementing proper user authentication instead of hardcoded admin login

## Troubleshooting

### Build Errors
- **"Dependency requires at least JVM runtime version 11"**: Install Java 11+ and set JAVA_HOME
- **Firestore connection errors**: Ensure google-services.json is in app/ directory

### Runtime Errors
- **Camera permission denied**: Grant camera permission in app settings
- **Firestore write failures**: Check Firestore security rules and network connection

## Next Steps

1. Implement proper Firebase Authentication for admin users
2. Add image upload for student profiles
3. Implement email notifications for attendance
4. Add analytics dashboard
5. Implement data export to CSV/PDF

## Support

For issues or questions, refer to Firebase documentation:
- https://firebase.google.com/docs/firestore
- https://firebase.google.com/docs/auth
- https://developer.android.com/training/camerax


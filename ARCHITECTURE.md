# PH232 App Architecture & Flow Diagram

## Application Flow Chart

```
┌─────────────────────────────┐
│     MainActivity            │
│   (Login Screen)            │
└──────────────┬──────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
┌─────────────────┐  ┌──────────────────┐
│ Student Login   │  │ Admin Login      │
│ (PH: 001)       │  │ (admin123)       │
└────────┬────────┘  └────────┬─────────┘
         │                    │
         ▼                    ▼
┌──────────────────────┐  ┌────────────────────────┐
│ DashboardActivity    │  │ AdminDashboardActivity │
│ (User Interface)     │  │ (Admin Interface)      │
└──────────┬───────────┘  └──────────┬─────────────┘
           │                         │
      ┌────┴──────┬─────┐      ┌────┴──────┬──────┬──────┐
      │            │     │      │           │      │      │
      ▼            ▼     ▼      ▼           ▼      ▼      ▼
┌──────────┐ ┌────────┐ ┌─────┐ ┌─────┐ ┌────┐ ┌──────┐ ┌────────┐
│Dashboard │ │Letters │ │Event│ │Dash │ │Let │ │Event │ │Student │
│Fragment  │ │Fragment│ │Frag │ │Frag │ │Frag│ │Frag  │ │Frag    │
│(QR Scan) │ │        │ │     │ │(Sta)│ │    │ │      │ │(Mgmt)  │
└────┬─────┘ └────────┘ └─────┘ └─────┘ └────┘ └──────┘ └────────┘
     │
     ▼
┌──────────────────┐
│  Firestore DB    │
│   Collections:   │
│  - attendance    │
│  - letters       │
│  - events        │
│  - students      │
└──────────────────┘
```

## Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      PH232 App                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Authentication Layer                       │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │  MainActivity - Login Handler                   │ │  │
│  │  │  - Student Login (3-digit PH)                   │ │  │
│  │  │  - Admin Login (hardcoded admin123)             │ │  │
│  │  │  - Route to appropriate dashboard               │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Student Dashboard Layer                      │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │  DashboardActivity                              │ │  │
│  │  │  ├─ DashboardFragment (QR Scanner)              │ │  │
│  │  │  │  └─ Camera + ML Kit Barcode Scanner          │ │  │
│  │  │  ├─ LettersFragment (Status View)               │ │  │
│  │  │  └─ EventsFragment (Calendar View)              │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          Admin Dashboard Layer                       │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │  AdminDashboardActivity                         │ │  │
│  │  │  ├─ AdminDashboardFragment (Statistics)         │ │  │
│  │  │  ├─ AdminLettersFragment (Management)           │ │  │
│  │  │  ├─ AdminEventsFragment (Management)            │ │  │
│  │  │  └─ AdminStudentsFragment (Management)          │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │        Data Layer (Firestore)                        │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │  Collections:                                    │ │  │
│  │  │  ├─ students (Student documents)                │ │  │
│  │  │  ├─ letters (Letter documents)                  │ │  │
│  │  │  ├─ events (Event documents)                    │ │  │
│  │  │  └─ attendance (Attendance records)             │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
Student QR Scan Flow:
═════════════════════

┌─────────────┐
│   Student   │
│   Scans QR  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│ DashboardFragment       │
│ - QRCodeAnalyzer        │
│   processes frame       │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ ML Kit Barcode Scanner  │
│ Detects QR Code         │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ handleQRCodeScanned()       │
│ - Check for duplicate scan  │
│ - Record timestamp          │
└──────┬──────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ recordAttendance()           │
│ Create attendance data:      │
│ {                            │
│   studentId: "001"           │
│   eventQR: "<scanned>"       │
│   date: "2026-03-04"         │
│   time: "14:30:45"           │
│   timestamp: <millis>        │
│ }                            │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────┐
│ Firestore Upload         │
│ attendance collection    │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│ Success Toast            │
│ "Attendance recorded!"   │
└──────────────────────────┘
```

## Firestore Data Model

```
attendance/
├── document_1
│   ├── studentId: "001"
│   ├── eventQR: "EVENT_QR_CODE_123"
│   ├── date: "2026-03-04"
│   ├── time: "14:30:45"
│   └── timestamp: 1741158645000
│
├── document_2
│   ├── studentId: "002"
│   ├── eventQR: "EVENT_QR_CODE_456"
│   ├── date: "2026-03-04"
│   ├── time: "14:32:10"
│   └── timestamp: 1741158730000
│
└── ...

students/
├── student_001
│   ├── id: "001"
│   ├── name: "John Doe"
│   ├── section: "IT-1A"
│   ├── birthday: "2005-05-15"
│   ├── year: "1st Year"
│   └── status: "Active"
│
└── ...

letters/
├── letter_001
│   ├── name: "Requirement Letter"
│   ├── deadline: "2026-03-31"
│   ├── status: "On Hand"
│   └── dateCreated: "2026-03-04"
│
└── ...

events/
├── event_001
│   ├── name: "Presentation"
│   ├── date: "2026-03-10"
│   ├── time: "2:00 PM"
│   ├── location: "Auditorium"
│   └── qrCode: "EVENT_QR_CODE_123"
│
└── ...
```

## Navigation Structure

### Student Navigation
```
MainActivity (Login)
    │
    ├─► DashboardActivity
    │       ├─ Dashboard Tab (DashboardFragment)
    │       │   └─ QR Scanner, Flash, Attendance Status
    │       │
    │       ├─ Letters Tab (LettersFragment)
    │       │   └─ Pending/Completed Letters
    │       │
    │       └─ Events Tab (EventsFragment)
    │           └─ Calendar, Upcoming Events
    │
    └─► Logout ──► MainActivity
```

### Admin Navigation
```
MainActivity (Login)
    │
    ├─► AdminDashboardActivity
    │       ├─ Dashboard Tab (AdminDashboardFragment)
    │       │   └─ Statistics, Quick Actions
    │       │
    │       ├─ Letters Tab (AdminLettersFragment)
    │       │   └─ Manage Student Letters
    │       │
    │       ├─ Events Tab (AdminEventsFragment)
    │       │   └─ Create/Manage Events
    │       │
    │       └─ Students Tab (AdminStudentsFragment)
    │           └─ Manage Student Records
    │
    └─► Logout ──► MainActivity
```

## Technology Stack

```
┌────────────────────────┐
│   Android Framework    │
│   - Activities         │
│   - Fragments          │
│   - Bottom Navigation  │
└────────────────────────┘
           ▲
           │
┌────────────────────────┐
│   Libraries            │
│   - CameraX (Camera)   │
│   - ML Kit (Scanning)  │
│   - Firebase (Backend) │
│   - Material Design    │
└────────────────────────┘
           ▲
           │
┌────────────────────────┐
│   Backend Services     │
│   - Firestore DB       │
│   - Firebase Auth      │
└────────────────────────┘
```

## Login Decision Logic

```
User Enters Credentials
        │
        ▼
Is username "admin123" 
AND password "admin123"?
    │         │
   YES       NO
    │         │
    ▼         ▼
 Admin    Check if valid
 Login    3-digit format
    │         │
    │         ├─► Valid ──► Student Login
    │         │
    │         └─► Invalid ──► Show Error
    │
    ├─────────┬──────────┘
    │         │
    ▼         ▼
AdminDash  Dashboard
Activity   Activity
```

This architecture provides:
- Clear separation of concerns
- Modular fragment-based UI
- Real-time Firestore integration
- Scalable admin/student management
- Efficient QR code processing


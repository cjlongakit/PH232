# PH232 App - Quick Test Reference

## Test Credentials

### Student Login
```
PH Number: 001
Password: (any password)
```

### Admin Login
```
Username: admin123
Password: admin123
```

## Test Features

### Student User (PH: 001)
1. **QR Scanner Test**
   - Tap Camera for Attendance on Dashboard
   - Camera will start automatically
   - Tap flash icon to toggle light
   - Point at any QR code to test scanning
   - Successfully scanned QR codes will be recorded to Firestore

2. **View Attendance History**
   - Attendance records are stored in Firestore under `attendance` collection
   - Each record contains: studentId, eventQR, date, time, timestamp

### Admin User (admin123/admin123)
1. **Dashboard Statistics**
   - View total letters (counts from Firestore)
   - View turned in and on-hand counts
   - See recently updated items

2. **Manage Letters**
   - Filter letters by status
   - Search for specific letters
   - Add new letters (Add button)

3. **Manage Events**
   - View calendar
   - Create events with QR codes
   - View event details

4. **Manage Students**
   - Search students
   - View student information
   - Add new students

## Firestore Collection Structure

### Adding Test Data to Firestore

```javascript
// In Firestore Console, create these collections:

// Collection: students
{
  id: "001",
  name: "John Doe",
  section: "IT-1A",
  birthday: "2005-05-15",
  year: "1st Year",
  status: "Active"
}

// Collection: letters
{
  name: "Requirement Letter",
  deadline: "2026-03-31",
  status: "On Hand",
  dateCreated: "2026-03-04"
}

// Collection: events
{
  name: "Presentation",
  date: "2026-03-10",
  time: "2:00 PM",
  location: "Auditorium",
  qrCode: "EVENT001"
}
```

## Expected App Flow

### First Time User (Student)
1. Launch app → Login screen appears
2. Enter PH: 001, Password: anything → Dashboard loads
3. See "Dashboard" tab selected with camera ready
4. Bottom navigation shows: Dashboard, Letters, Events

### First Time Admin
1. Launch app → Login screen appears
2. Enter admin123/admin123 → Admin Dashboard loads
3. See stats and management options
4. Bottom navigation shows: Dashboard, Letters, Events, Students

## Firebase Setup Checklist

- [ ] Firebase project created
- [ ] Firestore database enabled (in native mode)
- [ ] Authentication enabled
- [ ] google-services.json placed in app/ directory
- [ ] Collections created in Firestore (optional - auto-created on first write)
- [ ] Security rules configured (allow test mode or proper rules)

## Common Test Scenarios

### Scenario 1: Test Attendance Recording
1. Login as student (001)
2. Tap Dashboard → Camera starts
3. Show any QR code to camera
4. Check Firestore `attendance` collection
5. Verify new record with studentId: "001" and scanned QR code

### Scenario 2: Test Admin Statistics
1. Login as admin (admin123/admin123)
2. View Dashboard tab
3. Statistics should load from Firestore
4. Add test data in Firestore if numbers are 0

### Scenario 3: Test Navigation
1. Test each bottom nav tab loads correct fragment
2. Verify header title updates
3. Test back button behavior

## Notes
- Make sure Java 11+ is installed before building
- Camera permission will be requested on first app launch
- Attendance is only recorded if QR code is successfully scanned
- Duplicate scans within 3 seconds are prevented


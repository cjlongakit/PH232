package com.example.ph232

data class Student(
    val id: String = "",
    val name: String = "",
    val section: String = "",
    val birthday: String = "",
    val year: String = "",
    val status: String = ""
)

data class Letter(
    val id: String = "",
    val name: String = "",
    val deadline: String = "",
    val status: String = "",
    val dateCreated: String = "",
    val title: String = "",
    val isCompleted: Boolean = false
)

data class Event(
    val id: String = "",
    val name: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val qrCode: String = "",
    val day: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val subTime: String = ""
)

data class Attendance(
    val id: String = "",
    val studentId: String = "",
    val eventQR: String = "",
    val date: String = "",
    val time: String = "",
    val timestamp: Long = 0
)

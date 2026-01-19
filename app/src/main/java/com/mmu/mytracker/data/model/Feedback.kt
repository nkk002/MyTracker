package com.mmu.mytracker.data.model

data class Feedback(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
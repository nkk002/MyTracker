package com.mmu.mytracker.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class BusLocation(
    val busId: String? = null,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    val routeId: String? = null,
    val timestamp: Long? = 0L
)
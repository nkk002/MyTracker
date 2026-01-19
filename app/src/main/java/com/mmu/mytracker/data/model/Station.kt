package com.mmu.mytracker.data.model

import com.google.firebase.firestore.PropertyName

data class Station(
    val name: String = "",

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    @get:PropertyName("services")
    val services: List<StationService> = emptyList()
)
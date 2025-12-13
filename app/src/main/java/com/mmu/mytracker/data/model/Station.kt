package com.mmu.mytracker.data.model

import com.google.firebase.firestore.PropertyName

data class Station(
    // 车站的名字，例如 "MRT Kajang Station"
    val name: String = "",

    // Firestore 里的 latitude 和 longitude
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // 车站下的服务列表 (MRT Line, Bus 等)
    // 注意：在 Firestore 里这通常是一个 Array of Maps
    @get:PropertyName("services")
    val services: List<StationService> = emptyList()
)
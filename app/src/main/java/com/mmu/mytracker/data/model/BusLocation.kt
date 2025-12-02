package com.mmu.mytracker.data.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * 代表公交车的实时位置信息。
 * @IgnoreExtraProperties 用于防止Firebase返回未知字段时导致Crash。
 */
@IgnoreExtraProperties
data class BusLocation(
    val busId: String? = null,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    val routeId: String? = null,
    val timestamp: Long? = 0L
)
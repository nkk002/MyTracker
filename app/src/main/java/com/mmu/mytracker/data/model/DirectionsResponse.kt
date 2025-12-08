package com.mmu.mytracker.data.model

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    @SerializedName("routes")
    val routes: List<Route>,

    @SerializedName("status")
    val status: String
)

data class Route(
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline,

    @SerializedName("legs")
    val legs: List<Leg>
)

data class OverviewPolyline(
    @SerializedName("points")
    val points: String
)

data class Leg(
    @SerializedName("distance")
    val distance: TextValue,

    @SerializedName("duration")
    val duration: TextValue,

    // --- 新增下面这两个字段 ---
    @SerializedName("arrival_time")
    val arrivalTime: TimeInfo?, // 可能为空，所以用 ?

    @SerializedName("departure_time")
    val departureTime: TimeInfo?
)

data class TextValue(
    val text: String,
    val value: Int
)

// --- 新增 TimeInfo 类 ---
data class TimeInfo(
    @SerializedName("text")
    val text: String, // 例如 "8:45 PM"
    @SerializedName("value")
    val value: Long   // 时间戳 (秒)
)
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
    @SerializedName("arrival_time")
    val arrivalTime: TimeInfo?,
    @SerializedName("departure_time")
    val departureTime: TimeInfo?,

    // --- 新增：解析步骤 ---
    @SerializedName("steps")
    val steps: List<Step>
)

// --- 新增 Step 相关类 ---
data class Step(
    @SerializedName("travel_mode")
    val travelMode: String, // "WALKING", "TRANSIT" 等
    @SerializedName("transit_details")
    val transitDetails: TransitDetails?
)

data class TransitDetails(
    @SerializedName("departure_time")
    val departureTime: TimeInfo?, // 这才是真正的列车发车时间
    @SerializedName("arrival_time")
    val arrivalTime: TimeInfo?,
    @SerializedName("headsign")
    val headsign: String?, // 列车方向，例如 "To Kwasa Damansara"
    @SerializedName("line")
    val line: TransitLine?
)

data class TransitLine(
    @SerializedName("name")
    val name: String?, // 例如 "Kajang Line"
    @SerializedName("short_name")
    val shortName: String? // "MRT"
)

data class TextValue(
    val text: String,
    val value: Int
)

data class TimeInfo(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Long
)
package com.mmu.mytracker.data.model

import com.google.gson.annotations.SerializedName

// 1. 根响应
data class DirectionsResponse(
    @SerializedName("routes")
    val routes: List<Route>,
    @SerializedName("status")
    val status: String
)

// 2. 路线信息
data class Route(
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline,

    // 🔥 关键：Legs 包含了路程的具体信息 (距离、时间)
    @SerializedName("legs")
    val legs: List<Leg>
)

data class OverviewPolyline(
    @SerializedName("points")
    val points: String
)

// 3. 路段详情 (每一段导航)
data class Leg(
    @SerializedName("distance")
    val distance: TextValue,

    @SerializedName("duration")
    val duration: TextValue
)

// 4. 通用文本值对象 (Google 返回的格式是 { "text": "15 mins", "value": 900 })
data class TextValue(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Int
)
package com.mmu.mytracker.data.model

import com.google.gson.annotations.SerializedName

// 顶层响应对象
data class DirectionsResponse(
    @SerializedName("routes")
    val routes: List<Route>,

    @SerializedName("status")
    val status: String
)

// 路线对象
data class Route(
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline,

    @SerializedName("legs")
    val legs: List<Leg>
)

// 编码的折线数据
data class OverviewPolyline(
    @SerializedName("points")
    val points: String // 这是我们需要解码的核心字符串
)

// 路线的分段信息（可选，用于显示距离和时间）
data class Leg(
    @SerializedName("distance")
    val distance: TextValue,

    @SerializedName("duration")
    val duration: TextValue
)

data class TextValue(
    val text: String,
    val value: Int
)
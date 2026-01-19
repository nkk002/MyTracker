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
    val duration: TextValue
)

data class TextValue(
    @SerializedName("text")
    val text: String,
    @SerializedName("value")
    val value: Int
)
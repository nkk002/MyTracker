package com.mmu.mytracker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// 使用 Parcelize 方便在 Intent 中传递
@Parcelize
data class StationService(
    val id: String = "",
    val name: String = "",       // e.g., "MRT Kajang Line"
    val type: String = "",       // e.g., "MRT", "LRT", "BUS"
    val direction: String = ""   // e.g., "To Kwasa Damansara"
) : Parcelable
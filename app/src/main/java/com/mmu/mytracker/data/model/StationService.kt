package com.mmu.mytracker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StationService(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val direction: String = "",
    // 🔥 新增这三个字段 (要和 Firestore 里的字段名完全一致)
    val frequency_min: Int = 0,
    val first_train: String = "",  // Format: "06:00"
    val last_train: String = ""
) : Parcelable
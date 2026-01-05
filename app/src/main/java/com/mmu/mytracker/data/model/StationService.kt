package com.mmu.mytracker.data.model

import android.os.Parcelable
import com.google.firebase.database.PropertyName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class StationService(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val direction: String = "",
    val first_train: String = "",
    val last_train: String = "",

    // 🔥 核心修改：使用私有变量 + 显式 Getter/Setter
    // 这样 Firebase 绝对能找到这两个字段
    @PropertyName("frequency_min")
    var _frequency_min: String? = "0",

    @PropertyName("offset_min")
    var _offset_min: String? = "0"

) : Parcelable {

    // 1. 获取频率 (Int)
    // 使用 @IgnoredOnParcel 防止 Parcelize 报错
    @IgnoredOnParcel
    var frequency_min: Int
        get() = try {
            _frequency_min?.toString()?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
        set(value) {
            _frequency_min = value.toString()
        }

    // 2. 获取偏移量 (Int)
    @IgnoredOnParcel
    var offset_min: Int
        get() = try {
            _offset_min?.toString()?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
        set(value) {
            _offset_min = value.toString()
        }
}
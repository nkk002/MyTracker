package com.mmu.mytracker.utils

import java.time.LocalTime
import java.time.ZoneId // 🔥 必须 Import 这个
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {

    fun getMinutesUntilNextTrain(firstTrainStr: String?, freq: Int): Long {
        try {
            if (firstTrainStr.isNullOrEmpty() || freq <= 0) return -1

            // 🔥 核心修改：强制获取马来西亚时间，而不是手机系统时间
            val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
            val now = LocalTime.now(malaysiaZone)

            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val firstTrain = LocalTime.parse(firstTrainStr, formatter)

            if (now.isBefore(firstTrain)) {
                return ChronoUnit.MINUTES.between(now, firstTrain)
            }

            val minutesSinceFirst = ChronoUnit.MINUTES.between(firstTrain, now)
            val minutesPassedSinceLastTrain = minutesSinceFirst % freq
            return freq - minutesPassedSinceLastTrain

        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    fun formatTimeDisplay(minutes: Long): String {
        return if (minutes >= 0) "$minutes mins" else "-- mins"
    }
}
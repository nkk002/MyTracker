package com.mmu.mytracker.utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {

    /**
     * 输入：首班车时间 (e.g. "06:00") 和 频率 (e.g. 8)
     * 输出：下一班车还有几分钟 (Long)
     */
    fun getMinutesUntilNextTrain(firstTrainStr: String?, freq: Int): Long {
        try {
            if (firstTrainStr.isNullOrEmpty() || freq <= 0) return -1

            val now = LocalTime.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val firstTrain = LocalTime.parse(firstTrainStr, formatter)

            // 如果现在比首班车还早
            if (now.isBefore(firstTrain)) {
                return ChronoUnit.MINUTES.between(now, firstTrain)
            }

            // 核心算法
            val minutesSinceFirst = ChronoUnit.MINUTES.between(firstTrain, now)
            val minutesPassedSinceLastTrain = minutesSinceFirst % freq
            return freq - minutesPassedSinceLastTrain

        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * 辅助方法：把分钟转成易读的 String
     */
    fun formatTimeDisplay(minutes: Long): String {
        return if (minutes >= 0) {
            "$minutes mins"
        } else {
            "-- mins"
        }
    }
}
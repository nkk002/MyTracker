package com.mmu.mytracker.utils

import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {

    // 辅助方法：获取单个下班车时间 (保留以备不时之需，但也加上了 offset)
    fun getMinutesUntilNextTrain(firstTrainStr: String?, freq: Int, offset: Int = 0): Long {
        val trains = getNextThreeTrains(firstTrainStr, freq, offset)
        return if (trains.isNotEmpty()) trains[0] else -1
    }

    // 🔥 核心修改：增加 offset 参数，计算未来三班车
    fun getNextThreeTrains(firstTrainStr: String?, freq: Int, offset: Int = 0): List<Long> {
        try {
            if (firstTrainStr.isNullOrEmpty() || freq <= 0) return emptyList()

            // 1. 获取马来西亚当前时间
            val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
            val now = LocalTime.now(malaysiaZone)

            // 2. 解析总站发车时间 (例如 "06:00")
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val depotStartTime = LocalTime.parse(firstTrainStr, formatter)

            // 3. 🔥 计算本站首班车时间 = 总站时间 + 偏移量
            var currentStationTrainTime = depotStartTime.plusMinutes(offset.toLong())

            val upcomingTrains = mutableListOf<Long>()

            // 4. 循环查找未来的班次
            // 限制循环次数防止死循环 (例如找接下来 24 小时内的车)
            for (i in 0 until 100) {
                // 如果这班车的时间 比 现在晚 (或者正好是现在)
                if (currentStationTrainTime.isAfter(now) || currentStationTrainTime == now) {
                    val minutesUntil = ChronoUnit.MINUTES.between(now, currentStationTrainTime)
                    upcomingTrains.add(minutesUntil)

                    // 只要找到 3 班就停止
                    if (upcomingTrains.size >= 3) break
                }

                // 计算下一班：加上频率间隔
                currentStationTrainTime = currentStationTrainTime.plusMinutes(freq.toLong())
            }

            return upcomingTrains

        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // 格式化显示 (例如 5 -> "5 min", 0 -> "Now", >60 -> "1 hr+")
    fun formatTimeDisplay(minutes: Long): String {
        return when {
            minutes < 0 -> "--"
            minutes == 0L -> "Now"
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "$minutes min"
        }
    }
}
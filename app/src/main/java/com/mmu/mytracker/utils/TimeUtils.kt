package com.mmu.mytracker.utils

import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {

    fun getMinutesUntilNextTrain(firstTrainStr: String?, freq: Int, offset: Int = 0): Long {
        val trains = getNextThreeTrains(firstTrainStr, freq, offset)
        return if (trains.isNotEmpty()) trains[0] else -1
    }

    fun getNextThreeTrains(firstTrainStr: String?, freq: Int, offset: Int = 0): List<Long> {
        try {
            if (firstTrainStr.isNullOrEmpty() || freq <= 0) return emptyList()

            val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
            val now = LocalTime.now(malaysiaZone)

            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val depotStartTime = LocalTime.parse(firstTrainStr, formatter)

            var currentStationTrainTime = depotStartTime.plusMinutes(offset.toLong())

            val upcomingTrains = mutableListOf<Long>()

            for (i in 0 until 500) {
                if (currentStationTrainTime.isAfter(now) || currentStationTrainTime == now) {
                    val minutesUntil = ChronoUnit.MINUTES.between(now, currentStationTrainTime)
                    upcomingTrains.add(minutesUntil)

                    if (upcomingTrains.size >= 3) break
                }

                currentStationTrainTime = currentStationTrainTime.plusMinutes(freq.toLong())
            }

            return upcomingTrains

        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun formatTimeDisplay(minutes: Long): String {
        return when {
            minutes < 0 -> "--"
            minutes == 0L -> "Now"
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "$minutes min"
        }
    }
}
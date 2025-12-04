package com.mmu.mytracker.data.remote.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    // 修改 StationRepository.kt

    suspend fun getServicesForStation(stationName: String): List<StationService> {
        // 1. 定义一个模拟数据的函数
        fun getMockServices(name: String): List<StationService> {
            val lowerName = name.lowercase()
            return when {
                // 只要名字里有 kajang 就返回这些服务
                lowerName.contains("kajang") -> listOf(
                    StationService("1", "MRT Kajang Line", "MRT", "To Kwasa Damansara"),
                    StationService("2", "Bus 450", "BUS", "To Pudu")
                )
                // 只要名字里有 sentral 就返回这些
                lowerName.contains("sentral") -> listOf(
                    StationService("3", "LRT Kelana Jaya", "LRT", "To Gombak"),
                    StationService("4", "KTM Seremban", "KTM", "To Batu Caves")
                )
                else -> emptyList()
            }
        }

        return try {
            // ... (保留你原来的 Firebase 查询代码) ...

            val snapshot = db.collection("stations")
                .whereEqualTo("name", stationName)
                .get()
                .await()

            val servicesList = mutableListOf<StationService>()
            // ... (保留你原来的解析代码) ...

            // 🔥 修改这里：如果 Firebase 没数据，就返回 Mock 数据
            if (servicesList.isEmpty()) {
                getMockServices(stationName)
            } else {
                servicesList
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 出错时也返回 Mock 数据，方便测试
            getMockServices(stationName)
        }
    }
}
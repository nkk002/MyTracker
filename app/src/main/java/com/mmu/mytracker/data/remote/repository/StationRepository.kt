package com.mmu.mytracker.data.remote.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    // 模拟查询：实际项目中你应该用 Firebase 查询
    suspend fun getServicesForStation(stationName: String): List<StationService> {
        // 1. 尝试去 Firebase 查 (假设你有一个 'stations' 集合)
        // 这里的逻辑是：先去 Firebase 找，找不到就返回模拟数据
        return try {
            val snapshot = db.collection("stations")
                .whereEqualTo("name", stationName)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                // 如果 Firebase 有数据，解析出来 (这里简化处理)
                // 实际需要根据你的 Firebase 结构解析
                val services = mutableListOf<StationService>()
                for (doc in snapshot.documents) {
                    // 假设你的文档里有个 services 数组
                    // val list = doc.get("services") ...
                }
                // 暂时返回模拟数据，因为你的 Firebase 可能还没建好
                getMockServices(stationName)
            } else {
                getMockServices(stationName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getMockServices(stationName) // 出错也返回模拟数据
        }
    }

    // 核心：模拟数据，保证你的 App 演示时有东西看
    private fun getMockServices(name: String): List<StationService> {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("kajang") -> listOf(
                StationService("1", "MRT Kajang Line", "MRT", "To Kwasa Damansara"),
                StationService("2", "KTM Seremban Line", "KTM", "To KL Sentral"),
                StationService("3", "Bus 450", "BUS", "To Pudu Sentral")
            )
            lowerName.contains("kl cc") || lowerName.contains("klcc") -> listOf(
                StationService("4", "LRT Kelana Jaya Line", "LRT", "To Putra Heights")
            )
            lowerName.contains("sentral") -> listOf(
                StationService("5", "LRT Kelana Jaya Line", "LRT", "To Gombak"),
                StationService("6", "MRT Kajang Line", "MRT", "To Kajang"),
                StationService("7", "KLIA Express", "ERL", "To KLIA")
            )
            else -> emptyList() // 没匹配到服务
        }
    }
}
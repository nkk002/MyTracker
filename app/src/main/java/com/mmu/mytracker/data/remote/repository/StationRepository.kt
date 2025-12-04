package com.mmu.mytracker.data.remote.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getServicesForStation(stationName: String): List<StationService> {
        return try {
            // 1. 去 Firestore 的 'stations' 集合查找名字匹配的车站
            // 注意：这里假设你在 Firebase 里存的字段叫 'name'
            val snapshot = db.collection("stations")
                .whereEqualTo("name", stationName)
                .get()
                .await()

            val servicesList = mutableListOf<StationService>()

            // 2. 解析数据
            for (document in snapshot.documents) {
                // 假设你的 Firestore 文档里有一个叫 'services' 的 Map 列表
                val servicesData = document.get("services") as? List<Map<String, String>>

                servicesData?.forEach { serviceMap ->
                    servicesList.add(
                        StationService(
                            name = serviceMap["name"] ?: "",
                            type = serviceMap["type"] ?: "",
                            direction = serviceMap["direction"] ?: ""
                        )
                    )
                }
            }

            // 如果 Firebase 没数据，返回空列表 (或者你可以保留 Mock 作为备用)
            servicesList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
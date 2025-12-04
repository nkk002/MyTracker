package com.mmu.mytracker.data.remote.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getServicesForStation(stationName: String): List<StationService> {
        val servicesList = mutableListOf<StationService>()

        return try {
            Log.d("Firestore", "正在查询车站: $stationName")

            // 1. 去 Firestore 的 'stations' 集合查找名字匹配的车站
            val snapshot = db.collection("stations")
                .whereEqualTo("name", stationName)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d("Firestore", "找不到车站: $stationName。请确认Firebase里的 'name' 字段是否完全一致。")
                // 如果为了演示安全，这里可以取消注释调用 Mock 数据：
                // return getMockServices(stationName)
                return emptyList()
            }

            // 2. 解析数据
            for (document in snapshot.documents) {
                Log.d("Firestore", "找到文档ID: ${document.id}")

                // 获取 'services' 数组
                val servicesData = document.get("services") as? List<Map<String, String>>

                servicesData?.forEach { serviceMap ->
                    val newService = StationService(
                        // 使用安全调用，防止字段缺失
                        name = serviceMap["name"] ?: "Unknown Service",
                        type = serviceMap["type"] ?: "BUS",
                        direction = serviceMap["direction"] ?: ""
                    )
                    servicesList.add(newService)
                }
            }

            Log.d("Firestore", "解析完成，共 ${servicesList.size} 个服务")
            servicesList

        } catch (e: Exception) {
            Log.e("Firestore", "查询出错: ${e.message}")
            e.printStackTrace()
            // 出错时返回空列表
            emptyList()
        }
    }

    // (Mock 函数可以先删掉，或者留着备用)
}
package com.mmu.mytracker.data.remote.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    // 修改 app/src/main/java/com/mmu/mytracker/data/remote/repository/StationRepository.kt

    suspend fun getServicesForStation(stationName: String): List<StationService> {
        val servicesList = mutableListOf<StationService>()
        val searchKey = stationName.lowercase().trim() // 去掉首尾空格并转小写

        try {
            Log.d("FirestoreDebug", "=== 开始调试 ===")
            Log.d("FirestoreDebug", "Google请求的名字: [$stationName]")

            // 1. 哪怕只为了调试，也先获取所有车站看看 (生产环境不建议，但调试时非常有用)
            val allStationsSnapshot = db.collection("stations").get().await()

            Log.d("FirestoreDebug", "数据库里的 'stations' 集合共有 ${allStationsSnapshot.size()} 个文档")

            for (document in allStationsSnapshot.documents) {
                val dbName = document.getString("name") // 获取 'name' 字段
                val docId = document.id

                Log.d("FirestoreDebug", "--- 检查文档: ID=[$docId] ---")
                if (dbName == null) {
                    Log.e("FirestoreDebug", "❌ 严重错误: 这个文档没有 'name' 字段！请在Firebase里添加 'name' 字段。")
                } else {
                    Log.d("FirestoreDebug", "✅ 找到 'name' 字段: [$dbName]")

                    // 2. 尝试模糊匹配 (不区分大小写，且容忍部分匹配)
                    if (dbName.lowercase().trim() == searchKey) {
                        Log.d("FirestoreDebug", "🎯 匹配成功！！")

                        // 解析 services
                        val servicesData = document.get("services") as? List<Map<String, String>>
                        servicesData?.forEach { serviceMap ->
                            servicesList.add(
                                StationService(
                                    name = serviceMap["name"] ?: "Unknown",
                                    type = serviceMap["type"] ?: "BUS",
                                    direction = serviceMap["direction"] ?: ""
                                )
                            )
                        }
                    } else {
                        Log.d("FirestoreDebug", "⚠️ 匹配失败: [$dbName] != [$searchKey]")
                    }
                }
            }

            return servicesList

        } catch (e: Exception) {
            Log.e("FirestoreDebug", "连接错误: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    // (Mock 函数可以先删掉，或者留着备用)
}
package com.mmu.mytracker.data.remote.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
// 修复: 引入 Firebase 相关类
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
// 修复: 引入协程相关类
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await // 这里的 await 需要 kotlinx-coroutines-play-services 依赖
import com.mmu.mytracker.data.model.BusLocation
import com.mmu.mytracker.data.remote.api.RetrofitInstance
import com.mmu.mytracker.data.model.Leg

class TransportRepository {

    // 获取Firebase数据库实例
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    // 指向 "bus_locations" 根节点
    private val locationsRef: DatabaseReference = database.getReference("bus_locations")

    // 指向 "reports" 根节点
    private val reportsRef = database.getReference("reports")

    /**
     * 更新公交车位置
     */
    suspend fun updateBusLocation(busId: String, location: LatLng, routeId: String) {
        val busData = BusLocation(
            busId = busId,
            latitude = location.latitude,
            longitude = location.longitude,
            routeId = routeId,
            timestamp = System.currentTimeMillis()
        )

        try {
            // 修复: await() 需要 'kotlinx-coroutines-play-services' 依赖
            locationsRef.child(busId).setValue(busData).await()
            Log.d("TransportRepo", "Location updated for $busId")
        } catch (e: Exception) {
            Log.e("TransportRepo", "Error updating location", e)
            throw e
        }
    }

    /**
     * 实时监听特定公交车的位置变化
     */
    fun observeBusLocation(busId: String): Flow<BusLocation?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 将快照转换为 BusLocation 对象
                val location = snapshot.getValue(BusLocation::class.java)
                trySend(location)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransportRepo", "Firebase listen cancelled", error.toException())
                close(error.toException())
            }
        }

        // 注册监听器
        locationsRef.child(busId).addValueEventListener(listener)

        // 当 Flow 停止收集时移除监听器
        awaitClose {
            Log.d("TransportRepo", "Removing listener for $busId")
            locationsRef.child(busId).removeEventListener(listener)
        }
    }

    /**
     * 提交详细的用户报告
     */
    suspend fun submitReport(
        line: String,
        station: String,
        crowd: String,
        delay: String,
        comment: String,
    ): Boolean {
        return try {
            val reportId = reportsRef.push().key ?: return false

            val reportData = hashMapOf(
                "reportId" to reportId,
                "transportLine" to line,
                "station" to station, // 🔥 保存车站名字
                "crowdLevel" to crowd,
                "delayTime" to delay,
                "comment" to comment,
                "timestamp" to System.currentTimeMillis()
            )

            reportsRef.child(reportId).setValue(reportData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 调用 Google Directions API 获取路线
     */
    suspend fun getRoutePolyline(origin: String, destination: String, apiKey: String): String? {
        return try {
            val response = RetrofitInstance.api.getDirections(origin, destination, apiKey)
            // 修复逻辑: 检查 routes 列表不为空，并且取第一个元素的 overviewPolyline
            if (response.isSuccessful &&!response.body()?.routes.isNullOrEmpty()) {
                // 这里加了 first()，因为 routes 是一个 List
                response.body()!!.routes.first().overviewPolyline.points
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("TransportRepo", "Directions API error", e)
            null
        }
    }

    suspend fun getTripDetails(origin: String, destination: String, apiKey: String): Leg? {
        return try {
            // 这里我们显式传入 "subway" (代表 MRT/LRT)
            val response = RetrofitInstance.api.getDirections(
                origin = origin,
                destination = destination,
                apiKey = apiKey,
                mode = "transit",
                transitMode = "subway" // <--- 强制只看 MRT
            )

            if (response.isSuccessful && !response.body()?.routes.isNullOrEmpty()) {
                // 现在拿到的 First Route 肯定是 MRT 了
                response.body()!!.routes.first().legs.first()
            } else {
                // ... 错误处理保持不变
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    /**
     * 实时监听特定路线的报告 (Waze-style Alert 核心)
     * @param targetLine 用户当前关注的路线，例如 "MRT Kajang Line"
     */
    fun observeRealTimeReports(targetLine: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.callbackFlow {
        // 监听 30 分钟内的数据
        val thirtyMinsAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        val query = reportsRef.orderByChild("timestamp").startAt(thirtyMinsAgo.toDouble())

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val line = child.child("transportLine").getValue(String::class.java)
                    if (line == targetLine) {
                        val data = child.value as? Map<String, Any>
                        if (data != null) list.add(data)
                    }
                }
                // 🔥 重点：把整个 List 发送出去
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
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

class TransportRepository {

    // 获取Firebase数据库实例
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    // 指向 "bus_locations" 根节点
    private val locationsRef: DatabaseReference = database.getReference("bus_locations")

    // 指向 "reports" 根节点
    private val reportsRef: DatabaseReference = database.getReference("reports")

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
        transportLine: String,
        crowdLevel: String,
        delayMinutes: Int,
        comment: String
    ): Boolean {
        return try {
            val key = reportsRef.push().key?: return false

            val reportData = mapOf(
                "id" to key,
                "transportLine" to transportLine,
                "crowdLevel" to crowdLevel,
                "delayMinutes" to delayMinutes,
                "comment" to comment,
                "timestamp" to System.currentTimeMillis()
            )

            reportsRef.child(key).setValue(reportData).await()
            true
        } catch (e: Exception) {
            Log.e("TransportRepo", "Report submission failed", e)
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
    // ... (现有的代码)

    /**
     * 实时监听特定路线的报告 (Waze-style Alert 核心)
     * @param targetLine 用户当前关注的路线，例如 "MRT Kajang Line"
     */
    fun observeRealTimeReports(targetLine: String): Flow<Map<String, Any>?> = callbackFlow {
        // 只监听最近 30 分钟内的报告 (避免旧新闻弹出)
        val thirtyMinsAgo = System.currentTimeMillis() - (30 * 60 * 1000)

        val query = reportsRef.orderByChild("timestamp").startAt(thirtyMinsAgo.toDouble())

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val reportLine = child.child("transportLine").getValue(String::class.java)

                    // 核心过滤：只有当报告的路线 = 用户当前的路线，才通知
                    if (reportLine == targetLine) {
                        val reportData = child.value as? Map<String, Any>
                        trySend(reportData)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
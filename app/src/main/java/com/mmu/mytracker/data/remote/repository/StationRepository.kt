package com.mmu.mytracker.data.remote.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.data.model.Station
import com.mmu.mytracker.data.model.StationService
import kotlinx.coroutines.tasks.await

class StationRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllStations(): List<Station> {
        return try {
            val snapshot = db.collection("stations").get().await()
            // 将 Firestore document 自动转换为 Station 对象
            // 前提是你的 Firestore 字段名 (latitude, longitude, services) 和 Station data class 一致
            val stations = snapshot.toObjects(Station::class.java)

            Log.d("StationRepo", "Successfully fetched ${stations.size} stations of data")
            stations
        } catch (e: Exception) {
            Log.e("StationRepo", "Failed to fetch stations: ${e.message}")
            emptyList()
        }
    }

    // (旧的方法 getServicesForStation 可以保留作为备用，或者删掉)
    suspend fun getServicesForStation(stationName: String): List<StationService> {
        // ... (保留你原有的逻辑，如果还想支持纯名字搜索的话) ...
        return emptyList()
    }
}
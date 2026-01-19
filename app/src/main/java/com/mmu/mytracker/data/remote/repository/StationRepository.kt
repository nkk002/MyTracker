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
            val stations = snapshot.toObjects(Station::class.java)

            Log.d("StationRepo", "Successfully fetched ${stations.size} stations of data")
            stations
        } catch (e: Exception) {
            Log.e("StationRepo", "Failed to fetch stations: ${e.message}")
            emptyList()
        }
    }

    suspend fun getServicesForStation(stationName: String): List<StationService> {
        return emptyList()
    }
}
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

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private val locationsRef: DatabaseReference = database.getReference("bus_locations")

    private val reportsRef = database.getReference("reports")

    suspend fun updateBusLocation(busId: String, location: LatLng, routeId: String) {
        val busData = BusLocation(
            busId = busId,
            latitude = location.latitude,
            longitude = location.longitude,
            routeId = routeId,
            timestamp = System.currentTimeMillis()
        )

        try {
            locationsRef.child(busId).setValue(busData).await()
            Log.d("TransportRepo", "Location updated for $busId")
        } catch (e: Exception) {
            Log.e("TransportRepo", "Error updating location", e)
            throw e
        }
    }

    fun observeBusLocation(busId: String): Flow<BusLocation?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(BusLocation::class.java)
                trySend(location)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransportRepo", "Firebase listen cancelled", error.toException())
                close(error.toException())
            }
        }

        locationsRef.child(busId).addValueEventListener(listener)

        awaitClose {
            Log.d("TransportRepo", "Removing listener for $busId")
            locationsRef.child(busId).removeEventListener(listener)
        }
    }

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
                "station" to station,
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

    suspend fun getRoutePolyline(origin: String, destination: String, apiKey: String): String? {
        return try {
            val response = RetrofitInstance.api.getDirections(origin, destination, apiKey)
            if (response.isSuccessful &&!response.body()?.routes.isNullOrEmpty()) {
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
            val response = RetrofitInstance.api.getDirections(
                origin = origin,
                destination = destination,
                apiKey = apiKey,
                mode = "transit",
                transitMode = "subway"
            )

            if (response.isSuccessful && !response.body()?.routes.isNullOrEmpty()) {
                response.body()!!.routes.first().legs.first()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun observeRealTimeReports(targetLine: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.callbackFlow {
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
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
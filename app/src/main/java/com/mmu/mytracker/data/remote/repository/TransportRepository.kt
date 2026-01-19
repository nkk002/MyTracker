package com.mmu.mytracker.data.remote.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.mmu.mytracker.data.model.BusLocation
import com.mmu.mytracker.data.remote.api.RetrofitInstance
import com.mmu.mytracker.data.model.Leg

class TransportRepository {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private val reportsRef = database.getReference("reports")

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
    fun observeRealTimeReports(targetLine: String): Flow<List<Map<String, Any>>> = callbackFlow {
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
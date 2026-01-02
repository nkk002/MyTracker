package com.mmu.mytracker.ui.view.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.ui.adapter.AlertAdapter
import com.mmu.mytracker.utils.ActiveRouteManager
import com.mmu.mytracker.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()
    private val stationRepository = StationRepository()
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0

    private lateinit var alertAdapter: AlertAdapter
    private lateinit var recyclerAlerts: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        val destName = intent.getStringExtra("dest_name") ?: "Unknown Station"
        val serviceName = intent.getStringExtra("service_name") ?: "Transport Service"
        destLat = intent.getDoubleExtra("dest_lat", 0.0)
        destLng = intent.getDoubleExtra("dest_lng", 0.0)

        // UI 初始化
        findViewById<TextView>(R.id.tvHeaderTitle).text = destName
        findViewById<TextView>(R.id.tvServiceName).text = serviceName

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<CardView>(R.id.btnStartRoute).setOnClickListener {
            ActiveRouteManager.saveRoute(this, destName, serviceName, destLat, destLng)
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            // 🔥 新增：告诉 MainActivity 必须切回 Home Tab
            intent.putExtra("GO_TO_HOME", true)

            startActivity(intent)
            finish()
        }

        // 初始化 RecyclerView
        recyclerAlerts = findViewById(R.id.recyclerAlerts)
        recyclerAlerts.layoutManager = LinearLayoutManager(this)
        alertAdapter = AlertAdapter(emptyList())
        recyclerAlerts.adapter = alertAdapter

        // 启动逻辑
        startListeningForAlerts(serviceName, destName)
        fetchStationDetailsAndCalculateTime(destName, serviceName)

        // 启动每分钟刷新一次 UI
        startAutoRefreshAdapter()
    }

    private fun fetchStationDetailsAndCalculateTime(stationName: String, serviceName: String) {
        val tvTrain1Count = findViewById<TextView>(R.id.tvTrain1Countdown)
        val tvTrain1Arr = findViewById<TextView>(R.id.tvTrain1Arrival)
        val tvTrain2Count = findViewById<TextView>(R.id.tvTrain2Countdown)
        val tvTrain2Arr = findViewById<TextView>(R.id.tvTrain2Arrival)
        val tvTrain3Count = findViewById<TextView>(R.id.tvTrain3Countdown)
        val tvTrain3Arr = findViewById<TextView>(R.id.tvTrain3Arrival)

        tvTrain1Count.text = "..."
        tvTrain1Arr.text = "--:--"

        lifecycleScope.launch {
            try {
                val allStations = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }
                val station = allStations.find { it.name == stationName }

                if (station != null) {
                    val service = station.services.find {
                        it.name.equals(serviceName, ignoreCase = true) ||
                                it.type.equals(serviceName, ignoreCase = true) ||
                                serviceName.contains(it.type, ignoreCase = true)
                    }

                    if (service != null) {
                        val nextTrains = TimeUtils.getNextThreeTrains(service.first_train, service.frequency_min)
                        val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
                        val now = LocalTime.now(malaysiaZone)
                        val formatter = DateTimeFormatter.ofPattern("hh:mm a")

                        if (nextTrains.isNotEmpty()) {
                            tvTrain1Count.text = TimeUtils.formatTimeDisplay(nextTrains[0])
                            tvTrain1Arr.text = if (nextTrains[0] >= 0) now.plusMinutes(nextTrains[0]).format(formatter) else "N/A"
                        } else {
                            tvTrain1Count.text = "--"
                            tvTrain1Arr.text = "--"
                        }

                        if (nextTrains.size >= 2) {
                            tvTrain2Count.text = TimeUtils.formatTimeDisplay(nextTrains[1])
                            tvTrain2Arr.text = if (nextTrains[1] >= 0) now.plusMinutes(nextTrains[1]).format(formatter) else "N/A"
                        } else {
                            tvTrain2Count.text = "--"
                            tvTrain2Arr.text = "--"
                        }

                        if (nextTrains.size >= 3) {
                            tvTrain3Count.text = TimeUtils.formatTimeDisplay(nextTrains[2])
                            tvTrain3Arr.text = if (nextTrains[2] >= 0) now.plusMinutes(nextTrains[2]).format(formatter) else "N/A"
                        } else {
                            tvTrain3Count.text = "--"
                            tvTrain3Arr.text = "--"
                        }

                    } else {
                        tvTrain1Count.text = "--"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvTrain1Count.text = "Err"
            }
        }
    }

    private fun startListeningForAlerts(userSelectedLine: String, currentStationName: String) {
        lifecycleScope.launch {
            transportRepository.observeRealTimeReports(userSelectedLine).collect { allReports ->
                val relevantReports = allReports.filter { report ->
                    val station = report["station"] as? String ?: "General"
                    val timestamp = report["timestamp"] as? Long ?: 0L
                    val isNotExpired = (System.currentTimeMillis() - timestamp) < (30 * 60 * 1000)
                    val isMatch = station.contains("General", ignoreCase = true) ||
                            station.equals(currentStationName, ignoreCase = true)
                    isMatch && isNotExpired
                }

                val sortedReports = relevantReports.sortedByDescending {
                    it["timestamp"] as? Long ?: 0L
                }

                if (sortedReports.isNotEmpty()) {
                    recyclerAlerts.visibility = View.VISIBLE
                    alertAdapter.updateList(sortedReports)
                } else {
                    recyclerAlerts.visibility = View.GONE
                }
            }
        }
    }

    private fun startAutoRefreshAdapter() {
        lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (::alertAdapter.isInitialized && recyclerAlerts.visibility == View.VISIBLE) {
                    alertAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}
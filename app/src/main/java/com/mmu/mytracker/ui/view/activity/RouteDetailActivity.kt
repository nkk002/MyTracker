package com.mmu.mytracker.ui.view.activity

import android.content.Intent
import android.os.Bundle
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
import com.mmu.mytracker.ui.adapter.AlertAdapter // 记得 Import 新建的 Adapter
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

    // 🔥 新增变量：Adapter 和 RecyclerView
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
            startActivity(intent)
            finish()
        }

        // 🔥 1. 初始化 RecyclerView
        recyclerAlerts = findViewById(R.id.recyclerAlerts)
        recyclerAlerts.layoutManager = LinearLayoutManager(this)
        alertAdapter = AlertAdapter(emptyList()) // 初始为空
        recyclerAlerts.adapter = alertAdapter

        // 启动逻辑
        startListeningForAlerts(serviceName, destName)
        fetchStationDetailsAndCalculateTime(destName, serviceName)

        // 🔥 2. 启动每分钟刷新一次 UI (为了更新 "x mins ago")
        startAutoRefreshAdapter()
    }

    // 这个函数保持不变
    private fun fetchStationDetailsAndCalculateTime(stationName: String, serviceName: String) {
        // ... (保持原本的逻辑) ...
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
        val tvArrival = findViewById<TextView>(R.id.tvArrival)
        tvNextTrain.text = "Loading..."
        tvArrival.text = "--:--"

        lifecycleScope.launch {
            try {
                val allStations = withContext(Dispatchers.IO) { stationRepository.getAllStations() }
                val station = allStations.find { it.name == stationName }
                if (station != null) {
                    val service = station.services.find {
                        it.name.equals(serviceName, ignoreCase = true) ||
                                it.type.equals(serviceName, ignoreCase = true) ||
                                serviceName.contains(it.type, ignoreCase = true)
                    }
                    if (service != null) {
                        val mins = TimeUtils.getMinutesUntilNextTrain(service.first_train, service.frequency_min)
                        val timeStr = TimeUtils.formatTimeDisplay(mins)
                        tvNextTrain.text = timeStr
                        if (mins >= 0) {
                            val now = LocalTime.now(ZoneId.of("Asia/Kuala_Lumpur"))
                            val arrivalTime = now.plusMinutes(mins)
                            tvArrival.text = arrivalTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
                        } else { tvArrival.text = "N/A" }
                    } else { tvNextTrain.text = "--"; tvArrival.text = "--" }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startListeningForAlerts(userSelectedLine: String, currentStationName: String) {
        lifecycleScope.launch {
            // 注意：TransportRepository.observeRealTimeReports 需要返回 List<Map>
            // 之前的步骤里我们已经把它改成了 return List
            transportRepository.observeRealTimeReports(userSelectedLine).collect { allReports ->

                // 1. 筛选 (General 或 当前车站)
                val relevantReports = allReports.filter { report ->
                    val station = report["station"] as? String ?: "General"
                    val timestamp = report["timestamp"] as? Long ?: 0L

                    // 检查是否过期 (30分钟)
                    val isNotExpired = (System.currentTimeMillis() - timestamp) < (30 * 60 * 1000)

                    val isMatch = station.contains("General", ignoreCase = true) ||
                            station.equals(currentStationName, ignoreCase = true)

                    isMatch && isNotExpired
                }

                // 2. 🔥 排序：最新的在上面 (Descending)
                val sortedReports = relevantReports.sortedByDescending {
                    it["timestamp"] as? Long ?: 0L
                }

                // 3. 更新 UI
                if (sortedReports.isNotEmpty()) {
                    recyclerAlerts.visibility = View.VISIBLE
                    alertAdapter.updateList(sortedReports)
                } else {
                    recyclerAlerts.visibility = View.GONE
                }
            }
        }
    }

    // 🔥 3. 自动刷新时间显示的简单实现
    private fun startAutoRefreshAdapter() {
        lifecycleScope.launch {
            while (isActive) { // 只要页面还在
                delay(60000) // 等 60 秒
                if (::alertAdapter.isInitialized && recyclerAlerts.visibility == View.VISIBLE) {
                    // 通知 Adapter 刷新界面 (更新 x mins ago)
                    alertAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}
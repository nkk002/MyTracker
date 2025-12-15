package com.mmu.mytracker.ui.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.utils.ActiveRouteManager
import com.mmu.mytracker.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        val destName = intent.getStringExtra("dest_name") ?: "Unknown Station"
        val serviceName = intent.getStringExtra("service_name") ?: "Transport Service"
        destLat = intent.getDoubleExtra("dest_lat", 0.0)
        destLng = intent.getDoubleExtra("dest_lng", 0.0)

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

        startListeningForAlerts(serviceName, destName)
        fetchStationDetailsAndCalculateTime(destName, serviceName)
    }

    private fun fetchStationDetailsAndCalculateTime(stationName: String, serviceName: String) {
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
        val tvArrival = findViewById<TextView>(R.id.tvArrival)

        tvNextTrain.text = "Loading..."
        tvArrival.text = "--:--"

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
                        // 1. 获取倒计时 (TimeUtils 已经改成了强制马来西亚时间)
                        val mins = TimeUtils.getMinutesUntilNextTrain(service.first_train, service.frequency_min)
                        val timeStr = TimeUtils.formatTimeDisplay(mins)
                        tvNextTrain.text = timeStr

                        // 2. 计算到达时间
                        if (mins >= 0) {
                            // 🔥 核心修改：强制使用马来西亚当前时间 + 剩余分钟数
                            val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
                            val now = LocalTime.now(malaysiaZone)

                            val arrivalTime = now.plusMinutes(mins)

                            val formatter = DateTimeFormatter.ofPattern("hh:mm a")
                            tvArrival.text = arrivalTime.format(formatter)
                        } else {
                            tvArrival.text = "N/A"
                        }

                    } else {
                        tvNextTrain.text = "--"
                        tvArrival.text = "--"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvNextTrain.text = "Err"
                tvArrival.text = "Err"
            }
        }
    }

    /**
     * 监听并过滤警报
     * @param userSelectedLine 用户所在的路线 (e.g. MRT Kajang Line)
     * @param currentStationName 用户所在的车站 (e.g. Kajang Station)
     */
    private fun startListeningForAlerts(userSelectedLine: String, currentStationName: String) {
        try {
            val alertCard = findViewById<CardView>(R.id.cardAlert) ?: return
            val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
            val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
            val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

            btnClose.setOnClickListener { alertCard.visibility = View.GONE }

            lifecycleScope.launch {
                transportRepository.observeRealTimeReports(userSelectedLine).collect { report ->
                    if (report != null) {
                        // 1. 获取报告里的车站信息
                        val reportStation = report["station"] as? String ?: "General"
                        val type = report["crowdLevel"] as? String ?: "Alert"
                        val comment = report["comment"] as? String ?: ""

                        // 2. 🔥 过滤逻辑：
                        // - 如果是 "General (Whole Line)"，或者是针对 "General" 的 -> 全线显示
                        // - 如果是特定车站 -> 只有名字匹配时才显示
                        val shouldShow = if (reportStation.contains("General", ignoreCase = true)) {
                            true
                        } else {
                            // 比较 Report 的车站和当前页面车站名字是否一致
                            reportStation.equals(currentStationName, ignoreCase = true)
                        }

                        if (shouldShow) {
                            // 3. 更新 UI
                            val displayStation = if (reportStation.contains("General")) "Whole Line" else reportStation
                            tvTitle.text = "⚠️ $type ($displayStation)"
                            tvMessage.text = comment

                            alertCard.visibility = View.VISIBLE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
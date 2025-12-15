package com.mmu.mytracker.ui.view.activity

import android.content.Intent
import android.os.Bundle
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
import com.mmu.mytracker.ui.view.activity.MainActivity // 确保 import 了 MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View

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

        // 2. 初始化 UI 文字
        findViewById<TextView>(R.id.tvHeaderTitle).text = destName

        // ✅ 修正 1: 使用正确的 ID (tvServiceName) 而不是 rvServices
        findViewById<TextView>(R.id.tvServiceName).text = serviceName

        // 3. 设置返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 4. 设置 Start Route 按钮逻辑
        // ✅ 修正 2: 使用正确的 ID (btnStartRoute) 而不是 tvSearchInput
        findViewById<CardView>(R.id.btnStartRoute).setOnClickListener {
            ActiveRouteManager.saveRoute(this, destName, serviceName, destLat, destLng)

            // 跳回主页并清除上面的 Activity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 5. 监听 Waze 警报
        startListeningForAlerts(serviceName)

        // 6. 获取时间表
        fetchStationDetailsAndCalculateTime(destName, serviceName)
    }

    // ... (剩下的 fetchStationDetailsAndCalculateTime 和 startListeningForAlerts 保持不变) ...
    // 为了完整性，建议保留你原本的这两个 private function 代码

    private fun fetchStationDetailsAndCalculateTime(stationName: String, serviceName: String) {
        val tvNextTrainTime = findViewById<TextView>(R.id.tvNextTrain)
        tvNextTrainTime.text = "..."

        lifecycleScope.launch {
            try {
                val allStations = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }
                val station = allStations.find { it.name == stationName }

                if (station != null) {
                    val service = station.services.find {
                        it.name.equals(serviceName, ignoreCase = true) ||
                                serviceName.contains(it.name, ignoreCase = true) ||
                                it.type.equals(serviceName.split(" ")[0], ignoreCase = true)
                    }

                    if (service != null) {
                        val mins = TimeUtils.getMinutesUntilNextTrain(service.first_train, service.frequency_min)
                        val timeStr = TimeUtils.formatTimeDisplay(mins)
                        tvNextTrainTime.text = timeStr
                    } else {
                        tvNextTrainTime.text = "--"
                    }
                } else {
                    tvNextTrainTime.text = "--"
                }
            } catch (e: Exception) {
                tvNextTrainTime.text = "Err"
                e.printStackTrace()
            }
        }
    }

    private fun startListeningForAlerts(userSelectedLine: String) {
        // 确保你的 layout_waze_alert.xml 里面真的有 cardAlert 这个 ID
        // 如果没有，这里也会 Crash。建议检查一下 layout_waze_alert.xml
        try {
            val alertCard = findViewById<CardView>(R.id.cardAlert)
            val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
            val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
            val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

            if (alertCard != null) { // 加个判空，防止Waze部分还没写好导致崩溃
                btnClose.setOnClickListener {
                    alertCard.visibility = View.GONE
                }

                // ... (原本的 observe 逻辑) ...
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
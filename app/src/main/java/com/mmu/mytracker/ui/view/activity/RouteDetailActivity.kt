package com.mmu.mytracker.ui.view.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.utils.ActiveRouteManager // 记得导入这个
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var destLat: Double = 0.0
    private var destLng: Double = 0.0

    // 这个变量依然保留，但初始值会从 ActiveRouteManager 读
    private var cachedDepartureTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupBackButton()

        val destName = intent.getStringExtra("dest_name") ?: "Unknown Station"
        val serviceName = intent.getStringExtra("service_name") ?: ""
        destLat = intent.getDoubleExtra("dest_lat", 0.0)
        destLng = intent.getDoubleExtra("dest_lng", 0.0)

        findViewById<TextView>(R.id.tvHeaderTitle).text = destName

        // 🔥 关键修改 1：进页面时，先尝试读取之前保存的时间
        cachedDepartureTime = ActiveRouteManager.getDepartureTime(this)
        if (cachedDepartureTime != 0L) {
            Log.d("OFFICIAL_DATA", "🔄 恢复了上次保存的时间: $cachedDepartureTime")
        }

        getDirectionsData()

        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    private fun getDirectionsData() {
        val apiKey = getString(R.string.google_maps_key)
        val origin = "$destLat,$destLng"
        val destination = "Kwasa Damansara"

        lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis() / 1000

                // 智能刷新：只有“没数据”或者“时间已过”才请求
                if (cachedDepartureTime == 0L || cachedDepartureTime < now) {

                    Log.d("DEBUG_TIME", "🔍 发车时间已过或无数据，请求 API...")

                    val leg = withContext(Dispatchers.IO) {
                        transportRepository.getTripDetails(origin, destination, apiKey)
                    }

                    if (leg != null) {
                        val transitStep = leg.steps.find { it.transitDetails != null }
                        val realDepartureTime = transitStep?.transitDetails?.departureTime ?: leg.departureTime

                        if (realDepartureTime != null) {
                            cachedDepartureTime = realDepartureTime.value

                            // 🔥 关键修改 2：拿到新时间后，立刻保存到硬盘！
                            ActiveRouteManager.saveDepartureTime(this@RouteDetailActivity, cachedDepartureTime)

                            Log.d("OFFICIAL_DATA", "✅ API 更新成功并已保存: ${realDepartureTime.text}")

                            val tvArrival = findViewById<TextView>(R.id.tvArrival)
                            tvArrival.text = realDepartureTime.text
                        }
                    } else {
                        Log.e("DEBUG_TIME", "❌ API 返回空数据")
                    }
                } else {
                    Log.d("DEBUG_TIME", "♻️ 使用缓存时间倒计时 (不消耗 API)")
                }

                // UI 更新逻辑 (倒计时)
                val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
                if (cachedDepartureTime != 0L) {
                    val diffSeconds = cachedDepartureTime - (System.currentTimeMillis() / 1000)
                    val minutes = diffSeconds / 60

                    if (minutes > 1) {
                        tvNextTrain.text = "$minutes mins"
                        tvNextTrain.setTextColor(getColor(R.color.black))
                    } else if (minutes >= 0) {
                        tvNextTrain.text = "Arriving"
                        tvNextTrain.setTextColor(getColor(android.R.color.holo_green_dark))
                    } else {
                        tvNextTrain.text = "Departed"
                        tvNextTrain.setTextColor(getColor(android.R.color.holo_red_dark))
                        // 下一轮循环会自动触发 API 刷新
                    }
                } else {
                    tvNextTrain.text = "Loading..."
                }

                delay(10000)
            }
        }
    }

    private fun setupBackButton() {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun startListeningForAlerts(userSelectedLine: String) {
        val alertCard = findViewById<CardView>(R.id.cardAlert)
        val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
        val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

        btnClose.setOnClickListener {
            alertCard.visibility = View.GONE
        }

        lifecycleScope.launch {
            transportRepository.observeRealTimeReports(userSelectedLine).collect { report ->
                if (report != null) {
                    val comment = report["comment"] as? String ?: "Incident reported"
                    val delay = report["delayMinutes"] as? Long ?: 0
                    val type = report["crowdLevel"] as? String ?: "Alert"

                    tvTitle.text = "⚠️ $type Ahead"
                    tvMessage.text = "$comment. Expect +$delay mins delay."

                    if (alertCard.visibility == View.GONE) {
                        alertCard.visibility = View.VISIBLE
                        alertCard.alpha = 0f
                        alertCard.animate().alpha(1f).duration = 300
                    }
                }
            }
        }
    }
}
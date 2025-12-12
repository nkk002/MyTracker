package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 存储目标车站坐标
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0

    // 【新增】用来缓存上一班车的发车时间戳 (秒)
    private var cachedDepartureTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupBackButton()

        // 1. 获取 Intent 数据
        val destName = intent.getStringExtra("dest_name") ?: "Unknown Station"
        val serviceName = intent.getStringExtra("service_name") ?: ""
        destLat = intent.getDoubleExtra("dest_lat", 0.0)
        destLng = intent.getDoubleExtra("dest_lng", 0.0)

        // 设置标题
        findViewById<TextView>(R.id.tvHeaderTitle).text = destName

        // 2. 开始获取真实时间数据
        // 注意：这里不需要先获取定位了，因为是“车站看板模式”，直接用车站坐标查
        getDirectionsData()

        // 3. 开启 Crowdsource 监听
        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    // 移除了 fetchRealTimeData，直接使用 getDirectionsData
    private fun getDirectionsData() {
        val apiKey = getString(R.string.google_maps_key)

        // 【关键】起点设为车站坐标，模拟“我就在车站”
        val origin = "$destLat,$destLng"
        // 终点 (建议后续从上个页面传过来，这里暂用硬编码演示)
        val destination = "Kwasa Damansara"

        lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis() / 1000 // 当前手机时间(秒)

                // =============================================================
                // 🚀 核心逻辑：智能刷新
                // 只有当 "没有缓存数据" 或者 "这班车已经开走了(cached < now)" 时，才请求 API
                // =============================================================
                if (cachedDepartureTime == 0L || cachedDepartureTime < now) {

                    Log.d("DEBUG_TIME", "🔍 发车时间已过或无数据，正在请求 Google API 获取下一班...")

                    val leg = withContext(Dispatchers.IO) {
                        transportRepository.getTripDetails(origin, destination, apiKey)
                    }

                    if (leg != null) {
                        // 1. 尝试从详细步骤中找到 "TRANSIT" (地铁/公交) 的那一步
                        val transitStep = leg.steps.find { it.transitDetails != null }

                        // 2. 优先使用 Transit 里的时间 (列车时刻表)，找不到才用 Leg 时间
                        val realDepartureTime = transitStep?.transitDetails?.departureTime ?: leg.departureTime

                        if (realDepartureTime != null) {
                            // ✅ 成功拿到新班次！存入缓存！
                            cachedDepartureTime = realDepartureTime.value
                            Log.d("OFFICIAL_DATA", "✅ API 更新成功: 下一班车是 ${realDepartureTime.text} (时间戳: $cachedDepartureTime)")

                            // 🔴 修改点：现在右边的 Arrival 显示的是“本站发车时间”，而不是“终点站到达时间”
                            val tvArrival = findViewById<TextView>(R.id.tvArrival)
                            tvArrival.text = realDepartureTime.text
                        }
                    } else {
                        Log.e("DEBUG_TIME", "❌ API 返回空数据 (可能是深夜没车或网络问题)")
                    }
                } else {
                    // 如果缓存的时间还没过，就跳过 API 请求，只在本地做倒计时
                    Log.d("DEBUG_TIME", "♻️ 使用缓存数据进行倒计时 (无需请求 API)")
                }

                // =============================================================
                // ⏰ UI 倒计时更新 (这一步每次循环都会跑，负责计算剩余分钟)
                // =============================================================
                val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)

                if (cachedDepartureTime != 0L) {
                    val diffSeconds = cachedDepartureTime - (System.currentTimeMillis() / 1000)
                    val minutes = diffSeconds / 60

                    Log.d("OFFICIAL_DATA", "UI 更新: 剩余 $minutes mins")

                    if (minutes > 1) {
                        tvNextTrain.text = "$minutes mins"
                        tvNextTrain.setTextColor(getColor(R.color.black))
                    } else if (minutes >= 0) {
                        // 剩 0 或 1 分钟
                        tvNextTrain.text = "Arriving"
                        tvNextTrain.setTextColor(getColor(android.R.color.holo_green_dark))
                    } else {
                        // 变成了负数 (车走了)
                        tvNextTrain.text = "Departed"
                        tvNextTrain.setTextColor(getColor(android.R.color.holo_red_dark))

                        // 【注意】显示 Departed 后，
                        // 下一次循环 (10秒后)，因为 cachedDepartureTime < now，
                        // 上面的 if 判断会自动成立，从而触发 API 请求获取下一班车！
                    }
                } else {
                    tvNextTrain.text = "Loading..."
                }

                // 改为每 10 秒刷新一次 UI，倒计时更流畅
                // 这不会浪费 API 次数，因为上面的 if 会拦截不必要的网络请求
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
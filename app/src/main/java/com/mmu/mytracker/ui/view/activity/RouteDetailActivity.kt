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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 存储目标车站坐标
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0

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
        fetchRealTimeData()

        // 3. 开启 Crowdsource 监听 (你原本的逻辑)
        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    private fun fetchRealTimeData() {
        // 检查定位权限
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission needed for ETA", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取当前位置
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // 拿到当前位置后，请求 API
                getDirectionsData(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "Cannot get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDirectionsData(currentLat: Double, currentLng: Double) {
        val apiKey = getString(R.string.google_maps_key)
        val origin = "$currentLat,$currentLng"
        val destination = "$destLat,$destLng"

        // 使用协程开启一个循环
        lifecycleScope.launch {
            // isActive 保证了当用户退出这个页面时，循环会自动停止，不会浪费电和流量
            while (isActive) {
                Log.d("DEBUG_TIME", "Refreshing data...")

                // 1. 请求数据 (切到 IO 线程)
                val leg = withContext(Dispatchers.IO) {
                    transportRepository.getTripDetails(origin, destination, apiKey)
                }

                // 2. 更新 UI (主线程)
                if (leg != null) {
                    val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
                    val tvArrival = findViewById<TextView>(R.id.tvArrival)

                    // ... (这里放您原本的更新 UI 代码: 更新 tvArrival 和 tvNextTrain) ...

                    // 示例:
                    if (leg.arrivalTime != null) {
                        tvArrival.text = leg.arrivalTime.text
                    }
                    // ... 更新 Next Train 逻辑 ...
                }

                // 3. 等待 60 秒再进行下一次刷新
                // Google API 是收费的，建议不要刷得太快，1分钟一次刚刚好
                delay(60000)
            }
        }
    }

    private fun setupBackButton() {
        // ... (保持你之前的代码不变)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    // ... (保持 startListeningForAlerts 代码不变)
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
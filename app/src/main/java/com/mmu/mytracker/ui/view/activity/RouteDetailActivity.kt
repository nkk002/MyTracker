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

        lifecycleScope.launch {
            while (isActive) { // 循环开始
                Log.d("DEBUG_TIME", "正在刷新时间数据...")

                // 1. 请求数据 (IO线程)
                val leg = withContext(Dispatchers.IO) {
                    // 记得确认这里是否加了 transitMode = "subway" 参数
                    transportRepository.getTripDetails(origin, destination, apiKey)
                }

                // 2. 更新 UI (主线程)
                if (leg != null) {
                    val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
                    val tvArrival = findViewById<TextView>(R.id.tvArrival)

                    // --- A. 更新 Arrival Time (到达时间) ---
                    if (leg.arrivalTime != null) {
                        tvArrival.text = leg.arrivalTime.text
                    } else {
                        tvArrival.text = "Walk"
                    }

                    // --- B. 更新 Next Train (这里是之前缺失的逻辑) ---
                    if (leg.departureTime != null) {
                        val diffSeconds = leg.departureTime.value - (System.currentTimeMillis() / 1000)
                        val minutes = diffSeconds / 60

                        if (minutes > 0) {
                            tvNextTrain.text = "$minutes mins"     // 还有 X 分钟
                        } else if (minutes >= -1) {
                            tvNextTrain.text = "Arriving"          // 刚刚到 (0 到 -1 分钟)
                        } else {
                            tvNextTrain.text = "Departed"          // 已经走了 (小于 -1 分钟)
                            // 提示：如果变成 Departed，通常需要等待 API 返回下一班车的数据
                        }
                    } else {
                        tvNextTrain.text = "Now"
                    }
                } else {
                    Log.e("DEBUG_TIME", "获取数据失败 (Leg is null)")
                }

                // 3. 等待 60 秒 (每分钟刷新一次)
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
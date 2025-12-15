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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()
    private val stationRepository = StationRepository()
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0

    // 🔥 1. 新增变量：用来控制自动过期的定时器
    private val expirationHandler = Handler(Looper.getMainLooper())
    private var expirationRunnable: Runnable? = null
    private var currentAlertTimestamp: Long = 0L // 记录当前显示警报的时间

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

        // 监听警报
        startListeningForAlerts(serviceName, destName)

        // 获取时间表
        fetchStationDetailsAndCalculateTime(destName, serviceName)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出页面时，销毁定时器，防止内存泄漏
        expirationRunnable?.let { expirationHandler.removeCallbacks(it) }
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
                        val mins = TimeUtils.getMinutesUntilNextTrain(service.first_train, service.frequency_min)
                        val timeStr = TimeUtils.formatTimeDisplay(mins)
                        tvNextTrain.text = timeStr

                        if (mins >= 0) {
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

    private fun startListeningForAlerts(userSelectedLine: String, currentStationName: String) {
        try {
            val alertCard = findViewById<CardView>(R.id.cardAlert) ?: return
            val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
            val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
            val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

            btnClose.setOnClickListener { alertCard.visibility = View.GONE }

            // 🔥 2. 启动自动检查循环
            startExpirationCheckLoop(alertCard)

            lifecycleScope.launch {
                transportRepository.observeRealTimeReports(userSelectedLine).collect { report ->
                    if (report != null) {
                        val reportStation = report["station"] as? String ?: "General"
                        val type = report["crowdLevel"] as? String ?: "Alert"
                        val comment = report["comment"] as? String ?: ""
                        val timestamp = report["timestamp"] as? Long ?: System.currentTimeMillis()
                        val delay = report["delayTime"] as? String ?: "0"

                        // 🔥 3. 记录当前警报的时间戳
                        currentAlertTimestamp = timestamp

                        // 计算“几分钟前”
                        val currentTime = System.currentTimeMillis()
                        val diffMillis = currentTime - timestamp
                        val minsAgo = diffMillis / (1000 * 60)

                        // 🔴 核心判断：如果已经超过 30 分钟，直接忽略，根本不显示
                        if (minsAgo > 30) {
                            alertCard.visibility = View.GONE
                            return@collect
                        }

                        val timeDisplay = if (minsAgo < 1) "Just now" else "$minsAgo mins ago"

                        // 过滤逻辑
                        val shouldShow = if (reportStation.contains("General", ignoreCase = true)) {
                            true
                        } else {
                            reportStation.equals(currentStationName, ignoreCase = true)
                        }

                        if (shouldShow) {
                            val displayStation = if (reportStation.contains("General")) "Whole Line" else reportStation

                            // 🔥 优化显示格式
                            // 标题: ⚠️ Crowd: High (Station) • 5 mins ago
                            tvTitle.text = "⚠️ Crowd level: $type • $timeDisplay\n"

                            // 内容: Comment (+ 10 mins delay)
                            val builder = SpannableStringBuilder()

                            // 2. 先放入普通的 comment
                            builder.append(comment)
                            builder.append("\n") // 换行

                            // 3. 记录开始变粗的位置
                            val start = builder.length

                            // 4. 放入要变粗的文字
                            builder.append("\n(Estimate Delay: +$delay mins)")

                            // 5. 设置粗体 (Bold)
                            builder.setSpan(
                                StyleSpan(Typeface.BOLD),
                                start,
                                builder.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            // 6. 显示出来
                            tvMessage.text = builder

                            if (alertCard.visibility == View.GONE) {
                                alertCard.visibility = View.VISIBLE
                                alertCard.alpha = 0f
                                alertCard.animate().alpha(1f).duration = 300
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
    }

    // 🔥 4. 自动过期检查逻辑
    private fun startExpirationCheckLoop(alertCard: CardView) {
        expirationRunnable = object : Runnable {
            override fun run() {
                try {
                    // 如果当前没有在显示警报，就不需要检查
                    if (alertCard.visibility == View.VISIBLE && currentAlertTimestamp > 0) {

                        val now = System.currentTimeMillis()
                        val diffMinutes = (now - currentAlertTimestamp) / (1000 * 60)

                        // 如果超过 30 分钟 -> 自动消失
                        if (diffMinutes > 30) {
                            alertCard.visibility = View.GONE
                            // 也可以选择不移除回调，继续跑，等待下一个警报
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // 每 60 秒 (1分钟) 检查一次
                    expirationHandler.postDelayed(this, 60000)
                }
            }
        }
        // 立即启动
        expirationHandler.post(expirationRunnable!!)
    }
}
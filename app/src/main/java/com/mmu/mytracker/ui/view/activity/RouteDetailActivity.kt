package com.mmu.mytracker.ui.view.activity

import android.os.Bundle
import android.view.View // 修复: visibility (View.GONE/VISIBLE) 需要这个
import android.widget.EditText
import android.widget.ImageButton // 修复: ImageButton 爆红
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView // 修复: CardView 爆红
import androidx.lifecycle.lifecycleScope // 修复: lifecycleScope 爆红
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository // 修复: Repository 引用
import kotlinx.coroutines.launch // 修复: launch 爆红

class RouteDetailActivity : AppCompatActivity() {

    // 修复: 之前缺少了这个变量的声明，导致 transportRepository 爆红
    private val transportRepository = TransportRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        // 1. 获取传递过来的数据
        val destName = intent.getStringExtra("dest_name") ?: "Unknown"
        val serviceName = intent.getStringExtra("service_name") ?: ""

        // 2. 绑定 UI
        val tvDest = findViewById<TextView>(R.id.tvDestination)
        val etCurrent = findViewById<EditText>(R.id.etCurrentLocation)

        tvDest.text = destName

        // 模拟：如果是 Kajang，默认 Current Location 填 Stadium Kajang
        if (destName.contains("Kajang")) {
            etCurrent.setText("MRT Stadium Kajang")
        } else {
            etCurrent.setText("My Current Location")
        }

        // 3. 动态更新 Info Card
        updateRealTimeInfo(serviceName)

        // 4. 启动 Waze 风格的警告监听 (重要：之前忘了在这里调用它)
        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    private fun updateRealTimeInfo(serviceName: String) {
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
        // 简单模拟
        tvNextTrain.text = "4 mins"
    }

    // 监听 Waze 风格的警告
    private fun startListeningForAlerts(userSelectedLine: String) {
        val alertCard = findViewById<CardView>(R.id.cardAlert)
        val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
        val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

        // 关闭按钮逻辑
        btnClose.setOnClickListener {
            alertCard.visibility = View.GONE
        }

        // 开始监听 Firebase
        lifecycleScope.launch {
            transportRepository.observeRealTimeReports(userSelectedLine).collect { report ->
                if (report != null) {
                    // 解析报告内容
                    val comment = report["comment"] as? String ?: "Incident reported"
                    val delay = report["delayMinutes"] as? Long ?: 0
                    val type = report["crowdLevel"] as? String ?: "Alert"

                    // 更新 UI
                    tvTitle.text = "⚠️ $type Ahead"
                    tvMessage.text = "$comment. Expect +$delay mins delay."

                    // 显示弹窗 (带一点动画)
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
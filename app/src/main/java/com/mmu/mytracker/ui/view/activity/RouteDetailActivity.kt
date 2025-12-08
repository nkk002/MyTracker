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
import com.mmu.mytracker.data.remote.repository.TransportRepository
import kotlinx.coroutines.launch

class RouteDetailActivity : AppCompatActivity() {

    private val transportRepository = TransportRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        setupBackButton()

        // 1. 获取 Intent 传递的数据
        val destName = intent.getStringExtra("dest_name") ?: "Unknown Station"
        val serviceName = intent.getStringExtra("service_name") ?: ""

        // 2. [修改点] 将标题设置为车站名字 (因为移除了下方的显示栏)
        val tvHeader = findViewById<TextView>(R.id.tvHeaderTitle)
        tvHeader.text = destName

        // 3. 更新时间信息
        updateRealTimeInfo(serviceName)

        // 4. [关键点] 开启 Crowdsource 监听
        // 当有人提交报告时，这里会收到通知并弹出 Alert
        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    private fun setupBackButton() {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            // 返回到主页 (而不是搜索页)
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun updateRealTimeInfo(serviceName: String) {
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
        // 这里只是示例，之后你可以连接 API 获取真实时间
        tvNextTrain.text = "4 mins"
    }

    // 监听 Crowdsource 报告的逻辑 (Waze Style Popup)
    private fun startListeningForAlerts(userSelectedLine: String) {
        val alertCard = findViewById<CardView>(R.id.cardAlert)
        val tvTitle = findViewById<TextView>(R.id.tvAlertTitle)
        val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseAlert)

        btnClose.setOnClickListener {
            alertCard.visibility = View.GONE
        }

        lifecycleScope.launch {
            // 这里监听你在 TransportRepository 写好的 Flow
            transportRepository.observeRealTimeReports(userSelectedLine).collect { report ->
                if (report != null) {
                    val comment = report["comment"] as? String ?: "Incident reported"
                    val delay = report["delayMinutes"] as? Long ?: 0
                    val type = report["crowdLevel"] as? String ?: "Alert"

                    // 更新 UI 内容
                    tvTitle.text = "⚠️ $type Ahead"
                    tvMessage.text = "$comment. Expect +$delay mins delay."

                    // 显示弹窗动画
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
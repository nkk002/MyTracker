package com.mmu.mytracker.ui.view.activity

import android.content.Intent // 记得加这个 Import
import android.os.Bundle
import android.view.View
import android.widget.EditText
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

        // --- 新增：设置返回按钮 ---
        setupBackButton()

        // ... (其他初始化代码保持不变) ...

        val destName = intent.getStringExtra("dest_name") ?: "Unknown"
        val serviceName = intent.getStringExtra("service_name") ?: ""

        val tvDest = findViewById<TextView>(R.id.tvDestination)
        val etCurrent = findViewById<EditText>(R.id.etCurrentLocation)

        tvDest.text = destName

        if (destName.contains("Kajang")) {
            etCurrent.setText("MRT Stadium Kajang")
        } else {
            etCurrent.setText("My Current Location")
        }

        updateRealTimeInfo(serviceName)

        if (serviceName.isNotEmpty()) {
            startListeningForAlerts(serviceName)
        }
    }

    // --- 新增函数：处理返回逻辑 ---
    private fun setupBackButton() {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            // 选项 A: 只是关闭当前页面，返回到 SearchActivity (标准做法)
            // finish()

            // 选项 B: 彻底关闭，直接回到 MainActivity (Homepage) - 推荐用于此场景
            // 因为通常用户看完路线想回主页看地图，而不是回搜索页
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun updateRealTimeInfo(serviceName: String) {
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)
        tvNextTrain.text = "4 mins"
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
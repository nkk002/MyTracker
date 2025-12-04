package com.mmu.mytracker.ui.view.activity

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mmu.mytracker.R

class RouteDetailActivity : AppCompatActivity() {

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

        // 3. 动态更新 Info Card (模拟来自 Firebase 的实时数据)
        updateRealTimeInfo(serviceName)
    }

    private fun updateRealTimeInfo(serviceName: String) {
        val tvNextTrain = findViewById<TextView>(R.id.tvNextTrain)

        // 这里可以接入 Firebase 监听实时数据
        // 现在先用静态展示
        tvNextTrain.text = "4 mins"
    }
}
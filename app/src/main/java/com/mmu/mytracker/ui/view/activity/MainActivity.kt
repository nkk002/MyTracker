package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mmu.mytracker.R
import com.mmu.mytracker.ui.view.fragment.ReportBottomSheetFragment
import com.mmu.mytracker.utils.ActiveRouteManager

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化地图
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 2. 初始化各UI组件
        setupSearchBar()
        setupLiveTrackingCard()
        setupBottomNavigation() // 🔥 新增：设置底部导航栏逻辑
    }

    override fun onResume() {
        super.onResume()
        // 每次回到主页，检查是否有保存的路线，如果有则显示 Live Tracking 卡片
        updateLiveTrackingCard()

        // 确保选中 Live Tracking 选项 (因为我们在这个页面)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_home
    }

    // --- 🗺️ 地图逻辑 ---
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    // --- 🔍 搜索栏逻辑 ---
    private fun setupSearchBar() {
        val searchCard = findViewById<CardView>(R.id.search_card)
        searchCard.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }
    }

    // --- 🧭 底部导航栏逻辑 ---
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { // Live Tracking
                    // 已经在主页了，不需要做额外操作，或者可以将地图视角移回当前位置
                    true
                }
                R.id.nav_report -> { // Crowdsource Report
                    // 弹出报告窗口
                    val bottomSheet = ReportBottomSheetFragment()
                    bottomSheet.show(supportFragmentManager, "ReportBottomSheet")
                    // 返回 false 表示虽然点击了，但不切换选中状态 (或者你可以根据需求让它选中)
                    // 这里我们返回 false，让它保持在 "Live Tracking" 选中状态，因为 Report 只是个弹窗
                    false
                }
                else -> false
            }
        }
    }

    // --- 🔴 Live Tracking 卡片逻辑 ---
    private fun setupLiveTrackingCard() {
        val cardLive = findViewById<CardView>(R.id.cardLiveTracking)
        val btnClose = findViewById<ImageButton>(R.id.btnCloseLive)

        // 点击卡片 -> 跳转到 RouteDetailActivity (详情页)
        cardLive.setOnClickListener {
            val routeData = ActiveRouteManager.getRoute(this)
            if (routeData != null) {
                val intent = Intent(this, RouteDetailActivity::class.java)
                intent.putExtra("dest_name", routeData["destName"] as String)
                intent.putExtra("service_name", routeData["serviceName"] as String)
                intent.putExtra("dest_lat", routeData["destLat"] as Double)
                intent.putExtra("dest_lng", routeData["destLng"] as Double)
                startActivity(intent)
            }
        }

        // 点击叉叉 -> 删除路线并隐藏卡片
        btnClose.setOnClickListener {
            ActiveRouteManager.clearRoute(this)
            cardLive.visibility = View.GONE
            Toast.makeText(this, "Route cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLiveTrackingCard() {
        val cardLive = findViewById<CardView>(R.id.cardLiveTracking)
        val tvStationName = findViewById<TextView>(R.id.tvLiveStationName)

        val routeData = ActiveRouteManager.getRoute(this)

        if (routeData != null) {
            cardLive.visibility = View.VISIBLE
            tvStationName.text = routeData["destName"] as String
        } else {
            cardLive.visibility = View.GONE
        }
    }
}
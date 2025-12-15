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
import android.widget.FrameLayout
import com.mmu.mytracker.ui.view.fragment.NearbyFragment

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

        // 获取界面上的 View
        val mapFragmentView = findViewById<View>(R.id.mapFragment)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)
        val searchCard = findViewById<CardView>(R.id.search_card)
        val liveTrackingCard = findViewById<CardView>(R.id.cardLiveTracking) // 获取旧的卡片，切页面时最好隐藏它

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // 🗺️ 情况 1: 点击地图 (Home)
                R.id.nav_home -> {
                    // 显示地图和搜索栏
                    mapFragmentView.visibility = View.VISIBLE
                    searchCard.visibility = View.VISIBLE

                    // 隐藏 Nearby 页面
                    fragmentContainer.visibility = View.GONE

                    // 如果有正在进行的路线，恢复显示 Live Tracking Card (可选)
                    val routeData = ActiveRouteManager.getRoute(this)
                    if (routeData != null) {
                        liveTrackingCard.visibility = View.VISIBLE
                    }
                    true
                }

                // 🚉 情况 2: 点击 Nearby Stations (新增)
                R.id.nav_nearby -> {
                    // 隐藏地图、搜索栏和悬浮卡片
                    mapFragmentView.visibility = View.GONE
                    searchCard.visibility = View.GONE
                    liveTrackingCard.visibility = View.GONE

                    // 显示 Nearby 容器
                    fragmentContainer.visibility = View.VISIBLE

                    // 加载 NearbyFragment
                    // 注意：为了避免重复加载，可以先判断是否已经添加
                    val existingFragment = supportFragmentManager.findFragmentByTag("NearbyFragment")
                    if (existingFragment == null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, NearbyFragment(), "NearbyFragment")
                            .commit()
                    }
                    true
                }

                // 📝 情况 3: 点击 Report
                R.id.nav_report -> {
                    val bottomSheet = ReportBottomSheetFragment()
                    bottomSheet.show(supportFragmentManager, "ReportBottomSheet")
                    false // 返回 false 表示不选中这个 tab，只弹窗
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
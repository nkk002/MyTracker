package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.maps.android.PolyUtil
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.ui.view.fragment.ReportBottomSheetFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient // 1. 新增定位客户端
    private val repository = TransportRepository()
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // 定义 Launcher 来接收 SearchActivity 返回的结果
    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val lat = data?.getDoubleExtra("selected_lat", 0.0)
            val lng = data?.getDoubleExtra("selected_lng", 0.0)
            val name = data?.getStringExtra("selected_name")

            if (lat != null && lng != null && lat != 0.0) {
                val location = LatLng(lat, lng)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                map.clear()
                map.addMarker(MarkerOptions().position(location).title(name))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 2. 初始化定位服务
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        setupFakeSearchBar()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBottomNavigation()
    }

    private fun setupFakeSearchBar() {
        val fakeSearch = findViewById<TextView>(R.id.tvFakeSearch)
        fakeSearch.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            searchLauncher.launch(intent)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // 点击 Home 时，如果有权限，也重新聚焦到当前位置
                    getDeviceLocation()
                    true
                }
                R.id.nav_report -> {
                    showReportBottomSheet()
                    false
                }
                else -> false
            }
        }
    }

    private fun showReportBottomSheet() {
        val reportFragment = ReportBottomSheetFragment()
        reportFragment.show(supportFragmentManager, "ReportBottomSheet")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // 开启定位图层
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            // 3. 权限已有，直接获取位置并移动镜头
            getDeviceLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * 4. 核心函数：获取设备当前位置并移动地图
     */
    private fun getDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // 将地图移动到检测到的位置
                        val lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude),
                                    15f // 缩放级别
                                )
                            )
                        }
                    } else {
                        Log.d("Map", "Current location is null. Using defaults.")
                        // 如果获取不到位置（比如模拟器没设置位置），可以移动到默认城市（如吉隆坡）
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(3.1579, 101.7123), 12f))
                        map.uiSettings.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 5. 用户刚点了“允许”，开启图层并获取位置
                enableMyLocation()
            } else {
                Toast.makeText(this, "Location permissions are required to use the map function.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
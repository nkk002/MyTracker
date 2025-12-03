package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.maps.android.PolyUtil
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.ui.view.fragment.ReportBottomSheetFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val repository = TransportRepository()
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化 Places SDK (用于搜索地址和巴士站)
        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        // 2. 设置搜索栏监听器
        setupAutocompleteFragment()

        // 3. 加载地图
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 4. 设置底部导航
        setupBottomNavigation()
    }

    /**
     * 设置顶部的搜索栏功能
     */
    // 在 MainActivity 类中

// 定义一个 Launcher 来接收结果
    private val searchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
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

        // ... 其他初始化代码 ...

        // 替换掉原来的 setupAutocompleteFragment()
        setupFakeSearchBar()
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
                    if (::map.isInitialized) {
                        //点击 Home 回到吉隆坡中心
                        val cityCenter = LatLng(3.1579, 101.7123)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(cityCenter, 12f))
                    }
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
        enableMyLocation()

        // 注意：我把 drawDemoRoute() 暂时注释掉了。
        // 因为有了搜索栏，我们通常不希望一进来就画一条固定的线，而是等待用户搜索。
        // drawDemoRoute()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    //保留这个函数作为工具，以后可以用它画用户搜索的路线
    private fun drawDemoRoute() {
        lifecycleScope.launch {
            val origin = "KL Sentral"
            val destination = "Mid Valley Megamall"
            val apiKey = getString(R.string.google_maps_key)

            val encodedPolyline = repository.getRoutePolyline(origin, destination, apiKey)

            if (encodedPolyline!= null) {
                val pathPoints: List<LatLng> = PolyUtil.decode(encodedPolyline)
                val polylineOptions = PolylineOptions()
                    .addAll(pathPoints)
                    .width(15f)
                    .color(Color.BLUE)
                    .geodesic(true)

                map.addPolyline(polylineOptions)
                if (pathPoints.isNotEmpty()) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.first(), 14f))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0]== PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Location permissions are required to use the map function.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
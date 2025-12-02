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
    private fun setupAutocompleteFragment() {
        // 这里的 R.id.autocomplete_fragment 必须在 activity_main.xml 里存在
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as? AutocompleteSupportFragment?: return

        // 限制搜索范围为马来西亚 (MY)，这样搜巴士站更准
        autocompleteFragment.setCountry("MY")

        // 设定我们需要获取的数据：地点ID，名字，经纬度
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

        // 监听用户选中的地点
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // 用户选中地点后：
                place.latLng?.let { latLng ->
                    // 1. 移动镜头
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                    // 2. 清除旧标记并添加新标记
                    map.clear()
                    map.addMarker(MarkerOptions().position(latLng).title(place.name))

                    // 3. (可选) 如果你想自动画路线，可以在这里调用 drawRouteTo(latLng)
                    // 目前我们仅显示标记
                }
            }

            override fun onError(status: Status) {
                Log.e("Search", "搜索出错: $status")
                Toast.makeText(this@MainActivity, "搜索出错: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })
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
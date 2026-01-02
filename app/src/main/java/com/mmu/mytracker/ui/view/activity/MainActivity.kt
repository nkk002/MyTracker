package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.api.RetrofitInstance
import com.mmu.mytracker.ui.view.fragment.NearbyFragment
import com.mmu.mytracker.ui.view.fragment.ReportBottomSheetFragment
import com.mmu.mytracker.utils.ActiveRouteManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Tracking UI 变量
    private lateinit var cardTracking: CardView
    private lateinit var tvStationName: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvEta: TextView
    private lateinit var btnClose: ImageButton

    // Tracking 状态变量
    private var locationCallback: LocationCallback? = null
    private var currentDestinationMarker: Marker? = null
    private var currentRouteLine: Polyline? = null

    // 🔥 防止重复请求 API 的标记
    private var isRouteFetched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        setupBottomNavigation()
    }

    private fun setupUI() {
        // Search Bar
        val cardSearch = findViewById<CardView>(R.id.search_card)
        cardSearch?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // Tracking Card
        cardTracking = findViewById(R.id.cardLiveTracking)
        tvStationName = findViewById(R.id.tvLiveStationName)
        tvDistance = findViewById(R.id.tvLiveDistance)
        tvEta = findViewById(R.id.tvLiveEta)
        btnClose = findViewById(R.id.btnCloseLive)

        btnClose.setOnClickListener { stopTracking() }
    }

    override fun onResume() {
        super.onResume()
        checkActiveTracking()
    }

    private fun checkActiveTracking() {
        val route = ActiveRouteManager.getRoute(this)

        if (route != null) {
            val name = route["destName"] as? String ?: "Unknown Station"
            cardTracking.visibility = View.VISIBLE
            tvStationName.text = name
            startLocationUpdates()
        } else {
            cardTracking.visibility = View.GONE
            stopLocationUpdates()
        }
    }

    private fun stopTracking() {
        ActiveRouteManager.clearRoute(this)
        cardTracking.visibility = View.GONE
        stopLocationUpdates()

        currentDestinationMarker?.remove()
        currentDestinationMarker = null
        currentRouteLine?.remove()
        currentRouteLine = null
        isRouteFetched = false // 重置，下次可以重新请求

        Toast.makeText(this, "Navigation Stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        }
        checkActiveTracking()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    updateTrackingLogic(location)
                }
            }
            val request = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    private fun updateTrackingLogic(userLoc: Location) {
        val route = ActiveRouteManager.getRoute(this) ?: return

        val destLat = route["destLat"] as? Double ?: 0.0
        val destLng = route["destLng"] as? Double ?: 0.0
        val name = route["destName"] as? String ?: "Station"

        val destLoc = Location("destination").apply { latitude = destLat; longitude = destLng }

        // 1. 只更新距离 (本地计算距离非常快且免费)
        val distanceMeters = userLoc.distanceTo(destLoc)
        val distanceKm = distanceMeters / 1000
        tvDistance.text = String.format("%.2f km", distanceKm)

        // ❌ 删除或注释掉下面这两行 (这就是导致时间不准的罪魁祸首！)
        // val etaMins = (distanceMeters / 500).toInt()
        // tvEta.text = if (etaMins < 1) "Arriving" else "$etaMins min"

        // 2. 地图操作
        if (::map.isInitialized) {
            val userLatLng = LatLng(userLoc.latitude, userLoc.longitude)
            val destLatLng = LatLng(destLat, destLng)

            if (currentDestinationMarker == null) {
                currentDestinationMarker = map.addMarker(
                    MarkerOptions().position(destLatLng).title(name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            }

            // 🔥 如果还没获取过路线，去请求 API
            if (!isRouteFetched) {
                fetchAndDrawRoute(userLatLng, destLatLng)
            }
        }
    }

    // 🔥 核心：请求 Directions API 并显示真实时间
    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        isRouteFetched = true

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${dest.latitude},${dest.longitude}"
        val apiKey = getString(R.string.google_maps_key)

        lifecycleScope.launch {
            try {
                // 调用 API (确保 DirectionsApiService 里的 mode="driving" 或 "walking")
                val response = RetrofitInstance.api.getDirections(originStr, destStr, apiKey)

                if (response.isSuccessful && response.body() != null) {
                    val routes = response.body()!!.routes
                    if (routes.isNotEmpty()) {
                        val route = routes[0]

                        // 1. 画线
                        val encodedString = route.overviewPolyline.points
                        val path = decodePolyline(encodedString)

                        if (currentRouteLine != null) currentRouteLine?.remove()
                        val polylineOptions = PolylineOptions()
                            .addAll(path)
                            .width(15f)
                            .color(Color.BLUE)
                            .geodesic(true)
                        currentRouteLine = map.addPolyline(polylineOptions)

                        // 🔥 2. 获取 Google 计算的精准时间
                        if (route.legs.isNotEmpty()) {
                            val leg = route.legs[0]
                            val googleDuration = leg.duration.text // 例如 "15 mins"

                            // 更新界面显示
                            tvEta.text = googleDuration
                        }

                    } else {
                        Log.e("Directions", "No routes found")
                    }
                } else {
                    Log.e("Directions", "API Error: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRouteFetched = false
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val mapFragmentView = findViewById<View>(R.id.mapFragment)
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)
        val searchCard = findViewById<CardView>(R.id.search_card)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    mapFragmentView.visibility = View.VISIBLE
                    searchCard?.visibility = View.VISIBLE
                    fragmentContainer.visibility = View.GONE
                    checkActiveTracking()
                    true
                }
                R.id.nav_nearby -> {
                    mapFragmentView.visibility = View.GONE
                    searchCard?.visibility = View.GONE
                    cardTracking.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE

                    val existingFragment = supportFragmentManager.findFragmentByTag("NearbyFragment")
                    if (existingFragment == null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, NearbyFragment(), "NearbyFragment")
                            .commit()
                    }
                    true
                }
                R.id.nav_report -> {
                    ReportBottomSheetFragment().show(supportFragmentManager, "ReportBottomSheet")
                    false
                }
                else -> false
            }
        }
    }
}
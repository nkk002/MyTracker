package com.mmu.mytracker.ui.view.activity

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    private lateinit var cardTracking: CardView
    private lateinit var tvStationName: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvEta: TextView
    private lateinit var btnClose: ImageButton

    private var locationCallback: LocationCallback? = null
    private var currentDestinationMarker: Marker? = null
    private var currentRouteLine: Polyline? = null

    private var isRouteFetched = false
    private var lastStationName: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(this, "Location permission is required to show your position", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        setupBottomNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setupUI() {
        val cardSearch = findViewById<CardView>(R.id.search_card)
        cardSearch?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        cardTracking = findViewById(R.id.cardLiveTracking)
        tvStationName = findViewById(R.id.tvLiveStationName)
        tvDistance = findViewById(R.id.tvLiveDistance)
        tvEta = findViewById(R.id.tvLiveEta)
        btnClose = findViewById(R.id.btnCloseLive)

        cardTracking.setOnClickListener {
            val route = ActiveRouteManager.getRoute(this)
            if (route != null) {
                val intent = Intent(this, RouteDetailActivity::class.java)
                intent.putExtra("dest_name", route["destName"] as? String)
                intent.putExtra("service_name", route["serviceName"] as? String)
                intent.putExtra("dest_lat", route["destLat"] as? Double ?: 0.0)
                intent.putExtra("dest_lng", route["destLng"] as? Double ?: 0.0)
                startActivity(intent)
            }
        }

        btnClose.setOnClickListener { stopTracking() }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra("GO_TO_HOME", false)) {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            if (bottomNav.selectedItemId != R.id.nav_home) {
                bottomNav.selectedItemId = R.id.nav_home
            }
            intent.removeExtra("GO_TO_HOME")
        }
        checkActiveTracking()
    }

    private fun checkActiveTracking() {
        val route = ActiveRouteManager.getRoute(this)

        if (route != null) {
            val currentStationName = route["destName"] as? String ?: "Unknown Station"

            if (currentStationName != lastStationName) {
                isRouteFetched = false
                lastStationName = currentStationName
                currentRouteLine?.remove()
                currentRouteLine = null
                currentDestinationMarker?.remove()
                currentDestinationMarker = null
                tvEta.text = "-- min"
            }

            cardTracking.visibility = View.VISIBLE
            tvStationName.text = currentStationName
            startLocationUpdates()
        } else {
            cardTracking.visibility = View.GONE
            stopLocationUpdates()
            lastStationName = null
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

        isRouteFetched = false
        lastStationName = null

        Toast.makeText(this, "Navigation Stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        checkActiveTracking()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        map.isMyLocationEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true

        if (ActiveRouteManager.getRoute(this) == null) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } else {
                    // val defaultKL = LatLng(3.1390, 101.6869)
                    // map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultKL, 10f))
                }
            }
        }
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

        val distanceMeters = userLoc.distanceTo(destLoc)
        val distanceKm = distanceMeters / 1000
        tvDistance.text = String.format("%.2f km", distanceKm)

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

            if (!isRouteFetched) {
                fetchAndDrawRoute(userLatLng, destLatLng)
            }
        }
    }

    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        isRouteFetched = true

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${dest.latitude},${dest.longitude}"
        val apiKey = getString(R.string.google_maps_key)

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getDirections(originStr, destStr, apiKey)

                if (response.isSuccessful && response.body() != null) {
                    val routes = response.body()!!.routes
                    if (routes.isNotEmpty()) {
                        val route = routes[0]

                        val encodedString = route.overviewPolyline.points
                        val path = decodePolyline(encodedString)

                        if (currentRouteLine != null) currentRouteLine?.remove()
                        val polylineOptions = PolylineOptions()
                            .addAll(path)
                            .width(15f)
                            .color(Color.BLUE)
                            .geodesic(true)
                        currentRouteLine = map.addPolyline(polylineOptions)

                        if (route.legs.isNotEmpty()) {
                            val leg = route.legs[0]
                            val googleDuration = leg.duration.text
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
                R.id.nav_feedback -> {
                    val intent = Intent(this, FeedbackActivity::class.java)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
    }
}
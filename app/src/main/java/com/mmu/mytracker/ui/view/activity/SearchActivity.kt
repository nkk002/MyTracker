package com.mmu.mytracker.ui.view.activity

import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.RecentPlace
import com.mmu.mytracker.data.model.Station
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.ui.adapter.RecentSearchAdapter
import com.mmu.mytracker.ui.view.fragment.ServiceSelectionBottomSheet
import com.mmu.mytracker.utils.ActiveRouteManager
import com.mmu.mytracker.utils.SearchHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var historyManager: SearchHistoryManager
    private lateinit var adapter: RecentSearchAdapter
    private val stationRepository = StationRepository()

    private val autocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    handleSelectedPlace(place)
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                result.data?.let { intent ->
                    val status = Autocomplete.getStatusFromIntent(intent)
                    Toast.makeText(this, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            Activity.RESULT_CANCELED -> {
                // User canceled
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        historyManager = SearchHistoryManager(this)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        setupRecyclerView()
        setupFakeSearchBar()
        setupBackButton()

        if (savedInstanceState == null) {
            startGoogleSearch()
        }
    }

    private fun setupBackButton() {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerRecentSearches)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 初始化 Adapter
        adapter = RecentSearchAdapter(
            historyManager.getHistory().toMutableList(),
            onItemClick = { clickedPlace ->
                // 🔥 1. 点击历史记录 -> 触发像 Google Search 一样的逻辑
                handleHistoryClick(clickedPlace)
            },
            onDeleteClick = { placeToDelete ->
                // 🔥 2. 点击删除 -> 从 SharedPrefs 移除
                historyManager.removePlace(placeToDelete) // 记得在 Manager 里加这个方法
                adapter.updateData(historyManager.getHistory())
            }
        )
        recyclerView.adapter = adapter
    }

    private fun handleHistoryClick(recentPlace: RecentPlace) {
        val fakePlace = Place.builder()
            .setName(recentPlace.name)
            .setAddress(recentPlace.address)
            .setLatLng(com.google.android.gms.maps.model.LatLng(recentPlace.lat, recentPlace.lng))
            .setPlaceTypes(listOf("transit_station")) // 假装它是车站，触发后续逻辑
            .build()

        handleSelectedPlace(fakePlace)
    }

    private fun setupFakeSearchBar() {
        findViewById<TextView>(R.id.tvSearchInput).setOnClickListener {
            startGoogleSearch()
        }
    }

    private fun startGoogleSearch() {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES
        )

        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("MY"))
            .build(this)

        autocompleteLauncher.launch(intent)
    }

    private fun handleSelectedPlace(place: Place) {
        val googlePlaceName = place.name ?: "Unknown"
        val userLat = place.latLng?.latitude ?: 0.0
        val userLng = place.latLng?.longitude ?: 0.0

        val placeTypes = place.placeTypes ?: emptyList()
        val strictTransportTypes = setOf("transit_station", "bus_station", "train_station", "subway_station", "light_rail_station")
        val transportKeywords = listOf("mrt", "lrt", "ktm", "station", "stesen", "sentral", "terminal", "bus stop")

        val isTransportRelated = placeTypes.any { it in strictTransportTypes } ||
                transportKeywords.any { googlePlaceName.lowercase().contains(it) }

        if (isTransportRelated) {
            lifecycleScope.launch {
                Toast.makeText(this@SearchActivity, "Finding nearest station...", Toast.LENGTH_SHORT).show()

                // Step A: 准备用户选中的位置
                val selectedLocation = Location("user_selected").apply {
                    latitude = userLat
                    longitude = userLng
                }

                // Step B: 获取 Firestore 所有车站
                val allStations = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }

                // Step C: 寻找最近的车站 (500米范围内)
                var nearestStation: Station? = null
                var minDistance = Float.MAX_VALUE
                val MATCH_THRESHOLD_METERS = 500f

                for (station in allStations) {
                    val stationLocation = Location("firestore_station").apply {
                        latitude = station.latitude
                        longitude = station.longitude
                    }

                    val distance = selectedLocation.distanceTo(stationLocation)

                    if (distance <= MATCH_THRESHOLD_METERS && distance < minDistance) {
                        minDistance = distance
                        nearestStation = station
                    }
                }

                // Step D: 处理结果
                if (nearestStation != null) {
                    val officialName = nearestStation.name
                    val services = nearestStation.services

                    if (services.isNotEmpty()) {
                        val bottomSheet = ServiceSelectionBottomSheet(officialName, services) { selectedService ->

                            // ❌ [DELETE] 这一段被删除了！不要在这里保存路线！
                            // ActiveRouteManager.saveRoute(...)

                            // ✅ [KEEP] 只保留跳转逻辑，把数据传给 RouteDetailActivity
                            val intent = Intent(this@SearchActivity, RouteDetailActivity::class.java)
                            intent.putExtra("dest_name", officialName)
                            intent.putExtra("dest_lat", nearestStation.latitude)
                            intent.putExtra("dest_lng", nearestStation.longitude)
                            intent.putExtra("service_name", selectedService.name)
                            startActivity(intent)

                            // 保存搜索历史 (View Only)
                            val recent = RecentPlace(googlePlaceName, place.address ?: "", userLat, userLng)
                            historyManager.savePlace(recent)
                        }
                        bottomSheet.show(supportFragmentManager, "ServiceSelection")
                    } else {
                        Toast.makeText(this@SearchActivity, "Station found but no services configured.", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    // 没找到匹配的车站
                    Toast.makeText(this@SearchActivity, "No supported station found nearby (within 500m).", Toast.LENGTH_LONG).show()

                    val recent = RecentPlace(googlePlaceName, place.address ?: "", userLat, userLng)
                    historyManager.savePlace(recent)
                    returnResult(recent.name, recent.lat, recent.lng)
                }
            }
        } else {
            Toast.makeText(this, "Please select a valid Transport Station", Toast.LENGTH_LONG).show()
        }
    }

    private fun returnResult(name: String, lat: Double, lng: Double) {
        val intent = Intent()
        intent.putExtra("selected_name", name)
        intent.putExtra("selected_lat", lat)
        intent.putExtra("selected_lng", lng)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
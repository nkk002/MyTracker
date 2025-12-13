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

    // 定义 Google 搜索启动器
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
                // 用户取消搜索，不做操作
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

        // 如果是第一次进入，自动弹出搜索框
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

        adapter = RecentSearchAdapter(historyManager.getHistory()) { clickedPlace ->
            // 点击历史记录，直接返回结果
            returnResult(clickedPlace.name, clickedPlace.lat, clickedPlace.lng)
        }
        recyclerView.adapter = adapter
    }

    private fun setupFakeSearchBar() {
        findViewById<TextView>(R.id.tvSearchInput).setOnClickListener {
            startGoogleSearch()
        }
    }

    private fun startGoogleSearch() {
        // 请求 ID, Name, LatLng, Types
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

    /**
     * 🔥 核心修改：处理用户选中的地点
     * 不再对比名字，而是对比坐标距离 (Distance Matching)
     */
    private fun handleSelectedPlace(place: Place) {
        val googlePlaceName = place.name ?: "Unknown"
        val userLat = place.latLng?.latitude ?: 0.0
        val userLng = place.latLng?.longitude ?: 0.0

        // 1. 初步筛选：是否是交通相关地点 (保留原逻辑作为第一道防线)
        val placeTypes = place.placeTypes ?: emptyList()
        val strictTransportTypes = setOf("transit_station", "bus_station", "train_station", "subway_station", "light_rail_station")
        val transportKeywords = listOf("mrt", "lrt", "ktm", "station", "stesen", "sentral", "terminal", "bus stop")

        val isTransportRelated = placeTypes.any { it in strictTransportTypes } ||
                transportKeywords.any { googlePlaceName.lowercase().contains(it) }

        if (isTransportRelated) {
            // 开始寻找最近的车站
            lifecycleScope.launch {
                Toast.makeText(this@SearchActivity, "Finding nearest station...", Toast.LENGTH_SHORT).show()

                // Step A: 准备用户选中的位置对象
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
                    // 🎉 匹配成功！(比如用户选了 Gate A，我们找到了主车站)
                    val officialName = nearestStation.name
                    val services = nearestStation.services

                    if (services.isNotEmpty()) {
                        // 弹出 BottomSheet 供用户选择服务
                        val bottomSheet = ServiceSelectionBottomSheet(officialName, services) { selectedService ->

                            // 保存路线 (使用官方车站坐标，而非用户点击的坐标，这样更准)
                            ActiveRouteManager.saveRoute(
                                this@SearchActivity,
                                officialName,
                                selectedService.name,
                                nearestStation.latitude,
                                nearestStation.longitude
                            )

                            // 跳转到详情页
                            val intent = Intent(this@SearchActivity, RouteDetailActivity::class.java)
                            intent.putExtra("dest_name", officialName)
                            intent.putExtra("dest_lat", nearestStation.latitude)
                            intent.putExtra("dest_lng", nearestStation.longitude)
                            intent.putExtra("service_name", selectedService.name)
                            startActivity(intent)

                            // 保存到历史记录 (显示用户搜的名字，但保存官方坐标)
                            val recent = RecentPlace(googlePlaceName, place.address ?: "", userLat, userLng)
                            historyManager.savePlace(recent)
                        }
                        bottomSheet.show(supportFragmentManager, "ServiceSelection")
                    } else {
                        Toast.makeText(this@SearchActivity, "Station found but no services configured.", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    //  没找到匹配的车站
                    Toast.makeText(this@SearchActivity, "No supported station found nearby (within 500m).", Toast.LENGTH_LONG).show()

                    // 依旧作为普通地点保存历史
                    val recent = RecentPlace(googlePlaceName, place.address ?: "", userLat, userLng)
                    historyManager.savePlace(recent)
                    returnResult(recent.name, recent.lat, recent.lng)
                }
            }
        } else {
            // ❌ 如果选的根本不是车站 (比如选了 KFC)
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
package com.mmu.mytracker.ui.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.RecentPlace
import com.mmu.mytracker.ui.adapter.RecentSearchAdapter
import com.mmu.mytracker.utils.SearchHistoryManager
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.ui.view.fragment.ServiceSelectionBottomSheet
import com.mmu.mytracker.ui.view.activity.RouteDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// 添加这些 imports 到文件顶部
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var historyManager: SearchHistoryManager
    private lateinit var adapter: RecentSearchAdapter

    // 1. 定义 Google 搜索启动器
    private val autocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                // 用户选中了地点
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    handleSelectedPlace(place)
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                // 发生错误
                result.data?.let { intent ->
                    val status = Autocomplete.getStatusFromIntent(intent)
                    Toast.makeText(this, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            Activity.RESULT_CANCELED -> {
                // 用户取消了搜索（按了返回键），停留在 SearchActivity 显示历史记录
                // 不做任何操作
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        historyManager = SearchHistoryManager(this)

        // 初始化 Places (防止 MainActivity 没初始化)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        setupRecyclerView()
        setupFakeSearchBar()
        setupBackButton()

        // 2. 核心逻辑：如果是第一次进入页面，自动弹出搜索框！
        if (savedInstanceState == null) {
            startGoogleSearch()
        }
    }

    private fun setupBackButton() {
        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // 关闭当前页面，返回上一页 (Homepage)
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
        // 如果用户之前取消了搜索，现在想重新搜，点击这个伪搜索栏再次触发
        findViewById<TextView>(R.id.tvSearchInput).setOnClickListener {
            startGoogleSearch()
        }
    }

    // 3. 启动 Google 全屏搜索界面的方法
    private fun startGoogleSearch() {
        // 1. 重要：必须增加 Place.Field.TYPES 字段，否则拿不到地点类型
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES // <--- 新增这个
        )

        // 构建 Intent
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("MY")) // 限制马来西亚
            // 尝试过滤：虽然 Google 不保证 100% 只显示车站，但这会提高车站的优先级
            // 注意：Android SDK 对这里支持的过滤器有限，主要靠后面的“验证”步骤
            // .setTypesFilter(listOf("transit_station"))
            .build(this)

        autocompleteLauncher.launch(intent)
    }

    private val stationRepository = StationRepository()

    private fun handleSelectedPlace(place: Place) {
        val placeName = place.name ?: "Unknown"
        val lat = place.latLng?.latitude ?: 0.0
        val lng = place.latLng?.longitude ?: 0.0

        // --- 1. 验证逻辑 (保留你之前的过滤逻辑) ---
        val placeTypes = place.placeTypes ?: emptyList()
        val strictTransportTypes = setOf("transit_station", "bus_station", "train_station", "subway_station", "light_rail_station")
        val transportKeywords = listOf("mrt", "lrt", "ktm", "station", "stesen", "sentral", "terminal", "bus stop")

        val isValid = placeTypes.any { it in strictTransportTypes } ||
                transportKeywords.any { placeName.lowercase().contains(it) }

        if (isValid) {
            // ✅ 通过验证，它是车站

            // --- 2. 核心修改：去 Firebase (Repository) 查服务 ---
            // 使用协程在后台查询
            lifecycleScope.launch {
                // 弹个 Loading (可选)
                Toast.makeText(this@SearchActivity, "Checking services...", Toast.LENGTH_SHORT).show()

                // Step 2: 拿着名字去查服务
                val services = withContext(Dispatchers.IO) {
                    stationRepository.getServicesForStation(placeName)
                }

                if (services.isNotEmpty()) {
                    // --- 3. Step 3: 如果有服务，弹出 BottomSheet ---
                    val bottomSheet = ServiceSelectionBottomSheet(placeName, services) { selectedService ->

                        // --- 4. Step 4: 用户选了服务，跳转详情页 ---
                        val intent = Intent(this@SearchActivity, RouteDetailActivity::class.java)
                        intent.putExtra("dest_name", placeName)
                        intent.putExtra("dest_lat", lat)
                        intent.putExtra("dest_lng", lng)
                        intent.putExtra("service_name", selectedService.name)
                        startActivity(intent)

                        // 可选：是否还要保存历史记录？
                        val recent = RecentPlace(placeName, place.address ?: "", lat, lng)
                        historyManager.savePlace(recent)
                    }
                    bottomSheet.show(supportFragmentManager, "ServiceSelection")

                } else {
                    // 如果没查到服务 (比如是个冷门车站)，直接走旧逻辑：返回主页定位
                    val recent = RecentPlace(placeName, place.address ?: "", lat, lng)
                    historyManager.savePlace(recent)
                    returnResult(recent.name, recent.lat, recent.lng)
                }
            }

        } else {
            // ❌ 拦截
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
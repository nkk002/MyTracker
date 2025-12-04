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

    private fun handleSelectedPlace(place: Place) {
        // 1. 使用字符串定义我们接受的交通站点类型 (新版写法)
        // 这些是 Google Places API 标准的类型字符串
        val validTransportTypes = setOf(
            "transit_station",      // 公共交通站点 (核心)
            "bus_station",          // 巴士站
            "train_station",        // 火车站
            "subway_station",       // 地铁站
            "light_rail_station",   // 轻轨站
            "monorail_station",      // 单轨站 (KL Monorail 需要这个)
            "establishment",        // ⚠️ 这是一个万能标签，加上它几乎所有地点都能过
            "point_of_interest",    // ⚠️ 同上
            "route"                 // 有时候路线也会被搜出来
        )

        // 2. 获取用户选中地点的类型列表 (使用 placeTypes 替代 types)
        // 注意：place.placeTypes 返回的是 List<String>，所以不会报错
        val placeTypes = place.placeTypes
        android.util.Log.d("CheckPlace", "选中地点: ${place.name}")
        android.util.Log.d("CheckPlace", "拥有类型: $placeTypes")
        // 3. 检查：选中的地点是否包含任何一个我们接受的类型？
        val isValid = placeTypes?.any { it in validTransportTypes } == true

        if (isValid) {
            // --- 情况 A: 是车站 ---
            val recent = RecentPlace(
                name = place.name ?: "Unknown",
                address = place.address ?: "",
                lat = place.latLng?.latitude ?: 0.0,
                lng = place.latLng?.longitude ?: 0.0
            )
            historyManager.savePlace(recent)
            returnResult(recent.name, recent.lat, recent.lng)

        } else {
            // --- 情况 B: 是无关地点 ---
            // 提示用户只能选车站
            Toast.makeText(this, "Please select a valid Transport Station (Bus/MRT/LRT)", Toast.LENGTH_LONG).show()
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
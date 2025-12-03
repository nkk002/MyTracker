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

        // 2. 核心逻辑：如果是第一次进入页面，自动弹出搜索框！
        if (savedInstanceState == null) {
            startGoogleSearch()
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
        // 设置我们需要获取的字段
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)

        // 构建 Intent
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("MY")) // 限制搜索范围为马来西亚
            .build(this)

        autocompleteLauncher.launch(intent)
    }

    private fun handleSelectedPlace(place: Place) {
        // 保存到历史记录
        val recent = RecentPlace(
            name = place.name ?: "Unknown",
            address = place.address ?: "",
            lat = place.latLng?.latitude ?: 0.0,
            lng = place.latLng?.longitude ?: 0.0
        )
        historyManager.savePlace(recent)

        // 返回结果给 MainActivity
        returnResult(recent.name, recent.lat, recent.lng)
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
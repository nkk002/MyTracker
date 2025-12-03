package com.mmu.mytracker.ui.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.RecentPlace
import com.mmu.mytracker.ui.adapter.RecentSearchAdapter
import com.mmu.mytracker.utils.SearchHistoryManager

class SearchActivity : AppCompatActivity() {

    private lateinit var historyManager: SearchHistoryManager
    private lateinit var adapter: RecentSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        historyManager = SearchHistoryManager(this)

        // 1. 设置 Places
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        setupAutocomplete()

        // 2. 设置 RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerRecentSearches)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RecentSearchAdapter(historyManager.getHistory()) { clickedPlace ->
            // 点击历史记录，直接返回结果
            returnResult(clickedPlace.name, clickedPlace.lat, clickedPlace.lng)
        }
        recyclerView.adapter = adapter
    }

    private fun setupAutocomplete() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment_search)
                as? AutocompleteSupportFragment ?: return

        autocompleteFragment.setCountries(listOf("MY"))
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS))

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // 保存到历史记录
                val recent = RecentPlace(
                    name = place.name ?: "Unknown",
                    address = place.address ?: "",
                    lat = place.latLng?.latitude ?: 0.0,
                    lng = place.latLng?.longitude ?: 0.0
                )
                historyManager.savePlace(recent)

                // 返回结果
                returnResult(recent.name, recent.lat, recent.lng)
            }

            override fun onError(status: Status) {
                Toast.makeText(this@SearchActivity, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })
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
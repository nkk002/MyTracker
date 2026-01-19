package com.mmu.mytracker.ui.view.activity

import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.RecentPlace
import com.mmu.mytracker.data.model.Station
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.ui.adapter.RecentSearchAdapter
import com.mmu.mytracker.ui.view.fragment.ServiceSelectionBottomSheet
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

        adapter = RecentSearchAdapter(
            historyManager.getHistory().toMutableList(),
            onItemClick = { clickedPlace ->
                handleHistoryClick(clickedPlace)
            },
            onDeleteClick = { placeToDelete ->
                historyManager.removePlace(placeToDelete)
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

        lifecycleScope.launch {
            Toast.makeText(this@SearchActivity, "Finding stations near $googlePlaceName...", Toast.LENGTH_SHORT).show()

            val selectedLocation = Location("user_selected").apply {
                latitude = userLat
                longitude = userLng
            }

            val allStations = withContext(Dispatchers.IO) {
                stationRepository.getAllStations()
            }

            val nearbyStations = mutableListOf<Pair<Station, Float>>()
            val MATCH_THRESHOLD_METERS = 350f

            for (station in allStations) {
                val stationLocation = Location("firestore_station").apply {
                    latitude = station.latitude
                    longitude = station.longitude
                }
                val distance = selectedLocation.distanceTo(stationLocation)

                if (distance <= MATCH_THRESHOLD_METERS) {
                    nearbyStations.add(Pair(station, distance))
                }
            }

            nearbyStations.sortBy { it.second }

            if (nearbyStations.isNotEmpty()) {
                if (nearbyStations.size == 1) {
                    openStationOptions(nearbyStations[0].first)
                } else {
                    showStationChooserDialog(nearbyStations)
                }

                val recent = RecentPlace(googlePlaceName, place.address ?: "", userLat, userLng)
                historyManager.savePlace(recent)

            } else {
                Toast.makeText(this@SearchActivity, "No supported station found nearby (within 500m).", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStationChooserDialog(stations: List<Pair<Station, Float>>) {
        val bottomSheetDialog = BottomSheetDialog(this)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 32, 0, 32)
            background = androidx.core.content.ContextCompat.getDrawable(context, android.R.color.white)
        }

        val titleView = TextView(this).apply {
            text = "Select Station"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.BLACK)
            setPadding(48, 0, 48, 16)
        }
        container.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Multiple stations found nearby. Please choose the correct one:"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(48, 0, 48, 24)
        }
        container.addView(subtitleView)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = StationSelectionAdapter(stations) { selectedStation ->
                bottomSheetDialog.dismiss()
                openStationOptions(selectedStation)
            }
        }
        container.addView(recyclerView)

        bottomSheetDialog.setContentView(container)
        bottomSheetDialog.show()
    }

    private fun openStationOptions(station: Station) {
        val officialName = station.name
        val services = station.services

        if (services.isNotEmpty()) {
            val bottomSheet = ServiceSelectionBottomSheet(officialName, services) { selectedService ->
                val intent = Intent(this@SearchActivity, RouteDetailActivity::class.java)
                intent.putExtra("dest_name", officialName)
                intent.putExtra("dest_lat", station.latitude)
                intent.putExtra("dest_lng", station.longitude)
                intent.putExtra("service_name", selectedService.name)
                startActivity(intent)
            }
            bottomSheet.show(supportFragmentManager, "ServiceSelection")
        } else {
            Toast.makeText(this, "Station found but no services configured.", Toast.LENGTH_SHORT).show()
        }
    }
}

class StationSelectionAdapter(
    private val stations: List<Pair<Station, Float>>,
    private val onClick: (Station) -> Unit
) : RecyclerView.Adapter<StationSelectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStationName)
        val tvDist: TextView = view.findViewById(R.id.tvStationDist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_busstation_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (station, distance) = stations[position]

        holder.tvName.text = station.name
        holder.tvDist.text = String.format("📍 %.0fm away", distance)

        holder.itemView.setOnClickListener {
            onClick(station)
        }
    }

    override fun getItemCount() = stations.size
}
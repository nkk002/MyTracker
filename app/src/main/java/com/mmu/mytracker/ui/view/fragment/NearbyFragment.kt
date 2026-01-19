package com.mmu.mytracker.ui.view.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButtonToggleGroup
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.Station
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.ui.view.activity.RouteDetailActivity
import com.mmu.mytracker.ui.adapter.NearbyStationAdapter
import com.mmu.mytracker.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NearbyFragment : Fragment() {

    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val stationRepository = StationRepository()
    private lateinit var adapter: NearbyStationAdapter

    private var selectedType = "MRT"

    private var cachedAllStations: List<Station>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            refreshData(showLoading = true)
        } else {
            Toast.makeText(context, "Location needed for nearby stations", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_nearby, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleGroup = view.findViewById(R.id.toggleGroupTransport)
        recyclerView = view.findViewById(R.id.recyclerNearbyResults)
        progressBar = view.findViewById(R.id.progressBarNearby)

        setupRecyclerView()

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedType = if (checkedId == R.id.btnSelectMrt) "MRT" else "BUS"
                refreshData(showLoading = false)
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            refreshData(showLoading = true)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        startAutoRefreshLoop()
    }

    private fun startAutoRefreshLoop() {
        lifecycleScope.launch {
            while (isActive) {
                delay(30000)
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    refreshData(showLoading = false)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = NearbyStationAdapter(emptyList()) { station ->
            val intent = Intent(requireContext(), RouteDetailActivity::class.java)
            intent.putExtra("dest_name", station.name)
            intent.putExtra("dest_lat", station.latitude)
            intent.putExtra("dest_lng", station.longitude)

            val targetService = station.services.find { it.type.equals(selectedType, ignoreCase = true) }
            val exactServiceName = targetService?.name ?: "$selectedType Service"
            intent.putExtra("service_name", exactServiceName)

            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    @SuppressLint("MissingPermission")
    private fun refreshData(showLoading: Boolean) {
        if (showLoading) progressBar.visibility = View.VISIBLE

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                calculateNearbyStations(location)
            } else {
                if (showLoading) {
                    Toast.makeText(context, "Getting location...", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
        }
    }

    private fun calculateNearbyStations(userLocation: Location) {
        lifecycleScope.launch {
            try {
                val allStations = if (cachedAllStations != null) {
                    cachedAllStations!!
                } else {
                    val fetched = withContext(Dispatchers.IO) {
                        stationRepository.getAllStations()
                    }
                    cachedAllStations = fetched
                    fetched
                }

                val filteredList = allStations.filter { station ->
                    station.services.any { it.type.equals(selectedType, ignoreCase = true) }
                }
                    .map { station ->
                        val stationLoc = Location("").apply {
                            latitude = station.latitude
                            longitude = station.longitude
                        }
                        val distance = userLocation.distanceTo(stationLoc)

                        val matchingService = station.services.find { it.type.equals(selectedType, ignoreCase = true) }

                        val infoText = if (matchingService != null) {
                            val distKm = "%.2f km".format(distance / 1000)

                            val mins = TimeUtils.getMinutesUntilNextTrain(
                                matchingService.first_train,
                                matchingService.frequency_min,
                                matchingService.offset_min
                            )
                            val timeString = TimeUtils.formatTimeDisplay(mins)

                            "$distKm away • Next ${matchingService.type} : $timeString"
                        } else {
                            "Service available"
                        }

                        Triple(station, distance, infoText)
                    }
                    .sortedBy { it.second }
                    .take(4)

                val finalData = filteredList.map { Pair(it.first, it.third) }
                adapter.updateData(finalData)

            } catch (e: Exception) {
                if (progressBar.visibility == View.VISIBLE) {
                }
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
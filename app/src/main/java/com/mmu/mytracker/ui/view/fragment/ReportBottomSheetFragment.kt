package com.mmu.mytracker.ui.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.TransportRepository
import com.mmu.mytracker.data.remote.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportBottomSheetFragment : BottomSheetDialogFragment() {

    private val transportRepository = TransportRepository()
    private val stationRepository = StationRepository()

    private lateinit var spinnerLine: Spinner
    private lateinit var spinnerStation: Spinner
    private lateinit var radioGroup: RadioGroup
    private lateinit var etComment: EditText
    private lateinit var btnSubmit: Button
    private lateinit var etDelayTime: EditText

    private val lines = listOf("Select Line", "MRT Kajang Line", "MRT Putrajaya Line","Bus T460")

    private var allStationsCache: List<com.mmu.mytracker.data.model.Station> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerLine = view.findViewById(R.id.spinnerLine)
        spinnerStation = view.findViewById(R.id.spinnerStation)
        radioGroup = view.findViewById(R.id.radioGroupCrowd)
        etComment = view.findViewById(R.id.etComments)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)
        etDelayTime = view.findViewById(R.id.etDelayTime)

        setupLineSpinner()
        fetchAllStations()
        btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun setupLineSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLine.adapter = adapter

        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLine = lines[position]

                if (selectedLine != "Select Line") {
                    filterStationsByLine(selectedLine)
                } else {
                    resetStationSpinner()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun filterStationsByLine(selectedLine: String) {
        if (allStationsCache.isEmpty()) return

        val keyword = when (selectedLine) {
            "MRT Kajang Line" -> "Kajang"
            "MRT Putrajaya Line" -> "Putrajaya"
            "Bus T460" -> "T460"
            else -> ""
        }

        if (keyword.isEmpty()) return

        // 2. 筛选车站
        val filteredNames = allStationsCache.filter { station ->
            station.services.any { service ->
                service.name.contains(keyword, ignoreCase = true) ||
                        service.type.contains(keyword, ignoreCase = true)
            }
        }.map { it.name }.sorted()
        val finalStationList = mutableListOf("General (Whole Line)")
        finalStationList.addAll(filteredNames)

        updateStationSpinner(finalStationList)
    }

    private fun resetStationSpinner() {
        val defaultList = listOf("Select Line First")
        updateStationSpinner(defaultList)
    }

    private fun updateStationSpinner(data: List<String>) {
        if (!isAdded) return

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, data)
        adapter.setDropDownViewResource(R.layout.item_spinner_multiline)
        spinnerStation.adapter = adapter
    }

    private fun fetchAllStations() {
        lifecycleScope.launch {
            try {
                val stations = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }
                allStationsCache = stations

                val currentLine = spinnerLine.selectedItem.toString()
                if (currentLine != "Select Line") {
                    filterStationsByLine(currentLine)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun submitReport() {
        val line = spinnerLine.selectedItem.toString()
        val station = spinnerStation.selectedItem?.toString() ?: "General"

        if (line == "Select Line") {
            Toast.makeText(context, "Please select a transport line", Toast.LENGTH_SHORT).show()
            return
        }

        val crowdLevel = when (radioGroup.checkedRadioButtonId) {
            R.id.rbLow -> "Low"
            R.id.rbMedium -> "Medium"
            R.id.rbHigh -> "High"
            else -> "Medium"
        }

        val delay = etDelayTime.text.toString().ifEmpty { "0" }
        val comment = etComment.text.toString()

        lifecycleScope.launch {
            val success = transportRepository.submitReport(line, station, crowdLevel, delay, comment)
            if (success) {
                Toast.makeText(context, "Report submitted!", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
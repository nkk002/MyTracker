package com.mmu.mytracker.ui.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mmu.mytracker.R
import com.mmu.mytracker.data.remote.repository.StationRepository
import com.mmu.mytracker.data.remote.repository.TransportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportFragment : Fragment() {

    private val transportRepository = TransportRepository()
    private val stationRepository = StationRepository()

    private lateinit var spinnerLine: Spinner
    private lateinit var spinnerStation: Spinner
    private lateinit var radioGroup: RadioGroup
    private lateinit var etComment: EditText
    private lateinit var btnSubmit: Button
    private lateinit var etDelayTime: EditText

    private val lines = listOf("Select Line", "MRT Kajang Line", "MRT Putrajaya Line", "Bus T460")
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
        etDelayTime = view.findViewById(R.id.etDelayTime)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)

        setupSpinners()

        btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun setupSpinners() {
        val adapterLine = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lines)
        adapterLine.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLine.adapter = adapterLine

        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLine = lines[position]
                if (selectedLine != "Select Line") {
                    filterStationsByLine(selectedLine)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stations = stationRepository.getAllStations()
                withContext(Dispatchers.Main) {
                    allStationsCache = stations
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun filterStationsByLine(line: String) {
        val filteredStations = allStationsCache.filter {
            true
        }.map { it.name }

        val stationList = mutableListOf("Select Station")
        stationList.addAll(filteredStations)

        val adapterStation = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, stationList)
        adapterStation.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = adapterStation
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
                Toast.makeText(context, "Report submitted successfully!", Toast.LENGTH_SHORT).show()
                etComment.text.clear()
                etDelayTime.text.clear()
            } else {
                Toast.makeText(context, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
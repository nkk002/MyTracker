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
import com.mmu.mytracker.data.remote.repository.StationRepository //
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportBottomSheetFragment : BottomSheetDialogFragment() {

    private val transportRepository = TransportRepository()
    private val stationRepository = StationRepository() // 1. 新增 StationRepository

    private lateinit var spinnerLine: Spinner
    private lateinit var spinnerStation: Spinner // 2. 绑定新的 Spinner
    private lateinit var radioGroup: RadioGroup
    private lateinit var etComment: EditText
    private lateinit var btnSubmit: Button

    // 预设路线列表
    private val lines = listOf("Select Line","MRT Kajang Line", "MRT Putrajaya Line")

    // 缓存所有车站数据，避免每次选择都去下载
    private var allStationsCache: List<com.mmu.mytracker.data.model.Station> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false) //
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerLine = view.findViewById(R.id.spinnerLine)
        spinnerStation = view.findViewById(R.id.spinnerStation) // 绑定 XML 里的 ID
        radioGroup = view.findViewById(R.id.radioGroupCrowd)
        etComment = view.findViewById(R.id.etComments) // 注意 XML 里是 etComments
        btnSubmit = view.findViewById(R.id.btnSubmitReport)

        setupLineSpinner()

        // 3. 在后台预加载所有车站数据
        lifecycleScope.launch {
            try {
                allStationsCache = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun setupLineSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLine.adapter = adapter

        // 4. 监听 Line 选择，联动更新 Station
        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLine = lines[position]
                updateStationSpinner(selectedLine)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 5. 根据选中的 Line 筛选车站
    private fun updateStationSpinner(lineName: String) {
        val stationList = mutableListOf<String>()

        if (lineName == "Select Line") {
            stationList.add("Select Line First")
        } else {
            // 默认选项：整条线通用
            stationList.add("General (Whole Line)")

            // 筛选逻辑：找到包含该 Service 的车站
            val filtered = allStationsCache.filter { station ->
                station.services.any { service ->
                    // 模糊匹配：比如 "MRT Kajang Line" 包含 "MRT" 或者名字相符
                    lineName.contains(service.type, ignoreCase = true) ||
                            lineName.contains(service.name, ignoreCase = true) ||
                            service.name.contains(lineName, ignoreCase = true)
                }
            }.map { it.name }.sorted()

            stationList.addAll(filtered)
        }

        val stationAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, stationList)
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = stationAdapter
    }

    private fun submitReport() {
        val line = spinnerLine.selectedItem.toString()
        // 获取选中的车站，如果为空则默认为 General
        val station = spinnerStation.selectedItem?.toString() ?: "General (Whole Line)"

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

        val comment = etComment.text.toString()

        lifecycleScope.launch {
            // 6. 调用 Repository 提交，多传一个 station 参数
            val success = transportRepository.submitReport(line, station, crowdLevel, comment)
            if (success) {
                Toast.makeText(context, "Report submitted!", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Failed to submit", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
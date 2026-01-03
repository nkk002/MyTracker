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

    // 定义线路选项
    private val lines = listOf("Select Line", "MRT Kajang Line", "MRT Putrajaya Line","Bus T460")

    // 缓存所有车站数据
    private var allStationsCache: List<com.mmu.mytracker.data.model.Station> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 绑定 Views
        spinnerLine = view.findViewById(R.id.spinnerLine)
        spinnerStation = view.findViewById(R.id.spinnerStation)
        radioGroup = view.findViewById(R.id.radioGroupCrowd)
        etComment = view.findViewById(R.id.etComments)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)
        etDelayTime = view.findViewById(R.id.etDelayTime)

        // 2. 初始化 Line Spinner
        setupLineSpinner()

        // 3. 预加载车站数据 (这样用户点选时不用等)
        fetchAllStations()

        // 4. 提交按钮点击事件
        btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun setupLineSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLine.adapter = adapter

        // 监听 Line 选择事件
        spinnerLine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLine = lines[position]

                // 如果选了具体线路，就去过滤车站
                if (selectedLine != "Select Line") {
                    filterStationsByLine(selectedLine)
                } else {
                    // 如果选回了默认，清空或重置车站列表
                    resetStationSpinner()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 🔥 核心逻辑：根据选中的 Line 过滤 Station
    private fun filterStationsByLine(selectedLine: String) {
        if (allStationsCache.isEmpty()) return

        // 1. 确定过滤关键字 (简化匹配逻辑)
        val keyword = when (selectedLine) {
            "MRT Kajang Line" -> "Kajang"     // 只要服务名包含 Kajang
            "MRT Putrajaya Line" -> "Putrajaya" // 只要服务名包含 Putrajaya
            "Bus T460" -> "T460"
            else -> ""
        }

        if (keyword.isEmpty()) return

        // 2. 筛选车站
        val filteredNames = allStationsCache.filter { station ->
            // 检查该车站的 services 列表里，有没有名字包含关键字的
            station.services.any { service ->
                service.name.contains(keyword, ignoreCase = true) ||
                        service.type.contains(keyword, ignoreCase = true)
            }
        }.map { it.name }.sorted() // 提取名字并排序

        // 3. 添加一个默认选项 "General (Whole Line)"
        val finalStationList = mutableListOf("General (Whole Line)")
        finalStationList.addAll(filteredNames)

        // 4. 更新 Station Spinner
        updateStationSpinner(finalStationList)
    }

    private fun resetStationSpinner() {
        val defaultList = listOf("Select Line First")
        updateStationSpinner(defaultList)
    }

    private fun updateStationSpinner(data: List<String>) {
        // 确保 Fragment 还在才更新 UI
        if (!isAdded) return

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, data)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = adapter
    }

    private fun fetchAllStations() {
        lifecycleScope.launch {
            try {
                // 在后台线程加载数据
                val stations = withContext(Dispatchers.IO) {
                    stationRepository.getAllStations()
                }
                allStationsCache = stations

                // 数据加载完后，如果用户已经选了线路，立即刷新一次
                val currentLine = spinnerLine.selectedItem.toString()
                if (currentLine != "Select Line") {
                    filterStationsByLine(currentLine)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 如果失败，可以给 allStationsCache 一个空列表防止崩溃
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
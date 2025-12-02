package com.mmu.mytracker.ui.view.fragment

import com.mmu.mytracker.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmu.mytracker.data.remote.repository.TransportRepository
import kotlinx.coroutines.launch

class ReportBottomSheetFragment : BottomSheetDialogFragment() {

    private val repository = TransportRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 获取界面上的控件
        val spinnerLine = view.findViewById<Spinner>(R.id.spinnerTransportLine)
        val spinnerCrowd = view.findViewById<Spinner>(R.id.spinnerCrowdLevel)
        val etDelay = view.findViewById<EditText>(R.id.etDelayTime)
        val etComments = view.findViewById<EditText>(R.id.etComments)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitReport)

        // 2. 设置下拉菜单的数据 (Adapter)
        // 路线列表
        val lines = arrayOf("LRT Kelana Jaya", "MRT Kajang", "Bus 780", "Bus 300", "Monorail")
        val lineAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, lines)
        spinnerLine.adapter = lineAdapter

        // 拥挤程度列表
        val crowds = arrayOf("🟢 Low ", "🟡 Medium ", "🟠 High ", "🔴 Packed ")
        val crowdAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, crowds)
        spinnerCrowd.adapter = crowdAdapter

        // 3. 处理点击事件
        btnSubmit.setOnClickListener {
            // 获取用户输入的数据
            val selectedLine = spinnerLine.selectedItem.toString()
            val selectedCrowd = spinnerCrowd.selectedItem.toString()
            val delayText = etDelay.text.toString()
            val comment = etComments.text.toString()

            val delayMinutes = if (delayText.isNotEmpty()) delayText.toInt() else 0

            // 提交到 Firebase
            lifecycleScope.launch {
                btnSubmit.isEnabled = false // 防止重复点击

                val success = repository.submitReport(
                    transportLine = selectedLine,
                    crowdLevel = selectedCrowd,
                    delayMinutes = delayMinutes,
                    comment = comment
                )

                if (success) {
                    Toast.makeText(context, "Submit report successfully! Thanks for your contribution", Toast.LENGTH_SHORT).show()
                    dismiss() // 关闭弹窗
                } else {
                    Toast.makeText(context, "Submit report failed，Please check your internet connection", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                }
            }
        }
    }
}
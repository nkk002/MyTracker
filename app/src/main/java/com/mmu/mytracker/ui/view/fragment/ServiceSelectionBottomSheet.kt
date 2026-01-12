package com.mmu.mytracker.ui.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.StationService

class ServiceSelectionBottomSheet(
    private val stationName: String,
    private val services: List<StationService>,
    private val onServiceSelected: (StationService) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_service_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvStationTitle).text = stationName

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvServices)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // 使用简单的 Adapter
        recyclerView.adapter = ServiceAdapter(services) { service ->
            onServiceSelected(service)
            dismiss() // 选中后关闭弹窗
        }
    }

    // 内部 Adapter 类
    class ServiceAdapter(
        private val list: List<StationService>,
        private val onClick: (StationService) -> Unit
    ) : RecyclerView.Adapter<ServiceAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            // 🔥 修正：使用 R.id.text1 (对应 item_service_multiline.xml 里的 ID)
            // 之前写 android.R.id.text1 是错的，因为我们用了自定义 XML
            val text: TextView = v.findViewById(R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // 加载支持多行的布局
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_service_multiline, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]

            // 优化显示：如果有方向才显示括号，没有就不显示
            val directionInfo = if (item.direction.isNotEmpty()) " (${item.direction})" else ""

            // 例如: "🚆 Bus T460 (To Kajang)"
            holder.text.text = "🚆 ${item.name}$directionInfo"

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
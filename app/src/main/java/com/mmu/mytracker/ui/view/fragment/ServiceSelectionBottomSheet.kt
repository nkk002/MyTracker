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
        // 使用简单的 Adapter (这里写个内部类简化代码)
        recyclerView.adapter = ServiceAdapter(services) { service ->
            onServiceSelected(service)
            dismiss() // 选中后关闭弹窗
        }
    }

    // 简单的内部 Adapter
    class ServiceAdapter(
        private val list: List<StationService>,
        private val onClick: (StationService) -> Unit
    ) : RecyclerView.Adapter<ServiceAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(android.R.id.text1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.text.text = "🚆 ${item.name} (${item.direction})"
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = list.size
    }
}
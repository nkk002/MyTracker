package com.mmu.mytracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.Station
import com.mmu.mytracker.data.model.StationService

class NearbyStationAdapter(
    // 数据源：包含 Station 对象和计算好的提示文字 (e.g. "Next MRT in 5 mins")
    private var stations: List<Pair<Station, String>>,
    private val onClick: (Station) -> Unit
) : RecyclerView.Adapter<NearbyStationAdapter.ViewHolder>() {

    // 内部类：ViewHolder
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStationName)
        val tvInfo: TextView = view.findViewById(R.id.tvNextTrainInfo)
        val tvDistance: TextView = view.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 加载布局文件 (下面会提供这个 layout 代码)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_station, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (station, infoText) = stations[position]

        // 1. 设置车站名字
        holder.tvName.text = station.name

        // 2. 设置动态信息 (比如: "1.2km away • Next MRT: ~5 mins")
        // 这个 infoText 是我们在 Fragment 里计算好传进来的
        holder.tvInfo.text = infoText

        // 3. 设置右侧辅助文字 (可选，或者直接让 infoText 包含距离)
        holder.tvDistance.text = "View >"

        // 4. 点击事件
        holder.itemView.setOnClickListener {
            onClick(station)
        }
    }

    override fun getItemCount() = stations.size

    // 刷新数据的方法
    fun updateData(newData: List<Pair<Station, String>>) {
        stations = newData
        notifyDataSetChanged()
    }
}
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
    private var stations: List<Pair<Station, String>>,
    private val onClick: (Station) -> Unit
) : RecyclerView.Adapter<NearbyStationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStationName)
        val tvInfo: TextView = view.findViewById(R.id.tvNextTrainInfo)
        val tvDistance: TextView = view.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_station, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (station, infoText) = stations[position]

        holder.tvName.text = station.name

        holder.tvInfo.text = infoText

        holder.tvDistance.text = "View >"

        holder.itemView.setOnClickListener {
            onClick(station)
        }
    }

    override fun getItemCount() = stations.size

    fun updateData(newData: List<Pair<Station, String>>) {
        stations = newData
        notifyDataSetChanged()
    }
}
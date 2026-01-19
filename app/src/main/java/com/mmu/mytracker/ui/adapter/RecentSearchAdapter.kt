package com.mmu.mytracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.RecentPlace

class RecentSearchAdapter(
    private var historyList: MutableList<RecentPlace>,
    private val onItemClick: (RecentPlace) -> Unit,
    private val onDeleteClick: (RecentPlace) -> Unit
) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPlaceName)
        val tvAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = historyList[position]
        holder.tvName.text = place.name
        holder.tvAddress.text = place.address

        holder.itemView.setOnClickListener {
            onItemClick(place)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(place)
        }
    }

    override fun getItemCount() = historyList.size

    fun updateData(newList: List<RecentPlace>) {
        historyList = newList.toMutableList()
        notifyDataSetChanged()
    }
}
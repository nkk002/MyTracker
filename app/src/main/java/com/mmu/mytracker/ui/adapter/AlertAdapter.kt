package com.mmu.mytracker.ui.adapter

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmu.mytracker.R

class AlertAdapter(private var alerts: List<Map<String, Any>>) :
    RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvAlertTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvAlertMessage)
        val tvTime: TextView = view.findViewById(R.id.tvAlertTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert_card, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val report = alerts[position]

        val type = report["crowdLevel"] as? String ?: "Alert"
        val station = report["station"] as? String ?: "General"
        val comment = report["comment"] as? String ?: ""
        val delay = report["delayTime"] as? String ?: "0"
        val timestamp = report["timestamp"] as? Long ?: System.currentTimeMillis()

        val displayStation = if (station.contains("General")) "Whole Line" else station
        holder.tvTitle.text = "⚠️ $type ($displayStation)"

        val diffMillis = System.currentTimeMillis() - timestamp
        val minsAgo = diffMillis / (1000 * 60)
        holder.tvTime.text = if (minsAgo < 1) "Now" else "${minsAgo}m ago"

        if (delay != "0") {
            val builder = SpannableStringBuilder()
            builder.append(comment)
            builder.append("\n")
            val start = builder.length
            builder.append("\n(Estimate Delay Time: +$delay mins)")
            builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.tvMessage.text = builder
        } else {
            holder.tvMessage.text = comment
        }
    }

    override fun getItemCount() = alerts.size

    fun updateList(newAlerts: List<Map<String, Any>>) {
        alerts = newAlerts
        notifyDataSetChanged()
    }
}
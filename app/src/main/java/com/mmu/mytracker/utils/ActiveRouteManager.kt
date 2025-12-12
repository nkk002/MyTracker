package com.mmu.mytracker.utils

import android.content.Context
import android.content.SharedPreferences

object ActiveRouteManager {
    private const val PREF_NAME = "active_route_pref"
    private const val KEY_DEST_NAME = "dest_name"
    private const val KEY_SERVICE_NAME = "service_name"
    private const val KEY_LAT = "dest_lat"
    private const val KEY_LNG = "dest_lng"

    // 保存路线
    fun saveRoute(context: Context, destName: String, serviceName: String, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DEST_NAME, destName)
            putString(KEY_SERVICE_NAME, serviceName)
            putString(KEY_LAT, lat.toString())
            putString(KEY_LNG, lng.toString())
            apply()
        }
    }

    // 获取当前路线 (如果没有则返回 null)
    fun getRoute(context: Context): Map<String, Any>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_DEST_NAME, null) ?: return null
        val service = prefs.getString(KEY_SERVICE_NAME, "") ?: ""
        val lat = prefs.getString(KEY_LAT, "0.0")?.toDoubleOrNull() ?: 0.0
        val lng = prefs.getString(KEY_LNG, "0.0")?.toDoubleOrNull() ?: 0.0

        return mapOf(
            "destName" to name,
            "serviceName" to service,
            "destLat" to lat,
            "destLng" to lng
        )
    }

    // 删除路线 (点击打叉时调用)
    fun clearRoute(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
package com.mmu.mytracker.utils

import android.content.Context
import android.content.SharedPreferences

object ActiveRouteManager {
    private const val PREF_NAME = "active_route_pref"
    private const val KEY_DEST_NAME = "dest_name"
    private const val KEY_SERVICE_NAME = "service_name"
    private const val KEY_LAT = "dest_lat"
    private const val KEY_LNG = "dest_lng"

    // 🔥 新增：用来存发车时间的 Key
    private const val KEY_DEPARTURE_TIME = "departure_time"

    // 保存路线基本信息
    fun saveRoute(context: Context, destName: String, serviceName: String, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DEST_NAME, destName)
            putString(KEY_SERVICE_NAME, serviceName)
            putString(KEY_LAT, lat.toString())
            putString(KEY_LNG, lng.toString())
            // 每次保存新路线时，把旧的时间清空，以免混淆
            putLong(KEY_DEPARTURE_TIME, 0L)
            apply()
        }
    }

    // 🔥 新增：单独保存发车时间
    fun saveDepartureTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DEPARTURE_TIME, time).apply()
    }

    // 🔥 新增：获取发车时间
    fun getDepartureTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_DEPARTURE_TIME, 0L)
    }

    // 获取当前路线
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

    // 删除路线
    fun clearRoute(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
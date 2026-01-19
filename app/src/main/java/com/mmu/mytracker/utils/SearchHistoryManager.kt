package com.mmu.mytracker.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmu.mytracker.data.model.RecentPlace

class SearchHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePlace(place: RecentPlace) {
        val history = getHistory().toMutableList()
        history.removeAll { it.name == place.name }
        history.add(0, place)

        if (history.size > 10) {
            history.removeAt(history.lastIndex)
        }

        prefs.edit().putString("history_list", gson.toJson(history)).apply()
    }

    fun removePlace(placeToRemove: RecentPlace) {
        val currentList = getHistory().toMutableList()
        currentList.removeAll { it.name == placeToRemove.name && it.address == placeToRemove.address }

        saveList(currentList)
    }

    private fun saveList(list: List<RecentPlace>) {
        val json = gson.toJson(list)
        prefs.edit().putString("history_list", json).apply()
    }

    fun getHistory(): List<RecentPlace> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        val type = object : TypeToken<List<RecentPlace>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearHistory() {
        prefs.edit().clear().apply()
    }
}
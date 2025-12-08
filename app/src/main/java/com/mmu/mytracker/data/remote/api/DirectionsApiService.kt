package com.mmu.mytracker.data.remote.api

import com.mmu.mytracker.data.model.DirectionsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {

    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "transit",
        // --- 新增下面这行：强制指定交通工具类型 ---
        // subway = MRT/LRT/Subway
        // train = KTM/Intercity (我们要避开这个)
        // bus = 巴士 (我们要避开这个)
        @Query("transit_mode") transitMode: String = "subway"
    ): Response<DirectionsResponse>
}